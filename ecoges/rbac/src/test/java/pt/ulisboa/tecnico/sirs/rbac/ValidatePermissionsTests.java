package pt.ulisboa.tecnico.sirs.rbac;

import org.junit.*;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.InvalidRoleException;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.PermissionDeniedException;
import pt.ulisboa.tecnico.sirs.rbac.grpc.*;
import pt.ulisboa.tecnico.sirs.security.Security;

import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ValidatePermissionsTests {

    private static Rbac rbac;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private static PublicKey publicKey;

    // Trust Store
    private static final String KEY_STORE_FILE = "src/main/resources/rbac.keystore";
    private static final String KEY_STORE_PASSWORD = "mypassrbac";
    private static final String KEY_STORE_ALIAS_RBAC = "rbac";

    @BeforeClass
    public static void setup() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        rbac = new Rbac();

        // Retrieve public key
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());
        X509Certificate certificateRBAC = (X509Certificate) trustStore.getCertificate(KEY_STORE_ALIAS_RBAC);
        publicKey = certificateRBAC.getPublicKey();
    }

    @Test
    public void validatePermissionGrantedAMTest() throws InvalidRoleException, PermissionDeniedException, UnrecoverableKeyException, NoSuchPaddingException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, SignatureException, InvalidKeyException {
        ValidatePermissionResponse response = rbac.validatePermissions("account manager", Role.ACCOUNT_MANAGER, PermissionType.PERSONAL_DATA);
        ValidatePermissionResponse.Ticket ticket = response.getData();

        Assert.assertEquals("account manager", ticket.getUsername());
        Assert.assertEquals(Role.ACCOUNT_MANAGER.name(), ticket.getRole().name());
        Assert.assertEquals(PermissionType.PERSONAL_DATA.name(), ticket.getPermission().name());

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime requestIssuedAt = LocalDateTime.parse(ticket.getRequestIssuedAt(), dtf);
        Assert.assertTrue(requestIssuedAt.isBefore(now));

        LocalDateTime requestValidUntil = LocalDateTime.parse(ticket.getRequestValidUntil(), dtf);
        Assert.assertTrue(requestValidUntil.isAfter(now));

        Assert.assertTrue(Security.verifySignature(publicKey, response.getSignature().toByteArray(), response.getData().toByteArray()));
    }

    @Test
    public void validatePermissionGrantedEMTest() throws InvalidRoleException, PermissionDeniedException, UnrecoverableKeyException, NoSuchPaddingException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, SignatureException, InvalidKeyException {
        ValidatePermissionResponse response = rbac.validatePermissions("energy manager", Role.ENERGY_MANAGER, PermissionType.ENERGY_DATA);
        ValidatePermissionResponse.Ticket ticket = response.getData();

        Assert.assertEquals("energy manager", ticket.getUsername());
        Assert.assertEquals(Role.ENERGY_MANAGER.name(), ticket.getRole().name());
        Assert.assertEquals(PermissionType.ENERGY_DATA.name(), ticket.getPermission().name());

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime requestIssuedAt = LocalDateTime.parse(ticket.getRequestIssuedAt(), dtf);
        Assert.assertTrue(requestIssuedAt.isBefore(now));

        LocalDateTime requestValidUntil = LocalDateTime.parse(ticket.getRequestValidUntil(), dtf);
        Assert.assertTrue(requestValidUntil.isAfter(now));

        Assert.assertTrue(Security.verifySignature(publicKey, response.getSignature().toByteArray(), response.getData().toByteArray()));
    }

    @Test
    public void validatePermissionDeniedAMTest()  {
        Assert.assertThrows(PermissionDeniedException.class, () ->
            rbac.validatePermissions("account manager", Role.ACCOUNT_MANAGER, PermissionType.ENERGY_DATA));
    }

    @Test
    public void validatePermissionDeniedEMTest()  {
        Assert.assertThrows(PermissionDeniedException.class, () ->
                rbac.validatePermissions("energy manager", Role.ENERGY_MANAGER, PermissionType.PERSONAL_DATA));
    }
}
