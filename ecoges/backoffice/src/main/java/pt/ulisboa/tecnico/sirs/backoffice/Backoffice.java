package pt.ulisboa.tecnico.sirs.backoffice;

import pt.ulisboa.tecnico.sirs.backoffice.exceptions.HelloException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Backoffice {

    private final Connection dbConnection;

    public Backoffice(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public String hello(String name) throws HelloException {
        String result = "", query;
        Statement statement;
        ResultSet resultSet;

        if (dbConnection == null){
            return "Hello, " + name + "! Could not connect to database";
        }

        // Test DB (TO DELETE LATER)
        try {

            query = "INSERT INTO admin(username, password) VALUES ('username', 'password')";
            statement = dbConnection.createStatement();
            statement.execute(query);

            query = "SELECT * FROM admin";
            statement = dbConnection.createStatement();
            resultSet = statement.executeQuery(query);

            while (resultSet.next()) {
                String username = resultSet.getString("username");
                String password = resultSet.getString("password");
                result += ("username: " + username + ", password: " + password + "\n");
            }
        } catch (SQLException e){
            throw new HelloException("SQL Exception");
        }

        return "Hello, " + name + "! Database content: \n" + result;
    }


}
