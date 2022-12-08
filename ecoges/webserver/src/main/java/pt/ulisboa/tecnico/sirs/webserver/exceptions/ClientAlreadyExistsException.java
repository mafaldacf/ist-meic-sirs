package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class ClientAlreadyExistsException extends Exception {

    private static final long serialVersionUID = 1L;

    public ClientAlreadyExistsException(String email) {
        super("Client with email '" + email + "' already exists.");
    }
}
