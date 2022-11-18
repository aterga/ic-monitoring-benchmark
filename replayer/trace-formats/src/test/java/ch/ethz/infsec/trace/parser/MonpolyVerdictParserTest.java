package ch.ethz.infsec.trace.parser;

import ch.ethz.infsec.monitor.DataType;
import ch.ethz.infsec.monitor.Fact;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class MonpolyVerdictParserTest {
    private ArrayList<Fact> sink;
    private MonpolyVerdictParser parser;

    @Before
    public void setUp() {
        sink = new ArrayList<>();
    }

    @Test
    public void testSuccessfulParse1() throws Exception {
        parser = new MonpolyVerdictParser(Collections.emptyList());
        parser.parseLine(sink::add, "@123 (time point 1001): true\n");
        assertEquals(Arrays.asList(
                Fact.make("", 123L),
                Fact.terminator(123L)
        ), sink);
        assertEquals(sink.get(0).getTimepoint(), 1001L);
        assertEquals(sink.get(1).getTimepoint(), 1001L);
    }

    @Test
    public void testSuccessfulParse2() throws Exception {
        parser = new MonpolyVerdictParser(Collections.singletonList(DataType.STRING));
        parser.parseLine(sink::add, "@456 (time point 1002): (xyz)");
        assertEquals(Arrays.asList(
                Fact.make("", 456L, "xyz"),
                Fact.terminator(456L)
        ), sink);
        assertEquals(sink.get(0).getTimepoint(), 1002L);
        assertEquals(sink.get(1).getTimepoint(), 1002L);
    }

    @Test
    public void testSuccessfulParse3() throws Exception {
        parser = new MonpolyVerdictParser(Arrays.asList(DataType.INTEGRAL, DataType.STRING));
        parser.parseLine(sink::add, "@789 (time point 1003): (42,\"bar\") (-42,1b)");
        parser.endOfInput(sink::add);
        assertEquals(Arrays.asList(
                Fact.make("", 789L, 42L, "bar"),
                Fact.make("", 789L, -42L, "1b"),
                Fact.terminator(789L)
        ), sink);
        assertEquals(sink.get(0).getTimepoint(), 1003L);
        assertEquals(sink.get(1).getTimepoint(), 1003L);
        assertEquals(sink.get(2).getTimepoint(), 1003L);
    }

    private void assertParseFailure(String input) {
        try {
            parser.parseLine(sink::add, input);
            parser.endOfInput(sink::add);
            fail("expected a ParseException");
        } catch (ParseException ignored) {
        }
        assertTrue(sink.isEmpty());
    }

    @Test
    public void testParseFailure() throws Exception {
        parser = new MonpolyVerdictParser(Collections.singletonList(DataType.STRING));
        assertParseFailure("foo");
        assertParseFailure("@123 foo (bar)");
        assertParseFailure("@123 (time point 1001): xyz");

        parser.parseLine(sink::add, "@123 (time point 1001): (xyz)\n");
        assertEquals(Arrays.asList(
                Fact.make("", 123L, "xyz"),
                Fact.terminator(123L)
        ), sink);
    }

    @Test
    public void testSerialization() throws Exception {
        parser = new MonpolyVerdictParser(Collections.singletonList(DataType.STRING));
        parser.parseLine(sink::add, "@123 (time point 1001): (xyz)\n");
        sink.clear();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ObjectOutputStream objectOut = new ObjectOutputStream(out);
        objectOut.writeObject(parser);

        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        final ObjectInputStream objectIn = new ObjectInputStream(in);
        parser = (MonpolyVerdictParser) objectIn.readObject();

        parser.parseLine(sink::add, "@123 (time point 1002): (a)\n");
        parser.endOfInput(sink::add);
        assertEquals(Arrays.asList(
                Fact.make("", 123L, "a"),
                Fact.terminator(123L)
        ), sink);
    }
}
