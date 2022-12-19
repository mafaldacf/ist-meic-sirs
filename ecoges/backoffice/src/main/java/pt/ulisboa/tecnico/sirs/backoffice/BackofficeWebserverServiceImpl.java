package pt.ulisboa.tecnico.sirs.backoffice;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.backoffice.exceptions.CompartmentKeyException;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.*;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.List;

public class BackofficeWebserverServiceImpl extends BackofficeWebserverServiceGrpc.BackofficeWebserverServiceImplBase {
	private static Backoffice server;

	public BackofficeWebserverServiceImpl(Backoffice backofficeServer) {
		server = backofficeServer;
	}

	@Override
	public void getCompartmentKeys(getCompartmentKeysRequest request, StreamObserver<getCompartmentKeysResponse> responseObserver) {
		try {
			getCompartmentKeysResponse.Builder builder = getCompartmentKeysResponse.newBuilder();
			List<byte[]> keys = server.getCompartmentKeys();

			builder.setPersonalInfoKey(ByteString.copyFrom(keys.get(0)));
			builder.setEnergyPanelKey(ByteString.copyFrom(keys.get(1)));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException |
				 SQLException | CompartmentKeyException | KeyStoreException | IOException | CertificateException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
