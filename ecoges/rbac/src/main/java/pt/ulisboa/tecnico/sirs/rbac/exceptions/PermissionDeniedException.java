package pt.ulisboa.tecnico.sirs.rbac.exceptions;

public class PermissionDeniedException extends Exception {

    private static final long serialVersionUID = 1L;

    public PermissionDeniedException(String role) {
        super("Permission denied for role '" + role + "'.");
    }
}
