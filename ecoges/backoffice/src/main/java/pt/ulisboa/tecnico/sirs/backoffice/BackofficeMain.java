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
	private static final String KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT = "accountManagement";
	private static final String KEY_STORE_ALIAS_ENERGY_MANAGEMENT = "energyManagement";

	private static KeyPair backofficeKeyPair;
	private static KeyPair accountManagementKeyPair;
	private static KeyPair energyManagementKeyPair;

	private static Key personalInfoKey;

	private static Key energyPanelKey;

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

			loadKeyPairs();

			// Database
			System.out.println("Setting up database connection on " + DATABASE_URL);
			Class.forName(DATABASE_DRIVER);
			dbConnection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
			if (dbConnection != null) setupDatabase();

			// Services
			backofficeServer = new Backoffice(dbConnection, backofficeKeyPair, accountManagementKeyPair, energyManagementKeyPair);
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
		}
	}

	private static void loadKeyPairs() throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
			IOException, UnrecoverableKeyException {
		PrivateKey privateKey;
		PublicKey publicKey;

		KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
		keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

		// Backoffice key pair
		privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_BACKOFFICE, KEY_STORE_PASSWORD.toCharArray());
		publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_BACKOFFICE).getPublicKey();
		backofficeKeyPair = new KeyPair(publicKey, privateKey);

		// Account Management key pair
		privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT, KEY_STORE_PASSWORD.toCharArray());
		publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT).getPublicKey();
		accountManagementKeyPair = new KeyPair(publicKey, privateKey);

		// Energy Management key pair
		privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_ENERGY_MANAGEMENT, KEY_STORE_PASSWORD.toCharArray());
		publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_ENERGY_MANAGEMENT).getPublicKey();
		energyManagementKeyPair = new KeyPair(publicKey, privateKey);

		System.out.println("Successfully loaded key pairs from java key store!");
	}

	private static void generateCompartmentKeys() throws NoSuchAlgorithmException, SQLException, IllegalBlockSizeException,
			NoSuchPaddingException, InvalidKeyException {

		PreparedStatement st;
		ResultSet rs;

		// Check if keys already exist
		st = dbConnection.prepareStatement(READ_COMPARTMENT_KEYS);
		rs = st.executeQuery();

		if (rs.next()){
			personalInfoKey = Crypto.unwrapKey(backofficeKeyPair.getPrivate(), rs.getBytes(1));
			energyPanelKey = Crypto.unwrapKey(backofficeKeyPair.getPrivate(), rs.getBytes(2));
			st.close();
			return;
		}
		st.close();

		System.out.println("Generating compartment keys...");


		// Generate key for personal info compartment
		KeyGenerator keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(128);
		personalInfoKey = keyGen.generateKey();
		byte[] wrappedPersonalInfoKey = Crypto.wrapKey(backofficeKeyPair.getPublic(), personalInfoKey);

		// Generate key for energy panel compartment
		keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(128);
		energyPanelKey = keyGen.generateKey();
		byte[] wrappedEnergyPanelKey = Crypto.wrapKey(backofficeKeyPair.getPublic(), energyPanelKey);

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
			statement.execute(DROP_PERMISSION_TABLE);
			statement.execute(DROP_ADMIN_TABLE);
			statement.execute(DROP_COMPARTMENT_KEYS_TABLE);


			statement = dbConnection.createStatement();
			statement.execute(CREATE_ADMIN_TABLE);
			statement.execute(CREATE_PERMISSION_TABLE);
			statement.execute(CREATE_COMPARTMENT_KEYS_TABLE);

			generateCompartmentKeys();
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
			System.out.println("EXITING!! " + rs.getInt(1));
			st.close();
			return;
		}
		st.close();

		// Account Management
		st = dbConnection.prepareStatement(CREATE_ACCOUNT_MANAGER_PERMISSION);
		st.setBoolean(1, true); // personal info
		st.setBoolean(2, false); // energy panel
		st.setBytes(3, Crypto.wrapKey(accountManagementKeyPair.getPublic(), personalInfoKey)); // personal info key
		st.executeUpdate();
		st.close();

		// Energy Management
		st = dbConnection.prepareStatement(CREATE_ENERGY_MANAGER_PERMISSION);
		st.setBoolean(1, false); // personal info
		st.setBoolean(2, true); // energy panel
		st.setBytes(3, Crypto.wrapKey(energyManagementKeyPair.getPublic(), energyPanelKey)); // energy panel key
		st.executeUpdate();
		st.close();
	}
}
