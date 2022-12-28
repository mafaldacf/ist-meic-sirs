package pt.ulisboa.tecnico.sirs.backoffice;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.backoffice.exceptions.*;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.*;
import pt.ulisboa.tecnico.sirs.webserver.grpc.Compartment;
import pt.ulisboa.tecnico.sirs.webserver.grpc.GetCompartmentKeyRequest;
import pt.ulisboa.tecnico.sirs.webserver.grpc.GetCompartmentKeyResponse;
import pt.ulisboa.tecnico.sirs.webserver.grpc.WebserverBackofficeServiceGrpc;
import pt.ulisboa.tecnico.sirs.rbac.grpc.*;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.InputStream;
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

    private static final String WEBSERVER_CERTIFICATE_PATH = "../tlscerts/webserver.crt";
    private final WebserverBackofficeServiceGrpc.WebserverBackofficeServiceBlockingStub webserver;
    private final RbacServiceGrpc.RbacServiceBlockingStub rbacserver;

    // Data compartments
    private static final String KEY_STORE_FILE = "src/main/resources/backoffice.keystore";
    private static final String KEY_STORE_PASSWORD = "backoffice";
    private static final String KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT = "accountManagement";
    private static final String KEY_STORE_ALIAS_ENERGY_MANAGEMENT = "energyManagement";

    public Backoffice(Connection dbConnection, String webserverHost, int webserverPort, String rbacHost, int rbacPort) throws IOException {

        this.dbConnection = dbConnection;

        String target = webserverHost + ":" + webserverPort;
        InputStream cert = Files.newInputStream(Paths.get(WEBSERVER_CERTIFICATE_PATH));
        ManagedChannel channel = NettyChannelBuilder.forTarget(target).sslContext(GrpcSslContexts.forClient().trustManager(cert).build()).build();
        webserver = WebserverBackofficeServiceGrpc.newBlockingStub(channel);

        String targetRbac = rbacHost + ":" + rbacPort;
		InputStream certRbac = Files.newInputStream(Paths.get("../tlscerts/rbac-server.crt"));

		ManagedChannel channelRbac = NettyChannelBuilder.forTarget(targetRbac).sslContext(GrpcSslContexts.forClient().trustManager(certRbac).build()).build();
		rbacserver = RbacServiceGrpc.newBlockingStub(channel);
    }

    /*
    ------------------------------------------------
    -------- WEBSERVER BACKOFFICE SERVICE ----------
    ------------------------------------------------
    */

    public Key requestCompartmentKey(Compartment compartment, String role) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidRoleException, KeyStoreException, IOException,
            CertificateException, UnrecoverableKeyException, SignatureException, StatusRuntimeException {

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
                .setCompartment(compartment)
                .setCertificate(ByteString.copyFrom(certificate.getEncoded()))
                .build();

        ByteString signature = Security.signMessage(privateKey, data.toByteArray());

        GetCompartmentKeyRequest request = GetCompartmentKeyRequest.newBuilder()
                .setData(data)
                .setSignature(signature)
                .build();

        GetCompartmentKeyResponse response = webserver.getCompartmentKey(request);

        return Security.unwrapKey(privateKey, response.getKey().toByteArray());
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
            throws SQLException, AdminAlreadyExistsException {

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

        // register username
        st = dbConnection.prepareStatement(CREATE_ADMIN);

        st.setString(1, username);
        st.setString(2, password);
        st.setString(3, role);

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

        st = dbConnection.prepareStatement(READ_ADMIN_PASSWORD_ROLE);
        st.setString(1, username);

        rs = st.executeQuery();

        if (rs.next()) {
            String dbPassword = rs.getString(1);
            if (!password.equals(dbPassword)) {
                throw new WrongPasswordException();
            }
            role = rs.getString(2);
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

    public PersonalInfo checkPersonalInfo(String username, String email, String hashedToken)
        throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, AdminDoesNotExistException,
        InvalidRoleException, PermissionDeniedException, CompartmentKeyException, IllegalBlockSizeException,
        NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException,
        CertificateException, KeyStoreException, IOException, SignatureException, StatusRuntimeException {

        PersonalInfo personalInfo;
        PreparedStatement st;
        ResultSet rs;
        String role;

        validateSession(username, hashedToken);
        // TODO: chamar função do Rbac here
        st = dbConnection.prepareStatement(READ_ADMIN_ROLE);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()) {
            role = rs.getString(1);
        }
        else {
            throw new AdminDoesNotExistException(username);
        }

        //TODO: FZR FUNÇÃO PARA TORNAR ISTO DINAMICO 
        ValidatePermissionRequest request = ValidatePermissionRequest.newBuilder()
            .setRole(Role.valueOf(role))
            .setPermission(PermissionType.PERSONAL_DATA)
            .build();

        ValidatePermissionResponse response = rbacserver.validatePermissions(request);

        st.close();

        String personalInfoKeyString = requestCompartmentKey(Compartment.PERSONAL_INFO, role).toString();

        // get personal info
        st = dbConnection.prepareStatement(READ_CLIENT_PERSONAL_INFO);
        st.setString(1, personalInfoKeyString);
        st.setString(2, personalInfoKeyString);
        st.setString(3, personalInfoKeyString);
        st.setString(4, email);
        rs = st.executeQuery();

        if (rs.next()) {
            String name = rs.getString(1);
            email = rs.getString(2);
            String address = rs.getString(3);
            String iban = rs.getString(4);
            String plan = rs.getString(5);

            personalInfo = PersonalInfo.newBuilder()
                    .setName(name)
                    .setEmail(email)
                    .setAddress(address)
                    .setIBAN(iban)
                    .setPlan(PlanType.valueOf(plan))
                    .build();
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }

        st.close();

        return personalInfo;
    }

    public EnergyPanel checkEnergyPanel(String username, String email, String hashedToken)
        throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, AdminDoesNotExistException,
        InvalidRoleException, PermissionDeniedException, CompartmentKeyException, IllegalBlockSizeException,
        NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException,
        CertificateException, KeyStoreException, IOException, SignatureException, StatusRuntimeException {

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
            throw new AdminDoesNotExistException(username);
        }

        //TODO: FZR FUNÇÃO PARA TORNAR ISTO DINAMICO 
        ValidatePermissionRequest request = ValidatePermissionRequest.newBuilder()
            .setRole(Role.valueOf(role))
            .setPermission(PermissionType.ENERGY_DATA)
            .build();

        ValidatePermissionResponse response = rbacserver.validatePermissions(request);

        st.close();

        String energyPanelKeyString = requestCompartmentKey(Compartment.ENERGY_PANEL, role).toString();

        int clientId = getClientId(email);

        appliances = getAppliances(clientId, energyPanelKeyString);
        solarPanels = getSolarPanels(clientId, energyPanelKeyString);

        st = dbConnection.prepareStatement(READ_CLIENT_ENERGY_PANEL);
        st.setString(1, energyPanelKeyString);
        st.setString(2, energyPanelKeyString);
        st.setString(3, energyPanelKeyString);
        st.setString(4, energyPanelKeyString);
        st.setString(5, email);
        rs = st.executeQuery();

        if (rs.next()) {
            float energyConsumed = rs.getFloat(1);
            float energyConsumedDaytime = rs.getFloat(2);
            float energyConsumedNight = rs.getFloat(3);
            float energyProduced = rs.getFloat(4);

            energyPanel = EnergyPanel.newBuilder()
                    .setEnergyConsumed(energyConsumed)
                    .setEnergyConsumedDaytime(energyConsumedDaytime)
                    .setEnergyConsumedNight(energyConsumedNight)
                    .setEnergyProduced(energyProduced)
                    .addAllAppliances(appliances)
                    .addAllSolarPanels(solarPanels)
                    .build();
        }
        else {
            st.close();
            throw new ClientDoesNotExistException(email);
        }

        st.close();

        return energyPanel;
    }

    /*
    ------------------------------------------------------
    ---------------- AUXILIARY FUNCTIONS -----------------
    ------------------------------------------------------
    */

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

        public List<Appliance> getAppliances(int clientId, String energyPanelKeyString) throws SQLException {
        PreparedStatement st;
        ResultSet rs;
        List<Appliance> appliances = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_APPLIANCES);
        st.setString(1, energyPanelKeyString);
        st.setString(2, energyPanelKeyString);
        st.setString(3, energyPanelKeyString);
        st.setInt(4, clientId);
        rs = st.executeQuery();

        while (rs.next()) {
            String name = rs.getString(1);
            String brand = rs.getString(2);
            float energyConsumed = rs.getFloat(3);
            float energyConsumedDaytime = rs.getFloat(4);
            float energyConsumedNight = rs.getFloat(5);

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

    public List<SolarPanel> getSolarPanels(int clientId, String energyPanelKeyString) throws SQLException {
        PreparedStatement st;
        ResultSet rs;
        List<SolarPanel> solarPanels = new ArrayList<>();

        st = dbConnection.prepareStatement(READ_SOLAR_PANELS);
        st.setString(1, energyPanelKeyString);
        st.setInt(2, clientId);
        rs = st.executeQuery();

        while (rs.next()) {
            String name = rs.getString(1);
            String brand = rs.getString(2);
            float energyProduced = rs.getInt(3);

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
