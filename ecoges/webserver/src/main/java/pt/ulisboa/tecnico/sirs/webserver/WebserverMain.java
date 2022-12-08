package pt.ulisboa.tecnico.sirs.webserver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;

public class WebserverMain {
	private static InputStream cert;
	private static InputStream key;

	private static Connection dbConnection = null;

	private static final String dbUser = "ecoges";
	private static final String dbPassword = "admin";

	private static final String dbDriver = "com.mysql.cj.jdbc.Driver";

	private static String dbURL = "jdbc:mysql://localhost:3306/clientdb"; // default value

	private static int serverPort = 8000;


	// Usage: <serverPort> <databaseHost> <databasePort>
	public static void main(String[] args) throws IOException {
		try {
			if (args.length == 3) {
				serverPort = Integer.parseInt(args[0]);
				String dbHost = args[1];
				int dbPort = Integer.parseInt(args[2]);
				dbURL = "jdbc:mysql://" + dbHost + ":" + dbPort + "/clientdb";
			}

		} catch (NumberFormatException e) {
			System.out.println("Invalid arguments.");
			System.out.println("Usage: [<serverPort>] [<databaseHost>] [<databasePort>]");
			System.exit(1);
		}

		try {
			cert = Files.newInputStream(Paths.get("../keyscerts/webserver.crt"));
			key = Files.newInputStream(Paths.get("../keyscerts/webserver.pem"));
		} catch(IllegalArgumentException | UnsupportedOperationException | IOException e){
			System.out.println("Could not load server key or certificate.");
			System.exit(1);
		}

		SslContext sslContext = GrpcSslContexts.forServer(cert, key).build();

		System.out.println(">>> " + WebserverMain.class.getSimpleName() + " <<<");

		try {
			// Database
			System.out.println("Setting up database connection on " + dbURL);
			Class.forName(dbDriver);
			dbConnection = DriverManager.getConnection(dbURL, dbUser, dbPassword);
			if (dbConnection != null) setupDatabase();

			// Service
			WebserverServiceImpl impl = new WebserverServiceImpl(dbConnection);
			Server server = NettyServerBuilder.forPort(serverPort).sslContext(sslContext).addService(impl).build();
			server.start();
			System.out.println("Listening on port " + serverPort + "...");


			// Do not exit the main thread. Wait until server is terminated.
			server.awaitTermination();
		} catch (InterruptedException e) {
			System.out.println("ERROR: Server aborted.");
		} catch (IOException e) {
			System.out.println("ERROR: Could not start server.");
		} catch (SQLException e) {
			System.out.println("ERROR: Could not connect to database: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			System.out.println("ERROR: Database class not found.");
		} finally {
			System.exit(1);
		}
	}

	private static void setupDatabase() {
		String query;
		Statement statement;

		try {
			query = "DROP TABLE IF EXISTS client";
			statement = dbConnection.createStatement();
			statement.execute(query);

			query = "CREATE TABLE client (" +
					"id INTEGER NOT NULL AUTO_INCREMENT, " +
					"email VARCHAR(50) NOT NULL," +
					"password VARCHAR(25) NOT NULL," +
					"address VARCHAR(25) NOT NULL," +
					"plan VARCHAR(15) DEFAULT 'FLAT_RATE'," + // plan = FLAT_RATE or BI_HOURLY_RATE
					"energyConsumedPerMonth DECIMAL(15, 2) DEFAULT 0, " +
					"energyConsumedPerHour DECIMAL(15, 2) DEFAULT 0," +
					"token VARCHAR(64) DEFAULT ''," +
					"UNIQUE (email)," +
					"PRIMARY KEY (id))";
			statement = dbConnection.createStatement();
			statement.execute(query);

			System.out.println("Database is ready!");
		} catch (SQLException e) {
			System.out.println("Could not set up database: "+ e.getMessage());
			System.exit(1);
		}
	}
}
