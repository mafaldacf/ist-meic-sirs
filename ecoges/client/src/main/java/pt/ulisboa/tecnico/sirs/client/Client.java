package pt.ulisboa.tecnico.sirs.client;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Client {
	private static ManagedChannel channel;
	private static ServerServiceGrpc.ServerServiceBlockingStub server;

	public static void init(String host, int port) throws IOException {
		String target = host + ":" + port;
		InputStream cert = Files.newInputStream(Paths.get("../keyscerts/webserver.crt"));

		channel = NettyChannelBuilder.forTarget(target).sslContext(GrpcSslContexts.forClient().trustManager(cert).build()).build();
		server = ServerServiceGrpc.newBlockingStub(channel);
	}

	public static void close(){
		channel.shutdown();
	}

	public static boolean register(String email, String password, String address, int plan) {
		try {
			RegisterRequest request = RegisterRequest.newBuilder()
					.setEmail(email)
					.setPassword(password)
					.setAddress(address)
					.setPlan(PlanType.forNumber(plan-1))
					.build();
			AckResponse response = server.register(request);
			return(response.getAck());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static String login(String email, String password) {
		try {
			LoginRequest request = LoginRequest.newBuilder().setEmail(email).setPassword(password).build();
			LoginResponse response = server.login(request);
			return(response.getHashedToken());
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

	public static String checkPersonalInfo(String email, String hashedToken) {
		String result = new String();
		try {
			CheckPersonalInfoRequest request = CheckPersonalInfoRequest.newBuilder()
					.setEmail(email)
					.setHashedToken(hashedToken)
					.build();
			CheckPersonalInfoResponse response = server.checkPersonalInfo(request);
			result += "Email: " + response.getEmail()
						+ ", Address: " + response.getAddress()
						+ ", Plan: " + response.getPlan().name();
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return result;
	}

	public static String checkEnergyConsumption(String email, String hashedToken) {
		String result = new String();
		try {
			CheckEnergyConsumptionRequest request = CheckEnergyConsumptionRequest.newBuilder()
					.setEmail(email)
					.setHashedToken(hashedToken)
					.build();
			CheckEnergyConsumptionResponse response = server.checkEnergyConsumption(request);
			result += "Energy Consumption per Month: " + response.getEnergyConsumedPerMonth() + " kW"
					+ ", Energy Consumption per Hour: " + response.getEnergyConsumedPerHour() + " kW";
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

