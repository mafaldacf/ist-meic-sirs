package pt.ulisboa.tecnico.sirs.webserver;

public class DatabaseQueries {

    public static final String CREATE_CLIENT = "INSERT INTO client(email, password, address, plan, energyConsumedPerMonth, energyConsumedPerHour) VALUES(?, ?, ?, ?, ?, ?)";

    public static final String READ_CLIENT = "SELECT * FROM client WHERE email = ?";

    public static final String READ_CLIENT_PERSONAL_INFO = "SELECT email, address, plan FROM client WHERE email = ?";

    public static final String READ_CLIENT_ENERGY_CONSUMPTION = "SELECT energyConsumedPerMonth, energyConsumedPerHour FROM client WHERE email= ? ";

    public static final String READ_CLIENT_TOKEN = "SELECT token FROM client WHERE email = ?";

    public static final String READ_CLIENT_COUNT = "SELECT COUNT(*) FROM client WHERE email = ?";

    public static final String UPDATE_CLIENT_TOKEN = "UPDATE client SET token = ? WHERE email = ?";

    public static final String UPDATE_CLIENT_ADDRESS = "UPDATE client SET address = ? WHERE email = ?";

    public static final String UPDATE_CLIENT_PLAN = "UPDATE client SET plan = ? WHERE email = ?";
}
