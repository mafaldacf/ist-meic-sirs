package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class HelloException extends Exception {

    private static final long serialVersionUID = 1L;

    public HelloException() {
        super("Invalid name.");
    }

    public HelloException(String message) {
        super(message);
    }
}
