package pt.ulisboa.tecnico.sirs.rbac;

public class DatabaseQueries {
    public static final String DROP_ADMIN_TABLE = "DROP TABLE IF EXISTS admin";
    public static final String DROP_PERMISSION_TABLE = "DROP TABLE IF EXISTS permission";

    public static final String CREATE_ADMIN_TABLE =
        "CREATE TABLE admin (id INTEGER NOT NULL AUTO_INCREMENT, " +
        "username VARCHAR(150) NOT NULL," +
        "password VARCHAR(150) NOT NULL," +
        "token VARCHAR(64) DEFAULT ''," +
        "role VARCHAR(25) NOT NULL," + // ACCOUNT_MANAGER, ENERGY_MANAGER
        "UNIQUE (username)," +
        "PRIMARY KEY (id)) ";
        //"ENGINE=InnoDB ENCRYPTION='Y'";

    public static final String CREATE_PERMISSION_TABLE =
            "CREATE TABLE permission (" +
                    "id INTEGER NOT NULL AUTO_INCREMENT, " +
                    "role VARCHAR(25) NOT NULL, " +
                    "personal_info BOOLEAN NOT NULL, " +
                    "energy_panel BOOLEAN NOT NULL, " +
                    "UNIQUE (role), " +
                    "PRIMARY KEY(id)) ";
                    //"ENGINE=InnoDB ENCRYPTION='Y'";

    public static final String CREATE_ACCOUNT_MANAGER_PERMISSION = "INSERT INTO permission(role, personal_info, energy_panel) VALUES('ACCOUNT_MANAGER', ?, ?)";
    public static final String CREATE_ENERGY_MANAGER_PERMISSION = "INSERT INTO permission(role, personal_info, energy_panel) VALUES('ENERGY_MANAGER', ?, ?)";

    public static final String READ_PERMISSION_COUNT = "SELECT COUNT(*) FROM permission";

    public static final String READ_ADMIN_ROLE = "SELECT role FROM admin WHERE username = ?";
    public static final String READ_PERMISSION_PERSONAL_INFO = "SELECT personal_info FROM permission WHERE role = ?";
    public static final String READ_PERMISSION_ENERGY_PANEL = "SELECT energy_panel FROM permission WHERE role = ?";

    public static final String READ_CLIENT_ID = "SELECT id FROM client WHERE email = ?";
    public static final String READ_CLIENT_ENERGY_PANEL = "SELECT AES_DECRYPT(energyConsumed, ?), AES_DECRYPT(energyConsumedDaytime, ?), AES_DECRYPT(energyConsumedNight, ?), AES_DECRYPT(energyProduced, ?) FROM client WHERE email = ? ";

    public static final String READ_APPLIANCES = "SELECT name, brand, AES_DECRYPT(energyConsumed, ?), AES_DECRYPT(energyConsumedDaytime, ?), AES_DECRYPT(energyConsumedNight, ?) FROM appliance WHERE client_id = ? ";
    public static final String READ_SOLAR_PANELS = "SELECT name, brand, AES_DECRYPT(energyProduced, ?) FROM solarpanel WHERE client_id = ? ";

    public static final String READ_CLIENT_PERSONAL_INFO = "SELECT name, email, AES_DECRYPT(address, ?), AES_DECRYPT(iban, ?), AES_DECRYPT(plan, ?) FROM client WHERE email = ?";
}
