package pt.ulisboa.tecnico.sirs.backoffice;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.Appliance;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.EnergyPanel;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.PersonalInfo;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.PlanType;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.SolarPanel;
import pt.ulisboa.tecnico.sirs.contracts.grpc.RoleType;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.backoffice.exceptions.*;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.*;
import pt.ulisboa.tecnico.sirs.contracts.grpc.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static pt.ulisboa.tecnico.sirs.backoffice.DatabaseQueries.*;

public class Backoffice {
    private final Connection dbConnection;
    private WebserverBackofficeServiceGrpc.WebserverBackofficeServiceBlockingStub webserver;
    private RbacServiceGrpc.RbacServiceBlockingStub rbacserver;

    // TLS
    private static final String TRUST_STORE_FILE = "src/main/resources/backoffice.truststore";
    private static final String TRUST_STORE_PASSWORD = "mypassbackoffice";
    private static final String TRUST_STORE_ALIAS_CA = "ca";
    private static final String TRUST_STORE_ALIAS_WEBSERVER = "webserver";
    private static final String TRUST_STORE_ALIAS_RBAC = "rbac";

    // Data compartments
    private static final String KEY_STORE_FILE = "src/main/resources/backoffice.keystore";
    private static final String KEY_STORE_PASSWORD = "mypassbackoffice";
    private static final String KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT = "accountManagement";
    private static final String KEY_STORE_ALIAS_ENERGY_MANAGEMENT = "energyManagement";

    public Backoffice(Connection dbConnection, String webserverHost, int webserverPort, String rbacHost, int rbacPort) throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        this.dbConnection = dbConnection;

        String target = webserverHost + ":" + webserverPort;
        String targetRbac = rbacHost + ":" + rbacPort;

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(Files.newInputStream(Paths.get(TRUST_STORE_FILE)), TRUST_STORE_PASSWORD.toCharArray());

