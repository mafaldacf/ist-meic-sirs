package pt.ulisboa.tecnico.sirs.backoffice;

public class DatabaseQueries {

    public static final String CREATE_ADMIN = "INSERT INTO admin(username, password) VALUES(?, ?)";

    public static final String READ_ADMIN = "SELECT * FROM admin WHERE username = ?";

    public static final String READ_ADMIN_TOKEN = "SELECT token FROM admin WHERE username = ?";

    public static final String READ_ADMIN_COUNT = "SELECT COUNT(*) FROM admin WHERE username = ?";

    public static final String READ_ALL_CLIENTS_INFO = "SELECT email, address, plan, energyConsumedPerMonth, energyConsumedPerHour FROM client";

    public static final String UPDATE_ADMIN_TOKEN = "UPDATE admin SET token = ? WHERE username = ?";

    public static final String DELETE_CLIENT = "DELETE FROM client WHERE email = ?";
}
