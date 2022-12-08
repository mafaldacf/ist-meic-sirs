package pt.ulisboa.tecnico.sirs.admin;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Admin {
	private static ManagedChannel channel;
	private static ServerServiceGrpc.ServerServiceBlockingStub server;

	public static void init(String host, int port) throws IOException {
		String target = host + ":" + port;
		InputStream cert = Files.newInputStream(Paths.get("../keyscerts/backoffice.crt"));

		channel = NettyChannelBuilder.forTarget(target).sslContext(GrpcSslContexts.forClient().trustManager(cert).build()).build();
		server = ServerServiceGrpc.newBlockingStub(channel);
	}

	public static void close(){
		channel.shutdown();
	}

	public static boolean register(String username, String password) {
		try {
			RegisterRequest request = RegisterRequest.newBuilder()
					.setUsername(username)
					.setPassword(password)
					.build();
			AckResponse response = server.register(request);
			return(response.getAck());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static String login(String username, String password) {
		try {
			LoginRequest request = LoginRequest.newBuilder()
					.setUsername(username)
					.setPassword(password)
					.build();
			LoginResponse response = server.login(request);
			return(response.getHashedToken());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	public static boolean logout(String username, String hashedToken) {
		try {
			LogoutRequest request = LogoutRequest.newBuilder()
					.setUsername(username)
					.setHashedToken(hashedToken)
					.build();
			AckResponse response = server.logout(request);
			return(response.getAck());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}

	public static String listClients(String username, String hashedToken) {
		String clients = new String();
		try {
			ListClientsRequest request = ListClientsRequest.newBuilder()
					.setUsername(username)
					.setHashedToken(hashedToken)
					.build();
			ListClientsResponse response = server.listClients(request);

			for (Client client: response.getClientsList()) {
				clients += "Username: " + client.getEmail()
						+ ", Address: " + client.getAddress()
						+ ", Plan: " + client.getPlanType().name()
						+ ", Energy Consumed Per Month: " + client.getEnergyConsumedPerMonth() + " kW"
						+ ", Energy Consumed Per Hour: " + client.getEnergyConsumedPerHour() + " kW"
						+ "\n";
			}
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return clients;
	}

	public static boolean deleteClient(String username, String email, String hashedToken) {
		try {
			DeleteClientRequest request = DeleteClientRequest.newBuilder()
					.setUsername(username)
					.setEmail(email)
					.setHashedToken(hashedToken)
					.build();
			AckResponse response = server.deleteClient(request);
			return(response.getAck());
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}
}

