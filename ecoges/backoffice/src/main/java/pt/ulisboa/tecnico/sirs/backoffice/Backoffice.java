package pt.ulisboa.tecnico.sirs.backoffice;

import pt.ulisboa.tecnico.sirs.backoffice.exceptions.HelloException;

public class Backoffice {

    public Backoffice() {
        //TODO
    }

    public String hello(String name) throws HelloException {
        if (name.equals("")){
            throw new HelloException();
        }
        return "Hello " + name;
    }


}
