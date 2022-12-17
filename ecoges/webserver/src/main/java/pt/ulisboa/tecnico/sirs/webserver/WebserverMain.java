package pt.ulisboa.tecnico.sirs.webserver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.sql.*;
import java.util.*;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import pt.ulisboa.tecnico.sirs.crypto.Crypto;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.CompartmentKeyException;
import pt.ulisboa.tecnico.sirs.webserver.grpc.PlanType;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;

import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.*;

public class WebserverMain {

	private static Webserver server;

	// TLS
	private static InputStream cert;
	private static InputStream key;

	private static final String CERTIFICATE_PATH = "../tlscerts/webserver.crt";
	private static final String KEY_PATH = "../tlscerts/webserver.pem";

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

	private static String databaseURL = "jdbc:mysql://localhost:3306/clientdb"; // default value

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
		try {
			if (args.length == 3) {
				serverPort = Integer.parseInt(args[0]);
				String dbHost = args[1];
				int dbPort = Integer.parseInt(args[2]);
				databaseURL = "jdbc:mysql://" + dbHost + ":" + dbPort + "/clientdb";
			}

		} catch (NumberFormatException e) {
			System.out.println("Invalid arguments.");
			System.out.println("Usage: [<serverPort>] [<databaseHost>] [<databasePort>]");
			System.exit(1);
		}

		try {
			cert = Files.newInputStream(Paths.get(CERTIFICATE_PATH));
			key = Files.newInputStream(Paths.get(KEY_PATH));
		} catch(IllegalArgumentException | UnsupportedOperationException | IOException e){
			System.out.println("Could not load server key or certificate.");
			System.exit(1);
		}

		try {
			SslContext sslContext = GrpcSslContexts.forServer(cert, key).build();
			System.out.println(">>> " + WebserverMain.class.getSimpleName() + " <<<");

			loadKeyPair();

			// Database
			System.out.println("Setting up database connection on " + databaseURL);
			Class.forName(DATABASE_DRIVER);
			dbConnection = DriverManager.getConnection(databaseURL, DATABASE_USER, DATABASE_PASSWORD);
			if (dbConnection != null) setupDatabase();

			// Service
			server = new Webserver(dbConnection);
			server.loadCompartmentKeys(keyPair);
			WebserverServiceImpl impl = new WebserverServiceImpl(server);
			Server server = NettyServerBuilder.forPort(serverPort).sslContext(sslContext).addService(impl).build();
			server.start();
			System.out.println("Listening on port " + serverPort + "...");

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

	private static void loadKeyPair() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException {
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
			statement.execute(DROP_INVOICE_TABLE);
			statement.execute(DROP_SOLAR_PANEL_TABLE);
			statement.execute(DROP_APPLIANCE_TABLE);
			statement.execute(DROP_CLIENT_TABLE);

			statement = dbConnection.createStatement();
			statement.execute(CREATE_CLIENT_TABLE);
			statement.execute(CREATE_APPLIANCE_TABLE);
			statement.execute(CREATE_SOLAR_PANEL_TABLE);
			statement.execute(CREATE_INVOICE_TABLE);
			statement.execute(CREATE_COMPARTMENT_KEYS_TABLE);

			generateCompartmentKeys();

			System.out.println("Database is ready!");
		} catch (NoSuchAlgorithmException | SQLException | IllegalBlockSizeException | NoSuchPaddingException | InvalidKeyException e) {
			System.out.println("Could not set up database: "+ e.getMessage());
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

		public void addInvoice(int client_id, float energyConsumed, float energyConsumedDaytime, float energyConsumedNight, String plan) {
			PreparedStatement st;
			float paymentAmount;

			if (plan.equals(PlanType.FLAT_RATE.name())) {
				paymentAmount = energyConsumed * FLAT_RATE_COST;
			}
			else {
				paymentAmount = energyConsumedDaytime * BI_HOURLY_DAYTIME_COST + energyConsumedNight * BI_HOURLY_NIGHT_COST;
			}

			paymentAmount = paymentAmount + paymentAmount * TAXES/100;

			try {
				st = dbConnection.prepareStatement(CREATE_INVOICE);
				st.setInt(1, client_id);
				st.setInt(2, currYear);
				st.setInt(3, currMonth);
				st.setFloat(4, paymentAmount);
				st.setFloat(5, energyConsumed);
				st.setFloat(6, energyConsumedDaytime);
				st.setFloat(7, energyConsumedNight);
				st.setString(8, plan);
				st.setInt(9, TAXES);
				st.executeUpdate();

			} catch (SQLException e) {
				System.out.println(e.getMessage());
			}
		}
		@Override
		public void run() {
			System.out.println("Generating new invoices for " + months.get(currMonth) + " " + currYear);
			PreparedStatement st;
			ResultSet rs;
			try {
				String energyPanelKey = server.getEnergyPanelKey();
				String personalInfoKey = server.getPersonalInfoKey();

				st = dbConnection.prepareStatement(READ_ALL_CLIENTS_ID_ENERGY_CONSUMPTION_PLAN);
				st.setString(1, energyPanelKey);
				st.setString(2, energyPanelKey);
				st.setString(3, energyPanelKey);
				st.setString(4, personalInfoKey);
				rs = st.executeQuery();

				while (rs.next()) {
					int client_id = rs.getInt(1);
					float energyConsumed = rs.getFloat(2);
					float energyConsumedDaytime = rs.getFloat(3);
					float energyConsumedNight = rs.getFloat(4);
					String plan = rs.getString(5);
					addInvoice(client_id, energyConsumed, energyConsumedDaytime, energyConsumedNight, plan);
				}
				incrNextDate();
			} catch (RuntimeException | SQLException e) {
				System.out.println("Could not generate invoices: " + e.getMessage());
			}

		}
	}
}
