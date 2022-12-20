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
			RegisterMobileRequest request = RegisterMobileRequest.newBuilder().setEmail(email).setPassword(password).build();
			RegisterMobileResponse response = server.registerMobile(request);
			String cred = response.getToken();
			return cred;
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	//TODO: Desenvolver method to factor e deppis no MobileMain.java a interface na consola
	public static string twoFactorMobile(String username, String token) {
		try {
			twoFactorMobileRequest request = twoFactorMobileRequest.newBuilder().setUsername(username).setToken(token).build();
			twoFactorMobileResponse response = server.twoFactorMobile(request);
			String 2FAkey = response.get2FAkey();
			return 2FAkey;
		} catch (StatusRuntimeException e) {
			System.out.println(e.getMessage());
		}
		return null;
	}
}

