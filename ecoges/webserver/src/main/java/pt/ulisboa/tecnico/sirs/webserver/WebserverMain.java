package pt.ulisboa.tecnico.sirs.webserver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.*;
import java.util.*;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.ClientDoesNotExistException;
import pt.ulisboa.tecnico.sirs.webserver.grpc.PlanType;

import javax.crypto.*;

import static io.grpc.netty.NettyServerBuilder.*;
import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.*;

public class WebserverMain {

	private static KeyPair keyPair;

	private static X509Certificate certificate;
	private static X509Certificate CACertificate;
	private static final String KEY_STORE_FILE = "src/main/resources/webserver.keystore";
	private static final String KEY_STORE_PASSWORD = "mypasswebserver";
	private static final String KEY_STORE_ALIAS_WEBSERVER = "webserver";

	private static final String TRUST_STORE_FILE = "src/main/resources/webserver.truststore";
	private static final String TRUST_STORE_PASSWORD = "mypasswebserver";
	private static final String TRUST_STORE_ALIAS_CA = "ca";

	// Data compartments

	private static SecretKey personalInfoKey;

	private static SecretKey energyPanelKey;

	// Database

	private static Connection dbConnection = null;

	private static final String DATABASE = "clientdb";
	private static final String DATABASE_USER = "ecoges";
	private static final String DATABASE_PASSWORD = "admin";

	private static final String DATABASE_DRIVER = "com.mysql.cj.jdbc.Driver";

	private static String dbUrl = "jdbc:mysql://localhost:3306/clientdb"; // default value

	// Invoices

	private static int serverPort = 8000;

	private static int currYear = 2020;
	private static int currMonth = 0;

	private static final List<String> months = new ArrayList<>(Arrays.asList
			("Jan", "Feb", "Mar", "Apr", "Mai", "Jun", "Jul", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"));

	private static final int TAXES = 25;
	private static final float FLAT_RATE_COST = 0.18F;
	private static final float BI_HOURLY_DAYTIME_COST = 0.20F;
	private static final float BI_HOURLY_NIGHT_COST = 0.15F;


	// Usage: <serverPort> <databaseHost> <databasePort>
	public static void main(String[] args) {

		System.out.println(">>> " + WebserverMain.class.getSimpleName() + " <<<");

		// Parse arguments
		try {
			if (args.length == 3) {
				// server
				serverPort = Integer.parseInt(args[0]);

				// database
				String dbHost = args[1];
				int dbPort = Integer.parseInt(args[2]);
				dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + DATABASE;
			}

		} catch (NumberFormatException e) {
			System.out.println("ERROR: Invalid arguments.");
			System.out.println("Usage: [<serverPort>] [<backofficeHost>] [<backofficePort>] [<databaseHost>] [<databasePort>]");
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

		try {
			// Database
			System.out.println("Setting up database connection on " + dbUrl);
			Class.forName(DATABASE_DRIVER);
			dbConnection = DriverManager.getConnection(dbUrl, DATABASE_USER, DATABASE_PASSWORD);
			setupDatabase();

			// Setup ssl context
			SslContext sslContext = GrpcSslContexts.configure(SslContextBuilder.forServer(keyPair.getPrivate(), certificate).trustManager(CACertificate)).build();

			// Service
			Webserver webserver = new Webserver(dbConnection, personalInfoKey, energyPanelKey);
			Server server = forPort(serverPort).sslContext(sslContext)
					.addService(new WebserverServiceImpl(webserver))
					.addService(new WebserverBackofficeServiceImpl(webserver))
					.build();
			server.start();
			System.out.println("Listening on port " + serverPort + "...\n");

			// Automatically generate invoices at each 15 seconds
			Timer time = new Timer();
			generateInvoices task = new generateInvoices();
			time.schedule(task, 20000, 15000);

			// Do not exit the main thread. Wait until server is terminated.
			server.awaitTermination();
		} catch (InterruptedException e) {
			System.out.println("ERROR: Server aborted: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("ERROR: Could not start server: " + e.getMessage());
		} catch (SQLException | ClassNotFoundException e) {
			System.out.println("ERROR: Could not connect to database: " + e.getMessage());
		} finally {
			System.out.println("Exiting...");
		}
	}

	private static void loadKeysCertificates() throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
			IOException, UnrecoverableKeyException {

		// Key Store
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

		PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_WEBSERVER, KEY_STORE_PASSWORD.toCharArray());
		PublicKey publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_WEBSERVER).getPublicKey();
		keyPair = new KeyPair(publicKey, privateKey);
		certificate = (X509Certificate) keyStore.getCertificate(KEY_STORE_ALIAS_WEBSERVER);

		// Trust Store
		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
		trustStore.load(Files.newInputStream(Paths.get(TRUST_STORE_FILE)), TRUST_STORE_PASSWORD.toCharArray());
		CACertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_CA);

		System.out.println("Successfully loaded key pairs and certificate from Java Keystore!");
	}

	private static void generateCompartmentKeys() throws NoSuchAlgorithmException, SQLException, IllegalBlockSizeException,
			NoSuchPaddingException, InvalidKeyException {

		PreparedStatement st;
		ResultSet rs;

		// Check if keys already exist
		st = dbConnection.prepareStatement(READ_COMPARTMENT_KEYS);
		rs = st.executeQuery();

		if (rs.next()){
			personalInfoKey = Security.unwrapKey(keyPair.getPrivate(), rs.getBytes(1));
			energyPanelKey = Security.unwrapKey(keyPair.getPrivate(), rs.getBytes(2));
			st.close();
			return;
		}
		st.close();

		System.out.println("Generating compartment keys...");


		// Generate key for personal info compartment
		KeyGenerator keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(256);
		personalInfoKey = keyGen.generateKey();
		byte[] wrappedPersonalInfoKey = Security.wrapKey(keyPair.getPublic(), personalInfoKey);

		// Generate key for energy panel compartment
		keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(256);
		energyPanelKey = keyGen.generateKey();
		byte[] wrappedEnergyPanelKey = Security.wrapKey(keyPair.getPublic(), energyPanelKey);

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
			boolean reachable = dbConnection.isValid(25);
			if (!reachable) {
				throw new SQLException("Unreachable database connection.");
			}

			statement = dbConnection.createStatement();

			statement.execute(DROP_INVOICE_TABLE);
			statement.execute(DROP_SOLAR_PANEL_TABLE);
			statement.execute(DROP_APPLIANCE_TABLE);
			statement.execute(DROP_CLIENT_TABLE);
			statement.execute(DROP_COMPARTMENT_KEYS_TABLE);

			statement.execute(CREATE_CLIENT_TABLE);
			statement.execute(CREATE_APPLIANCE_TABLE);
			statement.execute(CREATE_SOLAR_PANEL_TABLE);
			statement.execute(CREATE_INVOICE_TABLE);
			statement.execute(CREATE_COMPARTMENT_KEYS_TABLE);

			generateCompartmentKeys();

			System.out.println("Database is ready!");
		} catch (SQLException e) {
			System.out.println("Could not set up database: "+ e.getMessage());
			System.exit(1);
		} catch (IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
			System.out.println("Could not generate compartment keys: "+ e.getMessage());
			System.exit(1);
		}
	}

