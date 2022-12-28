package pt.ulisboa.tecnico.sirs.rbac;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.*;
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
import java.util.*;

public class Rbac {

    Map<Role, PermissionType> PermissionsByRoles = Map.ofEntries(
        Map.entry(Role.ENERGY_MANAGER, PermissionType.ENERGY_DATA),
        Map.entry(Role.ACCOUNT_MANAGER, PermissionType.PERSONAL_DATA)
    );

    public Rbac() { }

    //TODO: ADPTAR ESTA FUNC 
    /*public Key requestCompartmentKey(Compartment compartment, String role) throws NoSuchPaddingException,
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
    }*/


    /*
    ------------------------------------------------
    ---------------- ACCESS CONTROL ----------------
    ------------------------------------------------
    */

    public boolean validatePermissions(Role role, PermissionType permission) throws InvalidRoleException, 
        PermissionDeniedException 
    {
        if(PermissionsByRoles.containsKey(role)) {            
            if(PermissionsByRoles.get(role).equals(permission)) {
                return true;
            }
            else {
                throw new PermissionDeniedException(role.name());
            }
        }
        else {
            throw new InvalidRoleException(role.name());
        }
    }
}