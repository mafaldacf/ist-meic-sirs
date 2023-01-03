package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class InvalidTicketUsernameException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidTicketUsernameException(String ticketUsername, String requestUsername) {
        super("Ticket username '" + ticketUsername + "' does not correspond to the request username '" + requestUsername + "'.");
    }
}