	public static class generateInvoices extends TimerTask {

		public void incrNextDate() {
			if (currMonth == 11) {
				currMonth = 0;
				currYear++;
			}
			else {
				currMonth++;
			}
		}

		public byte[] getIv(String email) throws SQLException, ClientDoesNotExistException {
			PreparedStatement st;
			ResultSet rs;
			byte[] iv;

			st = dbConnection.prepareStatement(READ_CLIENT_IV);
			st.setString(1, email);
			rs = st.executeQuery();

			if (rs.next()) {
				iv = rs.getBytes(1);
			}
			else {
				st.close();
				throw new ClientDoesNotExistException(email);
			}

			st.close();
			return iv;
		}

		public void addInvoice(int client_id, float energyConsumed, float energyConsumedDaytime, float energyConsumedNight, String plan) throws NoSuchAlgorithmException, SQLException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, InvalidKeyException {
			PreparedStatement st;
			float paymentAmount;

			if (plan.equals(PlanType.FLAT_RATE.name())) {
				paymentAmount = energyConsumed * FLAT_RATE_COST;
			}
			else {
				paymentAmount = energyConsumedDaytime * BI_HOURLY_DAYTIME_COST + energyConsumedNight * BI_HOURLY_NIGHT_COST;
			}

			paymentAmount = paymentAmount + paymentAmount * TAXES/100;

			byte[] iv = Security.generateRandom();

			st = dbConnection.prepareStatement(CREATE_INVOICE);
			st.setBytes(1, iv);
			st.setInt(2, client_id);
			st.setInt(3, currYear);
			st.setInt(4, currMonth);
			st.setString(5, plan);
			st.setInt(6, TAXES);

			st.setBytes(7, Security.encryptData(Float.toString(paymentAmount), energyPanelKey, iv));
			st.setBytes(8, Security.encryptData(Float.toString(energyConsumed), energyPanelKey, iv));
			st.setBytes(9, Security.encryptData(Float.toString(energyConsumedDaytime), energyPanelKey, iv));
			st.setBytes(10, Security.encryptData(Float.toString(energyConsumedNight), energyPanelKey, iv));

			st.executeUpdate();
		}
		@Override
		public void run() {
			PreparedStatement st;
			ResultSet rs;
			try {
				st = dbConnection.prepareStatement(READ_ALL_CLIENTS_ID_ENERGY_CONSUMPTION_PLAN);
				rs = st.executeQuery();

				while (rs.next()) {
					int client_id = rs.getInt(1);
					String plan = rs.getString(2);
					byte[] iv = rs.getBytes(3);

					byte[] energyConsumedBytes = Security.decryptData(rs.getBytes(4), energyPanelKey, iv);
					byte[] energyConsumedDaytimeBytes = Security.decryptData(rs.getBytes(5), energyPanelKey, iv);
					byte[] energyConsumedNightBytes = Security.decryptData(rs.getBytes(6), energyPanelKey, iv);

					float energyConsumed = Float.parseFloat(new String(energyConsumedBytes));
					float energyConsumedDaytime = Float.parseFloat(new String(energyConsumedDaytimeBytes));
					float energyConsumedNight = Float.parseFloat(new String(energyConsumedNightBytes));

					addInvoice(client_id, energyConsumed, energyConsumedDaytime, energyConsumedNight, plan);
				}
				incrNextDate();
			} catch (RuntimeException | SQLException | NoSuchAlgorithmException | InvalidAlgorithmParameterException |
					 IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | InvalidKeyException e) {
				System.out.println("Could not generate invoices: " + e.getMessage());
			}

		}
	}
}