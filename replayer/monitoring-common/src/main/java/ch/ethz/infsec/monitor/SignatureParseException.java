package ch.ethz.infsec.monitor;

public class SignatureParseException extends RuntimeException {
    private static final long serialVersionUID = 9215846246749220515L;

    public SignatureParseException(String message) {
        super(message);
    }
}
