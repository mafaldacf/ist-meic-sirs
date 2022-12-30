package pt.ulisboa.tecnico.sirs.admin;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class AdminRegisterTests {

    private static String serverHost = "localhost";
    private static int serverPort = 8001;

    private static String username = "admin0";

    private static String password = "myverystrongPASSWORD0?";
    private static String token = "";
    @BeforeClass
    public static void setup() throws CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException {
        Admin.init(serverHost, serverPort);
    }

    @AfterClass
    public static void teardown() {
        Admin.close();
    }

    @Test
    public void registerTest(){
        Admin.register(username, password, 0);
    }

    @Test
    public void registerExistingAccountTest(){
        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            Admin.register(username, password, 0);
        });

        Assert.assertTrue(e.getMessage().contains(Status.ALREADY_EXISTS.getCode().toString()));
        Assert.assertTrue(e.getMessage().contains("Admin with username '" + username + "' already exists."));
    }
}
