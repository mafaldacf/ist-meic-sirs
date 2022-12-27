package pt.ulisboa.tecnico.sirs.rbac.exceptions;

public class InvalidRoleException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidRoleException(String role) {
        super("Invalid role '" + role + "'.");
    }
}
