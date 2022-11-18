package ch.ethz.infsec.trace.parser;

import ch.ethz.infsec.monitor.Fact;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsonTraceParserTest {
    private ArrayList<Fact> sink;
    private JsonTraceParser parser;

    @Before
    public void setUp() {
        sink = new ArrayList<>();
        parser = new JsonTraceParser(Arrays.asList("time", "stamp"));
    }

    @Test
    public void testSuccessfulParse() throws Exception {
        parser.parseLine(sink::add, "");
        assertTrue(sink.isEmpty());

        parser.parseLine(sink::add, "\n");
        assertTrue(sink.isEmpty());

        parser.parseLine(sink::add, "  \t \r\n");
        assertTrue(sink.isEmpty());

        parser.parseLine(sink::add, "123");
        assertEquals(Arrays.asList(
                Fact.make("int", 0L, 0L, 123L),
                Fact.make("root", 0L, 0L),
                Fact.terminator(0L)
        ), sink);

        sink.clear();
        parser.parseLine(sink::add, "{\"time\":123,\"x\":\"Hello\"}");
        assertEquals(Arrays.asList(
                Fact.make("int", 0L, 2L, 123L),
                Fact.make("key", 0L, 1L, "time", 2L),
                Fact.make("str", 0L, 3L, "Hello"),
                Fact.make("key", 0L, 1L, "x", 3L),
                Fact.make("root", 0L, 1L),
                Fact.terminator(0L)
        ), sink);

        sink.clear();
        parser.parseLine(sink::add, "{\"time\":{\"stamp\":\"1970-01-01T00:00:00.456Z\",\"x\":123}}");
        assertEquals(Arrays.asList(
                Fact.make("str", 456L, 6L, "1970-01-01T00:00:00.456Z"),
                Fact.make("key", 456L, 5L, "stamp", 6L),
                Fact.make("int", 456L, 7L, 123L),
                Fact.make("key", 456L, 5L, "x", 7L),
                Fact.make("key", 456L, 4L, "time", 5L),
                Fact.make("root", 456L, 4L),
                Fact.terminator(456L)
        ), sink);

        sink.clear();
        parser.parseLine(sink::add, "{\"time\":[{\"stamp\":\"1970-01-01T00:00:00.456Z\"},123]}");
        assertEquals(Arrays.asList(
                Fact.make("str", 0L, 11L, "1970-01-01T00:00:00.456Z"),
                Fact.make("key", 0L, 10L, "stamp", 11L),
                Fact.make("idx", 0L, 9L, 0L, 10L),
                Fact.make("int", 0L, 12L, 123L),
                Fact.make("idx", 0L, 9L, 1L, 12L),
                Fact.make("key", 0L, 8L, "time", 9L),
                Fact.make("root", 0L, 8L),
                Fact.terminator(0L)
        ), sink);

        sink.clear();
        parser.endOfInput(sink::add);
        assertTrue(sink.isEmpty());
    }

    @Test
    public void testDisjointIds() throws Exception {
        JsonTraceParser parser2 = new JsonTraceParser(Collections.singletonList("time"));
        parser.setTraceId(0, 2);
        parser2.setTraceId(1, 2);

        parser.parseLine(sink::add, "{\"foo\":123}");
        parser2.parseLine(sink::add, "{\"foo\":123}");

        assertEquals(Arrays.asList(
                Fact.make("int", 0L, 2L, 123L),
                Fact.make("key", 0L, 0L, "foo", 2L),
                Fact.make("root", 0L, 0L),
                Fact.terminator(0L),
                Fact.make("int", 0L, 3L, 123L),
                Fact.make("key", 0L, 1L, "foo", 3L),
                Fact.make("root", 0L, 1L),
                Fact.terminator(0L)
        ), sink);
    }
}
