package pt.ulisboa.tecnico.sirs.webserver;

public class DatabaseQueries {
    public static final String DROP_CLIENT_TABLE = "DROP TABLE IF EXISTS client";
    public static final String DROP_APPLIANCE_TABLE = "DROP TABLE IF EXISTS appliance";
    public static final String DROP_SOLAR_PANEL_TABLE = "DROP TABLE IF EXISTS solarpanel";
    public static final String DROP_INVOICE_TABLE = "DROP TABLE IF EXISTS invoice";

    public static final String DROP_COMPARTMENT_KEYS_TABLE = "DROP TABLE IF EXISTS compartment_keys";

    public static final String CREATE_COMPARTMENT_KEYS_TABLE =
            "CREATE TABLE compartment_keys (" +
                    "id INTEGER NOT NULL AUTO_INCREMENT, " +
                    "personal_info_key BLOB NOT NULL, " +
                    "energy_panel_key BLOB NOT NULL, " +
                    "PRIMARY KEY (id))";

    public static final String CREATE_CLIENT_TABLE =
        "CREATE TABLE client (" +
            "id INTEGER NOT NULL AUTO_INCREMENT, " +
            "name VARCHAR(150) NOT NULL, " +
            "email VARCHAR(150) NOT NULL," +
            "address BLOB NOT NULL," +
            "password VARCHAR(100) NOT NULL," +
            "iban BLOB NOT NULL," +
            "plan VARCHAR(25) NOT NULL," + // plan = FLAT_RATE or BI_HOURLY_RATE
            "energyConsumed BLOB, " +
            "energyConsumedDaytime BLOB, " +
            "energyConsumedNight BLOB, " +
            "energyProduced BLOB, " +
            "token VARCHAR(64) DEFAULT ''," +
            "salt BLOB," + // password hash
            "iv BLOB," + // initialization vector using in AES encryption with CBC mode
            "obf_address VARCHAR(150) NOT NULL, " +
            "obf_iban VARCHAR(150) NOT NULL, " +
            "obf_energyConsumed VARCHAR(150) NOT NULL, " +
            "obf_energyConsumedDaytime VARCHAR(150) NOT NULL, " +
            "obf_energyConsumedNight VARCHAR(150) NOT NULL, " +
            "obf_energyProduced VARCHAR(150) NOT NULL, " +
            "UNIQUE (email)," +
            "PRIMARY KEY (id))";

    public static final String CREATE_APPLIANCE_TABLE =
        "CREATE TABLE appliance (" +
            "id INTEGER NOT NULL AUTO_INCREMENT, " +
            "client_id INTEGER NOT NULL, " +
            "name VARCHAR(150) NOT NULL, " +
            "brand VARCHAR(150) NOT NULL, " +
            "energyConsumed BLOB, " +
            "energyConsumedDaytime BLOB, " +
            "energyConsumedNight BLOB, " +
            "iv BLOB," + // initialization vector using in AES encryption with CBC mode
            "UNIQUE (client_id, name, brand)," +
            "PRIMARY KEY (id), " +
            "FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE)";

    public static final String CREATE_SOLAR_PANEL_TABLE =
        "CREATE TABLE solarpanel (" +
            "id INTEGER NOT NULL AUTO_INCREMENT, " +
            "client_id INTEGER NOT NULL, " +
            "name VARCHAR(150) NOT NULL, " +
            "brand VARCHAR(150) NOT NULL, " +
            "energyProduced BLOB, " +
            "iv BLOB," + // initialization vector using in AES encryption with CBC mode
            "UNIQUE (client_id, name, brand)," +
            "PRIMARY KEY (id), " +
            "FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE)";

    public static final String CREATE_INVOICE_TABLE =
        "CREATE TABLE invoice (" +
                "id INTEGER NOT NULL AUTO_INCREMENT, " +
                "client_id INTEGER NOT NULL, " +
                "year INTEGER NOT NULL, " +
                "month INTEGER NOT NULL, " +
                "paymentAmount BLOB, " +
                "energyConsumed BLOB, " +
                "energyConsumedDaytime BLOB, " +
                "energyConsumedNight BLOB, " +
                "plan VARCHAR(15) NOT NULL, " +
                "taxes INTEGER NOT NULL, " +
                "iv BLOB," + // initialization vector using in AES encryption with CBC mode
                "UNIQUE (client_id, year, month)," +
                "PRIMARY KEY (id), " +
                "FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE)";

