package pt.ulisboa.tecnico.sirs.webserver;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.*;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;

public class WebserverBackofficeServiceImpl extends WebserverBackofficeServiceGrpc.WebserverBackofficeServiceImplBase {
	private static Webserver server;

	public WebserverBackofficeServiceImpl(Webserver webServer) throws SQLException, ClassNotFoundException {
		server = webServer;
	}

	@Override
	public void getCompartmentKey(GetCompartmentKeyRequest request, StreamObserver<GetCompartmentKeyResponse> responseObserver) {
		GetCompartmentKeyResponse.Builder builder = GetCompartmentKeyResponse.newBuilder();
		try {
			byte[] key = server.getCompartmentKey(request.getData(), request.getSignature(), request.getTicket(),
					request.getSignatureRBAC(), request.getTicketBytes(), request.getClientEmail());

			builder.setKey(ByteString.copyFrom(key));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (CompartmentKeyException e){
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		} catch (IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException |
				 InvalidKeySpecException | CertificateException | SignatureException | BadPaddingException  |
				 KeyStoreException | IOException | InvalidAlgorithmParameterException  e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidSignatureException | InvalidCertificateChainException | InvalidTicketUsernameException |
				 InvalidTicketCompartmentException | InvalidTicketRoleException | InvalidTicketIssuedTimeException |
				 InvalidTicketValidityTimeException | SQLException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void ackCompartmentKey(AckCompartmentKeyRequest request, StreamObserver<AckCompartmentKeyResponse> responseObserver) {
		AckCompartmentKeyResponse.Builder builder = AckCompartmentKeyResponse.newBuilder();
		try {
			server.ackCompartmentKey(request.getClientEmail(), request.getCompartment());

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException |
				 BadPaddingException | InvalidAlgorithmParameterException  e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (SQLException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
