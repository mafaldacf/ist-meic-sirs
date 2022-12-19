package pt.ulisboa.tecnico.sirs.backoffice;

import pt.ulisboa.tecnico.sirs.backoffice.exceptions.*;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.*;
import pt.ulisboa.tecnico.sirs.crypto.Crypto;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static pt.ulisboa.tecnico.sirs.backoffice.DatabaseQueries.*;

public class Backoffice {

    private final Connection dbConnection;
    private final KeyPair backofficeKeyPair;
    private final KeyPair accountManagementKeyPair;
    private final KeyPair energyManagementKeyPair;

    private static final String KEY_STORE_FILE = "src/main/resources/backoffice.jks";
    private static final String KEY_STORE_TYPE = "JKS";
    private static final String KEY_STORE_PASSWORD = "backoffice";
    private static final String KEY_STORE_ALIAS_WEBSERVER = "webserver";

    public Backoffice(Connection dbConnection, KeyPair backofficeKeyPair, KeyPair accountManagementKeyPair, KeyPair energyManagementKeyPair) {
        this.dbConnection = dbConnection;
        this.backofficeKeyPair = backofficeKeyPair;
        this.accountManagementKeyPair = accountManagementKeyPair;
        this.energyManagementKeyPair = energyManagementKeyPair;
    }

    public KeyPair getRoleKeyPair(String role) throws InvalidRoleException {
        switch(role) {
            case "ACCOUNT_MANAGER":
                return accountManagementKeyPair;
            case "ENERGY_MANAGER":
                return energyManagementKeyPair;
            default:
                throw new InvalidRoleException(role);
        }
    }

    public String getCompartmentKeyString(String role, String query) throws SQLException, CompartmentKeyException,
            IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidRoleException {

        PreparedStatement st;
        Key key;
        byte[] wrappedKey;
        ResultSet rs;

        KeyPair roleKeyPair = getRoleKeyPair(role);

        st = dbConnection.prepareStatement(query);
        st.setString(1, role);
        rs = st.executeQuery();

        if (rs.next()){
            wrappedKey = rs.getBytes(1);
            key = Crypto.unwrapKey(roleKeyPair.getPrivate(), wrappedKey);
        }
        else {
            st.close();
            throw new CompartmentKeyException();
        }
        st.close();

        return key.toString();
    }

    /*
    -----------------------------------------------
    -------------- WEBSERVER SERVICE --------------
    -----------------------------------------------
     */

    public List<byte[]> getCompartmentKeys() throws IllegalBlockSizeException, NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, SQLException, CompartmentKeyException, KeyStoreException,
            IOException, CertificateException {

        PreparedStatement st;
        byte[] wrappedPersonalInfoKey, wrappedEnergyPanelKey;
        ResultSet rs;
        Key personalInfoKey, energyPanelKey;

        // get all compartment keys
        st = dbConnection.prepareStatement(READ_COMPARTMENT_KEYS);
        rs = st.executeQuery();

        if (rs.next()){
            wrappedPersonalInfoKey = rs.getBytes(1);
            wrappedEnergyPanelKey = rs.getBytes(2);
            personalInfoKey = Crypto.unwrapKey(backofficeKeyPair.getPrivate(), wrappedPersonalInfoKey);
            energyPanelKey = Crypto.unwrapKey(backofficeKeyPair.getPrivate(), wrappedEnergyPanelKey);
        }
        else {
            st.close();
            throw new CompartmentKeyException();
        }
        st.close();

        // encrypt compartment keys with public key of webserver

        KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
        keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());
        PublicKey webserverPublicKey = keyStore.getCertificate(KEY_STORE_ALIAS_WEBSERVER).getPublicKey();

