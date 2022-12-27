package pt.ulisboa.tecnico.sirs.rbac;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.sql.*;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;

// STAY?
import pt.ulisboa.tecnico.sirs.webserver.grpc.WebserverBackofficeServiceGrpc;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static pt.ulisboa.tecnico.sirs.rbac.DatabaseQueries.*;

public class RbacMain {

	private static int serverPort = 8001;

	// TLS
	private static InputStream cert;
	private static InputStream key;

	private static final String CERTIFICATE_PATH = "../tlscerts/rbac.crt";
	private static final String KEY_PATH = "../tlscerts/rbac.pem";

	// Database

	private static Connection dbConnection = null;

	private static final String DATABASE_USER = "ecoges";
	private static final String DATABASE_PASSWORD = "admin";

	private static final String DATABASE_DRIVER = "com.mysql.cj.jdbc.Driver";

	private static String dbUrl = "jdbc:mysql://localhost:3306/clientdb"; // default value

	// Webserver
	private static String webserverHost = "localhost";
	private static int webserverPort = 8000;

	private static WebserverBackofficeServiceGrpc.WebserverBackofficeServiceBlockingStub webserver;


	// Usage: <serverPort> <webserverHost> <webserverPort> <databaseHost> <databasePort>
	public static void main(String[] args) {
		try {
			if (args.length == 5) {
				// server
				serverPort = Integer.parseInt(args[0]);

				// backoffice
				webserverHost = args[1];
				webserverPort = Integer.parseInt(args[2]);

				// database
				String dbHost = args[3];
				int dbPort = Integer.parseInt(args[2]);
				dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/clientdb";
			}

		} catch (NumberFormatException e) {
			System.out.println("Invalid arguments.");
			System.out.println("Usage: [<serverPort>] [<databaseHost>] [<databasePort>]");
			System.out.println("Exiting...");
			System.exit(1);
		}

		// Load server certificate for TLS

		try {
			cert = Files.newInputStream(Paths.get(CERTIFICATE_PATH));
			key = Files.newInputStream(Paths.get(KEY_PATH));
		} catch(IllegalArgumentException | UnsupportedOperationException | IOException e){
			System.out.println("ERROR: Could not load server key or certificate: " + e.getMessage());
			System.out.println("Exiting...");
			System.exit(1);
		}

		// Start server

		try {
			SslContext sslContext = GrpcSslContexts.forServer(cert, key).build();
			System.out.println(">>> " + RbacMain.class.getSimpleName() + " <<<");

			// Database
			//System.out.println("Setting up database connection on " + dbUrl);
			//Class.forName(DATABASE_DRIVER);
			//dbConnection = DriverManager.getConnection(dbUrl, DATABASE_USER, DATABASE_PASSWORD);
			//if (dbConnection != null) setupDatabase();

			// Services
			Rbac rbacServer = new Rbac(dbConnection, webserverHost, webserverPort);
			Server server = NettyServerBuilder.forPort(serverPort).sslContext(sslContext)
					.addService(new RbacServiceImpl(rbacServer)).build();
			server.start();
			System.out.println("Listening on port " + serverPort + "...");

			// Do not exit the main thread. Wait until server is terminated.
			server.awaitTermination();
		} catch (InterruptedException e) {
			System.out.println("ERROR: Server aborted: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("ERROR: Could not start server: " + e.getMessage());
		//} catch (SQLException e) {
		//	System.out.println("ERROR: Could not connect to database: " + e.getMessage());
		//} catch (ClassNotFoundException e) {
		//	System.out.println("ERROR: Database class not found: " + e.getMessage());
		} finally {
			System.out.println("Exiting...");
		}
	}



}
