package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class No2FAException extends Exception {

    private static final long serialVersionUID = 1L;

    public No2FAException() {
        super("User has no two factor authentication.");
    }
    
}
