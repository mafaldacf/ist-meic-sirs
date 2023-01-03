package pt.ulisboa.tecnico.sirs.webserver;

import com.google.protobuf.ByteString;
import org.junit.*;
import pt.ulisboa.tecnico.sirs.rbac.Rbac;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.InvalidRoleException;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.PermissionDeniedException;
import pt.ulisboa.tecnico.sirs.rbac.grpc.PermissionType;
import pt.ulisboa.tecnico.sirs.rbac.grpc.Role;
import pt.ulisboa.tecnico.sirs.rbac.grpc.ValidatePermissionRequest;
import pt.ulisboa.tecnico.sirs.rbac.grpc.ValidatePermissionResponse;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.*;
import pt.ulisboa.tecnico.sirs.webserver.grpc.Compartment;
import pt.ulisboa.tecnico.sirs.webserver.grpc.GetCompartmentKeyRequest;
import pt.ulisboa.tecnico.sirs.webserver.grpc.RoleTypes;

import javax.crypto.*;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;

public class RequestCompartmentKeysTests {

    private static Webserver webserver;
    private static Rbac rbac;

    private static X509Certificate testCertificate;
    private static PrivateKey testPrivateKey;

    private static X509Certificate AMCertificate;
    private static PrivateKey AMPrivateKey;
    private static X509Certificate EMCertificate;
    private static PrivateKey EMPrivateKey;

    private static SecretKey personalInfoKey;

    private static SecretKey energyPanelKey;
    private static final String KEY_STORE_FILE = "./../backoffice/src/main/resources/backoffice.keystore";
    private static final String KEY_STORE_PASSWORD = "mypassbackoffice";
    private static final String KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT = "accountManagement";
    private static final String KEY_STORE_ALIAS_ENERGY_MANAGEMENT = "energyManagement";

    // Testing purposes
    private static final String TEST_KEY_STORE_FILE = "src/test/resources/webserver-tests.keystore";
    private static final String TEST_KEY_STORE_PASSWORD = "mypasswebserver-tests";
    private static final String TEST_KEY_STORE_ALIAS_TEST = "tests";

