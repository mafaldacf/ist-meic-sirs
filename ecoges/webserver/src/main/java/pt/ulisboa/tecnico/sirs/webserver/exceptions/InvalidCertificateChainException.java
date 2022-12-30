package pt.ulisboa.tecnico.sirs.webserver.exceptions;

public class InvalidCertificateChainException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidCertificateChainException() {
        super("Invalid certificate chain.");
    }

}
