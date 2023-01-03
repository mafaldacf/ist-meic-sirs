package pt.ulisboa.tecnico.sirs.webserver.exceptions;

import java.time.LocalDateTime;

public class InvalidTicketIssuedTimeException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidTicketIssuedTimeException(LocalDateTime now, LocalDateTime issuedAt) {
        super("Ticket was issued at '" + issuedAt + "' but current time is '" + now + "'.");
    }
}
