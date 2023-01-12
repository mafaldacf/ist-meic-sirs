package pt.ulisboa.tecnico.sirs.backoffice;

public class DatabaseQueries {
    public static final String DROP_ADMIN_TABLE = "DROP TABLE IF EXISTS admin";

    public static final String CREATE_ADMIN_TABLE =
        "CREATE TABLE admin (id INTEGER NOT NULL AUTO_INCREMENT, " +
        "username VARCHAR(150) NOT NULL," +
        "password VARCHAR(150) NOT NULL," +
        "token VARCHAR(64) DEFAULT ''," +
        "salt BLOB," + // password hash
        "role VARCHAR(25) NOT NULL," + // ACCOUNT_MANAGER, ENERGY_MANAGER
        "UNIQUE (username)," +
        "PRIMARY KEY (id))";

    public static final String CREATE_ADMIN = "INSERT INTO admin(username, password, salt, role) VALUES(?, ?, ?, ?)";

    public static final String READ_ADMIN_PASSWORD_SALT_ROLE = "SELECT password, salt, role FROM admin WHERE username = ?";
    public static final String READ_ADMIN_ROLE = "SELECT role FROM admin WHERE username = ?";

    public static final String READ_CLIENT_ID = "SELECT id FROM client WHERE email = ?";
    public static final String READ_CLIENT_ENERGY_PANEL = "SELECT energyConsumed, energyConsumedDaytime, energyConsumedNight, energyProduced FROM client WHERE email = ? ";
    public static final String READ_CLIENT_IV_PERSONAL_DATA = "SELECT iv_personal_data FROM client WHERE email = ?";
    public static final String READ_CLIENT_IV_ENERGY_DATA = "SELECT iv_energy_data FROM client WHERE email = ?";

    public static final String READ_ADMIN_TOKEN = "SELECT token FROM admin WHERE username = ?";

    public static final String READ_APPLIANCES = "SELECT iv, name, brand, energyConsumed, energyConsumedDaytime, energyConsumedNight FROM appliance WHERE client_id = ? ";
    public static final String READ_SOLAR_PANELS = "SELECT iv, name, brand, energyProduced FROM solarpanel WHERE client_id = ? ";

    public static final String READ_CLIENT_PERSONAL_INFO = "SELECT name, email, plan, address, iban FROM client WHERE email = ?";

    public static final String READ_ADMIN_COUNT = "SELECT COUNT(*) FROM admin WHERE username = ?";
    public static final String READ_ALL_CLIENTS_NAME_EMAIL = "SELECT name, email FROM client";

    public static final String UPDATE_ADMIN_TOKEN = "UPDATE admin SET token = ? WHERE username = ?";

}
