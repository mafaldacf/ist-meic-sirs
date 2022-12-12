package pt.ulisboa.tecnico.sirs.backoffice;

public class DatabaseQueries {

    public static final String DROP_ADMIN_TABLE = "DROP TABLE IF EXISTS admin";

    public static final String CREATE_ADMIN_TABLE =
        "CREATE TABLE admin (id INTEGER NOT NULL AUTO_INCREMENT, " +
        "username VARCHAR(150) NOT NULL," +
        "password VARCHAR(150) NOT NULL," +
        "token VARCHAR(64) DEFAULT ''," +
        "role VARCHAR(25) NOT NULL," + // ACCOUNT_MANAGER, ENERGY_SYSTEM_MANAGER
        "UNIQUE (username)," +
        "PRIMARY KEY (id)) ";
        //"ENGINE=InnoDB ENCRYPTION='Y'";

    public static final String CREATE_ADMIN = "INSERT INTO admin(username, password, role) VALUES(?, ?, ?)";

    public static final String READ_ADMIN_PASSWORD_ROLE = "SELECT password, role FROM admin WHERE username = ?";

    public static final String READ_CLIENT_ID = "SELECT id FROM client WHERE email = ?";
    public static final String READ_CLIENT_ENERGY_CONSUMPTION_PRODUCTION = "SELECT energyConsumed, energyConsumedDaytime, energyConsumedNight, energyProduced FROM client WHERE email= ? ";

    public static final String READ_ADMIN_TOKEN = "SELECT token FROM admin WHERE username = ?";

    public static final String READ_APPLIANCES = "SELECT name, brand, energyConsumed, energyConsumedDaytime, energyConsumedNight FROM appliance WHERE client_id = ? ";
    public static final String READ_SOLAR_PANELS = "SELECT name, brand, energyProduced FROM solarpanel WHERE client_id = ? ";

    public static final String READ_CLIENT_PERSONAL_INFO = "SELECT name, email, address, iban, plan FROM client WHERE email = ?";

    public static final String READ_ADMIN_COUNT = "SELECT COUNT(*) FROM admin WHERE username = ?";
    public static final String READ_ALL_CLIENTS_NAME_EMAIL = "SELECT name, email FROM client";

    public static final String UPDATE_ADMIN_TOKEN = "UPDATE admin SET token = ? WHERE username = ?";

    public static final String DELETE_CLIENT = "DELETE FROM client WHERE email = ?";
}
