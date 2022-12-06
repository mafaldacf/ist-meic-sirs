package pt.ulisboa.tecnico.sirs.webserver;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.HelloException;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import java.sql.SQLException;

public class WebserverServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {
	private static Webserver server;

	public WebserverServiceImpl() throws SQLException, ClassNotFoundException {
		this.server = new Webserver();
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
