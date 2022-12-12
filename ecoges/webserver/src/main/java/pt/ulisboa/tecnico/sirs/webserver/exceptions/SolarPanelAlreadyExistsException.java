package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class SolarPanelAlreadyExistsException extends Exception {

    private static final long serialVersionUID = 1L;

    public SolarPanelAlreadyExistsException(String name, String brand) {
        super("Solar panel '" + name + "' from brand '" + brand + "' is already registered.");
    }
}
