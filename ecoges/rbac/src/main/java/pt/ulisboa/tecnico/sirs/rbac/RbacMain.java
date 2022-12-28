package pt.ulisboa.tecnico.sirs.rbac;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.sql.*;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class RbacMain {

	private static int serverPort = 8002;

	// TLS
	private static InputStream cert;
	private static InputStream key;

	private static final String CERTIFICATE_PATH = "../tlscerts/rbac-server.crt";
	private static final String KEY_PATH = "../tlscerts/rbac-server.pem";

	// Usage: <serverPort>
	public static void main(String[] args) {
		try {
			if (args.length == 1) {
				// server
				serverPort = Integer.parseInt(args[0]);
			}

		} catch (NumberFormatException e) {
			System.out.println("Invalid arguments.");
			System.out.println("Usage: [<serverPort>]");
			System.out.println("Exiting...");
			System.exit(1);
		}

		// Load server certificate for TLS

		try {
			cert = Files.newInputStream(Paths.get(CERTIFICATE_PATH));
			key = Files.newInputStream(Paths.get(KEY_PATH));
		} catch(IllegalArgumentException | UnsupportedOperationException | IOException e){
			System.out.println("ERROR: Could not load server key or certificate: " + e.getMessage());
			System.out.println("Exiting...");
			System.exit(1);
		}

		// Start server

		try {
			SslContext sslContext = GrpcSslContexts.forServer(cert, key).build();
			System.out.println(">>> " + RbacMain.class.getSimpleName() + " <<<");

			// Services
			Rbac rbacServer = new Rbac();
			Server server = NettyServerBuilder.forPort(serverPort).sslContext(sslContext)
					.addService(new RbacServiceImpl(rbacServer)).build();
			server.start();
			System.out.println("Listening on port " + serverPort + "...");

			// Do not exit the main thread. Wait until server is terminated.
			server.awaitTermination();
		} catch (InterruptedException e) {
			System.out.println("ERROR: Server aborted: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("ERROR: Could not start server: " + e.getMessage());
		} finally {
			System.out.println("Exiting...");
		}
	}
}