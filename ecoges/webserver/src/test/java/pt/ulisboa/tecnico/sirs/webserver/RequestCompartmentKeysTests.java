package pt.ulisboa.tecnico.sirs.webserver;

import com.google.protobuf.ByteString;
import org.junit.*;
import pt.ulisboa.tecnico.sirs.rbac.Rbac;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.InvalidRoleException;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.PermissionDeniedException;
import pt.ulisboa.tecnico.sirs.rbac.grpc.PermissionType;
import pt.ulisboa.tecnico.sirs.rbac.grpc.Role;
import pt.ulisboa.tecnico.sirs.rbac.grpc.ValidatePermissionResponse;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.*;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

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
import java.sql.*;

import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.*;
import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.CREATE_COMPARTMENT_KEYS_TABLE;

public class RequestCompartmentKeysTests {

    private static Webserver webserver;
    private static Rbac rbac;

    private static KeyPair keyPair;

    private static final String KEY_STORE_FILE = "src/main/resources/webserver.keystore";
    private static final String KEY_STORE_PASSWORD = "mypasswebserver";
    private static final String KEY_STORE_ALIAS_WEBSERVER = "webserver";
    private static X509Certificate testCertificate;
    private static PrivateKey testPrivateKey;

    private static X509Certificate AMCertificate;
    private static PrivateKey AMPrivateKey;
    private static X509Certificate EMCertificate;
    private static PrivateKey EMPrivateKey;

    private static SecretKey personalInfoKey;

    private static SecretKey energyPanelKey;
    private static final String BACKOFFICE_KEY_STORE_FILE = "./../backoffice/src/main/resources/backoffice.keystore";
    private static final String BACKOFFICE_KEY_STORE_PASSWORD = "mypassbackoffice";
    private static final String BACKOFFICE_KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT = "accountManagement";
    private static final String BACKOFFICE_KEY_STORE_ALIAS_ENERGY_MANAGEMENT = "energyManagement";

    // Database
    private static final String DBURL = "jdbc:mysql://localhost:3306/clientdb";
    private static final String DATABASE_USER = "ecoges";
    private static final String DATABASE_PASSWORD = "admin";
    private static Connection dbConnection = null;
    private static final String DATABASE_DRIVER = "com.mysql.cj.jdbc.Driver";

    // Testing purposes
    private static final String WEBSERVER_TEST_KEY_STORE_FILE = "src/test/resources/webserver-tests.keystore";
    private static final String WEBSERVER_TEST_KEY_STORE_PASSWORD = "mypasswebserver-tests";
    private static final String WEBSERVER_TEST_KEY_STORE_ALIAS_TEST = "tests";

    // Client credentials
    private static final String email = "junit2-clientemail";
    private static final String password = "junit2!StrongPassword";
    private static final String address = "address";
    private static final String iban = "iban";

    @BeforeClass
    public static void setup() throws CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, IOException, KeyStoreException, SQLException, ClassNotFoundException, InvalidAlgorithmParameterException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, ClientAlreadyExistsException, BadPaddingException, InvalidKeyException {
        loadKeysCertificates();
        loadTestCertificate();
        generateCompartmentKeys();
        setupDatabase();

        webserver = new Webserver(dbConnection, personalInfoKey, energyPanelKey, keyPair);
        webserver.register("name", email, password, "address", "iban", PlanType.FLAT_RATE.name());
        rbac = new Rbac("../rbac/src/main/resources/rbac.keystore");
    }
    @Test
    public void requestCompartmentKeyAMTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateException, KeyStoreException, IOException, UnrecoverableKeyException, InvalidSignatureException, InvalidAlgorithmParameterException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidCertificateChainException, InvalidKeySpecException, BadPaddingException, PermissionDeniedException, InvalidRoleException, InvalidTicketUsernameException, InvalidTicketCompartmentException, InvalidTicketIssuedTimeException, InvalidTicketRoleException, InvalidTicketValidityTimeException, SQLException, ClientDoesNotExistException {
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
                response.getSignature(), response.getData().toByteString(), email);

        SecretKey key = Security.unwrapKey(AMPrivateKey, wrappedKey);
        Assert.assertNotNull(key);

