package pt.ulisboa.tecnico.sirs.webserver;

import pt.ulisboa.tecnico.sirs.webserver.exceptions.HelloException;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.ClientDoesNotExistException;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.ClientAlreadyExistsException;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.WrongPasswordException;
import pt.ulisboa.tecnico.sirs.webserver.grpc.PlanType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Webserver {
    private final Connection dbConnection;

    public Webserver(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public void register(String username, String password, String address, String plan) throws SQLException, ClientAlreadyExistsException {
        String query;
        PreparedStatement st;
        ResultSet rs;

        // check if username is already registered
        query = "SELECT COUNT(*) FROM client WHERE username=?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, username);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) != 0){
            throw new ClientAlreadyExistsException(username);
        }

        st.close();

        // register username
        query = "INSERT INTO client(username, password, address, plan) VALUES(?, ?, ?, ?)";
        st = dbConnection.prepareStatement(query);

        st.setString(1, username);
        st.setString(2, password);
        st.setString(3, address);
        st.setString(4, plan);

        st.executeUpdate();


        st.close();
    }

    public void login(String username, String password) throws ClientDoesNotExistException, SQLException, WrongPasswordException {
        String query;
        PreparedStatement st;
        ResultSet rs;

        query = "SELECT * FROM client WHERE username=?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, username);

        rs = st.executeQuery();

        if (rs.next()) {
            String dbPassword = rs.getString(3);
            if (!password.equals(dbPassword)) {
                throw new WrongPasswordException();
            }
        }
        else {
            throw new ClientDoesNotExistException(username);
        }
        st.close();
    }

    public void validateClientSession(String username) throws SQLException, ClientDoesNotExistException {
        //TODO: we should not receive a username, we should receive a session cookie later
        String query;
        PreparedStatement st;
        ResultSet rs;

        query = "SELECT COUNT(*) FROM client WHERE username=?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, username);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) == 0){
            throw new ClientDoesNotExistException(username);
        }
        st.close();
    }

    public List<String> checkPersonalInfo(String username) throws ClientDoesNotExistException, SQLException {
        String query;
        PreparedStatement st;
        ResultSet rs;
        List<String> personalInfo = new ArrayList<>();

        validateClientSession(username);

        // get personal info
        query = "SELECT address, plan FROM client WHERE username=?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()) {
            String address = rs.getString(1);
            String plan = rs.getString(2);
            personalInfo.add(address);
            personalInfo.add(plan);
        }
        else {
            throw new ClientDoesNotExistException(username);
        }

        st.close();

        return personalInfo;
    }

    public List<Float> checkEnergyConsumption(String username) throws ClientDoesNotExistException, SQLException {
        String query;
        PreparedStatement st;
        ResultSet rs;
        List<Float> energyConsumption = new ArrayList<>();

        validateClientSession(username);

        // get personal info
        query = "SELECT energyConsumedPerMonth, energyConsumedPerHour FROM client WHERE username=?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()) {
            Float energyConsumedPerMonth = rs.getFloat(1);
            Float energyConsumedPerHour = rs.getFloat(2);
            energyConsumption.add(energyConsumedPerMonth);
            energyConsumption.add(energyConsumedPerHour);
        }
        else {
            throw new ClientDoesNotExistException(username);
        }

        st.close();

        return energyConsumption;
    }

    public void updateAddress(String username, String address) throws SQLException, ClientDoesNotExistException {
        String query;
        PreparedStatement st;

        validateClientSession(username);

        // get personal info
        query = "UPDATE client SET address = ? WHERE username = ?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, address);
        st.setString(2, username);
        st.executeUpdate();
        st.close();
    }

    public void updatePlan(String username, String plan) throws SQLException, ClientDoesNotExistException {
        String query;
        PreparedStatement st;

        validateClientSession(username);

        // get personal info
        query = "UPDATE client SET plan = ? WHERE username = ?";
        st = dbConnection.prepareStatement(query);
        st.setString(1, plan);
        st.setString(2, username);
        st.executeUpdate();
        st.close();
    }

}
