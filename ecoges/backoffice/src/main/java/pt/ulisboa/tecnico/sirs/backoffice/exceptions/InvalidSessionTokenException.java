package pt.ulisboa.tecnico.sirs.backoffice.exceptions;

public class InvalidSessionTokenException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidSessionTokenException() {
        super("Invalid session token.");
    }
}
