package pt.ulisboa.tecnico.sirs.webserver;

import pt.ulisboa.tecnico.sirs.crypto.Crypto;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.*;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Webserver {
    private final Connection dbConnection;

    public Webserver(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public String setClientSession(String email)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, SQLException {

        String query;
        PreparedStatement st;

        String token = Crypto.generateToken();
        String hashedToken = Crypto.hash(token);

        query = "UPDATE client SET token = ? WHERE email = ?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, hashedToken);
        st.setString(2, email);
        st.executeUpdate();
        st.close();

        return hashedToken;
    }

    public void validateSession(String email, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException {

        String query;
        PreparedStatement st;
        ResultSet rs;

        query = "SELECT COUNT(*) FROM client WHERE email = ?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) == 0){
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        query = "SELECT token FROM client WHERE email = ?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next()){
            String dbHashedToken = rs.getString(1);
            if (!dbHashedToken.equals(hashedToken))
                throw new InvalidSessionTokenException();
        }
        st.close();
    }

    public void register(String email, String password, String address, String plan) throws SQLException, ClientAlreadyExistsException {
        String query;
        PreparedStatement st;
        ResultSet rs;

        // check if email is already registered
        query = "SELECT COUNT(*) FROM client WHERE email=?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, email);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) != 0){
            throw new ClientAlreadyExistsException(email);
        }

        st.close();

        // register email
        query = "INSERT INTO client(email, password, address, plan) VALUES(?, ?, ?, ?)";
        st = dbConnection.prepareStatement(query);

        st.setString(1, email);
        st.setString(2, password);
        st.setString(3, address);
        st.setString(4, plan);

        st.executeUpdate();


        st.close();
    }

    public String login(String email, String password)
            throws ClientDoesNotExistException, SQLException,
            WrongPasswordException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        String query;
        PreparedStatement st;
        ResultSet rs;

        query = "SELECT * FROM client WHERE email=?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, email);

        rs = st.executeQuery();

        if (rs.next()) {
            String dbPassword = rs.getString(3);
            if (!password.equals(dbPassword)) {
                throw new WrongPasswordException();
            }
        }
        else {
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        String hashedToken = setClientSession(email);

        return hashedToken;
    }

    public boolean logout(String email, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException, LogoutException {

        String query;
        PreparedStatement st;

        validateSession(email, hashedToken);

        query = "UPDATE client SET token = ? WHERE email = ?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, "");
        st.setString(2, email);

        int result = st.executeUpdate();

        if (result == 0) {
            throw new LogoutException();
        }

        return true;
    }

    public List<String> checkPersonalInfo(String clientEmail, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException {

        String query;
        PreparedStatement st;
        ResultSet rs;
        List<String> personalInfo = new ArrayList<>();

        validateSession(clientEmail, hashedToken);

        // get personal info
        query = "SELECT email, address, plan FROM client WHERE email=?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, clientEmail);
        rs = st.executeQuery();

        if (rs.next()) {
            String email = rs.getString(1);
            String address = rs.getString(2);
            String plan = rs.getString(3);
            personalInfo.add(email);
            personalInfo.add(address);
            personalInfo.add(plan);
        }
        else {
            throw new ClientDoesNotExistException(clientEmail);
        }

        st.close();

        return personalInfo;
    }

    public List<Float> checkEnergyConsumption(String email, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException {

        String query;
        PreparedStatement st;
        ResultSet rs;
        List<Float> energyConsumption = new ArrayList<>();

        validateSession(email, hashedToken);

        // get personal info
        query = "SELECT energyConsumedPerMonth, energyConsumedPerHour FROM client WHERE email=?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next()) {
            Float energyConsumedPerMonth = rs.getFloat(1);
            Float energyConsumedPerHour = rs.getFloat(2);
            energyConsumption.add(energyConsumedPerMonth);
            energyConsumption.add(energyConsumedPerHour);
        }
        else {
            throw new ClientDoesNotExistException(email);
        }

        st.close();

        return energyConsumption;
    }

    public void updateAddress(String email, String address, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException {

        String query;
        PreparedStatement st;

        validateSession(email, hashedToken);

        // get personal info
        query = "UPDATE client SET address = ? WHERE email = ?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, address);
        st.setString(2, email);
        st.executeUpdate();
        st.close();
    }

    public void updatePlan(String email, String plan, String hashedToken)
            throws SQLException, ClientDoesNotExistException, InvalidSessionTokenException {

        String query;
        PreparedStatement st;

        validateSession(email, hashedToken);

        // get personal info
        query = "UPDATE client SET plan = ? WHERE email = ?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, plan);
        st.setString(2, email);
        st.executeUpdate();
        st.close();
    }

}
