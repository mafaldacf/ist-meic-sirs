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
	public void checkPersonalInfo(CheckPersonalInfoRequest request, StreamObserver<CheckPersonalInfoResponse> responseObserver) {
		CheckPersonalInfoResponse.Builder builder = CheckPersonalInfoResponse.newBuilder();
		try {
			PersonalInfo personalInfo = server.checkPersonalInfo(request.getUsername(), request.getEmail(), request.getHashedToken());

			builder.setPersonalInfo(personalInfo);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException | IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException |
				 UnrecoverableKeyException | CertificateException | KeyStoreException | SignatureException |
				 InvalidKeyException | IOException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidRoleException | InvalidSessionTokenException | CompartmentKeyException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch (PermissionDeniedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientDoesNotExistException | AdminDoesNotExistException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		} catch (StatusRuntimeException e) {
			responseObserver.onError(e.getStatus().asRuntimeException());
		}
	}

	@Override
	public void checkEnergyPanel(CheckEnergyPanelRequest request, StreamObserver<CheckEnergyPanelResponse> responseObserver) {
		CheckEnergyPanelResponse.Builder builder = CheckEnergyPanelResponse.newBuilder();
		try {
			EnergyPanel energyPanel = server.checkEnergyPanel(request.getUsername(), request.getEmail(), request.getHashedToken());

			builder.setEnergyPanel(energyPanel);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException | IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException |
				 UnrecoverableKeyException | CertificateException | KeyStoreException | SignatureException |
				 InvalidKeyException | IOException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidRoleException | InvalidSessionTokenException | CompartmentKeyException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch (PermissionDeniedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientDoesNotExistException | AdminDoesNotExistException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		} catch (StatusRuntimeException e) {
			responseObserver.onError(e.getStatus().asRuntimeException());
		}
	}
}