    public static final String CREATE_CLIENT =
            "INSERT INTO client(name, email, password, salt, iv, plan, address, iban, energyConsumed, energyConsumedDaytime, energyConsumedNight, energyProduced, obf_address, obf_iban, obf_energyConsumed, obf_energyConsumedDaytime, obf_energyConsumedNight, obf_energyProduced) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public static final String CREATE_COMPARTMENT_KEYS = "INSERT INTO compartment_keys(personal_info_key, energy_panel_key) VALUES(?, ?)";

    public static final String CREATE_APPLIANCE = "INSERT INTO appliance(client_id, name, brand, iv, energyConsumed, energyConsumedDaytime, energyConsumedNight) " +
            "VALUES(?, ?, ?, ?, ?, ?, ?)";
    public static final String CREATE_SOLAR_PANEL = "INSERT INTO solarpanel(client_id, name, brand, iv, energyProduced) VALUES(?, ?, ?, ?, ?)";
    public static final String CREATE_INVOICE =
            "INSERT INTO invoice(iv, client_id, year, month, plan, taxes, paymentAmount, energyConsumed, energyConsumedDaytime, energyConsumedNight) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    public static final String READ_CLIENT_NAME_PASSWORD_SALT = "SELECT name, password, salt FROM client WHERE email = ?";

    public static final String READ_CLIENT_ID = "SELECT id FROM client WHERE email = ?";

    public static final String READ_COMPARTMENT_KEYS = "SELECT personal_info_key, energy_panel_key FROM compartment_keys";

    public static final String READ_CLIENT_IV = "SELECT iv FROM client WHERE email = ?";
    public static final String READ_CLIENT_PERSONAL_INFO = "SELECT name, email, obf_address, obf_iban, plan FROM client WHERE email = ?";

    public static final String READ_CLIENT_ENERGY_PANEL = "SELECT energyConsumed,  energyConsumedDaytime,  energyConsumedNight, energyProduced FROM client WHERE email= ? ";
    public static final String READ_INVOICES = "SELECT iv, year, month, taxes, paymentAmount, energyConsumed, energyConsumedDaytime, energyConsumedNight, plan FROM invoice WHERE client_id = ? " +
            "ORDER BY year, month";

    public static final String READ_CLIENT_ENERGY_CONSUMPTION = "SELECT energyConsumed, energyConsumedDaytime,  energyConsumedNight FROM client WHERE email= ? ";
    //public static final String READ_CLIENT_ENERGY_CONSUMPTION = "SELECT obf_energyConsumed, obf_energyConsumedDaytime, obf_energyConsumedNight FROM client WHERE email= ?";

    public static final String READ_CLIENT_ENERGY_PRODUCTION = "SELECT energyProduced FROM client WHERE email= ? ";
    //public static final String READ_CLIENT_ENERGY_PRODUCTION = "SELECT obf_energyProduced FROM client WHERE email= ?";
    
    public static final String READ_CLIENT_TOKEN = "SELECT token FROM client WHERE email = ?";

    public static final String READ_ALL_CLIENTS_ID_ENERGY_CONSUMPTION_PLAN = "SELECT id, plan, iv, energyConsumed, energyConsumedDaytime, energyConsumedNight FROM client";

    public static final String READ_CLIENT_COUNT = "SELECT COUNT(*) FROM client WHERE email = ?";
    public static final String READ_APPLIANCE_COUNT = "SELECT COUNT(*) FROM appliance WHERE client_id = ? AND name = ? AND brand = ? ";
    public static final String READ_SOLAR_PANEL_COUNT = "SELECT COUNT(*) FROM solarpanel WHERE client_id = ? AND name = ? AND brand = ? ";

    public static final String READ_APPLIANCES = "SELECT iv, name, brand, energyConsumed, energyConsumedDaytime, energyConsumedNight FROM appliance WHERE client_id = ? ";
    public static final String READ_SOLAR_PANELS = "SELECT iv, name, brand, energyProduced FROM solarpanel WHERE client_id = ? ";

    public static final String UPDATE_CLIENT_ENERGY_CONSUMPTION = "UPDATE client SET energyConsumed = ?, energyConsumedDaytime = ?, energyConsumedNight = ?, obf_energyConsumed = ?, obf_energyConsumedDaytime = ?, obf_energyConsumedNight = ? WHERE email = ?";
    public static final String UPDATE_CLIENT_ENERGY_PRODUCTION = "UPDATE client SET energyProduced = ?, obf_energyProduced = ? WHERE email = ?";

    public static final String UPDATE_CLIENT_TOKEN = "UPDATE client SET token = ? WHERE email = ?";

    public static final String UPDATE_CLIENT_ADDRESS = "UPDATE client SET address = ?, obf_address = ? WHERE email = ?";

    public static final String UPDATE_CLIENT_PLAN = "UPDATE client SET plan = ? WHERE email = ?";
}
