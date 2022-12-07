package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class ClientDoesNotExistException extends Exception {

    private static final long serialVersionUID = 1L;

    public ClientDoesNotExistException(String username) {
        super("Client with username '" + username + "' does not exist.");
    }
}
