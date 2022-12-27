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


}
