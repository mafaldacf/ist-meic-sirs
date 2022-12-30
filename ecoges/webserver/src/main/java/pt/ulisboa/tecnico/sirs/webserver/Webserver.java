package pt.ulisboa.tecnico.sirs.webserver;

import com.google.protobuf.ByteString;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.*;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.*;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.*;

public class Webserver {
    private final Connection dbConnection;

    // Invoices
    private final int MAX_ENERGY_CONSUMPTION = 100;
    private final int MAX_ENERGY_PRODUCTION = 100;
    private static final List<String> months = new ArrayList<>(Arrays.asList
            ("Jan", "Feb", "Mar", "Apr", "Mai", "Jun", "Jul", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"));

    // Trust Store
    private static final String TRUST_STORE_FILE = "src/main/resources/webserver.truststore";
    private static final String TRUST_STORE_PASSWORD = "mypasswebserver";
    private static final String TRUST_STORE_ALIAS_CA = "ca";

    // Compartments
    private final SecretKey personalInfoKey;
    private final SecretKey energyPanelKey;

    public Webserver(Connection dbConnection, SecretKey personalInfoKey, SecretKey energyPanelKey) {
        this.dbConnection = dbConnection;
        this.personalInfoKey = personalInfoKey;
        this.energyPanelKey = energyPanelKey;
    }

    /*
    -----------------------------------------------
    -------------- BACKOFFICE SERVICE -------------
    -----------------------------------------------
     */

    public byte[] getCompartmentKey(GetCompartmentKeyRequest.RequestData data, ByteString signature) throws CompartmentKeyException,
            IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidKeySpecException, SignatureException, InvalidSignatureException, BadPaddingException,
            KeyStoreException, IOException, InvalidAlgorithmParameterException,
            InvalidCertificateChainException, CertificateException {

        Compartment compartment = data.getCompartment();
        byte[] certificateBytes = data.getCertificate().toByteArray();

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate departmentCertificate = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certificateBytes));
        PublicKey departmentPublicKey = departmentCertificate.getPublicKey();

        // Verify authenticity and integrity
        if (!Security.verifySignature(departmentPublicKey, signature.toByteArray(), data.toByteArray())) {
            throw new InvalidSignatureException();
        }

        // Validate certificate chain with trusted CA
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(Files.newInputStream(Paths.get(TRUST_STORE_FILE)), TRUST_STORE_PASSWORD.toCharArray());
        X509Certificate CACertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_CA);

        if (!Security.validateCertificateChain(departmentCertificate, CACertificate)) {
            throw new InvalidCertificateChainException();
        }

        // TODO: Validate signature of access control infrastructure (the response should be signed)

        // Get compartment keys

        if (compartment.equals(Compartment.PERSONAL_INFO)) {
            return Security.wrapKey(departmentPublicKey, personalInfoKey);
        }
        else if (compartment.equals(Compartment.ENERGY_PANEL)) {
            return Security.wrapKey(departmentPublicKey, energyPanelKey);
        }
        else {
            throw new CompartmentKeyException();
        }
    }
