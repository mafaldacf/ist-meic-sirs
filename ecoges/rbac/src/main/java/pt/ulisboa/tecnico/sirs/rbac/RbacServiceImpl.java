package pt.ulisboa.tecnico.sirs.rbac;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.*;
import pt.ulisboa.tecnico.sirs.rbac.grpc.*;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.List;

public class RbacServiceImpl extends RbacServiceGrpc.RbacServiceImplBase {
	private static Rbac server;

	public RbacServiceImpl(Rbac rbacServer) {
		server = rbacServer;
	}

	@Override
	public void validatePermissions(ValidatePermissionRequest request, StreamObserver<ValidatePermissionResponse> responseObserver) {
		ValidatePermissionResponse.Builder builder = ValidatePermissionResponse.newBuilder();
		try {
			server.validatePermissions(request.getRole(), request.getPermission());

			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (PermissionDeniedException e){
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidRoleException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

}
