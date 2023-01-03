package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class InvalidTicketRoleException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidTicketRoleException(String ticketRole, String requestRole) {
        super("Ticket role '" + ticketRole + "' does not correspond to the request role '" + requestRole + "'.");
    }
}
