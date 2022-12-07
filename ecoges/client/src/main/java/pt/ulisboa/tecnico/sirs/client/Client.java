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

	public static boolean register(String username, String password, String address, int plan) {
		try {
			RegisterRequest request = RegisterRequest.newBuilder()
					.setUsername(username)
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

	public static boolean login(String username, String password) {
		try {
			LoginRequest request = LoginRequest.newBuilder().setUsername(username).setPassword(password).build();
			AckResponse response = server.login(request);
			return(response.getAck());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static String checkPersonalInfo(String username) {
		String result = new String();
		try {
			CheckPersonalInfoRequest request = CheckPersonalInfoRequest.newBuilder().setUsername(username).build();
			CheckPersonalInfoResponse response = server.checkPersonalInfo(request);
			result += "Address: " + response.getAddress() + ", Plan: " + response.getPlan().name();
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return result;
	}

	public static String checkEnergyConsumption(String username) {
		String result = new String();
		try {
			CheckEnergyConsumptionRequest request = CheckEnergyConsumptionRequest.newBuilder().setUsername(username).build();
			CheckEnergyConsumptionResponse response = server.checkEnergyConsumption(request);
			result += "Energy Consumption per Month: " + response.getEnergyConsumedPerMonth()
					+ ", Energy Consumption per Hour: " + response.getEnergyConsumedPerHour();
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return result;
	}

	public static boolean updateAddress(String username, String address) {
		try {
			UpdateAddressRequest request = UpdateAddressRequest.newBuilder()
					.setUsername(username)
					.setAddress(address)
					.build();
			AckResponse response = server.updateAddress(request);
			return response.getAck();
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static boolean updatePlan(String username, int plan) {
		try {
			UpdatePlanRequest request = UpdatePlanRequest.newBuilder()
					.setUsername(username)
					.setPlan(PlanType.forNumber(plan-1))
					.build();
			AckResponse response = server.updatePlan(request);
			return response.getAck();
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}
}

