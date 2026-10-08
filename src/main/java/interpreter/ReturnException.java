package interpreter;

public class ReturnException extends RuntimeException {
    public final Object value;

    public ReturnException(Object value) {
        super(null, null, false, false);  // no message, no cause, no suppression, no stack trace
        this.value = value;
    }
}