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
					request.getSignatureRBAC(), request.getTicketBytes());

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
				 InvalidTicketValidityTimeException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
