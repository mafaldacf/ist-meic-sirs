package pt.ulisboa.tecnico.sirs.backoffice;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.*;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.webserver.grpc.Compartment;
import pt.ulisboa.tecnico.sirs.webserver.grpc.GetCompartmentKeyRequest;
import pt.ulisboa.tecnico.sirs.webserver.grpc.GetCompartmentKeyResponse;
import pt.ulisboa.tecnico.sirs.webserver.grpc.WebserverBackofficeServiceGrpc;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Random;

public class RequestCompartmentKeysTests {

    private static WebserverBackofficeServiceGrpc.WebserverBackofficeServiceBlockingStub webserver;
    private static ManagedChannel channel;

    private static final String webserverHost = "localhost";
    private static final int webserverPort = 8000;

    private static X509Certificate certificate;
    private static PrivateKey privateKey;
    // TLS
    private static final String TRUST_STORE_FILE = "src/main/resources/backoffice.truststore";
    private static final String TRUST_STORE_PASSWORD = "mypassbackoffice";
    private static final String TRUST_STORE_ALIAS_CA = "ca";
    private static final String TRUST_STORE_ALIAS_WEBSERVER = "webserver";

    // Data compartments
    private static final String KEY_STORE_FILE = "src/main/resources/backoffice.keystore";
    private static final String KEY_STORE_PASSWORD = "mypassbackoffice";
    private static final String KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT = "accountManagement";
    private static final String KEY_STORE_ALIAS_ENERGY_MANAGEMENT = "energyManagement";

    @BeforeClass
    public static void setup() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        String target = webserverHost + ":" + webserverPort;

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(Files.newInputStream(Paths.get(TRUST_STORE_FILE)), TRUST_STORE_PASSWORD.toCharArray());

        X509Certificate CACertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_CA);
        X509Certificate webserverCertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_WEBSERVER);

        // Setup ssl context for webserver connection
        SslContext sslContext = GrpcSslContexts.configure(SslContextBuilder.forClient()
                .trustManager(webserverCertificate, CACertificate)).build();

        channel = NettyChannelBuilder.forTarget(target).sslContext(sslContext).build();
        webserver = WebserverBackofficeServiceGrpc.newBlockingStub(channel);

        generateCertificate();
    }

    @After
    public void cleanup() {
    }

    @AfterClass
    public static void teardown() {
        channel.shutdown();
    }

    @Test
    public void requestCompartmentKeyTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateException, KeyStoreException, IOException, UnrecoverableKeyException {
        // Load account manager certificate
        PrivateKey privateKey;
        X509Certificate certificate;

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

        privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT, KEY_STORE_PASSWORD.toCharArray());
        certificate = (X509Certificate) keyStore.getCertificate(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT);


        GetCompartmentKeyRequest.RequestData data = GetCompartmentKeyRequest.RequestData.newBuilder()
                .setCompartment(Compartment.PERSONAL_INFO)
                .setCertificate(ByteString.copyFrom(certificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(privateKey, data.toByteArray());

        GetCompartmentKeyRequest request = GetCompartmentKeyRequest.newBuilder()
                .setData(data)
                .setSignature(signature)
                .build();

        GetCompartmentKeyResponse response = webserver.getCompartmentKey(request);
        Assert.assertNotNull(response.getKey());
    }

    @Test
    public void requestCompartmentKeyWithInvalidCertificateChainTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateEncodingException {

        GetCompartmentKeyRequest.RequestData data = GetCompartmentKeyRequest.RequestData.newBuilder()
                .setCompartment(Compartment.PERSONAL_INFO)
                .setCertificate(ByteString.copyFrom(certificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(privateKey, data.toByteArray());

        GetCompartmentKeyRequest request = GetCompartmentKeyRequest.newBuilder()
                .setData(data)
                .setSignature(signature)
                .build();

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            webserver.getCompartmentKey(request);
        });

        Assert.assertTrue(e.getMessage().contains(Status.INVALID_ARGUMENT.getCode().toString()));
        Assert.assertTrue(e.getMessage().contains("Invalid certificate chain."));
    }

    @Test
    public void requestCompartmentKeyWithInvalidSignatureTest() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, CertificateEncodingException {
        // Create random private key that does not match certificate
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");

        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        privateKey = keyPair.getPrivate();


        GetCompartmentKeyRequest.RequestData data = GetCompartmentKeyRequest.RequestData.newBuilder()
                .setCompartment(Compartment.PERSONAL_INFO)
                .setCertificate(ByteString.copyFrom(certificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(privateKey, data.toByteArray());

        GetCompartmentKeyRequest request = GetCompartmentKeyRequest.newBuilder()
                .setData(data)
                .setSignature(signature)
                .build();

        Exception e = Assert.assertThrows(StatusRuntimeException.class, () -> {
            webserver.getCompartmentKey(request);
        });

        Assert.assertTrue(e.getMessage().contains(Status.INVALID_ARGUMENT.getCode().toString()));
        Assert.assertTrue(e.getMessage().contains("Invalid signature."));
    }

    public static void generateCertificate() throws NoSuchAlgorithmException, CertificateEncodingException, SignatureException, InvalidKeyException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);

        KeyPair keyPair = keyGen.generateKeyPair();

        PublicKey publicKey = keyPair.getPublic();
        privateKey = keyPair.getPrivate();

        X509V3CertificateGenerator certificateGenerator = new X509V3CertificateGenerator();

        // Set the certificate details
        certificateGenerator.setSerialNumber(BigInteger.valueOf(new Random().nextInt()));
        certificateGenerator.setSubjectDN(new X509Principal("CN=localhost"));
        certificateGenerator.setIssuerDN(new X509Principal("CN=localhost"));
        certificateGenerator.setNotBefore(new Date());
        certificateGenerator.setNotAfter(new Date(System.currentTimeMillis() + 365 * 24 * 3600));
        certificateGenerator.setPublicKey(publicKey);
        certificateGenerator.setSignatureAlgorithm("SHA256WithRSAEncryption");

        certificate = certificateGenerator.generate(privateKey);
    }
}
