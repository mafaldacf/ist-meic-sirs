package pt.ulisboa.tecnico.sirs.rbac;

import org.junit.*;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.InvalidRoleException;
import pt.ulisboa.tecnico.sirs.rbac.exceptions.PermissionDeniedException;
import pt.ulisboa.tecnico.sirs.rbac.grpc.*;

public class ValidatePermissionsTests {

    private static Rbac rbac;

    @BeforeClass
    public static void setup() {
        rbac = new Rbac();
    }

    @Test
    public void validatePermissionGrantedAMTest() throws InvalidRoleException, PermissionDeniedException {
        boolean result = rbac.validatePermissions(Role.ACCOUNT_MANAGER, PermissionType.PERSONAL_DATA);
        Assert.assertTrue(result);
    }

    @Test
    public void validatePermissionGrantedEMTest() throws InvalidRoleException, PermissionDeniedException {
        boolean result = rbac.validatePermissions(Role.ENERGY_MANAGER, PermissionType.ENERGY_DATA);
        Assert.assertTrue(result);
    }

    @Test
    public void validatePermissionDeniedAMTest()  {
        Assert.assertThrows(PermissionDeniedException.class, () ->
            rbac.validatePermissions(Role.ACCOUNT_MANAGER, PermissionType.ENERGY_DATA));
    }

    @Test
    public void validatePermissionDeniedEMTest()  {
        Assert.assertThrows(PermissionDeniedException.class, () ->
                rbac.validatePermissions(Role.ENERGY_MANAGER, PermissionType.PERSONAL_DATA));
    }
}
