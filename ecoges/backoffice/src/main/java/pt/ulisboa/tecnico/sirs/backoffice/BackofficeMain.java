package pt.ulisboa.tecnico.sirs.backoffice;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;

public class BackofficeMain {
	private static Server server = null;
	private static BackofficeServiceImpl impl = null;
	private static InputStream cert;
	private static InputStream key;

	private static Connection dbConnection = null;

	private static final String dbURL = "jdbc:mysql://localhost:3306/clientdb";
	private static final String dbUser = "root";
	private static final String dbPassword = "admin";

	private static final String dbDriver = "com.mysql.cj.jdbc.Driver";


	public static void main(String[] args) throws IOException {

		if (args.length != 1) {
			System.out.println("Could not start server.");
			System.out.println("Usage: <serverPort>");
			return;
		}

		int serverPort = Integer.parseInt(args[0]);

		try {
			cert = Files.newInputStream(Paths.get("../keyscerts/backoffice.crt"));
			key = Files.newInputStream(Paths.get("../keyscerts/backoffice.pem"));
		} catch(IllegalArgumentException | UnsupportedOperationException | IOException e){
			System.out.println("Could not load server key or certificate.");
			return;
		}

		SslContext sslContext = GrpcSslContexts.forServer(cert, key).build();

		System.out.println(">>> " + BackofficeMain.class.getSimpleName() + " <<<");

		try {
			// Database
			Class.forName(dbDriver);
			dbConnection = DriverManager.getConnection(dbURL, dbUser, dbPassword);
			if (dbConnection != null) populateDatabase();

			// Service
			impl = new BackofficeServiceImpl(dbConnection);
			server = NettyServerBuilder.forPort(serverPort).sslContext(sslContext).addService(impl).build();
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
		}
	}

	private static void populateDatabase() {
		String query;
		Statement statement;

		try {
			query = "DROP TABLE IF EXISTS admin";
			statement = dbConnection.createStatement();
			statement.execute(query);

			query = "CREATE TABLE admin (id INTEGER NOT NULL AUTO_INCREMENT, " +
					"username VARCHAR(25) NOT NULL," +
					"password VARCHAR(25) NOT NULL," +
					"UNIQUE (username)," +
					"PRIMARY KEY (id))";
			statement = dbConnection.createStatement();
			statement.execute(query);
		} catch (SQLException e) {
			System.out.println("Could not populate database: "+ e.getMessage());
		}
	}
}
