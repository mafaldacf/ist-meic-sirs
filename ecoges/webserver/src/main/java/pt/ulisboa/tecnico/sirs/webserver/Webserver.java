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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.*;

public class Webserver {
    private Connection dbConnection;

    private static final List<String> months = new ArrayList<>(Arrays.asList
            ("Jan", "Feb", "Mar", "Apr", "Mai", "Jun", "Jul", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"));

    // Trust Store
    private static final String TRUST_STORE_FILE = "src/main/resources/webserver.truststore";
    private static final String TRUST_STORE_PASSWORD = "mypasswebserver";
    private static final String TRUST_STORE_ALIAS_CA = "ca";
    private static final String TRUST_STORE_ALIAS_RBAC = "rbac";

    // Compartments
    private final SecretKey personalInfoKey;
    private final SecretKey energyPanelKey;

    public Webserver(SecretKey personalInfoKey, SecretKey energyPanelKey) {
        this.personalInfoKey = personalInfoKey;
        this.energyPanelKey = energyPanelKey;
    }

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

    public PublicKey verifyDepartmentRequest(GetCompartmentKeyRequest.RequestData data, ByteString signature)
            throws CertificateException, InvalidSignatureException, KeyStoreException, IOException,
            InvalidAlgorithmParameterException, NoSuchAlgorithmException, InvalidCertificateChainException,
            SignatureException, InvalidKeyException {

        byte[] certificateBytes = data.getCertificate().toByteArray();

        // Retrieve department certificate from the request
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate departmentCertificate = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certificateBytes));
        PublicKey departmentPublicKey = departmentCertificate.getPublicKey();

        // Verify authenticity and integrity of the request
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

        return departmentPublicKey;
    }

    public void verifyRBACResponse(GetCompartmentKeyRequest.Ticket ticket, ByteString ticketBytes,
                                   ByteString signatureRBAC, GetCompartmentKeyRequest.RequestData request)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, SignatureException,
            InvalidKeyException, InvalidSignatureException, InvalidTicketUsernameException, InvalidTicketRoleException,
            InvalidTicketCompartmentException, InvalidTicketIssuedTimeException, InvalidTicketValidityTimeException {

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

        // Retrieve RBAC certificate from truststore
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(Files.newInputStream(Paths.get(TRUST_STORE_FILE)), TRUST_STORE_PASSWORD.toCharArray());
        X509Certificate certificateRBAC = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_RBAC);
        PublicKey publicKeyRBAC = certificateRBAC.getPublicKey();

        // Verify authenticity and integrity of the response
        if (!Security.verifySignature(publicKeyRBAC, signatureRBAC.toByteArray(), ticketBytes.toByteArray())) {
            throw new InvalidSignatureException();
        }

        // Validate ticket information with the department request
        if (!ticket.getUsername().equals(request.getUsername())) {
            throw new InvalidTicketUsernameException(ticket.getUsername(), request.getUsername());
        }
        if (!ticket.getRole().equals(request.getRole())) {
            throw new InvalidTicketRoleException(ticket.getRole().name(), request.getRole().name());
        }
        if (!ticket.getCompartment().equals(request.getCompartment())) {
            throw new InvalidTicketCompartmentException(ticket.getCompartment().name(), request.getCompartment().name());
        }

        // Validate ticket timestamps
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime requestIssuedAt = LocalDateTime.parse(ticket.getRequestIssuedAt(), dtf);
        if (requestIssuedAt.isAfter(now)) {
            throw new InvalidTicketIssuedTimeException(now, requestIssuedAt);
        }