    @BeforeClass
    public static void setup() throws CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, IOException, KeyStoreException {
        loadTestCertificate();
        generateCompartmentKeys();

        webserver = new Webserver(personalInfoKey, energyPanelKey);
        rbac = new Rbac("../rbac/src/main/resources/rbac.keystore");
    }
    @Test
    public void requestCompartmentKeyAMTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateException, KeyStoreException, IOException, UnrecoverableKeyException, InvalidSignatureException, InvalidAlgorithmParameterException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidCertificateChainException, InvalidKeySpecException, BadPaddingException, PermissionDeniedException, InvalidRoleException, InvalidTicketUsernameException, InvalidTicketCompartmentException, InvalidTicketIssuedTimeException, InvalidTicketRoleException, InvalidTicketValidityTimeException {
        loadAMDepartmentCertificate();

        // request permission to RBAC
        ValidatePermissionResponse response = rbac.validatePermissions("account manager", Role.ACCOUNT_MANAGER, PermissionType.PERSONAL_DATA);

        // request compartment keys
        GetCompartmentKeyRequest.RequestData data = GetCompartmentKeyRequest.RequestData.newBuilder()
                .setUsername("account manager")
                .setRole(RoleTypes.ACCOUNT_MANAGER)
                .setCompartment(Compartment.PERSONAL_DATA)
                .setCertificate(ByteString.copyFrom(AMCertificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(AMPrivateKey, data.toByteArray());

        byte[] wrappedKey = webserver.getCompartmentKey(data, signature, convertTicket(response.getData()),
                response.getSignature(), response.getData().toByteString());

        SecretKey key = Security.unwrapKey(AMPrivateKey, wrappedKey);
        Assert.assertEquals(personalInfoKey, key);
    }
    @Test
    public void requestCompartmentKeyEMTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateException, KeyStoreException, IOException, UnrecoverableKeyException, InvalidSignatureException, InvalidAlgorithmParameterException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidCertificateChainException, InvalidKeySpecException, BadPaddingException, PermissionDeniedException, InvalidRoleException, InvalidTicketUsernameException, InvalidTicketCompartmentException, InvalidTicketIssuedTimeException, InvalidTicketRoleException, InvalidTicketValidityTimeException {
        loadEMDepartmentCertificate();

        // request permission to RBAC
        ValidatePermissionResponse response = rbac.validatePermissions("energy manager", Role.ENERGY_MANAGER, PermissionType.ENERGY_DATA);

        // request compartment keys
        GetCompartmentKeyRequest.RequestData data = GetCompartmentKeyRequest.RequestData.newBuilder()
                .setUsername("energy manager")
                .setRole(RoleTypes.ENERGY_MANAGER)
                .setCompartment(Compartment.ENERGY_DATA)
                .setCertificate(ByteString.copyFrom(EMCertificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(EMPrivateKey, data.toByteArray());

        byte[] wrappedKey = webserver.getCompartmentKey(data, signature, convertTicket(response.getData()),
                response.getSignature(), response.getData().toByteString());

        SecretKey key = Security.unwrapKey(EMPrivateKey, wrappedKey);
        Assert.assertEquals(energyPanelKey, key);
    }

    @Test
    public void requestCompartmentKeyWithInvalidCertificateChainTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateEncodingException {
        GetCompartmentKeyRequest.RequestData data = GetCompartmentKeyRequest.RequestData.newBuilder()
                .setCompartment(Compartment.PERSONAL_DATA)
                .setCertificate(ByteString.copyFrom(testCertificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(testPrivateKey, data.toByteArray());

        Assert.assertThrows(InvalidCertificateChainException.class, () ->
            webserver.getCompartmentKey(data, signature, null, null, null));
    }

    @Test
    public void requestCompartmentKeyWithInvalidSignatureTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateEncodingException {
        // Create random private key that does not match certificate
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");

        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        PrivateKey privateKey = keyPair.getPrivate();


        GetCompartmentKeyRequest.RequestData data = GetCompartmentKeyRequest.RequestData.newBuilder()
                .setCompartment(Compartment.PERSONAL_DATA)
                .setCertificate(ByteString.copyFrom(testCertificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(privateKey, data.toByteArray());

        Assert.assertThrows(InvalidSignatureException.class, () ->
            webserver.getCompartmentKey(data, signature, null, null, null));
    }

    public static void loadAMDepartmentCertificate() throws IOException, KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

        AMPrivateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT, KEY_STORE_PASSWORD.toCharArray());
        AMCertificate = (X509Certificate) keyStore.getCertificate(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT);
    }

    public static void loadEMDepartmentCertificate() throws IOException, KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

        EMPrivateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_ENERGY_MANAGEMENT, KEY_STORE_PASSWORD.toCharArray());
        EMCertificate = (X509Certificate) keyStore.getCertificate(KEY_STORE_ALIAS_ENERGY_MANAGEMENT);
    }

    private static void generateCompartmentKeys() throws NoSuchAlgorithmException  {
        KeyGenerator keyGen;

        keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        personalInfoKey = keyGen.generateKey();

        keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        energyPanelKey = keyGen.generateKey();
    }

    public static void loadTestCertificate() throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(Files.newInputStream(Paths.get(TEST_KEY_STORE_FILE)), TEST_KEY_STORE_PASSWORD.toCharArray());

        testPrivateKey = (PrivateKey) keyStore.getKey(TEST_KEY_STORE_ALIAS_TEST, TEST_KEY_STORE_PASSWORD.toCharArray());
        testCertificate = (X509Certificate) keyStore.getCertificate(TEST_KEY_STORE_ALIAS_TEST);
    }

    public GetCompartmentKeyRequest.Ticket convertTicket(ValidatePermissionResponse.Ticket ticket) {
        return GetCompartmentKeyRequest.Ticket.newBuilder()
                .setUsername(ticket.getUsername())
                .setRole(RoleTypes.valueOf(ticket.getRole().name()))
                .setCompartment(Compartment.valueOf(ticket.getPermission().name()))
                .setRequestIssuedAt(ticket.getRequestIssuedAt())
                .setRequestValidUntil(ticket.getRequestValidUntil())
                .build();
    }
}