        List<byte[]> keys = new ArrayList<>();
        keys.add(Crypto.wrapKey(webserverPublicKey, personalInfoKey));
        keys.add(Crypto.wrapKey(webserverPublicKey, energyPanelKey));
        return keys;
    }

    /*
    ------------------------------------------------
    ------------- ADMIN SESSION TOKEN --------------
    ------------------------------------------------
     */

    public String setAdminSession(String username) throws NoSuchAlgorithmException, SQLException {

        PreparedStatement st;

        String token = Crypto.generateToken();
        String hashedToken = Crypto.hash(token);

        st = dbConnection.prepareStatement(UPDATE_ADMIN_TOKEN);
        st.setString(1, hashedToken);
        st.setString(2, username);
        st.executeUpdate();
        st.close();

        return hashedToken;
    }

    public void validateSession(String username, String hashedToken)
            throws SQLException, AdminDoesNotExistException, InvalidSessionTokenException {

        PreparedStatement st;
        ResultSet rs;

        st = dbConnection.prepareStatement(READ_ADMIN_COUNT);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) == 0){
            throw new AdminDoesNotExistException(username);
        }
        st.close();

        st = dbConnection.prepareStatement(READ_ADMIN_TOKEN);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()){
            String dbHashedToken = rs.getString(1);
            if (!dbHashedToken.equals(hashedToken))
                throw new InvalidSessionTokenException();
        }
        st.close();
    }

    /*
    -----------------------------------------------
    ---------------- ADMIN SERVICE ----------------
    -----------------------------------------------
     */

    public void register(String username, String password, String role)
            throws SQLException, AdminAlreadyExistsException {

        PreparedStatement st;
        ResultSet rs;

        // check if username is already registered
        st = dbConnection.prepareStatement(READ_ADMIN_COUNT);
        st.setString(1, username);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) != 0){
            throw new AdminAlreadyExistsException(username);
        }

        st.close();

        // register username
        st = dbConnection.prepareStatement(CREATE_ADMIN);

        st.setString(1, username);
        st.setString(2, password);
        st.setString(3, role);

        st.executeUpdate();


        st.close();
    }

    public List<String> login(String username, String password)
            throws AdminDoesNotExistException, SQLException, WrongPasswordException,
            NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        List<String> response = new ArrayList<>();
        PreparedStatement st;
        ResultSet rs;
        String role;

        st = dbConnection.prepareStatement(READ_ADMIN_PASSWORD_ROLE);
        st.setString(1, username);

        rs = st.executeQuery();

        if (rs.next()) {
            String dbPassword = rs.getString(1);
            if (!password.equals(dbPassword)) {
                throw new WrongPasswordException();
            }
            role = rs.getString(2);
        }
        else {
            throw new AdminDoesNotExistException(username);
        }
        st.close();

        String hashedToken = setAdminSession(username);
        response.add(role);
        response.add(hashedToken);
        return response;
    }

    public boolean logout(String username, String hashedToken)
            throws SQLException, AdminDoesNotExistException, InvalidSessionTokenException {

        PreparedStatement st;

        validateSession(username, hashedToken);

        st = dbConnection.prepareStatement(UPDATE_ADMIN_TOKEN);
        st.setString(1, "");
        st.setString(2, username);
        return true;
    }

    public List<Client> listClients(String username, String hashedToken)
            throws SQLException, AdminDoesNotExistException, InvalidSessionTokenException {
        Statement st;
        ResultSet rs;

        List<Client> clients = new ArrayList<>();

        validateSession(username, hashedToken);

        st = dbConnection.createStatement();
        rs = st.executeQuery(READ_ALL_CLIENTS_NAME_EMAIL);

        while(rs.next()) {
            String name = rs.getString(1);
            String email = rs.getString(2);

            Client client = Client.newBuilder()
                    .setName(name)
                    .setEmail(email)
                    .build();

            clients.add(client);
        }
        st.close();

        return clients;
    }

    public PersonalInfo checkPersonalInfo(String username, String email, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, AdminDoesNotExistException,
            InvalidRoleException, PermissionDeniedException, CompartmentKeyException, IllegalBlockSizeException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {

        PersonalInfo personalInfo;
        PreparedStatement st;
        ResultSet rs;

        validateSession(username, hashedToken);
        String role = verifyPermissions(username, READ_PERMISSION_PERSONAL_INFO);
        String personalInfoKeyString = getCompartmentKeyString(role, READ_PERSONAL_INFO_KEY_WITH_ROLE);

        // get personal info
        st = dbConnection.prepareStatement(READ_CLIENT_PERSONAL_INFO);
        st.setString(1, personalInfoKeyString);
        st.setString(2, personalInfoKeyString);
        st.setString(3, personalInfoKeyString);
        st.setString(4, email);
        rs = st.executeQuery();

        if (rs.next()) {
            String name = rs.getString(1);
            email = rs.getString(2);
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
            throw new ClientDoesNotExistException(email);
        }

        st.close();

        return personalInfo;
    }

    public EnergyPanel checkEnergyPanel(String username, String email, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, AdminDoesNotExistException,
            InvalidRoleException, PermissionDeniedException, CompartmentKeyException, IllegalBlockSizeException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {

        EnergyPanel energyPanel;
        List<Appliance> appliances;
        List<SolarPanel> solarPanels;
        PreparedStatement st;
        ResultSet rs;

        validateSession(username, hashedToken);
        String role = verifyPermissions(username, READ_PERMISSION_ENERGY_PANEL);
        String energyPanelKeyString = getCompartmentKeyString(role, READ_ENERGY_PANEL_KEY_WITH_ROLE);
        int clientId = getClientId(email);

        appliances = getAppliances(clientId, energyPanelKeyString);
        solarPanels = getSolarPanels(clientId, energyPanelKeyString);

        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_PANEL);
        st.setString(1, energyPanelKeyString);
        st.setString(2, energyPanelKeyString);
        st.setString(3, energyPanelKeyString);
        st.setString(4, energyPanelKeyString);
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

    public boolean deleteClient(String username, String email, String hashedToken)
            throws SQLException, AdminDoesNotExistException, ClientDoesNotExistException, InvalidSessionTokenException {
        PreparedStatement st;

        validateSession(username, hashedToken);

        int clientId = getClientId(email);

        st = dbConnection.prepareStatement(DELETE_CLIENT);
        st.setString(1, email);
        int success = st.executeUpdate();
        st.close();

        if (success == 0) {
            throw new ClientDoesNotExistException(email);
        }

        return true;
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

    public List<Appliance> getAppliances(int clientId, String energyPanelKeyString) throws SQLException {
        PreparedStatement st;
        ResultSet rs;
        List<Appliance> appliances = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_APPLIANCES);
        st.setString(1, energyPanelKeyString);
        st.setString(2, energyPanelKeyString);
        st.setString(3, energyPanelKeyString);
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

    public List<SolarPanel> getSolarPanels(int clientId, String energyPanelKeyString) throws SQLException {
        PreparedStatement st;
        ResultSet rs;
        List<SolarPanel> solarPanels = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_SOLAR_PANELS);
        st.setString(1, energyPanelKeyString);
        st.setInt(2, clientId);
        rs = st.executeQuery();


        while (rs.next()) {
            String name = rs.getString(1);
            String brand = rs.getString(2);
            float energyProduced = rs.getInt(3);

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

    public String verifyPermissions(String username, String query) throws SQLException, AdminDoesNotExistException,
            InvalidRoleException, PermissionDeniedException {

        PreparedStatement st;
        ResultSet rs;
        String role;

        st = dbConnection.prepareStatement(READ_ADMIN_ROLE);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()) {
            role = rs.getString(1);
        }
        else {
            throw new AdminDoesNotExistException(username);
        }

        st.close();

        st = dbConnection.prepareStatement(query);
        st.setString(1, role);
        rs = st.executeQuery();

        if (rs.next()) {
            boolean permitted = rs.getBoolean(1);
            if (!permitted) {
                st.close();
                throw new PermissionDeniedException(role);
            }

        }
        else {
            st.close();
            throw new InvalidRoleException(role);
        }

        st.close();
        return role;
    }

}
