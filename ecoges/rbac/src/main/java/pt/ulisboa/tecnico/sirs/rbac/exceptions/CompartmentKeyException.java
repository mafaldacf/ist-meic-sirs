package pt.ulisboa.tecnico.sirs.rbac.exceptions;

public class CompartmentKeyException extends Exception {

    private static final long serialVersionUID = 1L;

    public CompartmentKeyException() {
        super("Could not load compartment key.");
    }
}
