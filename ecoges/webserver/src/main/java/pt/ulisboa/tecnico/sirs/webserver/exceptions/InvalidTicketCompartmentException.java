package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class InvalidTicketCompartmentException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidTicketCompartmentException(String ticketCompartment, String requestCompartment) {
        super("Ticket compartment '" + ticketCompartment + "' does not correspond to the request compartment '" + requestCompartment + "'.");
    }
}
