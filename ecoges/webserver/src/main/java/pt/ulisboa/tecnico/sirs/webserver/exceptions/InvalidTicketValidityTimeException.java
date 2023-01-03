package pt.ulisboa.tecnico.sirs.webserver.exceptions;

import java.time.LocalDateTime;

public class InvalidTicketValidityTimeException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidTicketValidityTimeException(LocalDateTime now, LocalDateTime validUntil) {
        super("Ticket is no longer valid. Ticket was valid until '" + validUntil + "' and current time is '" + now + "'.");
    }
}
