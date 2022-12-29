package pt.ulisboa.tecnico.sirs.rbac;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.*;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class RbacMain {

	private static int serverPort = 8002;

	// TLS
	private static KeyPair keyPair;

	private static X509Certificate certificate;
	private static X509Certificate CACertificate;
	private static final String KEY_STORE_FILE = "src/main/resources/rbac.keystore";
	private static final String KEY_STORE_PASSWORD = "mypassrbac";
	private static final String KEY_STORE_ALIAS_RBAC = "rbac";

	private static final String TRUST_STORE_FILE = "src/main/resources/rbac.truststore";
	private static final String TRUST_STORE_PASSWORD = "mypassrbac";
	private static final String TRUST_STORE_ALIAS_CA = "ca";

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

		try {
			loadKeysCertificates();
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException e) {
			System.out.println("ERROR: Could not load server keys and certificates: " + e.getMessage());
			System.out.println("Exiting...");
			System.exit(1);
		}

		// Start server

		try {
			System.out.println(">>> " + RbacMain.class.getSimpleName() + " <<<");

			// Setup ssl context
			SslContext sslContext = GrpcSslContexts.configure(SslContextBuilder
					.forServer(keyPair.getPrivate(), certificate)
					.trustManager(CACertificate)).build();

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

	private static void loadKeysCertificates() throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
			IOException, UnrecoverableKeyException {
		PrivateKey privateKey;
		PublicKey publicKey;

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

		privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_RBAC, KEY_STORE_PASSWORD.toCharArray());
		publicKey = keyStore.getCertificate(KEY_STORE_ALIAS_RBAC).getPublicKey();
		keyPair = new KeyPair(publicKey, privateKey);
		certificate = (X509Certificate) keyStore.getCertificate(KEY_STORE_ALIAS_RBAC);

		// Trust Store
		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
		trustStore.load(Files.newInputStream(Paths.get(TRUST_STORE_FILE)), TRUST_STORE_PASSWORD.toCharArray());
		CACertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_CA);

		System.out.println("Successfully loaded key pairs from Java Keystore!");
	}
}