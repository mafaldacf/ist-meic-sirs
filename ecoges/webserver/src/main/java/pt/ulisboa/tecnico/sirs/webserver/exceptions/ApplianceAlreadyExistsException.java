package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class ApplianceAlreadyExistsException extends Exception {

    private static final long serialVersionUID = 1L;

    public ApplianceAlreadyExistsException(String name, String brand) {
        super("Appliance '" + name + "' from brand '" + brand + "' is already registered.");
    }
}
