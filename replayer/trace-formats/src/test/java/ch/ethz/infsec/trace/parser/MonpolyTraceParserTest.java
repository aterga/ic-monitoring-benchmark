package ch.ethz.infsec.trace.parser;

import ch.ethz.infsec.monitor.CustomSignature;
import ch.ethz.infsec.monitor.DataType;
import ch.ethz.infsec.monitor.Fact;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

import static org.junit.Assert.*;

public class MonpolyTraceParserTest {
    private ArrayList<Fact> sink;
    private MonpolyTraceParser parser;

    @Before
    public void setUp() {
        sink = new ArrayList<>();
        HashMap<String, List<DataType>> signature = new HashMap<>();
        signature.put("a", Collections.emptyList());
        signature.put("b", Collections.emptyList());
        signature.put("abc", Collections.emptyList());
        signature.put("def1", Collections.singletonList(DataType.STRING));
        signature.put("def2", Arrays.asList(DataType.STRING, DataType.STRING));
        signature.put("g", Arrays.asList(DataType.STRING, DataType.INTEGRAL));
        parser = new MonpolyTraceParser(new CustomSignature(signature));
    }

    @Test
    public void testSuccessfulParse() throws Exception {
        parser.parse(sink::add, "@123 @ 456 a b () \n;");
        parser.parse(sink::add, "@ 456 abc()() def1(123");
        parser.parse(sink::add, ")\n\n(foo) def2([foo],\"(bar\\\")\") ( a1 , \" 2b \")\r\n @789");
        parser.endOfInput(sink::add);

        assertEquals(Arrays.asList(
                Fact.terminator(123L),
                Fact.make("b", 456L),
                Fact.terminator(456L),
                Fact.make("abc", 456L),
                Fact.make("abc", 456L),
                Fact.make("def1", 456L, "123"),
                Fact.make("def1", 456L, "foo"),
                Fact.make("def2", 456L, "[foo]", "(bar\\\")"),
                Fact.make("def2", 456L, "a1", " 2b "),
                Fact.terminator(456L),
                Fact.terminator(789L)
        ), sink);

        sink.clear();
        parser.parse(sink::add, "@123 g (b,7)(c,-8) @456");
        assertEquals(Arrays.asList(
                Fact.make("g", 123L, "b", 7L),
                Fact.make("g", 123L, "c", -8L),
                Fact.terminator(123L)
        ), sink);
    }

    @Test
    public void testCommand() throws Exception {
        parser.parse(sink::add, ">foo<\n@1 a()");
        parser.endOfInput(sink::add);
        assertEquals(Arrays.asList(
                Fact.meta("foo"),
                Fact.make("a", 1L),
                Fact.terminator(1L)
        ), sink);

        sink.clear();
        parser.parse(sink::add, "@1 def1(q) >foo \"bar <\" 123<");
        assertEquals(Arrays.asList(
                Fact.make("def1", 1L, "q"),
                Fact.terminator(1L),
                Fact.meta("foo", "bar <", "123")
        ), sink);
    }

    @Test
    public void testDatabaseTerminator() throws Exception {
        parser.parse(sink::add, "@123 a();");
        assertEquals(Arrays.asList(
                Fact.make("a", 123L),
                Fact.terminator(123L)
        ), sink);
    }

    private void assertParseFailure(String input) {
        try {
            parser.parse(sink::add, input);
            parser.endOfInput(sink::add);
            fail("expected a ParseException");
        } catch (ParseException ignored) {
        }
        assertTrue(sink.isEmpty());
    }

    @Test
    public void testParseFailure() throws Exception {
        assertParseFailure("foo");
        assertParseFailure("@ @");
        assertParseFailure("@123 def2(,)");
        assertParseFailure("@123 def1(bar)(");

        parser.parse(sink::add, "@123 def2 (b,c)(d,e) @456");
        assertEquals(Arrays.asList(
                Fact.make("def2", 123L, "b", "c"),
                Fact.make("def2", 123L, "d", "e"),
                Fact.terminator(123L)
        ), sink);
    }

    @Test
    public void testSerialization() throws Exception {
        parser.parse(sink::add, "@123 def2 (b,c)(d,e");
        sink.clear();

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ObjectOutputStream objectOut = new ObjectOutputStream(out);
        objectOut.writeObject(parser);

        final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        final ObjectInputStream objectIn = new ObjectInputStream(in);
        parser = (MonpolyTraceParser) objectIn.readObject();

        parser.parse(sink::add, "f) @456");
        parser.endOfInput(sink::add);
        assertEquals(Arrays.asList(
                Fact.make("def2", 123L, "b", "c"),
                Fact.make("def2", 123L, "d", "ef"),
                Fact.terminator(123L),
                Fact.terminator(456L)
        ), sink);
    }

    @Test
    public void testComment() throws Exception {
        parser.parse(sink::add, "# a comment that never ends");
        parser.endOfInput(sink::add);
        parser.parseLine(sink::add, "# ignore me");
        parser.parseLine(sink::add, "@1 def1(2) #(3)");
        parser.endOfInput(sink::add);

        assertEquals(Arrays.asList(
                Fact.make("def1", 1L, "2"),
                Fact.terminator(1L)
        ), sink);
    }
}
