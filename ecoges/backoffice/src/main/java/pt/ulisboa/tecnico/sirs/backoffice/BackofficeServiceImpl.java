package pt.ulisboa.tecnico.sirs.backoffice;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.backoffice.exceptions.HelloException;
import pt.ulisboa.tecnico.sirs.backoffice.grpc.*;

public class BackofficeServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {
	private static Backoffice server;

	public BackofficeServiceImpl() {
		this.server = new Backoffice();
	}

	@Override
	public void hello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
		HelloResponse.Builder builder = HelloResponse.newBuilder();
		try {
			String response = server.hello(request.getName());

			builder.setResponse(response);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch(HelloException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
