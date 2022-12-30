package pt.ulisboa.tecnico.sirs.admin;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.*;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

public class AdminLoginTests {

    private static String serverHost = "localhost";
    private static int serverPort = 8001;

    private static String username = "admin1";

    private static String password = "myverystrongPASSWORD1?";
    private static String token = "";

    @BeforeClass
    public static void setup() throws CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException {
        Admin.init(serverHost, serverPort);

        // register
        Admin.register(username, password, 0);
    }

    @After
    public void cleanup() {
        Admin.logout(username, token);
    }

    @AfterClass
    public static void teardown() {
        Admin.close();
    }

    @Test
    public void loginTest(){
        List<String> cred = Admin.login(username, password);
        if (cred != null) {
            token = cred.get(1);
        }
    }

    @Test
    public void loginWrongPasswordTest(){
        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            List<String> cred = Admin.login(username, "wrongPassword");
            if (cred != null) {
                token = cred.get(1);
            }
        });

        Assert.assertTrue(e.getMessage().contains(Status.INVALID_ARGUMENT.getCode().toString()));
        Assert.assertTrue(e.getMessage().contains("Wrong password."));
    }
}