        LocalDateTime requestValidUntil = LocalDateTime.parse(ticket.getRequestValidUntil(), dtf);
        if (requestValidUntil.isBefore(now)) {
            throw new InvalidTicketValidityTimeException(now, requestValidUntil);
        }
    }

    public byte[] getCompartmentKey(GetCompartmentKeyRequest.RequestData data, ByteString signature,
                                    GetCompartmentKeyRequest.Ticket ticket, ByteString signatureRBAC,
                                    ByteString ticketBytes)
            throws CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidKeySpecException, SignatureException, InvalidSignatureException, BadPaddingException,
            KeyStoreException, IOException, InvalidAlgorithmParameterException, InvalidCertificateChainException,
            CertificateException, InvalidTicketUsernameException, InvalidTicketCompartmentException, InvalidTicketRoleException,
            InvalidTicketIssuedTimeException, InvalidTicketValidityTimeException {

        PublicKey departmentPublicKey = verifyDepartmentRequest(data, signature);
        verifyRBACResponse(ticket, ticketBytes, signatureRBAC, data);

        Compartment compartment = data.getCompartment();

        if (compartment.equals(Compartment.PERSONAL_DATA)) {
            return Security.wrapKey(departmentPublicKey, personalInfoKey);
        }
        else if (compartment.equals(Compartment.ENERGY_DATA)) {
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

    private String obfuscate(String text){
        int len = text.length();
        if (text == null || len <= 1) {
            return "***";
        }
        char[] chars = text.toCharArray();
        
        if (len == 2){
            chars[0] = '*';
            return new String(chars);
        } else if (len == 3){
            chars[0] = '*';
            chars[1] = '*';
            return new String(chars);
        } else if (len == 4){
            chars[0] = '*';
            chars[1] = '*';
            chars[2] = '*';
            return new String(chars);
        }

        for (int i = 0; i < chars.length - 3; i++) {
            chars[i] = '*';
        }
        return new String(chars);
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
            throws SQLException, ClientAlreadyExistsException, NoSuchAlgorithmException, CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException {
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

        byte[] salt = Security.generateRandom();
        String hashedPassword = Security.hashWithSalt(password, salt);

        byte[] iv = Security.generateRandom();

        st = dbConnection.prepareStatement(CREATE_CLIENT);
        st.setString(1, name);
        st.setString(2, email);
        st.setString(3, hashedPassword);
        st.setBytes(4, salt);
        st.setBytes(5, iv);
        st.setString(6, plan);

        st.setBytes(7, Security.encryptData(address, personalInfoKey, iv));
        st.setBytes(8, Security.encryptData(iban, personalInfoKey, iv));

        st.setBytes(9, Security.encryptData(Float.toString(0), energyPanelKey, iv));
        st.setBytes(10, Security.encryptData(Float.toString(0), energyPanelKey, iv));
        st.setBytes(11, Security.encryptData(Float.toString(0), energyPanelKey, iv));
        st.setBytes(12, Security.encryptData(Float.toString(0), energyPanelKey, iv));

        // obfuscated info
        st.setString(13, obfuscate(address));
        st.setString(14, obfuscate(iban));

        float noval = 0;
        st.setString(15, obfuscate(Float.toString(noval)));
        st.setString(16, obfuscate(Float.toString(noval)));
        st.setString(17, obfuscate(Float.toString(noval)));
        st.setString(18, obfuscate(Float.toString(noval)));

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
            InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException {

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
        int MAX_ENERGY_CONSUMPTION = 100;
        float energyConsumedDaytime = (float)(Math.random()* MAX_ENERGY_CONSUMPTION);
        float energyConsumedNight = (float)(Math.random()* MAX_ENERGY_CONSUMPTION);
        float energyConsumed = energyConsumedDaytime + energyConsumedNight;

        byte[] iv = Security.generateRandom();

        st = dbConnection.prepareStatement(CREATE_APPLIANCE);
        st.setInt(1, getClientId(email));
        st.setString(2, applianceName);
        st.setString(3, applianceBrand);
        st.setBytes(4, iv);

        st.setBytes(5, Security.encryptData(Float.toString(energyConsumed), energyPanelKey, iv));
        st.setBytes(6, Security.encryptData(Float.toString(energyConsumedDaytime), energyPanelKey, iv));
        st.setBytes(7, Security.encryptData(Float.toString(energyConsumedNight), energyPanelKey, iv));

        st.executeUpdate();
        st.close();

        updateEnergyConsumption(email, energyConsumed, energyConsumedDaytime, energyConsumedNight);
    }

    public void addSolarPanel(String email, String solarPanelName, String solarPanelBrand, String hashedToken)
            throws SQLException, InvalidSessionTokenException, ClientDoesNotExistException, SolarPanelAlreadyExistsException,
            CompartmentKeyException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException {

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
        int MAX_ENERGY_PRODUCTION = 100;
        float energyProduced = (float)(Math.random()* MAX_ENERGY_PRODUCTION);

        byte[] iv = Security.generateRandom();

        // add solar panel
        st = dbConnection.prepareStatement(CREATE_SOLAR_PANEL);
        st.setInt(1, client_id);
        st.setString(2, solarPanelName);
        st.setString(3, solarPanelBrand);
        st.setBytes(4, iv);

        st.setBytes(5, Security.encryptData(Float.toString(energyProduced), energyPanelKey, iv));

        st.executeUpdate();
        st.close();

        updateEnergyProduction(email, energyProduced);
    }

    public PersonalInfo checkPersonalInfo(String clientEmail, String hashedToken) throws ClientDoesNotExistException,
            SQLException, InvalidSessionTokenException, CompartmentKeyException, IllegalBlockSizeException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException {

        PersonalInfo personalInfo;
        PreparedStatement st;
        ResultSet rs;

        validateSession(clientEmail, hashedToken);

        byte[] iv = getIv(clientEmail);

        // get personal info
        st = dbConnection.prepareStatement(READ_CLIENT_PERSONAL_INFO);
        st.setString(1, clientEmail);
        rs = st.executeQuery();

        if (rs.next()) {
            String name = rs.getString(1);
            String email = rs.getString(2);
            String obf_address = rs.getString(3);
            String obf_iban = rs.getString(4);
            String plan = rs.getString(5);

            personalInfo = PersonalInfo.newBuilder()
                    .setName(name)
                    .setEmail(email)
                    .setAddress(obf_address)
                    .setIBAN(obf_iban)
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
            NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException {

        EnergyPanel energyPanel;
        List<Appliance> appliances;
        List<SolarPanel> solarPanels;
        PreparedStatement st;
        ResultSet rs;

        validateSession(email, hashedToken);
        int client_id = getClientId(email);

        appliances = getAppliances(client_id);
        solarPanels = getSolarPanels(client_id);

        byte[] iv = getIv(email);

        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_PANEL);
        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next()) {
            byte[] energyConsumed = Security.decryptData(rs.getBytes(1), energyPanelKey, iv);
            byte[] energyConsumedDaytime = Security.decryptData(rs.getBytes(2), energyPanelKey, iv);
            byte[] energyConsumedNight = Security.decryptData(rs.getBytes(3), energyPanelKey, iv);
            byte[] energyProduced = Security.decryptData(rs.getBytes(4), energyPanelKey, iv);

            String obfEnergyConsumed = obfuscate(new String(energyConsumed));
            String obfEnergyConsumedDaytime = obfuscate(new String(energyConsumedDaytime));
            String obfEnergyConsumedNight = obfuscate(new String(energyConsumedNight));
            String obfEnergyProduced = obfuscate(new String(energyProduced));

            energyPanel = EnergyPanel.newBuilder()
                    .setEnergyConsumed(obfEnergyConsumed)
                    .setEnergyConsumedDaytime(obfEnergyConsumedDaytime)
                    .setEnergyConsumedNight(obfEnergyConsumedNight)
                    .setEnergyProduced(obfEnergyProduced)
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
            throws SQLException, InvalidSessionTokenException, ClientDoesNotExistException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        PreparedStatement st;
        ResultSet rs;
        List<Invoice> invoices = new ArrayList<>();

        validateSession(email, hashedToken);
        int client_id = getClientId(email);

        st = dbConnection.prepareStatement(READ_INVOICES);
        st.setInt(1, client_id);
        rs = st.executeQuery();

        while (rs.next()) {
            byte[] iv = rs.getBytes(1);
            int year = rs.getInt(2);
            int month = rs.getInt(3);
            int taxes = rs.getInt(4);

            byte[] paymentAmount = Security.decryptData(rs.getBytes(5), energyPanelKey, iv);
            byte[] energyConsumed = Security.decryptData(rs.getBytes(6), energyPanelKey, iv);
            byte[] energyConsumedDaytime = Security.decryptData(rs.getBytes(7), energyPanelKey, iv);
            byte[] energyConsumedNight = Security.decryptData(rs.getBytes(8), energyPanelKey, iv);

            String obfPaymentAmount = obfuscate(new String(paymentAmount));
            String obfEnergyConsumed = obfuscate(new String(energyConsumed));
            String obfEnergyConsumedDaytime = obfuscate(new String(energyConsumedDaytime));
            String obfEnergyConsumedNight = obfuscate(new String(energyConsumedNight));

            String plan = rs.getString(9);

            Invoice invoice = Invoice.newBuilder()
                    .setYear(year)
                    .setMonth(months.get(month))
                    .setPaymentAmount(obfPaymentAmount)
                    .setEnergyConsumed(obfEnergyConsumed)
                    .setEnergyConsumedDaytime(obfEnergyConsumedDaytime)
                    .setEnergyConsumedNight(obfEnergyConsumedNight)
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
            IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException,
            BadPaddingException {

        PreparedStatement st;

        validateSession(email, hashedToken);

        byte[] iv = getIv(email);

        st = dbConnection.prepareStatement(UPDATE_CLIENT_ADDRESS);
        st.setBytes(1, Security.encryptData(address, personalInfoKey, iv));
        st.setString(2, obfuscate(address));
        st.setString(3, email);
        st.executeUpdate();
        st.close();
    }

    public void updatePlan(String email, String plan, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException, CompartmentKeyException,
            IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException {

        validateSession(email, hashedToken);

        byte[] iv = getIv(email);

        PreparedStatement st = dbConnection.prepareStatement(UPDATE_CLIENT_PLAN);
        st.setString(1, plan);
        st.setString(2, email);
        st.executeUpdate();
        st.close();
    }

    /*
    ------------------------------------------------------
    ---------------- AUXILIARY FUNCTIONS -----------------
    ------------------------------------------------------
     */

    public byte[] getIv(String email) throws SQLException, ClientDoesNotExistException {
        PreparedStatement st;
        ResultSet rs;
        byte[] iv;

        st = dbConnection.prepareStatement(READ_CLIENT_IV);
        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next()) {
            iv = rs.getBytes(1);
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }

        st.close();
        return iv;
    }

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
            throws SQLException, ClientDoesNotExistException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        PreparedStatement st;
        ResultSet rs;
        float currEnergyConsumed, currEnergyConsumedDaytime, currEnergyConsumedNight;

        byte[] iv = getIv(email);

        // get current energy consumption
        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_CONSUMPTION);
        st.setString(1, email);

        rs = st.executeQuery();
        if (rs.next()){
            byte[] currEnergyConsumedBytes = Security.decryptData(rs.getBytes(1), energyPanelKey, iv);
            byte[] currEnergyConsumedDaytimeBytes = Security.decryptData(rs.getBytes(2), energyPanelKey, iv);
            byte[] currEnergyConsumedNightBytes = Security.decryptData(rs.getBytes(3), energyPanelKey, iv);

            currEnergyConsumed = Float.parseFloat(new String(currEnergyConsumedBytes));
            currEnergyConsumedDaytime = Float.parseFloat(new String(currEnergyConsumedDaytimeBytes));
            currEnergyConsumedNight = Float.parseFloat(new String(currEnergyConsumedNightBytes));
        }
        else {
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        energyConsumed += currEnergyConsumed;
        energyConsumedDaytime += currEnergyConsumedDaytime;
        energyConsumedNight += currEnergyConsumedNight;

        // update energy consumption
        st = dbConnection.prepareStatement(UPDATE_CLIENT_ENERGY_CONSUMPTION);
        st.setBytes(1, Security.encryptData(String.valueOf(energyConsumed), energyPanelKey, iv));
        st.setBytes(2, Security.encryptData(String.valueOf(energyConsumedDaytime), energyPanelKey, iv));
        st.setBytes(3, Security.encryptData(String.valueOf(energyConsumedNight), energyPanelKey, iv));

        st.setString(4, obfuscate(Float.toString(energyConsumed)));
        st.setString(5, obfuscate(Float.toString(energyConsumedDaytime)));
        st.setString(6, obfuscate(Float.toString(energyConsumedNight)));

        st.setString(7, email);
        st.executeUpdate();
        st.close();
    }

    public void updateEnergyProduction(String email, float energyProduced) throws SQLException, ClientDoesNotExistException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        PreparedStatement st;
        ResultSet rs;
        float currEnergyProduced;

        byte[] iv = getIv(email);

        // get current energy consumption
        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_PRODUCTION);
        st.setString(1, email);
        rs = st.executeQuery();
        if (rs.next()){
            byte[] currEnergyProducedBytes = Security.decryptData(rs.getBytes(1), energyPanelKey, iv);
            currEnergyProduced = Float.parseFloat(new String(currEnergyProducedBytes));
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        energyProduced += currEnergyProduced;

        // update energy consumption
        st = dbConnection.prepareStatement(UPDATE_CLIENT_ENERGY_PRODUCTION);
        st.setBytes(1, Security.encryptData(String.valueOf(energyProduced), energyPanelKey, iv));
        st.setString(2, obfuscate(Float.toString(energyProduced)));
        st.setString(3, email);
        st.executeUpdate();
        st.close();
    }

    public List<Appliance> getAppliances(int clientId) throws SQLException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        PreparedStatement st;
        ResultSet rs;
        List<Appliance> appliances = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_APPLIANCES);
        st.setInt(1, clientId);
        rs = st.executeQuery();

        while (rs.next()) {
            byte[] iv = rs.getBytes(1);
            String name = rs.getString(2);
            String brand = rs.getString(3);

            byte[] energyConsumed = Security.decryptData(rs.getBytes(4), energyPanelKey, iv);
            byte[] energyConsumedDaytime = Security.decryptData(rs.getBytes(5), energyPanelKey, iv);
            byte[] energyConsumedNight = Security.decryptData(rs.getBytes(6), energyPanelKey, iv);

            String obfEnergyConsumed = obfuscate(new String(energyConsumed));
            String obfEnergyConsumedDaytime = obfuscate(new String(energyConsumedDaytime));
            String obfEnergyConsumedNight = obfuscate(new String(energyConsumedNight));

            Appliance appliance = Appliance.newBuilder()
                    .setName(name)
                    .setBrand(brand)
                    .setEnergyConsumed(obfEnergyConsumed)
                    .setEnergyConsumedDaytime(obfEnergyConsumedDaytime)
                    .setEnergyConsumedNight(obfEnergyConsumedNight)
                    .build();
            appliances.add(appliance);
        }
        st.close();

        return appliances;
    }

    public List<SolarPanel> getSolarPanels(int clientId) throws SQLException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        PreparedStatement st;
        ResultSet rs;
        List<SolarPanel> solarPanels = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_SOLAR_PANELS);
        st.setInt(1, clientId);
        rs = st.executeQuery();


        while (rs.next()) {
            byte[] iv = rs.getBytes(1);
            String name = rs.getString(2);
            String brand = rs.getString(3);

            byte[] energyProduced = Security.decryptData(rs.getBytes(4), energyPanelKey, iv);
            String obfEnergyProduced = obfuscate(new String(energyProduced));

            SolarPanel solarPanel = SolarPanel.newBuilder()
                    .setName(name)
                    .setBrand(brand)
                    .setEnergyProduced(obfEnergyProduced)
                    .build();
            solarPanels.add(solarPanel);
        }
        st.close();

        return solarPanels;
    }

}