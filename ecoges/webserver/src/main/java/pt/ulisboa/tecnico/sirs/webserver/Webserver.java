package pt.ulisboa.tecnico.sirs.webserver;

import pt.ulisboa.tecnico.sirs.crypto.Crypto;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.*;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.*;

public class Webserver {
    private final Connection dbConnection;
    private final int MAX_ENERGY_CONSUMPTION = 100;
    private final int MAX_ENERGY_PRODUCTION = 100;

    private static List<String> months = new ArrayList<>(Arrays.asList
            ("Jan", "Feb", "Mar", "Apr", "Mai", "Jun", "Jul", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"));

    public Webserver(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /*
    ------------------------------------------------------
    ---------------- CLIENT SESSION TOKEN ----------------
    ------------------------------------------------------
     */

    public String setClientSession(String email) throws NoSuchAlgorithmException, SQLException {
        PreparedStatement st;

        String token = Crypto.generateToken();
        String hashedToken = Crypto.hash(token);

        st = dbConnection.prepareStatement(UPDATE_CLIENT_TOKEN);
        st.setString(1, hashedToken);
        st.setString(2, email);
        st.executeUpdate();
        st.close();

        return hashedToken;
    }

    public void validateSession(String email, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException, No2FAException {
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

        // Check if user has 2FA set up, if not, no actions can be made
        st = dbConnection.prepareStatement(READ_MOBILE_TOKEN_WITH_EMAIL);
        st.setString(1, email);
        rs = st.executeQuery();
        if(!rs.next() || rs.getString(1).equals("")){
            throw new No2FAException();
        }
        st.close();
    }

    /*
    ------------------------------------------------------
    --------------- CLIENT FUNCTIONALITIES ---------------
    ------------------------------------------------------
     */
    


    public void register(String name, String email, String password, String address, String iban, String plan) throws SQLException, ClientAlreadyExistsException, NoSuchAlgorithmException {
        PreparedStatement st;
        ResultSet rs;

        // check if email is already registered
        st = dbConnection.prepareStatement(READ_CLIENT_COUNT);
        st.setString(1, email);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) != 0){
            throw new ClientAlreadyExistsException(email);
        }

        st.close();

        // create client

        byte[] salt = Crypto.generateSalt();
        String hashedPassword = Crypto.hashWithSalt(password, salt);

        st = dbConnection.prepareStatement(CREATE_CLIENT);
        st.setString(1, name);
        st.setString(2, email);
        st.setString(3, address);
        st.setString(4, hashedPassword);
        st.setString(5, iban);
        st.setString(6, plan);
        st.setBytes(7, salt);
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
            String hashedPassword = Crypto.hashWithSalt(password, salt);
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
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException, No2FAException {
        PreparedStatement st;

        validateSession(email, hashedToken);

        st = dbConnection.prepareStatement(UPDATE_CLIENT_TOKEN);
        st.setString(1, "");
        st.setString(2, email);

        st.executeUpdate();
    }

    public String registerMobile(String email, String password) throws SQLException, ClientDoesNotExistException, WrongPasswordException ,NoSuchAlgorithmException, UserAlreadyHasMobileException {
        PreparedStatement st;
        ResultSet rs;
        int id;

        st = dbConnection.prepareStatement(READ_CLIENT_ID_PASSWORD_SALT);
        st.setString(1, email);

        rs = st.executeQuery();

        if (rs.next()) {
            id = rs.getInt(1);
            String dbHashedPassword = rs.getString(2);
            byte[] salt = rs.getBytes(3);
            String hashedPassword = Crypto.hashWithSalt(password, salt);
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

        st = dbConnection.prepareStatement(READ_MOBILE_TOKEN_SALT);
        st.setInt(1, id);

        rs = st.executeQuery();

        if(rs.next()){
            throw new UserAlreadyHasMobileException();
        }
        st.close();

        byte[] salt = Crypto.generateSalt();
        String token = Crypto.generateToken();
        String hashedToken = Crypto.hashWithSalt(token, salt);

        st = dbConnection.prepareStatement(CREATE_MOBILE); 

        st.setString(1, hashedToken);
        st.setInt(2, id);
        st.setBytes(3, salt);
        st.executeUpdate();
        st.close();

        return token;
    }

    public void addApplicance(String email, String applianceName, String applianceBrand, String hashedToken)
            throws SQLException, InvalidSessionTokenException, ClientDoesNotExistException, ApplianceAlreadyExistsException, No2FAException {

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
        st.setFloat(5, energyConsumedDaytime);
        st.setFloat(6, energyConsumedNight);
        st.executeUpdate();
        st.close();

        updateEnergyConsumption(email, energyConsumed, energyConsumedDaytime, energyConsumedNight);
    }

    public void addSolarPanel(String email, String solarPanelName, String solarPanelBrand, String hashedToken)
            throws SQLException, InvalidSessionTokenException, ClientDoesNotExistException, SolarPanelAlreadyExistsException, No2FAException {
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
        st.executeUpdate();
        st.close();

        updateEnergyProduction(email, energyProduced);
    }

    public PersonalInfo checkPersonalInfo(String clientEmail, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, No2FAException {
        PersonalInfo personalInfo;
        PreparedStatement st;
        ResultSet rs;

        validateSession(clientEmail, hashedToken);

        // get personal info
        st = dbConnection.prepareStatement(READ_CLIENT_PERSONAL_INFO);
        st.setString(1, clientEmail);
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
            st.close();
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(clientEmail);
        }

        

        return personalInfo;
    }

    public EnergyPanel checkEnergyPanel(String email, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, No2FAException {
        EnergyPanel energyPanel;
        List<Appliance> appliances;
        List<SolarPanel> solarPanels;
        PreparedStatement st;
        ResultSet rs;

        validateSession(email, hashedToken);
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

    public List<Invoice> checkInvoices(String email, String hashedToken)
            throws SQLException, InvalidSessionTokenException, ClientDoesNotExistException, No2FAException {
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
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException, No2FAException {
        PreparedStatement st;

        validateSession(email, hashedToken);

        // update address
        st = dbConnection.prepareStatement(UPDATE_CLIENT_ADDRESS);
        st.setString(1, address);
        st.setString(2, email);
        st.executeUpdate();
        st.close();
    }

    public void updatePlan(String email, String plan, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException, No2FAException {

        validateSession(email, hashedToken);

        // update plan
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
        st.setString(1, email);
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
        st.setFloat(2, currEnergyConsumedDaytime + energyConsumedDaytime);
        st.setFloat(3, currEnergyConsumedNight + energyConsumedNight);
        st.setString(4, email);
        st.executeUpdate();
        st.close();
    }

    public void updateEnergyProduction(String email, float energyProduced) throws SQLException, ClientDoesNotExistException {
        PreparedStatement st;
        ResultSet rs;
        float currEnergyProduced;

        // get current energy consumption
        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_PRODUCTION);
        st.setString(1, email);
        rs = st.executeQuery();
        if (rs.next()){
            currEnergyProduced = rs.getFloat(1);
        }
        else {
            throw new ClientDoesNotExistException(email);
        }
        currEnergyProduced = rs.getFloat(1);
        st.close();

        // update energy consumption
        st = dbConnection.prepareStatement(UPDATE_CLIENT_ENERGY_PRODUCTION);
        st.setFloat(1, currEnergyProduced + energyProduced);
        st.setString(2, email);
        st.executeUpdate();
        st.close();
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
