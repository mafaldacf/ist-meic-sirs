package pt.ulisboa.tecnico.sirs.backoffice;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;

public class BackofficeMain {
	private static Server server = null;
	private static BackofficeServiceImpl impl = null;
	private static InputStream cert;
	private static InputStream key;

	public static void main(String[] args) throws IOException {

		if (args.length != 1) {
			System.out.println("Could not start server.");
			System.out.println("Usage: <serverPort>");
			return;
		}

		int serverPort = Integer.parseInt(args[0]);

		try {
			cert = Files.newInputStream(Paths.get("../keyscerts/backoffice.crt"));
			key = Files.newInputStream(Paths.get("../keyscerts/backoffice.pem"));
		} catch(IllegalArgumentException | UnsupportedOperationException | IOException e){
			System.out.println("Could not load server key or certificate.");
			return;
		}

		SslContext sslContext = GrpcSslContexts.forServer(cert, key).build();

		System.out.println(">>> " + BackofficeMain.class.getSimpleName() + " <<<");

		try {
			impl = new BackofficeServiceImpl();
			server = NettyServerBuilder.forPort(serverPort).sslContext(sslContext).addService(impl).build();
			server.start();
			System.out.println("Listening on port " + serverPort + "...");

			// Do not exit the main thread. Wait until server is terminated.
			server.awaitTermination();
		} catch (InterruptedException e) {
			System.out.println("ERROR: Server aborted.");
		} catch (IOException e) {
			System.out.println("ERROR: Could not start server.");
		} finally {
			server.shutdown();
		}
	}
}
