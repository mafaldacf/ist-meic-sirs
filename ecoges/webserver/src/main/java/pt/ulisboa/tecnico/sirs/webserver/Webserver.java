package pt.ulisboa.tecnico.sirs.webserver;

import pt.ulisboa.tecnico.sirs.webserver.exceptions.HelloException;

import java.sql.*;

public class Webserver {
    private static Connection dbConnection = null;

    private static final String dbURL = "jdbc:mysql://localhost:3306/clientdb";
    private static final String dbUser = "root";
    private static final String dbPassword = "admin";

    public Webserver() {
        // connect to database (should not be a try but this is in case db is not setup)
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            dbConnection = DriverManager.getConnection(dbURL, dbUser, dbPassword);
        } catch (SQLException | ClassNotFoundException e){
            System.out.println("Could not connect to database");
        }
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
            query = "DROP TABLE IF EXISTS client";
            statement = dbConnection.createStatement();
            statement.execute(query);
            System.out.println("drop");

            query = "CREATE TABLE client (id INTEGER NOT NULL, " +
                    "energyConsumedPerMonth DECIMAL(13, 2), " +
                    "energyConsumedPerHour DECIMAL(13, 2)," +
                    "PRIMARY KEY (id))";
            statement = dbConnection.createStatement();
            statement.execute(query);

            query = "DELETE FROM client";
            statement = dbConnection.createStatement();
            statement.execute(query);

            query = "INSERT INTO client(id, energyConsumedPerMonth, energyConsumedPerHour) VALUES (0, 25.1, 1.2)";
            statement = dbConnection.createStatement();
            statement.execute(query);

            query = "SELECT * FROM client";
            statement = dbConnection.createStatement();
            resultSet = statement.executeQuery(query);

            while (resultSet.next()) {
                int id = Integer.parseInt(resultSet.getString("id"));
                float energyConsumedPerMonth = Float.parseFloat(resultSet.getString("energyConsumedPerMonth"));
                float energyConsumedPerHour = Float.parseFloat(resultSet.getString("energyConsumedPerHour"));
                result += ("ID: " + id + ", energy: " + energyConsumedPerMonth + ", " + energyConsumedPerHour + "\n");
            }
        } catch (SQLException e){
            throw new HelloException("SQL Exception");
        }

        return "Hello, " + name + "! Database content: \n" + result;
    }


}
