package pt.ulisboa.tecnico.sirs.rbac;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.*;
import pt.ulisboa.tecnico.sirs.contracts.grpc.*;

import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

public class RbacServiceImpl extends RbacServiceGrpc.RbacServiceImplBase {
	private static Rbac server;

	public RbacServiceImpl(Rbac rbacServer) {
		server = rbacServer;
	}

	@Override
	public void validatePermissions(ValidatePermissionRequest request, StreamObserver<ValidatePermissionResponse> responseObserver) {
		try {		
			ValidatePermissionResponse response = server.validatePermissions(request.getUsername(), request.getRole(), request.getPermission());
			ValidatePermissionResponse.Builder builder = response.toBuilder();

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (NoSuchPaddingException | NoSuchAlgorithmException | UnrecoverableKeyException | 
			CertificateException | KeyStoreException | SignatureException | InvalidKeyException | IOException e)
		{
   			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (PermissionDeniedException e){
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidRoleException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

}
