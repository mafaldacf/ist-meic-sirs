package pt.ulisboa.tecnico.sirs.admin;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.HelloRequest;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.HelloResponse;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.ServerServiceGrpc;

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

	public static String hello(String name) {
		try {
			HelloRequest request = HelloRequest.newBuilder().setName(name).build();
			HelloResponse response = server.hello(request);
			return(response.getResponse());
		} catch (StatusRuntimeException e) {
			return(e.getMessage());
		}
	}

	public static boolean register(String username, String password) {
		//TODO
		return false;
	}

	public static boolean login(String username, String password) {
		//TODO
		return false;
	}

	public static String listUsers() {
		//TODO
		return "";
	}

	public static String checkUser(int id) {
		//TODO
		return "";
	}
}