        X509Certificate CACertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_CA);
        X509Certificate webserverCertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_WEBSERVER);
        X509Certificate rbacCertificate = (X509Certificate) trustStore.getCertificate(TRUST_STORE_ALIAS_RBAC);

        // Setup ssl context for webserver connection
        SslContext sslContext = GrpcSslContexts.configure(SslContextBuilder.forClient()
                        .trustManager(webserverCertificate, CACertificate)).build();

        ManagedChannel channel = NettyChannelBuilder.forTarget(target).sslContext(sslContext).build();
        webserver = WebserverBackofficeServiceGrpc.newBlockingStub(channel);

        System.out.println("Connected to webserver on " + target);

        // Setup ssl context for rbac connection
        SslContext sslContextRbac = GrpcSslContexts.configure(SslContextBuilder.forClient()
                .trustManager(rbacCertificate, CACertificate)).build();

        ManagedChannel channelRbac = NettyChannelBuilder.forTarget(targetRbac).sslContext(sslContextRbac).build();
        rbacserver = RbacServiceGrpc.newBlockingStub(channelRbac);

        System.out.println("Connected to rbac on " + targetRbac);
    }

    /*
    ------------------------------------------------
    ------- WEBSERVER - BACKOFFICE SERVICE ---------
    ------------------------------------------------
    */

    public SecretKey requestCompartmentKey(String username, String clientEmail, CompartmentType compartment, String role,
                                           Ticket ticket, ByteString signatureRBAC) throws
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidRoleException,
            KeyStoreException, IOException, CertificateException, UnrecoverableKeyException, SignatureException,
            StatusRuntimeException {

        PrivateKey privateKey;
        X509Certificate certificate;

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

        if (role.equals(RoleType.ACCOUNT_MANAGER.toString())) {
            privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT, KEY_STORE_PASSWORD.toCharArray());
            certificate = (X509Certificate) keyStore.getCertificate(KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT);
        }
        else if (role.equals(RoleType.ENERGY_MANAGER.toString())) {
            privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_ENERGY_MANAGEMENT, KEY_STORE_PASSWORD.toCharArray());
            certificate = (X509Certificate) keyStore.getCertificate(KEY_STORE_ALIAS_ENERGY_MANAGEMENT);
        }
        else {
            throw new InvalidRoleException(role);
        }

        GetCompartmentKeyRequest.RequestData data = GetCompartmentKeyRequest.RequestData.newBuilder()
                .setUsername(username)
                .setRole(RoleType.valueOf(role))
                .setCompartment(compartment)
                .setCertificate(ByteString.copyFrom(certificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(privateKey, data.toByteArray());

        GetCompartmentKeyRequest request = GetCompartmentKeyRequest.newBuilder()
                .setData(data)
                .setSignature(signature)
                .setTicket(ticket)
                .setSignatureRBAC(signatureRBAC)
                .setClientEmail(clientEmail)
                .build();

        GetCompartmentKeyResponse response = webserver.getCompartmentKey(request);

        SecretKey key = Security.unwrapKey(privateKey, response.getKey().toByteArray());

        System.out.println("Successfully retrieved the temporary key for " + compartment.name() + " of " + clientEmail);

        return key;
    }

    public void ackCompartmentKey(String clientEmail, CompartmentType compartment) {
        AckCompartmentKeyRequest request = AckCompartmentKeyRequest.newBuilder()
                .setClientEmail(clientEmail)
                .setCompartment(compartment)
                .build();

        webserver.ackCompartmentKey(request);
    }

    /*
    ------------------------------------------------
    ------------- ADMIN SESSION TOKEN --------------
    ------------------------------------------------
    */

    public String setAdminSession(String username) throws NoSuchAlgorithmException, SQLException {

        PreparedStatement st;

        String token = Security.generateToken();
        String hashedToken = Security.hash(token);

        st = dbConnection.prepareStatement(UPDATE_ADMIN_TOKEN);
        st.setString(1, hashedToken);
        st.setString(2, username);
        st.executeUpdate();
        st.close();

        return hashedToken;
    }

    public void validateSession(String username, String hashedToken)
            throws SQLException, AdminDoesNotExistException, InvalidSessionTokenException {

        PreparedStatement st;
        ResultSet rs;

        st = dbConnection.prepareStatement(READ_ADMIN_COUNT);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) == 0){
            throw new AdminDoesNotExistException(username);
        }
        st.close();

        st = dbConnection.prepareStatement(READ_ADMIN_TOKEN);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()){
            String dbHashedToken = rs.getString(1);
            if (!dbHashedToken.equals(hashedToken))
                throw new InvalidSessionTokenException();
        }
        st.close();
    }

    /*
    -----------------------------------------------
    ---------------- ADMIN SERVICE ----------------
    -----------------------------------------------
    */

    public void register(String username, String password, String role)
            throws SQLException, AdminAlreadyExistsException, NoSuchAlgorithmException {

        PreparedStatement st;
        ResultSet rs;

        // check if username is already registered
        st = dbConnection.prepareStatement(READ_ADMIN_COUNT);
        st.setString(1, username);

        rs = st.executeQuery();

        if (rs.next() && rs.getInt(1) != 0){
            throw new AdminAlreadyExistsException(username);
        }

        st.close();

        byte[] salt = Security.generateRandom();
        String hashedPassword = Security.hashWithSalt(password, salt);

        // register username
        st = dbConnection.prepareStatement(CREATE_ADMIN);

        st.setString(1, username);
        st.setString(2, hashedPassword);
        st.setBytes(3, salt);
        st.setString(4, role);

        st.executeUpdate();


        st.close();
    }

    public List<String> login(String username, String password)
            throws AdminDoesNotExistException, SQLException, WrongPasswordException,
            NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        List<String> response = new ArrayList<>();
        PreparedStatement st;
        ResultSet rs;
        String role;

        st = dbConnection.prepareStatement(READ_ADMIN_PASSWORD_SALT_ROLE);
        st.setString(1, username);

        rs = st.executeQuery();

        if (rs.next()) {
            String dbHashedPassword = rs.getString(1);
            byte[] salt = rs.getBytes(2);
            role = rs.getString(3);

            String hashedPassword = Security.hashWithSalt(password, salt);
            if (!hashedPassword.equals(dbHashedPassword)) {
                st.close();
                throw new WrongPasswordException();
            }
        }
        else {
            throw new AdminDoesNotExistException(username);
        }
        st.close();

        String hashedToken = setAdminSession(username);
        response.add(role);
        response.add(hashedToken);
        return response;
    }

    public boolean logout(String username, String hashedToken)
            throws SQLException, AdminDoesNotExistException, InvalidSessionTokenException {

        PreparedStatement st;

        validateSession(username, hashedToken);

        st = dbConnection.prepareStatement(UPDATE_ADMIN_TOKEN);
        st.setString(1, "");
        st.setString(2, username);
        return true;
    }

    public List<Client> listClients(String username, String hashedToken)
            throws SQLException, AdminDoesNotExistException, InvalidSessionTokenException {
        Statement st;
        ResultSet rs;

        List<Client> clients = new ArrayList<>();

        validateSession(username, hashedToken);

        st = dbConnection.createStatement();
        rs = st.executeQuery(READ_ALL_CLIENTS_NAME_EMAIL);

        while(rs.next()) {
            String name = rs.getString(1);
            String email = rs.getString(2);

            Client client = Client.newBuilder()
                    .setName(name)
                    .setEmail(email)
                    .build();

            clients.add(client);
        }
        st.close();

        return clients;
    }

    public ValidatePermissionResponse validatePermission(String username, String role, CompartmentType permission)
    {
        ValidatePermissionRequest request = ValidatePermissionRequest.newBuilder()
            .setUsername(username)
            .setRole(RoleType.valueOf(role))
            .setPermission(permission)
            .build();

        return rbacserver.validatePermissions(request);
    }

    public PersonalInfo checkPersonalInfo(String username, String clientEmail, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, AdminDoesNotExistException,
            InvalidRoleException, PermissionDeniedException, CompartmentKeyException, IllegalBlockSizeException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, SignatureException, StatusRuntimeException,
            InvalidAlgorithmParameterException, BadPaddingException {

        PersonalInfo personalInfo;
        PreparedStatement st;
        ResultSet rs;
        String role;

        validateSession(username, hashedToken);

        st = dbConnection.prepareStatement(READ_ADMIN_ROLE);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()) {
            role = rs.getString(1);
        }
        else {
            st.close();
            throw new AdminDoesNotExistException(username);
        }

        ValidatePermissionResponse response = validatePermission(username, role, CompartmentType.PERSONAL_DATA);

        SecretKey temporaryKey = requestCompartmentKey(username, clientEmail, CompartmentType.PERSONAL_DATA, role, response.getData(), response.getSignature());

        try {
            byte[] iv = getIv(clientEmail, CompartmentType.PERSONAL_DATA);

            // get personal info
            st = dbConnection.prepareStatement(READ_CLIENT_PERSONAL_INFO);
            st.setString(1, clientEmail);
            rs = st.executeQuery();

            if (rs.next()) {
                String name = rs.getString(1);
                String email = rs.getString(2);
                String plan = rs.getString(3);

                String address = new String(Security.decryptData(rs.getBytes(4), temporaryKey, iv));
                String iban = new String(Security.decryptData(rs.getBytes(5), temporaryKey, iv));

                personalInfo = PersonalInfo.newBuilder()
                        .setName(name)
                        .setEmail(email)
                        .setAddress(address)
                        .setIBAN(iban)
                        .setPlan(PlanType.valueOf(plan))
                        .build();
            } else {
                st.close();
                throw new ClientDoesNotExistException(clientEmail);
            }
        } catch (Exception e) {
            ackCompartmentKey(clientEmail, CompartmentType.PERSONAL_DATA);
            throw e;
        }

        st.close();
        ackCompartmentKey(clientEmail, CompartmentType.PERSONAL_DATA);
        return personalInfo;
    }

    public EnergyPanel checkEnergyPanel(String username, String clientEmail, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, AdminDoesNotExistException,
            InvalidRoleException, PermissionDeniedException, CompartmentKeyException, IllegalBlockSizeException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, SignatureException, StatusRuntimeException, InvalidAlgorithmParameterException, BadPaddingException {

        EnergyPanel energyPanel;
        List<Appliance> appliances;
        List<SolarPanel> solarPanels;
        PreparedStatement st;
        ResultSet rs;
        String role;

        validateSession(username, hashedToken);
        
        st = dbConnection.prepareStatement(READ_ADMIN_ROLE);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()) {
            role = rs.getString(1);
        }
        else {
            st.close();
            throw new AdminDoesNotExistException(username);
        }

        ValidatePermissionResponse response = validatePermission(username, role, CompartmentType.ENERGY_DATA);

        SecretKey temporaryKey = requestCompartmentKey(username, clientEmail, CompartmentType.ENERGY_DATA, role, response.getData(), response.getSignature());

        try {
            byte[] iv = getIv(clientEmail, CompartmentType.ENERGY_DATA);

            int clientId = getClientId(clientEmail);

            appliances = getAppliances(clientId, temporaryKey);
            solarPanels = getSolarPanels(clientId, temporaryKey);

            st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_PANEL);
            st.setString(1, clientEmail);
            rs = st.executeQuery();

            if (rs.next()) {
                byte[] energyConsumedBytes = Security.decryptData(rs.getBytes(1), temporaryKey, iv);
                byte[] energyConsumedDaytimeBytes = Security.decryptData(rs.getBytes(2), temporaryKey, iv);
                byte[] energyConsumedNightBytes = Security.decryptData(rs.getBytes(3), temporaryKey, iv);
                byte[] energyProducedBytes = Security.decryptData(rs.getBytes(4), temporaryKey, iv);

                float energyConsumed = Float.parseFloat(new String(energyConsumedBytes));
                float energyConsumedDaytime = Float.parseFloat(new String(energyConsumedDaytimeBytes));
                float energyConsumedNight = Float.parseFloat(new String(energyConsumedNightBytes));
                float energyProduced = Float.parseFloat(new String(energyProducedBytes));

                energyPanel = EnergyPanel.newBuilder()
                        .setEnergyConsumed(energyConsumed)
                        .setEnergyConsumedDaytime(energyConsumedDaytime)
                        .setEnergyConsumedNight(energyConsumedNight)
                        .setEnergyProduced(energyProduced)
                        .addAllAppliances(appliances)
                        .addAllSolarPanels(solarPanels)
                        .build();
            } else {
                st.close();
                throw new ClientDoesNotExistException(clientEmail);
            }
        } catch (Exception e) {
            ackCompartmentKey(clientEmail, CompartmentType.ENERGY_DATA);
            throw e;
        }

        st.close();
        ackCompartmentKey(clientEmail, CompartmentType.ENERGY_DATA);
        return energyPanel;
    }

    /*
    ------------------------------------------------------
    ---------------- AUXILIARY FUNCTIONS -----------------
    ------------------------------------------------------
    */

    public byte[] getIv(String email, CompartmentType compartment) throws SQLException, ClientDoesNotExistException {
        PreparedStatement st;
        ResultSet rs;
        byte[] iv;

        if (compartment.equals(CompartmentType.PERSONAL_DATA)) {
            st = dbConnection.prepareStatement(READ_CLIENT_IV_PERSONAL_DATA);
        }
        else {
            st = dbConnection.prepareStatement(READ_CLIENT_IV_ENERGY_DATA);
        }

        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next()) {
            iv = rs.getBytes(1);
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }

        st.close();
        return iv;
    }

    public int getClientId(String email) throws SQLException, ClientDoesNotExistException {
        PreparedStatement st;
        ResultSet rs;
        int client_id;

        // get client id
        st = dbConnection.prepareStatement(READ_CLIENT_ID);
        st.setString(1, email);
        rs = st.executeQuery();

        if (rs.next()) {
            client_id = rs.getInt(1);
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }
        st.close();

        return client_id;
        }

        public List<Appliance> getAppliances(int clientId, SecretKey energyPanelKey) throws SQLException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        PreparedStatement st;
        ResultSet rs;
        List<Appliance> appliances = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_APPLIANCES);
        st.setInt(1, clientId);
        rs = st.executeQuery();

        while (rs.next()) {
            byte[] iv = rs.getBytes(1);
            String name = rs.getString(2);
            String brand = rs.getString(3);

            byte[] energyConsumedBytes = Security.decryptData(rs.getBytes(4), energyPanelKey, iv);
            byte[] energyConsumedDaytimeBytes = Security.decryptData(rs.getBytes(5), energyPanelKey, iv);
            byte[] energyConsumedNightBytes = Security.decryptData(rs.getBytes(6), energyPanelKey, iv);

            float energyConsumed = Float.parseFloat(new String(energyConsumedBytes));
            float energyConsumedDaytime = Float.parseFloat(new String(energyConsumedDaytimeBytes));
            float energyConsumedNight = Float.parseFloat(new String(energyConsumedNightBytes));

            Appliance appliance = Appliance.newBuilder()
                    .setName(name)
                    .setBrand(brand)
                    .setEnergyConsumed(energyConsumed)
                    .setEnergyConsumedDaytime(energyConsumedDaytime)
                    .setEnergyConsumedNight(energyConsumedNight)
                    .build();
            appliances.add(appliance);
        }
        st.close();

        return appliances;
    }

    public List<SolarPanel> getSolarPanels(int clientId, SecretKey energyPanelKey) throws SQLException,
            InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        PreparedStatement st;
        ResultSet rs;
        List<SolarPanel> solarPanels = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_SOLAR_PANELS);
        st.setInt(1, clientId);
        rs = st.executeQuery();

        while (rs.next()) {
            byte[] iv = rs.getBytes(1);
            String name = rs.getString(2);
            String brand = rs.getString(3);

            byte[] energyProducedBytes = Security.decryptData(rs.getBytes(4), energyPanelKey, iv);
            float energyProduced = Float.parseFloat(new String(energyProducedBytes));

            SolarPanel solarPanel = SolarPanel.newBuilder()
                    .setName(name)
                    .setBrand(brand)
                    .setEnergyProduced(energyProduced)
                    .build();
            solarPanels.add(solarPanel);
        }
        st.close();

        return solarPanels;
    }
}
