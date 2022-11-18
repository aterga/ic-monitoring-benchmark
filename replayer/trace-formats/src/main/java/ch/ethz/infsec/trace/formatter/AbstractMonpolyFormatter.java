package ch.ethz.infsec.trace.formatter;

import java.io.Serializable;

abstract class AbstractMonpolyFormatter implements Serializable {
    private static final long serialVersionUID = -1321702340607633180L;

    protected boolean lazyQuotes = true;
    StringBuilder builder = new StringBuilder();

    private boolean isSimpleStringChar(char c) {
        return (c >= '0' && c <= '9') ||
                (c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                c == '_' || c == '[' || c == ']' || c == '/' ||
                c == ':' || c == '-' || c == '.' || c == '!';
    }

    private boolean isSimpleString(String value) {
        for (int i = 0; i < value.length(); ++i) {
            if (!isSimpleStringChar(value.charAt(i))) {
                return false;
            }
        }
        return value.length()!=0;
    }

    private void printQuotedString(String value, StringBuilder builder) {
        builder.append('"');
        boolean afterBackslash = false;
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (afterBackslash) {
                builder.append(c);
                afterBackslash = false;
            } else if (c == '\\') {
                builder.append(c);
                afterBackslash = true;
            } else if (c == '"') {
                builder.append("\\\"");
            } else {
                builder.append(c);
            }
        }
        if (afterBackslash) {
            builder.append('\\');
        }
        builder.append('"');
    }

    private void printString(String value, StringBuilder builder) {
        if (isSimpleString(value)) {
            builder.append(value);
        } else {
            printQuotedString(value, builder);
        }
    }

    void printArgument(Object value, StringBuilder builder) {
        if (lazyQuotes) {
            printString(value.toString(), builder);
        } else {
            if (value instanceof String) {
                printQuotedString((String)value, builder);
            } else {
                builder.append(value);
            }
        }
    }

    public void setLazyQuotes(boolean lazyQuotes) {
        this.lazyQuotes = lazyQuotes;
    }
}
