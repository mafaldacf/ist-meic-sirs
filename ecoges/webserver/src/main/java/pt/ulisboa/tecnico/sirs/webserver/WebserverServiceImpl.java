package pt.ulisboa.tecnico.sirs.webserver;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.HelloException;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.ClientDoesNotExistException;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.ClientAlreadyExistsException;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.WrongPasswordException;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class WebserverServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {
	private static Webserver server;

	public WebserverServiceImpl(Connection dbConnection) throws SQLException, ClassNotFoundException {
		this.server = new Webserver(dbConnection);
	}

	@Override
	public void register(RegisterRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			server.register(request.getUsername(), request.getPassword(), request.getAddress(), request.getPlan().name());

			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientAlreadyExistsException e){
			responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void login(LoginRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			server.login(request.getUsername(), request.getPassword());

			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientDoesNotExistException e){
			responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
		} catch (WrongPasswordException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void checkPersonalInfo(CheckPersonalInfoRequest request, StreamObserver<CheckPersonalInfoResponse> responseObserver) {
		CheckPersonalInfoResponse.Builder builder = CheckPersonalInfoResponse.newBuilder();
		try {
			List<String> personalInfo = server.checkPersonalInfo(request.getUsername());

			builder.setAddress(personalInfo.get(0));
			builder.setPlan(PlanType.valueOf(personalInfo.get(1)));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientDoesNotExistException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void checkEnergyConsumption(CheckEnergyConsumptionRequest request, StreamObserver<CheckEnergyConsumptionResponse> responseObserver) {
		CheckEnergyConsumptionResponse.Builder builder = CheckEnergyConsumptionResponse.newBuilder();
		try {
			List<Float> energyConsumption = server.checkEnergyConsumption(request.getUsername());

			builder.setEnergyConsumedPerMonth(energyConsumption.get(0));
			builder.setEnergyConsumedPerHour(energyConsumption.get(1));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientDoesNotExistException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void updateAddress(UpdateAddressRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			server.updateAddress(request.getUsername(), request.getAddress());
			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientDoesNotExistException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void updatePlan(UpdatePlanRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			server.updatePlan(request.getUsername(), request.getPlan().name());
			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientDoesNotExistException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