/*
    ------------------------------------------------------
    ---------------- CLIENT SESSION TOKEN ----------------
    ------------------------------------------------------
     */

    public String setClientSession(String email) throws NoSuchAlgorithmException, SQLException {
        PreparedStatement st;

        String token = Security.generateToken();
        String hashedToken = Security.hash(token);

        st = dbConnection.prepareStatement(UPDATE_CLIENT_TOKEN);
        st.setString(1, hashedToken);
        st.setString(2, email);
        st.executeUpdate();
        st.close();

        return hashedToken;
    }

    public void validateSession(String email, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException {
        PreparedStatement st;
        ResultSet rs;

        st = dbConnection.prepareStatement(READ_CLIENT_COUNT);
        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) == 0){
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        st = dbConnection.prepareStatement(READ_CLIENT_TOKEN);
        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next()){
            String dbHashedToken = rs.getString(1);
            if (!dbHashedToken.equals(hashedToken))
                throw new InvalidSessionTokenException();
        }
        st.close();
    }

    /*
    ------------------------------------------------------
    --------------- CLIENT FUNCTIONALITIES ---------------
    ------------------------------------------------------
     */

    public void register(String name, String email, String password, String address, String iban, String plan)
            throws SQLException, ClientAlreadyExistsException, NoSuchAlgorithmException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidKeyException {
        PreparedStatement st;
        ResultSet rs;

        // check if email is already registered
        st = dbConnection.prepareStatement(READ_CLIENT_COUNT);
        st.setString(1, email);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) != 0){
            st.close();
            throw new ClientAlreadyExistsException(email);
        }

        st.close();

        // create client

        byte[] salt = Security.generateSalt();
        String hashedPassword = Security.hashWithSalt(password, salt);

        st = dbConnection.prepareStatement(CREATE_CLIENT);
        st.setString(1, name);
        st.setString(2, email);
        st.setString(3, hashedPassword);
        st.setBytes(4, salt);

        // encrypted compartment: personal info
        st.setString(5, address);
        st.setString(6, personalInfoKey.toString());
        st.setString(7, iban);
        st.setString(8, personalInfoKey.toString());
        st.setString(9, plan);
        st.setString(10, personalInfoKey.toString());

        // encrypted compartment: energy panel
        st.setFloat(11, 0);
        st.setString(12, energyPanelKey.toString());
        st.setFloat(13, 0);
        st.setString(14, energyPanelKey.toString());
        st.setFloat(15, 0);
        st.setString(16, energyPanelKey.toString());
        st.setFloat(17, 0);
        st.setString(18, energyPanelKey.toString());
        st.executeUpdate();
        st.close();
    }

    public ArrayList<String> login(String email, String password)
            throws ClientDoesNotExistException, SQLException,
            WrongPasswordException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        PreparedStatement st;
        ResultSet rs;
        String name;
        ArrayList<String> response = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_CLIENT_NAME_PASSWORD_SALT);
        st.setString(1, email);

        rs = st.executeQuery();

        if (rs.next()) {
            name = rs.getString(1);
            String dbHashedPassword = rs.getString(2);
            byte[] salt = rs.getBytes(3);
            String hashedPassword = Security.hashWithSalt(password, salt);
            if (!hashedPassword.equals(dbHashedPassword)) {
                st.close();
                throw new WrongPasswordException();
            }
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        String hashedToken = setClientSession(email);
        response.add(name);
        response.add(hashedToken);

        return response;
    }

    public void logout(String email, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException {
        PreparedStatement st;

        validateSession(email, hashedToken);

        st = dbConnection.prepareStatement(UPDATE_CLIENT_TOKEN);
        st.setString(1, "");
        st.setString(2, email);

        st.executeUpdate();
    }

    public void addApplicance(String email, String applianceName, String applianceBrand, String hashedToken)
            throws SQLException, InvalidSessionTokenException, ClientDoesNotExistException, ApplianceAlreadyExistsException,
            CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException {

        PreparedStatement st;
        ResultSet rs;

        validateSession(email, hashedToken);

        int client_id = getClientId(email);

        // check if appliance is already registered
        st = dbConnection.prepareStatement(READ_APPLIANCE_COUNT);
        st.setInt(1, client_id);
        st.setString(2, applianceName);
        st.setString(3, applianceBrand);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) != 0){
            throw new ApplianceAlreadyExistsException(applianceName, applianceBrand);
        }

        st.close();

        // generate random energy consumed
        float energyConsumedDaytime = (float)(Math.random()*MAX_ENERGY_CONSUMPTION);
        float energyConsumedNight = (float)(Math.random()*MAX_ENERGY_CONSUMPTION);
        float energyConsumed = energyConsumedDaytime + energyConsumedNight;

        // add appliance
        st = dbConnection.prepareStatement(CREATE_APPLIANCE);
        st.setInt(1, getClientId(email));
        st.setString(2, applianceName);
        st.setString(3, applianceBrand);
        st.setFloat(4, energyConsumed);
        st.setString(5, energyPanelKey.toString());
        st.setFloat(6, energyConsumedDaytime);
        st.setString(7, energyPanelKey.toString());
        st.setFloat(8, energyConsumedNight);
        st.setString(9, energyPanelKey.toString());
        st.executeUpdate();
        st.close();

        updateEnergyConsumption(email, energyConsumed, energyConsumedDaytime, energyConsumedNight);
    }

    public void addSolarPanel(String email, String solarPanelName, String solarPanelBrand, String hashedToken)
            throws SQLException, InvalidSessionTokenException, ClientDoesNotExistException, SolarPanelAlreadyExistsException,
            CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException {

        PreparedStatement st;
        ResultSet rs;

        validateSession(email, hashedToken);

        int client_id = getClientId(email);

        // check if solar panel is already registered
        st = dbConnection.prepareStatement(READ_SOLAR_PANEL_COUNT);
        st.setInt(1, client_id);
        st.setString(2, solarPanelName);
        st.setString(3, solarPanelBrand);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) != 0){
            throw new SolarPanelAlreadyExistsException(solarPanelName, solarPanelBrand);
        }

        // generate random energy produced
        float energyProduced = (float)(Math.random()*MAX_ENERGY_PRODUCTION);

        // add solar panel
        st = dbConnection.prepareStatement(CREATE_SOLAR_PANEL);
        st.setInt(1, client_id);
        st.setString(2, solarPanelName);
        st.setString(3, solarPanelBrand);
        st.setFloat(4, energyProduced);
        st.setString(5, energyPanelKey.toString());
        st.executeUpdate();
        st.close();

        updateEnergyProduction(email, energyProduced);
    }

    public PersonalInfo checkPersonalInfo(String clientEmail, String hashedToken) throws ClientDoesNotExistException,
            SQLException, InvalidSessionTokenException, CompartmentKeyException, IllegalBlockSizeException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {

        PersonalInfo personalInfo;
        PreparedStatement st;
        ResultSet rs;

        validateSession(clientEmail, hashedToken);

        // get personal info
        st = dbConnection.prepareStatement(READ_CLIENT_PERSONAL_INFO);

        //encrypted compartment: personal info
        st.setString(1, personalInfoKey.toString());
        st.setString(2, personalInfoKey.toString());
        st.setString(3, personalInfoKey.toString());

        st.setString(4, clientEmail);
        rs = st.executeQuery();

        if (rs.next()) {
            String name = rs.getString(1);
            String email = rs.getString(2);
            String address = rs.getString(3);
            String iban = rs.getString(4);
            String plan = rs.getString(5);

            personalInfo = PersonalInfo.newBuilder()
                    .setName(name)
                    .setEmail(email)
                    .setAddress(address)
                    .setIBAN(iban)
                    .setPlan(PlanType.valueOf(plan))
                    .build();
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(clientEmail);
        }

        st.close();

        return personalInfo;
    }

    public EnergyPanel checkEnergyPanel(String email, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException,
            CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException {

        EnergyPanel energyPanel;
        List<Appliance> appliances;
        List<SolarPanel> solarPanels;
        PreparedStatement st;
        ResultSet rs;

        validateSession(email, hashedToken);
        int client_id = getClientId(email);

        appliances = getAppliances(client_id);
        solarPanels = getSolarPanels(client_id);

        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_PANEL);
        //encrypted compartment: energy panel
        st.setString(1, energyPanelKey.toString());
        st.setString(2, energyPanelKey.toString());
        st.setString(3, energyPanelKey.toString());
        st.setString(4, energyPanelKey.toString());

        st.setString(5, email);
        rs = st.executeQuery();

        if (rs.next()) {
            float energyConsumed = rs.getFloat(1);
            float energyConsumedDaytime = rs.getFloat(2);
            float energyConsumedNight = rs.getFloat(3);
            float energyProduced = rs.getFloat(4);

            energyPanel = EnergyPanel.newBuilder()
                    .setEnergyConsumed(energyConsumed)
                    .setEnergyConsumedDaytime(energyConsumedDaytime)
                    .setEnergyConsumedNight(energyConsumedNight)
                    .setEnergyProduced(energyProduced)
                    .addAllAppliances(appliances)
                    .addAllSolarPanels(solarPanels)
                    .build();
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }

        st.close();

        return energyPanel;
    }

    public List<Invoice> checkInvoices(String email, String hashedToken)
            throws SQLException, InvalidSessionTokenException, ClientDoesNotExistException {
        PreparedStatement st;
        ResultSet rs;
        List<Invoice> invoices = new ArrayList<>();

        validateSession(email, hashedToken);
        int client_id = getClientId(email);

        st = dbConnection.prepareStatement(READ_INVOICES);
        st.setInt(1, client_id);
        rs = st.executeQuery();

        while (rs.next()) {
            int year = rs.getInt(1);
            int month = rs.getInt(2);
            float paymentAmount = rs.getFloat(3);
            float energyConsumed = rs.getFloat(4);
            float energyConsumedDaytime = rs.getFloat(5);
            float energyConsumedNight = rs.getFloat(6);
            String plan = rs.getString(7);
            int taxes = rs.getInt(8);

            Invoice invoice = Invoice.newBuilder()
                    .setYear(year)
                    .setMonth(months.get(month))
                    .setPaymentAmount(paymentAmount)
                    .setEnergyConsumed(energyConsumed)
                    .setEnergyConsumedDaytime(energyConsumedDaytime)
                    .setEnergyConsumedNight(energyConsumedNight)
                    .setPlan(PlanType.valueOf(plan))
                    .setTaxes(taxes)
                    .build();
            invoices.add(invoice);
        }

        st.close();

        return invoices;
    }

    public void updateAddress(String email, String address, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException, CompartmentKeyException,
            IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {

        PreparedStatement st;

        validateSession(email, hashedToken);

        // update address
        st = dbConnection.prepareStatement(UPDATE_CLIENT_ADDRESS);
        st.setString(1, address);
        st.setString(2, personalInfoKey.toString());
        st.setString(3, email);
        st.executeUpdate();
        st.close();
    }

    public void updatePlan(String email, String plan, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException, CompartmentKeyException,
            IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {

        validateSession(email, hashedToken);

        // update plan
        PreparedStatement st = dbConnection.prepareStatement(UPDATE_CLIENT_PLAN);
        st.setString(1, plan);
        st.setString(2, personalInfoKey.toString());
        st.setString(3, email);
        st.executeUpdate();
        st.close();
    }

    /*
    ------------------------------------------------------
    ---------------- AUXILIARY FUNCTIONS -----------------
    ------------------------------------------------------
     */

    public int getClientId(String email) throws SQLException, ClientDoesNotExistException {
        PreparedStatement st;
        ResultSet rs;
        int client_id;

        // get client id
        st = dbConnection.prepareStatement(READ_CLIENT_ID);
        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next()) {
            client_id = rs.getInt(1);
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        return client_id;
    }

    public void updateEnergyConsumption(String email, float energyConsumed, float energyConsumedDaytime, float energyConsumedNight)
            throws SQLException, ClientDoesNotExistException {

        PreparedStatement st;
        ResultSet rs;
        float currEnergyConsumed;
        float currEnergyConsumedDaytime;
        float currEnergyConsumedNight;

        // get current energy consumption
        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_CONSUMPTION);
        st.setString(1, energyPanelKey.toString());
        st.setString(2, energyPanelKey.toString());
        st.setString(3, energyPanelKey.toString());
        st.setString(4, email);

        rs = st.executeQuery();
        if (rs.next()){
            currEnergyConsumed = rs.getFloat(1);
            currEnergyConsumedDaytime = rs.getFloat(2);
            currEnergyConsumedNight = rs.getFloat(3);
        }
        else {
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        // update energy consumption
        st = dbConnection.prepareStatement(UPDATE_CLIENT_ENERGY_CONSUMPTION);
        st.setFloat(1, currEnergyConsumed + energyConsumed);
        st.setString(2, energyPanelKey.toString());
        st.setFloat(3, currEnergyConsumedDaytime + energyConsumedDaytime);
        st.setString(4, energyPanelKey.toString());
        st.setFloat(5, currEnergyConsumedNight + energyConsumedNight);
        st.setString(6, energyPanelKey.toString());
        st.setString(7, email);
        st.executeUpdate();
        st.close();
    }

    public void updateEnergyProduction(String email, float energyProduced) throws SQLException, ClientDoesNotExistException {

        PreparedStatement st;
        ResultSet rs;
        float currEnergyProduced;

        // get current energy consumption
        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_PRODUCTION);
        st.setString(1, energyPanelKey.toString());
        st.setString(2, email);
        rs = st.executeQuery();
        if (rs.next()){
            currEnergyProduced = rs.getFloat(1);
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        // update energy consumption
        st = dbConnection.prepareStatement(UPDATE_CLIENT_ENERGY_PRODUCTION);
        st.setFloat(1, currEnergyProduced + energyProduced);
        st.setString(2, energyPanelKey.toString());
        st.setString(3, email);
        st.executeUpdate();
        st.close();
    }

    public List<Appliance> getAppliances(int clientId) throws SQLException {

        PreparedStatement st;
        ResultSet rs;
        List<Appliance> appliances = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_APPLIANCES);
        st.setString(1, energyPanelKey.toString());
        st.setString(2, energyPanelKey.toString());
        st.setString(3, energyPanelKey.toString());
        st.setInt(4, clientId);
        rs = st.executeQuery();

        while (rs.next()) {
            String name = rs.getString(1);
            String brand = rs.getString(2);
            float energyConsumed = rs.getFloat(3);
            float energyConsumedDaytime = rs.getFloat(4);
            float energyConsumedNight = rs.getFloat(5);

            Appliance appliance = Appliance.newBuilder()
                    .setName(name)
                    .setBrand(brand)
                    .setEnergyConsumed(energyConsumed)
                    .setEnergyConsumedDaytime(energyConsumedDaytime)
                    .setEnergyConsumedNight(energyConsumedNight)
                    .build();
            appliances.add(appliance);
        }
        st.close();

        return appliances;
    }

    public List<SolarPanel> getSolarPanels(int clientId) throws SQLException  {

        PreparedStatement st;
        ResultSet rs;
        List<SolarPanel> solarPanels = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_SOLAR_PANELS);
        st.setString(1, energyPanelKey.toString());
        st.setInt(2, clientId);
        rs = st.executeQuery();


        while (rs.next()) {
            String name = rs.getString(1);
            String brand = rs.getString(2);
            float energyProduced = rs.getFloat(3);

            SolarPanel solarPanel = SolarPanel.newBuilder()
                    .setName(name)
                    .setBrand(brand)
                    .setEnergyProduced(energyProduced)
                    .build();
            solarPanels.add(solarPanel);
        }
        st.close();

        return solarPanels;
    }

}