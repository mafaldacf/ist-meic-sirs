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
import pt.ulisboa.tecnico.sirs.backoffice.exceptions.CompartmentKeyException;
import pt.ulisboa.tecnico.sirs.crypto.Crypto;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;

import static pt.ulisboa.tecnico.sirs.backoffice.DatabaseQueries.*;

public class BackofficeMain {

	private static Backoffice backofficeServer;

	// TLS
	private static InputStream cert;
	private static InputStream key;

	// Data compartments

	private static final String KEY_STORE_FILE = "src/main/resources/backoffice.jks";
	private static final String KEY_STORE_TYPE = "JKS";
	private static final String KEY_STORE_PASSWORD = "backoffice";
	private static final String KEY_STORE_ALIAS_BACKOFFICE = "backoffice";

	private static KeyPair keyPair;

	// Database

	private static Connection dbConnection = null;

	private static final String DATABASE_USER = "ecoges";
	private static final String DATABASE_PASSWORD = "admin";

	private static final String DATABASE_DRIVER = "com.mysql.cj.jdbc.Driver";

	private static String DATABASE_URL = "jdbc:mysql://localhost:3306/clientdb"; // default value

	private static int serverPort = 8001;


	// Usage: <serverPort> <databaseHost> <databasePort>
	public static void main(String[] args) {
		try {
			if (args.length == 3) {
				serverPort = Integer.parseInt(args[0]);
				String dbHost = args[1];
				int dbPort = Integer.parseInt(args[2]);
				DATABASE_URL = "jdbc:mysql://" + dbHost + ":" + dbPort + "/clientdb";
			}

		} catch (NumberFormatException e) {
			System.out.println("Invalid arguments.");
			System.out.println("Usage: [<serverPort>] [<databaseHost>] [<databasePort>]");
			System.exit(1);
		}

		try {
			cert = Files.newInputStream(Paths.get("../tlscerts/backoffice.crt"));
			key = Files.newInputStream(Paths.get("../tlscerts/backoffice.pem"));
		} catch(IllegalArgumentException | UnsupportedOperationException | IOException e){
			System.out.println("Could not load server key or certificate.");
			System.exit(1);
		}


		try {
			SslContext sslContext = GrpcSslContexts.forServer(cert, key).build();
			System.out.println(">>> " + BackofficeMain.class.getSimpleName() + " <<<");

			loadKeyPair();

			// Database
			System.out.println("Setting up database connection on " + DATABASE_URL);
			Class.forName(DATABASE_DRIVER);
			dbConnection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
			if (dbConnection != null) setupDatabase();

			// Services
			backofficeServer = new Backoffice(dbConnection, keyPair);
			backofficeServer.loadCompartmentKeys();
			Server server = NettyServerBuilder.forPort(serverPort).sslContext(sslContext)
					.addService(new BackofficeAdminServiceImpl(backofficeServer))
					.addService(new BackofficeWebserverServiceImpl(backofficeServer))
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
		} catch (UnrecoverableKeyException | CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
			System.out.println("ERROR: Could not load key pair: " + e.getMessage());
		} catch (CompartmentKeyException | IllegalBlockSizeException | NoSuchPaddingException | InvalidKeyException e) {
			System.out.println("ERROR: Could not load compartment keys: " + e.getMessage());
		}
	}

	private static void loadKeyPair() throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
			IOException, UnrecoverableKeyException {

		KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
		keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());
		PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_BACKOFFICE, KEY_STORE_PASSWORD.toCharArray());
		PublicKey publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_BACKOFFICE).getPublicKey();
		keyPair = new KeyPair(publicKey, privateKey);

		System.out.println("Successfully loaded key pair from java key store");
	}

	private static void generateCompartmentKeys() throws NoSuchAlgorithmException, SQLException, IllegalBlockSizeException,
			NoSuchPaddingException, InvalidKeyException {

		PreparedStatement st;
		ResultSet rs;

		// Check if keys already exist
		st = dbConnection.prepareStatement(READ_COMPARTMENT_KEYS_COUNT);
		rs = st.executeQuery();

		if (rs.next() && rs.getInt(1) != 0){
			st.close();
			return;
		}
		st.close();

		System.out.println("Generating compartment keys...");


		// Generate key for personal info compartment
		KeyGenerator keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(128);
		Key personalInfoKey = keyGen.generateKey();
		byte[] wrappedPersonalInfoKey = Crypto.wrapKey(keyPair.getPublic(), personalInfoKey);

		// Generate key for energy panel compartment
		keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(128);
		Key energyPanelKey = keyGen.generateKey();
		byte[] wrappedEnergyPanelKey = Crypto.wrapKey(keyPair.getPublic(), energyPanelKey);

		// Upload keys to database
		st = dbConnection.prepareStatement(CREATE_COMPARTMENT_KEYS);
		st.setBytes(1, wrappedPersonalInfoKey);
		st.setBytes(2, wrappedEnergyPanelKey);
		st.executeUpdate();
		st.close();
	}

	private static void setupDatabase() {
		Statement statement;

		try {
			statement = dbConnection.createStatement();
			statement.execute(DROP_COMPARTMENT_KEYS_TABLE);
			statement.execute(DROP_ROLE_PERMISSION_TABLE);
			statement.execute(DROP_ADMIN_TABLE);

			statement = dbConnection.createStatement();
			statement.execute(CREATE_ADMIN_TABLE);
			statement.execute(CREATE_PERMISSION_TABLE);
			statement.execute(CREATE_COMPARTMENT_KEYS_TABLE);

			createRolesPermissions();

			generateCompartmentKeys();

			System.out.println("Database is ready!");
		} catch (SQLException | IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
			System.out.println("Could not set up database: "+ e.getMessage());
			System.exit(1);
		}
	}

	public static void createRolesPermissions() throws SQLException {
		createAccountManagerPermissions();
		createEnergySystemManagerPermissions();
	}

	public static void createAccountManagerPermissions() throws SQLException {
		PreparedStatement st;
		st = dbConnection.prepareStatement(CREATE_PERMISSION);
		st.setString(1, "ACCOUNT_MANAGER");
		st.setBoolean(2, true);
		st.setBoolean(3, false);
		st.executeUpdate();
		st.close();
	}

	public static void createEnergySystemManagerPermissions() throws SQLException {
		PreparedStatement st;
		st = dbConnection.prepareStatement(CREATE_PERMISSION);
		st.setString(1, "ENERGY_SYSTEM_MANAGER");
		st.setBoolean(2, false);
		st.setBoolean(3, true);
		st.executeUpdate();
		st.close();
	}
}
