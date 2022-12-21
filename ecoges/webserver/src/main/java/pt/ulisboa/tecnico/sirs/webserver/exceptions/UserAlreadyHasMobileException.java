package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class UserAlreadyHasMobileException extends Exception {

    private static final long serialVersionUID = 1L;

    public UserAlreadyHasMobileException() {
        super("Selected user already has a mobile device registered");
    }
    
}
