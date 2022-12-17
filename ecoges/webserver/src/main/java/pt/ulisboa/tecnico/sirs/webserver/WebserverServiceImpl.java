package pt.ulisboa.tecnico.sirs.webserver;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.ulisboa.tecnico.sirs.webserver.exceptions.*;
import pt.ulisboa.tecnico.sirs.webserver.grpc.*;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WebserverServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {
	private static Webserver server;

	public WebserverServiceImpl(Webserver webServer) throws SQLException, ClassNotFoundException {
		server = webServer;
	}

	@Override
	public void register(RegisterRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			server.register(request.getName(), request.getEmail(), request.getPassword(), request.getAddress(), request.getIBAN(), request.getPlan().name());

			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (ClientAlreadyExistsException e){
			responseObserver.onError(Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
		} catch (NoSuchAlgorithmException | IllegalBlockSizeException | NoSuchPaddingException | InvalidKeyException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch (CompartmentKeyException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
		LoginResponse.Builder builder = LoginResponse.newBuilder();
		try {
			ArrayList<String> response = server.login(request.getEmail(), request.getPassword());

			builder.setName(response.get(0));
			builder.setHashedToken(response.get(1));

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
			server.logout(request.getEmail(), request.getHashedToken());

		} catch (SQLException | ClientDoesNotExistException | InvalidSessionTokenException e){
			// do nothing, client should be able to logout
		}

		builder.setAck(true);
		responseObserver.onNext(builder.build());
		responseObserver.onCompleted();
	}

	@Override
	public void addAppliance(AddEquipmentRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			server.addApplicance(request.getEmail(), request.getEquipmentName(), request.getEquipmentBrand(), request.getHashedToken());

			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (ApplianceAlreadyExistsException | InvalidSessionTokenException | ClientDoesNotExistException |
				 CompartmentKeyException | IllegalBlockSizeException | NoSuchPaddingException |
				 NoSuchAlgorithmException | InvalidKeyException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void addSolarPanel(AddEquipmentRequest request, StreamObserver<AckResponse> responseObserver) {
		AckResponse.Builder builder = AckResponse.newBuilder();
		try {
			server.addSolarPanel(request.getEmail(), request.getEquipmentName(), request.getEquipmentBrand(), request.getHashedToken());

			builder.setAck(true);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (SolarPanelAlreadyExistsException | InvalidSessionTokenException | ClientDoesNotExistException |
				 CompartmentKeyException | IllegalBlockSizeException | NoSuchPaddingException |
				 NoSuchAlgorithmException | InvalidKeyException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void checkPersonalInfo(CheckPersonalInfoRequest request, StreamObserver<CheckPersonalInfoResponse> responseObserver) {
		CheckPersonalInfoResponse.Builder builder = CheckPersonalInfoResponse.newBuilder();
		try {
			PersonalInfo personalInfo = server.checkPersonalInfo(request.getEmail(), request.getHashedToken());

			builder.setPersonalInfo(personalInfo);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | InvalidSessionTokenException | ClientDoesNotExistException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch (CompartmentKeyException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void checkEnergyPanel(CheckEnergyPanelRequest request, StreamObserver<CheckEnergyPanelResponse> responseObserver) {
		CheckEnergyPanelResponse.Builder builder = CheckEnergyPanelResponse.newBuilder();
		try {
			EnergyPanel energyPanel = server.checkEnergyPanel(request.getEmail(), request.getHashedToken());

			builder.setEnergyPanel(energyPanel);

			responseObserver.onNext(builder.build());
			responseObserver.onCompleted();
		} catch (SQLException e){
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		} catch (InvalidSessionTokenException | ClientDoesNotExistException | IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		} catch (CompartmentKeyException e) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void checkInvoices(CheckInvoicesRequest request, StreamObserver<CheckInvoicesResponse> responseObserver) {
		CheckInvoicesResponse.Builder builder = CheckInvoicesResponse.newBuilder();
		try {
			List<Invoice> invoices = server.checkInvoices(request.getEmail(), request.getHashedToken());

			builder.addAllInvoices(invoices);

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
		} catch (InvalidSessionTokenException | ClientDoesNotExistException | CompartmentKeyException |
				 IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e){
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
		} catch (InvalidSessionTokenException | ClientDoesNotExistException | CompartmentKeyException |
				 IllegalBlockSizeException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e){
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}
}