        PersonalInfo personalInfo = getPersonalInfo(email, key);
        Assert.assertEquals(address, personalInfo.getAddress());
        Assert.assertEquals(iban, personalInfo.getIBAN());
    }
    @Test
    public void requestCompartmentKeyEMTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateException, KeyStoreException, IOException, UnrecoverableKeyException, InvalidSignatureException, InvalidAlgorithmParameterException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidCertificateChainException, InvalidKeySpecException, BadPaddingException, PermissionDeniedException, InvalidRoleException, InvalidTicketUsernameException, InvalidTicketCompartmentException, InvalidTicketIssuedTimeException, InvalidTicketRoleException, InvalidTicketValidityTimeException, SQLException {
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
                response.getSignature(), response.getData().toByteString(), email);

        SecretKey key = Security.unwrapKey(EMPrivateKey, wrappedKey);
        Assert.assertNotNull(key);
    }

    @Test
    public void requestCompartmentKeyWithInvalidCertificateChainTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateEncodingException {
        GetCompartmentKeyRequest.RequestData data = GetCompartmentKeyRequest.RequestData.newBuilder()
                .setCompartment(Compartment.PERSONAL_DATA)
                .setCertificate(ByteString.copyFrom(testCertificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(testPrivateKey, data.toByteArray());

        Assert.assertThrows(InvalidCertificateChainException.class, () ->
            webserver.getCompartmentKey(data, signature, null, null, null, null));
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
            webserver.getCompartmentKey(data, signature, null, null, null, null));
    }

    public PersonalInfo getPersonalInfo(String clientEmail, SecretKey temporaryKey) throws SQLException, ClientDoesNotExistException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        PersonalInfo personalInfo;
        PreparedStatement st;
        ResultSet rs;

        // get personal info
        st = dbConnection.prepareStatement(READ_CLIENT_IV_AND_ENCRYPTED_PERSONAL_DATA);
        st.setString(1, clientEmail);
        rs = st.executeQuery();

        if (rs.next()) {
            byte[] iv = rs.getBytes(1);
            String address = new String(Security.decryptData(rs.getBytes(2), temporaryKey, iv));
            String iban = new String(Security.decryptData(rs.getBytes(3), temporaryKey, iv));

            personalInfo = PersonalInfo.newBuilder()
                    .setAddress(address)
                    .setIBAN(iban)
                    .build();
        } else {
            st.close();
            throw new ClientDoesNotExistException(clientEmail);
        }

        return personalInfo;
    }

    public static void loadAMDepartmentCertificate() throws IOException, KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(Files.newInputStream(Paths.get(BACKOFFICE_KEY_STORE_FILE)), BACKOFFICE_KEY_STORE_PASSWORD.toCharArray());

        AMPrivateKey = (PrivateKey) keyStore.getKey(BACKOFFICE_KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT, BACKOFFICE_KEY_STORE_PASSWORD.toCharArray());
        AMCertificate = (X509Certificate) keyStore.getCertificate(BACKOFFICE_KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT);
    }

    public static void loadEMDepartmentCertificate() throws IOException, KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(Files.newInputStream(Paths.get(BACKOFFICE_KEY_STORE_FILE)), BACKOFFICE_KEY_STORE_PASSWORD.toCharArray());

        EMPrivateKey = (PrivateKey) keyStore.getKey(BACKOFFICE_KEY_STORE_ALIAS_ENERGY_MANAGEMENT, BACKOFFICE_KEY_STORE_PASSWORD.toCharArray());
        EMCertificate = (X509Certificate) keyStore.getCertificate(BACKOFFICE_KEY_STORE_ALIAS_ENERGY_MANAGEMENT);
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
        keyStore.load(Files.newInputStream(Paths.get(WEBSERVER_TEST_KEY_STORE_FILE)), WEBSERVER_TEST_KEY_STORE_PASSWORD.toCharArray());

        testPrivateKey = (PrivateKey) keyStore.getKey(WEBSERVER_TEST_KEY_STORE_ALIAS_TEST, WEBSERVER_TEST_KEY_STORE_PASSWORD.toCharArray());
        testCertificate = (X509Certificate) keyStore.getCertificate(WEBSERVER_TEST_KEY_STORE_ALIAS_TEST);
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

    private static void loadKeysCertificates() throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
            IOException, UnrecoverableKeyException {

        // Key Store
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_WEBSERVER, KEY_STORE_PASSWORD.toCharArray());
        PublicKey publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_WEBSERVER).getPublicKey();
        keyPair = new KeyPair(publicKey, privateKey);

        System.out.println("Successfully loaded key pairs and certificate from Java Keystore!");
    }

    public static void setupDatabase() throws ClassNotFoundException, SQLException {
        Class.forName(DATABASE_DRIVER);
        dbConnection = DriverManager.getConnection(DBURL, DATABASE_USER, DATABASE_PASSWORD);

        Statement statement;

        try {
            boolean reachable = dbConnection.isValid(25);
            if (!reachable) {
                throw new SQLException("Unreachable database connection.");
            }

            statement = dbConnection.createStatement();
            statement.execute(DROP_INVOICE_TABLE);
            statement.execute(DROP_SOLAR_PANEL_TABLE);
            statement.execute(DROP_APPLIANCE_TABLE);
            statement.execute(DROP_CLIENT_TABLE);
            statement.execute(DROP_COMPARTMENT_KEYS_TABLE);

            statement = dbConnection.createStatement();
            statement.execute(CREATE_CLIENT_TABLE);
            statement.execute(CREATE_APPLIANCE_TABLE);
            statement.execute(CREATE_SOLAR_PANEL_TABLE);
            statement.execute(CREATE_INVOICE_TABLE);
            statement.execute(CREATE_COMPARTMENT_KEYS_TABLE);

            generateCompartmentKeys();

            System.out.println("Database is ready!");
        } catch (SQLException e) {
            System.out.println("Could not set up database: "+ e.getMessage());
            System.exit(1);
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Could not generate compartment keys: "+ e.getMessage());
            System.exit(1);
        }
    }
}
