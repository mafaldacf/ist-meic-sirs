package pt.ulisboa.tecnico.sirs.security;

import org.junit.*;
import pt.ulisboa.tecnico.sirs.security.exceptions.WeakPasswordException;

public class PasswordTests {
    private final static String strongPassword = "myverystrongPASSWORD1?";
    private final static String weakPassword = "weak";

    @Test
    public void weakPasswordTest(){
        Assert.assertThrows(WeakPasswordException.class, () ->
                Security.verifyStrongPassword(weakPassword));
    }

    @Test
    public void strongPasswordTest() throws WeakPasswordException {
        boolean strong = Security.verifyStrongPassword(strongPassword);
        Assert.assertTrue(strong);
    }
}
