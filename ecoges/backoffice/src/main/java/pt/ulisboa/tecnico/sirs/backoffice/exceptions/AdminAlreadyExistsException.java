package pt.ulisboa.tecnico.sirs.backoffice.exceptions;

public class AdminAlreadyExistsException extends Exception {

    private static final long serialVersionUID = 1L;

    public AdminAlreadyExistsException(String username) {
        super("Admin with username '" + username + "' already exists.");
    }
}
