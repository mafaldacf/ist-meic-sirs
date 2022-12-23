package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class InvalidSignatureException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidSignatureException() {
        super("Invalid Signature.");
    }

    public InvalidSignatureException(String message) {
        super("Could not validate signature: " + message);
    }
}
