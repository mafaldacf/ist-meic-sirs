package pt.ulisboa.tecnico.sirs.rbac.exceptions;

public class AdminDoesNotExistException extends Exception {

    private static final long serialVersionUID = 1L;

    public AdminDoesNotExistException(String username) {
        super("Admin with username '" + username + "' does not exist.");
    }
}
