package ch.ethz.infsec.trace.parser;

import ch.ethz.infsec.monitor.Fact;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class JsonTraceParser implements TraceParser {
    private static final long serialVersionUID = 1863404104242214316L;

    public static final String ROOT = "root";
    public static final String OBJECT_FIELD = "key";
    public static final String ARRAY_ELEMENT = "idx";
    public static final String INT = "int";
    public static final String STRING = "str";
    public static final String TRUE = "true";
    public static final String NULL = "null";

    private final List<String> timestampPath;

    // These are used to enforce disjoint IDs across parallel traces.
    private int traceId;
    private int numTraces;

    private long nextId;
    private long currentTimepoint;

    private transient JsonFactory jsonFactory;
    private transient String currentInput;
    private transient JsonParser jsonParser;
    private transient ArrayList<Fact> factBuffer;
    private transient int nestingLevel;
    private transient int timestampPrefix;
    private transient String rawTimestamp;

    public JsonTraceParser(List<String> timestampPath) {
        this.timestampPath = timestampPath;
        this.traceId = 0;
        this.numTraces = 1;
        this.nextId = traceId;
        this.currentTimepoint = 0;
    }

    private void beginParse(String input) throws IOException {
        if (jsonFactory == null) {
            jsonFactory = new JsonFactoryBuilder().build();
            factBuffer = new ArrayList<>(1024);
        }
        currentInput = input;
        jsonParser = jsonFactory.createParser(input);
        factBuffer.clear();
        nestingLevel = 0;
        timestampPrefix = 0;
        rawTimestamp = null;
    }

    private void fail() throws ParseException {
        throw new ParseException(currentInput);
    }

    private boolean atTimestamp() {
        return timestampPrefix == nestingLevel
                && timestampPrefix == timestampPath.size()
                && !timestampPath.isEmpty();
    }

    private boolean onTimestampPath() {
        return timestampPrefix == nestingLevel - 1
                && timestampPrefix < timestampPath.size();
    }

    private Long allocateId() {
        Long id = nextId;
        nextId += numTraces;
        return id;
    }

    private Long createInt(long value) {
        Long valueId = allocateId();
        factBuffer.add(Fact.make(INT, 0, valueId, value));
        return valueId;
    }

    private Long createString(String value) {
        Long valueId = allocateId();
        factBuffer.add(Fact.make(STRING, 0,valueId, value));
        return valueId;
    }

    private Long createFloat(String value) {
        // TODO(JS): Support floating-point numbers.
        return createString(value);
    }

    private Long createBoolean(boolean value) {
        Long valueId = allocateId();
        if (value) {
            factBuffer.add(Fact.make(TRUE, 0, valueId));
        }
        return valueId;
    }

    private Long createNull() {
        Long valueId = allocateId();
        factBuffer.add(Fact.make(NULL, 0, valueId));
        return valueId;
    }

    private Long parseValue(boolean arrayElement) throws IOException, ParseException {
        JsonToken token = jsonParser.nextToken();
        if (token == null) {
            return null;
        }
        switch (token) {
            case START_OBJECT:
                return parseObject();
            case START_ARRAY:
                return parseArray();
            case END_ARRAY:
                if (!arrayElement) {
                    fail();
                }
                return null;
            case VALUE_NUMBER_INT:
                return createInt(jsonParser.getLongValue());
            case VALUE_NUMBER_FLOAT:
                // TODO(JS): Support floating-point numbers.
                return createFloat(jsonParser.getText());
            case VALUE_STRING:
                if (atTimestamp()) {
                    rawTimestamp = jsonParser.getText();
                }
                return createString(jsonParser.getText());
            case VALUE_TRUE:
                return createBoolean(true);
            case VALUE_FALSE:
                return createBoolean(false);
            case VALUE_NULL:
                return createNull();
            default:
                fail();
        }
        return null;
    }

    private Long parseObject() throws IOException, ParseException {
        Long objectId = allocateId();
        ++nestingLevel;
        String nextTimestampSegment = onTimestampPath() ? timestampPath.get(timestampPrefix) : null;
        for (;;) {
            JsonToken token = jsonParser.nextToken();
            if (token == JsonToken.FIELD_NAME) {
                String fieldName = jsonParser.getText();
                boolean isTimestampSegment = fieldName.equals(nextTimestampSegment);
                if (isTimestampSegment) {
                    ++timestampPrefix;
                }
                Long valueId = parseValue(false);
                if (isTimestampSegment) {
                    --timestampPrefix;
                }
                factBuffer.add(Fact.make(OBJECT_FIELD, 0, objectId, fieldName, valueId));
            } else if (token == JsonToken.END_OBJECT) {
                break;
            } else {
                fail();
            }
        }
        --nestingLevel;
        return objectId;
    }

    private Long parseArray() throws IOException, ParseException {
        Long arrayId = allocateId();
        ++nestingLevel;
        for (long i = 0; ; ++i) {
            Long valueId = parseValue(true);
            if (valueId == null) {
                break;
            }
            factBuffer.add(Fact.make(ARRAY_ELEMENT, 0, arrayId, i, valueId));
        }
        --nestingLevel;
        return arrayId;
    }

    private void endParse(Consumer<Fact> sink) throws ParseException {
        long timestamp = 0;
        if (rawTimestamp != null) {
            try {
                timestamp = Instant.parse(rawTimestamp).toEpochMilli();
            } catch (DateTimeParseException e) {
                fail();
            }
        }
        for (Fact fact : factBuffer) {
            fact.setTimestamp(timestamp);
            fact.setTimepoint(currentTimepoint);
            sink.accept(fact);
        }
        factBuffer.clear();
        ++currentTimepoint;
    }

    @Override
    public void parseLine(Consumer<Fact> sink, String line) throws ParseException {
        try {
            beginParse(line);
            Long rootId = parseValue(false);
            if (rootId != null) {
                factBuffer.add(Fact.make(ROOT, 0, rootId));
                factBuffer.add(Fact.terminator(0));
            }
            endParse(sink);
        } catch (IOException e) {
            fail();
        }
    }

    @Override
    public void endOfInput(Consumer<Fact> sink) throws ParseException {
        // ignore
    }

    @Override
    public void setTerminatorMode(TerminatorMode mode) {
        // ignore
    }

    /**
     * Warning: resets the trace-local object ID allocation!
     *
     * @param id
     * @param numTraces
     */
    @Override
    public void setTraceId(int id, int numTraces) {
        if (numTraces < 1) {
            throw new IllegalArgumentException("numTraces");
        }
        this.traceId = id;
        this.numTraces = numTraces;
        this.nextId = id;
    }
}
