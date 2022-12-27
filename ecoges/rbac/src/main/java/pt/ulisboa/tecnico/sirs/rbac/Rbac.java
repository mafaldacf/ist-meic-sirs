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
import pt.ulisboa.tecnico.sirs.webserver.grpc.Compartment;
import pt.ulisboa.tecnico.sirs.webserver.grpc.GetCompartmentKeyRequest;
import pt.ulisboa.tecnico.sirs.webserver.grpc.GetCompartmentKeyResponse;
import pt.ulisboa.tecnico.sirs.webserver.grpc.WebserverBackofficeServiceGrpc;

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
        InvalidRoleException, PermissionDeniedException 
    {
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
}