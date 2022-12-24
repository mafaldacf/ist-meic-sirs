package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class InvalidHashException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidHashException() {
        super("Invalid Hash. Message was tampered with!");
    }

    public InvalidHashException(String message) {
        super("Could not validate hash: " + message);
    }
}
