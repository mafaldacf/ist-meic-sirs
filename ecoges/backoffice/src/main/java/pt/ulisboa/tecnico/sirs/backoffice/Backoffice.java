package pt.ulisboa.tecnico.sirs.backoffice;

import pt.ulisboa.tecnico.sirs.backoffice.exceptions.*;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.Client;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.PlanType;
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

    public String setAdminSession(String username)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, SQLException {

        String query;
        PreparedStatement st;

        String token = Crypto.generateToken();
        String hashedToken = Crypto.hash(token);

        query = UPDATE_ADMIN_TOKEN;
        st = dbConnection.prepareStatement(query);
        st.setString(1, hashedToken);
        st.setString(2, username);
        st.executeUpdate();
        st.close();

        return hashedToken;
    }

    public void validateSession(String username, String hashedToken)
            throws SQLException, AdminDoesNotExistException, InvalidSessionTokenException {

        String query;
        PreparedStatement st;
        ResultSet rs;

        query = READ_ADMIN_COUNT;
        st = dbConnection.prepareStatement(query);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) == 0){
            throw new AdminDoesNotExistException(username);
        }
        st.close();

        query = READ_ADMIN_TOKEN;
        st = dbConnection.prepareStatement(query);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()){
            String dbHashedToken = rs.getString(1);
            if (!dbHashedToken.equals(hashedToken))
                throw new InvalidSessionTokenException();
        }
        st.close();
    }

    public void register(String username, String password)
            throws SQLException, AdminAlreadyExistsException {

        String query;
        PreparedStatement st;
        ResultSet rs;

        // check if username is already registered
        query = READ_ADMIN_COUNT;
        st = dbConnection.prepareStatement(query);
        st.setString(1, username);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) != 0){
            throw new AdminAlreadyExistsException(username);
        }

        st.close();

        // register username
        query = CREATE_ADMIN;
        st = dbConnection.prepareStatement(query);

        st.setString(1, username);
        st.setString(2, password);

        st.executeUpdate();


        st.close();
    }

    public String login(String username, String password)
            throws AdminDoesNotExistException, SQLException, WrongPasswordException,
            NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        String query;
        PreparedStatement st;
        ResultSet rs;

        query = READ_ADMIN;
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
            throw new AdminDoesNotExistException(username);
        }
        st.close();

        String hashedToken = setAdminSession(username);
        return hashedToken;
    }

    public boolean logout(String username, String hashedToken)
            throws SQLException, AdminDoesNotExistException, InvalidSessionTokenException, LogoutException {

        String query;
        PreparedStatement st;

        validateSession(username, hashedToken);

        query = UPDATE_ADMIN_TOKEN;
        st = dbConnection.prepareStatement(query);
        st.setString(1, "");
        st.setString(2, username);

        int result = st.executeUpdate();

        if (result == 0) {
            throw new LogoutException();
        }

        return true;
    }

    public List<Client> listClients(String username, String hashedToken)
            throws SQLException, AdminDoesNotExistException, InvalidSessionTokenException {

        String query;
        Statement st;
        ResultSet rs;

        List<Client> clients = new ArrayList<>();

        validateSession(username, hashedToken);

        query = READ_ALL_CLIENTS_INFO;
        st = dbConnection.createStatement();
        rs = st.executeQuery(query);

        while(rs.next()) {
            String email = rs.getString(1);
            String address = rs.getString(2);
            String plan = rs.getString(3);
            Float energyConsumedPerMonth = rs.getFloat(4);
            Float energyConsumedPerHour = rs.getFloat(5);

            Client client = Client.newBuilder()
                    .setEmail(email)
                    .setAddress(address)
                    .setPlanType(PlanType.valueOf(plan))
                    .setEnergyConsumedPerMonth(energyConsumedPerMonth)
                    .setEnergyConsumedPerHour(energyConsumedPerHour)
                    .build();

            clients.add(client);
        }
        st.close();

        return clients;
    }

    public boolean deleteClient(String username, String email, String hashedToken)
            throws SQLException, AdminDoesNotExistException, ClientDoesNotExistException, InvalidSessionTokenException {

        String query;
        PreparedStatement st;

        validateSession(username, hashedToken);

        query = DELETE_CLIENT;
        st = dbConnection.prepareStatement(query);
        st.setString(1, email);
        int success = st.executeUpdate();
        st.close();

        if (success == 0) {
            throw new ClientDoesNotExistException(email);
        }

        return true;
    }

}
