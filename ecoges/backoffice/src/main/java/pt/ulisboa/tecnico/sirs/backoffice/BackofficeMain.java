package pt.ulisboa.tecnico.sirs.backoffice;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.*;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import pt.ulisboa.tecnico.sirs.webserver.grpc.WebserverBackofficeServiceGrpc;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static pt.ulisboa.tecnico.sirs.backoffice.DatabaseQueries.*;

public class BackofficeMain {

	private static int serverPort = 8001;

	// TLS
	private static KeyPair keyPair;

	private static X509Certificate certificate;
	private static X509Certificate CACertificate;
	private static final String KEY_STORE_FILE = "src/main/resources/backoffice.keystore";
	private static final String KEY_STORE_PASSWORD = "mypassbackoffice";
	private static final String KEY_STORE_ALIAS_BACKOFFICE = "backoffice";

	private static final String TRUST_STORE_FILE = "src/main/resources/backoffice.truststore";
	private static final String TRUST_STORE_PASSWORD = "mypassbackoffice";
	private static final String TRUST_STORE_ALIAS_CA = "ca";

	// Database
	private static Connection dbConnection = null;

	private static final String DATABASE_USER = "ecoges";
	private static final String DATABASE_PASSWORD = "admin";

	private static final String DATABASE_DRIVER = "com.mysql.cj.jdbc.Driver";

	private static String dbUrl = "jdbc:mysql://localhost:3306/clientdb"; // default value

	// Webserver
	private static String webserverHost = "localhost";
	private static int webserverPort = 8000;

	// Rbac
	private static String rbacHost = "localhost";
	private static int rbacPort = 8002;


	// Usage: <serverPort> <webserverHost> <webserverPort> <databaseHost> <databasePort> <rbacHost> <rbacPort>
	public static void main(String[] args) {
		System.out.println(">>> " + BackofficeMain.class.getSimpleName() + " <<<");

		try {
			if (args.length == 7) {
				// server
				serverPort = Integer.parseInt(args[0]);

				// webserver
				webserverHost = args[1];
				webserverPort = Integer.parseInt(args[2]);

				// database
				String dbHost = args[3];
				int dbPort = Integer.parseInt(args[4]);
				dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/clientdb";

				// rbac
				rbacHost = args[5];
				rbacPort = Integer.parseInt(args[6]);
			}

		} catch (NumberFormatException e) {
			System.out.println("Invalid arguments.");
			System.out.println("Usage: [<serverPort>] [<databaseHost>] [<databasePort>] [<rbacHost>] [<rbacPort>]");
			System.out.println("Exiting...");
			System.exit(1);
		}

		try {
			loadKeysCertificates();
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException e) {
			System.out.println("ERROR: Could not load server keys and certificates: " + e.getMessage());
			System.out.println("Exiting...");
			System.exit(1);
		}

		// Start server

		try {
			// Database
			System.out.println("Setting up database connection on " + dbUrl);
			Class.forName(DATABASE_DRIVER);
			dbConnection = DriverManager.getConnection(dbUrl, DATABASE_USER, DATABASE_PASSWORD);
			if (dbConnection != null) setupDatabase();

			// Setup ssl context
			SslContext sslContext = GrpcSslContexts.configure(SslContextBuilder
					.forServer(keyPair.getPrivate(), certificate)
					.trustManager(CACertificate)).build();

			// Services
			Backoffice backofficeServer = new Backoffice(dbConnection, webserverHost, webserverPort, rbacHost, rbacPort);
			Server server = NettyServerBuilder.forPort(serverPort).sslContext(sslContext)
					.addService(new BackofficeServiceImpl(backofficeServer))
					.build();
			server.start();
			System.out.println("Listening on port " + serverPort + "...\n");


			// Do not exit the main thread. Wait until server is terminated.
			server.awaitTermination();
		} catch (InterruptedException e) {
			System.out.println("ERROR: Server aborted: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("ERROR: Could not start server: " + e.getMessage());
		} catch (SQLException e) {
			System.out.println("ERROR: Could not connect to database: " + e.getMessage());
		} catch (ClassNotFoundException e) {
			System.out.println("ERROR: Database class not found: " + e.getMessage());
		} catch (CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
			System.out.println("ERROR: Invalid webserver certificate.");
		} finally {
			System.out.println("Exiting...");
		}
	}

	private static void loadKeysCertificates() throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
			IOException, UnrecoverableKeyException {
		PrivateKey privateKey;
		PublicKey publicKey;

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

		privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_BACKOFFICE, KEY_STORE_PASSWORD.toCharArray());
		publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_BACKOFFICE).getPublicKey();
		keyPair = new KeyPair(publicKey, privateKey);
		certificate = (X509Certificate) keyStore.getCertificate(KEY_STORE_ALIAS_BACKOFFICE);

		// Trust Store
		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
		trustStore.load(Files.newInputStream(Paths.get(TRUST_STORE_FILE)), TRUST_STORE_PASSWORD.toCharArray());
		CACertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_CA);

		System.out.println("Successfully loaded key pairs from Java Keystore!");
	}

	private static void setupDatabase() {
		Statement statement;

		try {
			boolean reachable = dbConnection.isValid(25);
			if (!reachable) {
				throw new SQLException("Unreachable database connection.");
			}

			statement = dbConnection.createStatement();
			statement.execute(DROP_ADMIN_TABLE);
			statement.execute(CREATE_ADMIN_TABLE);

			System.out.println("Database is ready!");
		} catch (SQLException e) {
			System.out.println("Could not set up database: "+ e.getMessage());
			System.exit(1);
		}
	}
}