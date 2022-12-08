package pt.ulisboa.tecnico.sirs.webserver;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.*;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
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
			server.register(request.getEmail(), request.getPassword(), request.getAddress(), request.getPlan().name());

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
	public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
		LoginResponse.Builder builder = LoginResponse.newBuilder();
		try {
			String hashedToken = server.login(request.getEmail(), request.getPassword());

			builder.setHashedToken(hashedToken);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientDoesNotExistException | WrongPasswordException | NoSuchAlgorithmException | SignatureException | InvalidKeyException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void logout(LogoutRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			boolean ack = server.logout(request.getEmail(), request.getHashedToken());

			builder.setAck(ack);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (RuntimeException | ClientDoesNotExistException | InvalidSessionTokenException | LogoutException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void checkPersonalInfo(CheckPersonalInfoRequest request, StreamObserver<CheckPersonalInfoResponse> responseObserver) {
		CheckPersonalInfoResponse.Builder builder = CheckPersonalInfoResponse.newBuilder();
		try {
			List<String> personalInfo = server.checkPersonalInfo(request.getEmail(), request.getHashedToken());

			builder.setEmail(personalInfo.get(0));
			builder.setAddress(personalInfo.get(1));
			builder.setPlan(PlanType.valueOf(personalInfo.get(2)));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidSessionTokenException | ClientDoesNotExistException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void checkEnergyConsumption(CheckEnergyConsumptionRequest request, StreamObserver<CheckEnergyConsumptionResponse> responseObserver) {
		CheckEnergyConsumptionResponse.Builder builder = CheckEnergyConsumptionResponse.newBuilder();
		try {
			List<Float> energyConsumption = server.checkEnergyConsumption(request.getEmail(), request.getHashedToken());

			builder.setEnergyConsumedPerMonth(energyConsumption.get(0));
			builder.setEnergyConsumedPerHour(energyConsumption.get(1));

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidSessionTokenException | ClientDoesNotExistException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void updateAddress(UpdateAddressRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			server.updateAddress(request.getEmail(), request.getAddress(), request.getHashedToken());
			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidSessionTokenException | ClientDoesNotExistException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void updatePlan(UpdatePlanRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			server.updatePlan(request.getEmail(), request.getPlan().name(), request.getHashedToken());
			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidSessionTokenException | ClientDoesNotExistException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
