package pt.ulisboa.tecnico.sirs.rbac;

import com.google.protobuf.ByteString;
import pt.ulisboa.tecnico.sirs.security.Security;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.*;
import pt.ulisboa.tecnico.sirs.rbac.grpc.*;

import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import javax.annotation.Nullable;
import java.util.*;
import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime; 

public class Rbac {
    
    private RbacServiceGrpc.RbacServiceBlockingStub rbacserver;

    Map<Role, PermissionType> PermissionsByRoles = Map.ofEntries(
        Map.entry(Role.ENERGY_MANAGER, PermissionType.ENERGY_DATA),
        Map.entry(Role.ACCOUNT_MANAGER, PermissionType.PERSONAL_DATA)
    );

    // Data compartments
	private static String KEY_STORE_FILE = "src/main/resources/rbac.keystore";
	private static final String KEY_STORE_PASSWORD = "mypassrbac";
    private static final String KEY_STORE_ALIAS_RBAC = "rbac"; 

    // constructor used in JUnit tests
    public Rbac(String keyStoreFilePath)
    {
        KEY_STORE_FILE = keyStoreFilePath;
    }

    public Rbac()
    {
        KEY_STORE_FILE = "../rbac/src/main/resources/rbac.keystore";
    }

    public ValidatePermissionResponse generateResponse(String username, Role role, PermissionType permission) throws
        NoSuchAlgorithmException, InvalidKeyException, KeyStoreException, IOException,
        CertificateException, UnrecoverableKeyException, SignatureException 
    {
        PrivateKey privateKey;

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(Files.newInputStream(Paths.get(KEY_STORE_FILE)), KEY_STORE_PASSWORD.toCharArray());

        privateKey = (PrivateKey) keyStore.getKey(KEY_STORE_ALIAS_RBAC, KEY_STORE_PASSWORD.toCharArray());
      
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nowPlusSeconds = now.plusSeconds(30);

        ValidatePermissionResponse.Ticket data = ValidatePermissionResponse.Ticket.newBuilder()
                .setUsername(username)
                .setRole(role)
                .setPermission(permission)
                .setRequestIssuedAt(dtf.format(now))
                .setRequestValidUntil(dtf.format(nowPlusSeconds))
                .build();

        ByteString signature = Security.signMessage(privateKey, data.toByteArray());

        ValidatePermissionResponse response = ValidatePermissionResponse.newBuilder()
                .setData(data)
                .setSignature(signature)
                .build();

        return response;
    }

    /*
    ------------------------------------------------
    ---------------- ACCESS CONTROL ----------------
    ------------------------------------------------
    */

    public ValidatePermissionResponse validatePermissions(String username, Role role, PermissionType permission) throws NoSuchPaddingException,
        NoSuchAlgorithmException, InvalidKeyException, KeyStoreException, IOException,
        CertificateException, UnrecoverableKeyException, SignatureException,
        PermissionDeniedException, InvalidRoleException
    {
        if(PermissionsByRoles.containsKey(role)) {            
            if(PermissionsByRoles.get(role).equals(permission)) {
                ValidatePermissionResponse response = generateResponse(username, role, permission);
                logGrantedAccess(response);
                return response;
            }
            else {
                logDeniedAccess(username, role.name(), permission.name());
                throw new PermissionDeniedException(role.name());
            }
        }
        else {
            throw new InvalidRoleException(role.name());
        }
    }

    public void logGrantedAccess(ValidatePermissionResponse response) {
        System.out.println("[+] Granted access to " + response.getData().getRole().name() + " "
                + response.getData().getUsername() + " for " + response.getData().getPermission().name()
                + " compartment: " + "issued at " + response.getData().getRequestIssuedAt()
                + " and valid until " + response.getData().getRequestValidUntil());
    }

    public void logDeniedAccess(String username, String role, String permission) {
        System.out.println("[-] Denied access to " + role + " " + username + " for " + permission + " compartment");
    }
}