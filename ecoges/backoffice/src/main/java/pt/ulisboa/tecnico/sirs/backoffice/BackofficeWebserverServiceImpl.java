package pt.ulisboa.tecnico.sirs.backoffice;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.*;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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
			List<String> keys = server.getCompartmentKeys();

			builder.setPersonalInfoKeyString(keys.get(0));
			builder.setEnergyPanelKeyString(keys.get(1));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
