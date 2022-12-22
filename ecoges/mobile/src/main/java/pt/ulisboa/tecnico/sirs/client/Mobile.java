package pt.ulisboa.tecnico.sirs.mobile;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class Mobile {
	private static ManagedChannel channel;
	private static ServerServiceGrpc.ServerServiceBlockingStub server;

	public static void init(String host, int port) throws IOException {
		String target = host + ":" + port;
		InputStream cert = Files.newInputStream(Paths.get("../tlscerts/webserver.crt"));

		channel = NettyChannelBuilder.forTarget(target).sslContext(GrpcSslContexts.forClient().trustManager(cert).build()).build();
		server = ServerServiceGrpc.newBlockingStub(channel);
	}

	public static void close(){
		channel.shutdown();
	}

	public static String registerMobile(String email, String password) {
		try {
			RegisterMobileRequest request = RegisterMobileRequest.newBuilder()
				.setEmail(email).setPassword(password).build();
			RegisterMobileResponse response = server.registerMobile(request);
			String cred = response.getToken();
			return cred;
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	public static ArrayList<String> loginMobile(String email, String password) {
		try {
			LoginMobileRequest request = LoginMobileRequest.newBuilder()
				.setEmail(email).setPassword(password).build();
			LoginMobileResponse response = server.loginMobile(request);
			ArrayList<String> cred = new ArrayList<>();
			cred.add(response.getName());
			cred.add(response.getToken());
			return cred;
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	public static String twoFactorMobile(String username, String token) {
		try {
			TwoFactorMobileRequest request = TwoFactorMobileRequest.newBuilder()
				.setUsername(username).setToken(token).build();
			TwoFactorMobileResponse response = server.twoFactorMobile(request);
			String twoFAkey = response.getTwoFAkey();
			return twoFAkey;
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	public static boolean logoutMobile(String email, String token) {
		try {
			LogoutMobileRequest request = LogoutMobileRequest.newBuilder()
					.setEmail(email).setToken(token).build();
			AckResponse response = server.logoutMobile(request);
			return response.getAck();
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return false;
	}
}

