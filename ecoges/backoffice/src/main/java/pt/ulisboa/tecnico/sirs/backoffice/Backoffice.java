package pt.ulisboa.tecnico.sirs.backoffice;

import pt.ulisboa.tecnico.sirs.backoffice.exceptions.*;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.*;
import pt.ulisboa.tecnico.sirs.crypto.Crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static pt.ulisboa.tecnico.sirs.backoffice.DatabaseQueries.*;

public class Backoffice {

    private final Connection dbConnection;

    public Backoffice(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

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

        int result = st.executeUpdate();

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
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, AdminDoesNotExistException {
        PersonalInfo personalInfo;
        PreparedStatement st;
        ResultSet rs;

        validateSession(username, hashedToken);

        // get personal info
        st = dbConnection.prepareStatement(READ_CLIENT_PERSONAL_INFO);
        st.setString(1, email);
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
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, AdminDoesNotExistException {
        EnergyPanel energyPanel;
        List<Appliance> appliances;
        List<SolarPanel> solarPanels;
        PreparedStatement st;
        ResultSet rs;

        validateSession(username, hashedToken);
        int client_id = getClientId(email);

        appliances = getAppliances(client_id);
        solarPanels = getSolarPanels(client_id);

        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_CONSUMPTION_PRODUCTION);
        st.setString(1, email);
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

    public List<Appliance> getAppliances(int client_id) throws SQLException {
        PreparedStatement st;
        ResultSet rs;
        List<Appliance> appliances = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_APPLIANCES);
        st.setInt(1, client_id);
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

    public List<SolarPanel> getSolarPanels(int client_id) throws SQLException {
        PreparedStatement st;
        ResultSet rs;
        List<SolarPanel> solarPanels = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_SOLAR_PANELS);
        st.setInt(1, client_id);
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

}
