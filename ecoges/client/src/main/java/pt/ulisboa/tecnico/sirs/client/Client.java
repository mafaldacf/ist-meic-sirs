package pt.ulisboa.tecnico.sirs.client;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class Client {

	private static ManagedChannel channel;
	private static WebserverServiceGrpc.WebserverServiceBlockingStub server;

	// TLS
	private static final String TRUST_STORE_FILE = "src/main/resources/client.truststore";
	private static final String TRUST_STORE_PASSWORD = "mypassclient";
	private static final String TRUST_STORE_ALIAS_CA = "ca";
	private static final String TRUST_STORE_ALIAS_WEBSERVER = "webserver";

	public static void init(String host, int port) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
		String target = host + ":" + port;

		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
		trustStore.load(Files.newInputStream(Paths.get(TRUST_STORE_FILE)), TRUST_STORE_PASSWORD.toCharArray());
		X509Certificate CACertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_CA);
		X509Certificate webserverCertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_WEBSERVER);

		// Setup ssl context
		SslContext sslContext = GrpcSslContexts.configure(SslContextBuilder.forClient().trustManager(webserverCertificate, CACertificate)).build();

		channel = NettyChannelBuilder.forTarget(target).sslContext(sslContext).build();
		server = WebserverServiceGrpc.newBlockingStub(channel);
	}

	public static void close(){
		channel.shutdown();
	}

	public static boolean register(String name, String email, String password, String address, String iban, int plan) {
		try {
			RegisterRequest request = RegisterRequest.newBuilder()
					.setName(name)
					.setEmail(email)
					.setPassword(password)
					.setAddress(address)
					.setIBAN(iban)
					.setPlan(PlanType.forNumber(plan-1))
					.build();
			AckResponse response = server.register(request);
			return(response.getAck());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static ArrayList<String> login(String email, String password) {
		try {
			LoginRequest request = LoginRequest.newBuilder().setEmail(email).setPassword(password).build();
			LoginResponse response = server.login(request);
			ArrayList<String> cred = new ArrayList<>();
			cred.add(response.getName());
			cred.add(response.getHashedToken());
			return cred;
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	public static boolean logout(String email, String hashedToken) {
		try {
			LogoutRequest request = LogoutRequest.newBuilder()
					.setEmail(email)
					.setHashedToken(hashedToken)
					.build();
			AckResponse response = server.logout(request);
			return(response.getAck());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static boolean addAppliance(String email, String applianceName, String applianceBrand, String hashedToken) {
		try {
			AddEquipmentRequest request = AddEquipmentRequest.newBuilder()
					.setEmail(email)
					.setEquipmentName(applianceName)
					.setEquipmentBrand(applianceBrand)
					.setHashedToken(hashedToken)
					.build();
			AckResponse response = server.addAppliance(request);
			return(response.getAck());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static boolean addSolarPanel(String email, String solarPanelName, String solarPanelBrand, String hashedToken) {
		try {
			AddEquipmentRequest request = AddEquipmentRequest.newBuilder()
					.setEmail(email)
					.setEquipmentName(solarPanelName)
					.setEquipmentBrand(solarPanelBrand)
					.setHashedToken(hashedToken)
					.build();
			AckResponse response = server.addSolarPanel(request);
			return(response.getAck());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static String checkPersonalInfo(String email, String hashedToken) {
		String result = "";
		try {
			CheckPersonalInfoRequest request = CheckPersonalInfoRequest.newBuilder()
					.setEmail(email)
					.setHashedToken(hashedToken)
					.build();
			CheckPersonalInfoResponse response = server.checkPersonalInfo(request);
			PersonalInfo personalInfo = response.getPersonalInfo();
			result += "Name: " + personalInfo.getName() + "\n"
						+ "Email: " + personalInfo.getEmail() + "\n"
						+ "Address: " + personalInfo.getAddress() + "\n"
						+ "IBAN: " + personalInfo.getIBAN() + "\n"
						+ "Energy Plan: " + personalInfo.getPlan().name();
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return result;
	}

	public static String checkEnergyPanel(String email, String hashedToken) {
		String result = "";
		try {
			CheckEnergyPanelRequest request = CheckEnergyPanelRequest.newBuilder()
					.setEmail(email)
					.setHashedToken(hashedToken)
					.build();
			CheckEnergyPanelResponse response = server.checkEnergyPanel(request);

			EnergyPanel energyPanel = response.getEnergyPanel();
			result += "Total Energy Consumed: " + energyPanel.getEnergyConsumed() + " kWh\n"
					+ "Total Energy Consumed Daytime: " + energyPanel.getEnergyConsumedDaytime() + " kWh \n"
					+ "Total Energy Consumed Night: " + energyPanel.getEnergyConsumedNight() + " kWh \n"
					+ "Total Energy Produced: " + energyPanel.getEnergyProduced() + " kWh \n";

			if (energyPanel.getAppliancesCount() > 0) {
				result += "Appliances Consumption:\n";
				for (Appliance appliance : energyPanel.getAppliancesList()) {
					result += "\t" + appliance.getName() + " (" + appliance.getBrand() + ")" + " > Total: "
							+ appliance.getEnergyConsumed() + " kWh, Daytime: " + appliance.getEnergyConsumedDaytime()
							+ " kWh, Night: " + appliance.getEnergyConsumedNight() + " kWh\n";
				}
			}

			if (energyPanel.getSolarPanelsCount() > 0) {
				result += "Solar Panels Production:\n";
				for (SolarPanel solarPanel : energyPanel.getSolarPanelsList()) {
					result += "\t" + solarPanel.getName() + " (" + solarPanel.getBrand() + ")" + " > Total: "
							+ solarPanel.getEnergyProduced() + " kWh \n";
				}
			}

		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return result;
	}

	public static String checkInvoices(String email, String hashedToken) {
		String result = "";
		try {
			CheckInvoicesRequest request = CheckInvoicesRequest.newBuilder()
					.setEmail(email)
					.setHashedToken(hashedToken)
					.build();
			CheckInvoicesResponse response = server.checkInvoices(request);

			if (response.getInvoicesCount() == 0) {
				result += "No invoices to display. Try again later.";
				return result;
			}

			result += "Available invoices:\n";

			for (Invoice invoice : response.getInvoicesList()) {
				result += "\t" + invoice.getMonth() + " " + invoice.getYear() + " > "
						+ "Taxes: " + invoice.getTaxes() + "%, "
						+ "Plan: " + invoice.getPlan() + ", "
						+ "Energy Consumed: "
						+ "Total = " + invoice.getEnergyConsumed() + " kWh, "
						+ "Daytime = " + invoice.getEnergyConsumedDaytime() + " kWh, "
						+ "Night = " + invoice.getEnergyConsumedNight() + " kWh, "
						+ "Payment Amount: " + invoice.getPaymentAmount() + " euros\n";
			}

		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return result;
	}

	public static boolean updateAddress(String email, String address, String hashedToken) {
		try {
			UpdateAddressRequest request = UpdateAddressRequest.newBuilder()
					.setEmail(email)
					.setAddress(address)
					.setHashedToken(hashedToken)
					.build();
			AckResponse response = server.updateAddress(request);
			return response.getAck();
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static boolean updatePlan(String email, int plan, String hashedToken) {
		try {
			UpdatePlanRequest request = UpdatePlanRequest.newBuilder()
					.setEmail(email)
					.setPlan(PlanType.forNumber(plan-1))
					.setHashedToken(hashedToken)
					.build();
			AckResponse response = server.updatePlan(request);
			return response.getAck();
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}
}

