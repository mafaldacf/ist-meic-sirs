package pt.ulisboa.tecnico.sirs.webserver;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.*;
import pt.ulisboa.tecnico.sirs.webserver.grpc.EnergyPanel;
import pt.ulisboa.tecnico.sirs.webserver.grpc.PersonalInfo;
import pt.ulisboa.tecnico.sirs.webserver.grpc.PlanType;

import javax.crypto.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.sql.*;

import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.*;
import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.CREATE_COMPARTMENT_KEYS_TABLE;

public class ClientRequestsTests {

    private static Webserver webserver;

    private static KeyPair keyPair;
    private static final String KEY_STORE_FILE = "src/main/resources/webserver.keystore";
    private static final String KEY_STORE_PASSWORD = "mypasswebserver";
    private static final String KEY_STORE_ALIAS_WEBSERVER = "webserver";

    // Database
    private static final String DBURL = "jdbc:mysql://localhost:3306/clientdb";
    private static final String DATABASE_USER = "ecoges";
    private static final String DATABASE_PASSWORD = "admin";
    private static Connection dbConnection = null;
    private static final String DATABASE_DRIVER = "com.mysql.cj.jdbc.Driver";

    // Data compartments
    private static SecretKey personalInfoKey;
    private static SecretKey energyPanelKey;

    // Client credentials

    private static String email = "junit2-clientemail";
    private static String password = "junit2!StrongPassword";
    private static String token = "";

    @BeforeClass
    public static void setup() throws ClassNotFoundException, SQLException, UnrecoverableKeyException, CertificateException, KeyStoreException, NoSuchAlgorithmException, IOException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, ClientAlreadyExistsException, InvalidKeyException {
        loadKeysCertificates();
        setupDatabase();
        webserver = new Webserver(dbConnection, personalInfoKey, energyPanelKey);
        webserver.register("name", email, password, "new address", "iban", PlanType.FLAT_RATE.name());
    }

    @Test
    public void updatePlanTest() throws SQLException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidSessionTokenException, NoSuchAlgorithmException, ClientDoesNotExistException, InvalidKeyException {
        webserver.updatePlan(email, PlanType.BI_HOURLY_RATE.name(), token);
    }

    @Test
    public void updateAddressTest() throws SQLException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidSessionTokenException, NoSuchAlgorithmException, ClientDoesNotExistException, InvalidKeyException {
        webserver.updateAddress(email, "address", token);
    }

    @Test
    public void CheckPersonalInfoTest() throws SQLException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidSessionTokenException, NoSuchAlgorithmException, ClientDoesNotExistException, InvalidKeyException {
        PersonalInfo personalInfo = webserver.checkPersonalInfo(email, token);
        Assert.assertEquals("name", personalInfo.getName());
        Assert.assertEquals("****ess", personalInfo.getAddress());
        Assert.assertEquals("***n", personalInfo.getIBAN());
        Assert.assertEquals(PlanType.BI_HOURLY_RATE, personalInfo.getPlan());
    }

    @Test
    public void CheckPersonalInfoInvalidTokenTest() {
        Assert.assertThrows(InvalidSessionTokenException.class, () ->
            webserver.checkPersonalInfo(email, "invalid token"));
    }

    @Test
    public void addApplianceTest() throws SQLException, ApplianceAlreadyExistsException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidSessionTokenException, NoSuchAlgorithmException, ClientDoesNotExistException, InvalidKeyException {
        webserver.addApplicance(email, "a1", "b1", token);
    }

    @Test
    public void addApplianceDiffBrandTest() throws SQLException, ApplianceAlreadyExistsException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidSessionTokenException, NoSuchAlgorithmException, ClientDoesNotExistException, InvalidKeyException {
        webserver.addApplicance(email, "a1", "b2", token);
    }

    @Test
    public void addSolarPanelTest() throws SolarPanelAlreadyExistsException, SQLException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidSessionTokenException, NoSuchAlgorithmException, ClientDoesNotExistException, InvalidKeyException {
        webserver.addSolarPanel(email, "s1", "b1", token);
    }

    @Test
    public void addApplianceAlreadyExistsTest() {
        Assert.assertThrows(ApplianceAlreadyExistsException.class, () ->
                webserver.addApplicance(email, "a1", "b1", token));
    }

    @Test
    public void addSolarPanelExistsTest() {
        Assert.assertThrows(SolarPanelAlreadyExistsException.class, () ->
                webserver.addSolarPanel(email, "s1", "b1", token));
    }

    @Test
    public void checkEnergyPanelTest() throws SQLException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidSessionTokenException, NoSuchAlgorithmException, ClientDoesNotExistException, InvalidKeyException {
        EnergyPanel energyPanel = webserver.checkEnergyPanel(email, token);
        Assert.assertEquals(2, energyPanel.getAppliancesCount());
        Assert.assertEquals(1, energyPanel.getSolarPanelsCount());
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
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | NoSuchPaddingException | InvalidKeyException e) {
            System.out.println("Could not generate compartment keys: "+ e.getMessage());
            System.exit(1);
        }
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

    private static void generateCompartmentKeys() throws NoSuchAlgorithmException, SQLException, IllegalBlockSizeException,
            NoSuchPaddingException, InvalidKeyException {

        PreparedStatement st;
        ResultSet rs;

        // Check if keys already exist
        st = dbConnection.prepareStatement(READ_COMPARTMENT_KEYS);
        rs = st.executeQuery();

        if (rs.next()){
            personalInfoKey = Security.unwrapKey(keyPair.getPrivate(), rs.getBytes(1));
            energyPanelKey = Security.unwrapKey(keyPair.getPrivate(), rs.getBytes(2));
            st.close();
            return;
        }
        st.close();

        System.out.println("Generating compartment keys...");


        // Generate key for personal info compartment
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        personalInfoKey = keyGen.generateKey();
        byte[] wrappedPersonalInfoKey = Security.wrapKey(keyPair.getPublic(), personalInfoKey);

        // Generate key for energy panel compartment
        keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        energyPanelKey = keyGen.generateKey();
        byte[] wrappedEnergyPanelKey = Security.wrapKey(keyPair.getPublic(), energyPanelKey);

        // Upload keys to database
        st = dbConnection.prepareStatement(CREATE_COMPARTMENT_KEYS);
        st.setBytes(1, wrappedPersonalInfoKey);
        st.setBytes(2, wrappedEnergyPanelKey);
        st.executeUpdate();
        st.close();
    }
}
