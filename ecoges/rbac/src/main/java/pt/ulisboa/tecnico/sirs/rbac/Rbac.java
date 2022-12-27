package pt.ulisboa.tecnico.sirs.rbac;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.*;
import pt.ulisboa.tecnico.sirs.rbac.grpc.*;

// STAY?
//import pt.ulisboa.tecnico.sirs.webserver.grpc.Compartment;
//import pt.ulisboa.tecnico.sirs.webserver.grpc.GetCompartmentKeyRequest;
//import pt.ulisboa.tecnico.sirs.webserver.grpc.GetCompartmentKeyResponse;
//import pt.ulisboa.tecnico.sirs.webserver.grpc.WebserverBackofficeServiceGrpc;

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

import static pt.ulisboa.tecnico.sirs.rbac.DatabaseQueries.*;

public class Rbac {
    private final Connection dbConnection;

    //TODO: ADAPT/UPDATE TO RBAC
    private static final String WEBSERVER_CERTIFICATE_PATH = "../tlscerts/webserver.crt";
    private final WebserverBackofficeServiceGrpc.WebserverBackofficeServiceBlockingStub webserver;

    //TODO: requestCompartmentKey
    // Data compartments
    private static final String KEY_STORE_FILE = "src/main/resources/backoffice.keystore";
    private static final String KEY_STORE_PASSWORD = "backoffice";
    private static final String KEY_STORE_ALIAS_ACCOUNT_MANAGEMENT = "accountManagement";
    private static final String KEY_STORE_ALIAS_ENERGY_MANAGEMENT = "energyManagement";

    public Rbac(Connection dbConnection, String webserverHost, int webserverPort) throws IOException {

        this.dbConnection = dbConnection;

        String target = webserverHost + ":" + webserverPort;
        InputStream cert = Files.newInputStream(Paths.get(WEBSERVER_CERTIFICATE_PATH));
        ManagedChannel channel = NettyChannelBuilder.forTarget(target).sslContext(GrpcSslContexts.forClient().trustManager(cert).build()).build();
        webserver = WebserverBackofficeServiceGrpc.newBlockingStub(channel);

    }

    /*
    ------------------------------------------------
    ---------------- ACCESS CONTROL ----------------
    ------------------------------------------------
    */

    public String validatePermissions(String username, String query) throws SQLException, AdminDoesNotExistException,
            InvalidRoleException, PermissionDeniedException {

        PreparedStatement st;
        ResultSet rs;
        String role;

        st = dbConnection.prepareStatement(READ_ADMIN_ROLE);
        st.setString(1, username);
        rs = st.executeQuery();

        if (rs.next()) {
            role = rs.getString(1);
        }
        else {
            throw new AdminDoesNotExistException(username);
        }

        st.close();

        st = dbConnection.prepareStatement(query);
        st.setString(1, role);
        rs = st.executeQuery();

        if (rs.next()) {
            boolean permitted = rs.getBoolean(1);
            if (!permitted) {
                st.close();
                throw new PermissionDeniedException(role);
            }

        }
        else {
            st.close();
            throw new InvalidRoleException(role);
        }

        st.close();
        return role;
    }

    public PersonalInfo checkPersonalInfo(String username, String email, String hashedToken)
            throws ClientDoesNotExistException, SQLException, InvalidSessionTokenException, AdminDoesNotExistException,
            InvalidRoleException, PermissionDeniedException, CompartmentKeyException, IllegalBlockSizeException,
            NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, SignatureException, StatusRuntimeException {

        PersonalInfo personalInfo;
        PreparedStatement st;
        ResultSet rs;

        validateSession(username, hashedToken);
        String role = validatePermissions(username, READ_PERMISSION_PERSONAL_INFO);

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

        validateSession(username, hashedToken);
        String role = validatePermissions(username, READ_PERMISSION_ENERGY_PANEL);

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
