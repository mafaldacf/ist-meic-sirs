package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class ClientAlreadyExistsException extends Exception {

    private static final long serialVersionUID = 1L;

    public ClientAlreadyExistsException(String username) {
        super("Client with username '" + username + "' already exists.");
    }
}
