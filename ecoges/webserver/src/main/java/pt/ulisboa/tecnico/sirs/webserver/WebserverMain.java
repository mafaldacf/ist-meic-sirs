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
import pt.ulisboa.tecnico.sirs.webserver.grpc.PlanType;

import static pt.ulisboa.tecnico.sirs.webserver.DatabaseQueries.*;

public class WebserverMain {
	private static InputStream cert;
	private static InputStream key;

	private static Connection dbConnection = null;

	private static final String dbUser = "ecoges";
	private static final String dbPassword = "admin";

	private static final String dbDriver = "com.mysql.cj.jdbc.Driver";

	private static String dbURL = "jdbc:mysql://localhost:3306/clientdb"; // default value

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
	public static void main(String[] args) throws IOException, UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
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
			cert = Files.newInputStream(Paths.get("../tlscerts/webserver.crt"));
			key = Files.newInputStream(Paths.get("../tlscerts/webserver.pem"));
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

			// Automatically generate invoices at each 15 seconds
			Timer time = new Timer();
			generateInvoices task = new generateInvoices();
			time.schedule(task, 20000, 15000);
			//TODO: new thread for automatic delete

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
			System.out.print("SOmething happened");
			System.exit(1);
		}
	}

	private static void setupDatabase() {
		Statement statement;

		try {
			statement = dbConnection.createStatement();
			statement.execute(DROP_INVOICE_TABLE);
			statement.execute(DROP_SOLAR_PANEL_TABLE);
			statement.execute(DROP_APPLIANCE_TABLE);
			statement.execute(DROP_MOBILE_TABLE);
			statement.execute(DROP_CLIENT_TABLE);
			


			statement = dbConnection.createStatement();
			statement.execute(CREATE_CLIENT_TABLE);
			statement.execute(CREATE_APPLIANCE_TABLE);
			statement.execute(CREATE_SOLAR_PANEL_TABLE);
			statement.execute(CREATE_INVOICE_TABLE);
			statement.execute(CREATE_MOBILE_TABLE);


			System.out.println("Database is ready!");
		} catch (SQLException e) {
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
			// System.out.println("Generating new invoices for " + months.get(currMonth) + " " + currYear);
			PreparedStatement st;
			ResultSet rs;
			try {
				st = dbConnection.prepareStatement(READ_ALL_CLIENTS_ID_ENERGY_CONSUMPTION_PLAN);
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
			} catch (SQLException e) {
				System.out.println(e.getMessage());
			}
		}
	}
}
