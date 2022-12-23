package pt.ulisboa.tecnico.sirs.backoffice;

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
import pt.ulisboa.tecnico.sirs.webserver.grpc.WebserverBackofficeServiceGrpc;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static pt.ulisboa.tecnico.sirs.backoffice.DatabaseQueries.*;

public class BackofficeMain {

	private static int serverPort = 8001;

	// Data compartments
	private static final String KEY_STORE_FILE = "src/main/resources/backoffice.keystore";
	private static final String KEY_STORE_PASSWORD = "backoffice";
	private static final String KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT = "accountManagement";
	private static final String KEY_STORE_ALIAS_ENERGY_MANAGEMENT = "energyManagement";

	private static KeyPair accountManagementKeyPair;
	private static KeyPair energyManagementKeyPair;

	// TLS
	private static InputStream cert;
	private static InputStream key;

	private static final String CERTIFICATE_PATH = "../tlscerts/backoffice.crt";
	private static final String KEY_PATH = "../tlscerts/backoffice.pem";

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
			System.out.println(">>> " + BackofficeMain.class.getSimpleName() + " <<<");

			loadKeyPairs();

			// Database
			System.out.println("Setting up database connection on " + dbUrl);
			Class.forName(DATABASE_DRIVER);
			dbConnection = DriverManager.getConnection(dbUrl, DATABASE_USER, DATABASE_PASSWORD);
			if (dbConnection != null) setupDatabase();

			// Services
			Backoffice backofficeServer = new Backoffice(dbConnection, webserverHost, webserverPort);
			Server server = NettyServerBuilder.forPort(serverPort).sslContext(sslContext)
					.addService(new BackofficeServiceImpl(backofficeServer))
					.build();
			server.start();
			System.out.println("Listening on port " + serverPort + "...");


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
		} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
			System.out.println("ERROR: Could not load compartment keys from JavaKeyStore: " + e.getMessage());
		} finally {
			System.out.println("Exiting...");
		}
	}

	private static void loadKeyPairs() throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
			IOException, UnrecoverableKeyException {
		PrivateKey privateKey;
		PublicKey publicKey;

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

		// Account Management
		privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT, KEY_STORE_PASSWORD.toCharArray());
		publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT).getPublicKey();
		accountManagementKeyPair = new KeyPair(publicKey, privateKey);

		// Energy Management
		privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_ENERGY_MANAGEMENT, KEY_STORE_PASSWORD.toCharArray());
		publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_ENERGY_MANAGEMENT).getPublicKey();
		energyManagementKeyPair = new KeyPair(publicKey, privateKey);

		System.out.println("Successfully loaded key pairs from JavaKeyStore!");
	}

	private static void setupDatabase() {
		Statement statement;

		try {
			statement = dbConnection.createStatement();
			statement.execute(DROP_PERMISSION_TABLE);
			statement.execute(DROP_ADMIN_TABLE);


			statement = dbConnection.createStatement();
			statement.execute(CREATE_ADMIN_TABLE);
			statement.execute(CREATE_PERMISSION_TABLE);

			generatePermissions();

			System.out.println("Database is ready!");
		} catch (SQLException | IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
			System.out.println("Could not set up database: "+ e.getMessage());
			System.exit(1);
		}
	}

	public static void generatePermissions() throws SQLException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
		PreparedStatement st;
		ResultSet rs;

		// Check if permissions already exist
		st = dbConnection.prepareStatement(READ_PERMISSION_COUNT);
		rs = st.executeQuery();

		if (rs.next() && rs.getInt(1) != 0){
			st.close();
			return;
		}
		st.close();

		// Account Management
		st = dbConnection.prepareStatement(CREATE_ACCOUNT_MANAGER_PERMISSION);
		st.setBoolean(1, true); // personal info
		st.setBoolean(2, false); // energy panel
		st.executeUpdate();
		st.close();

		// Energy Management
		st = dbConnection.prepareStatement(CREATE_ENERGY_MANAGER_PERMISSION);
		st.setBoolean(1, false); // personal info
		st.setBoolean(2, true); // energy panel
		st.executeUpdate();
		st.close();
	}
}
