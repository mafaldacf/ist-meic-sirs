package pt.ulisboa.tecnico.sirs.crypto.exceptions;

public class WeakPasswordException extends Exception {

    private static final long serialVersionUID = 1L;

    public WeakPasswordException() {
        super("The entered password is not strong (10 to 30 characters and at least: 1 uppercase, 1 lowercase, 1 digit and 1 special character)");
    }
}
