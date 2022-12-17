-- webserver

DROP TABLE IF EXISTS client;
DROP TABLE IF EXISTS appliance;
DROP TABLE IF EXISTS solarpanel;
DROP TABLE IF EXISTS invoice;

CREATE TABLE client (id INTEGER NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL, 
    email VARCHAR(150) NOT NULL, 
    address BLOB NOT NULL, 
    password VARCHAR(100) NOT NULL, 
    iban BLOB NOT NULL, 
    plan BLOB NOT NULL, -- plan = FLAT_RATE or BI_HOURLY_RATE
    energyConsumed BLOB, 
    energyConsumedDaytime BLOB, 
    energyConsumedNight BLOB, 
    energyProduced BLOB, 
    token VARCHAR(64) DEFAULT '', 
    salt BLOB, 
    UNIQUE (email), 
    PRIMARY KEY (id), 
    ENGINE=InnoDB ENCRYPTION='Y'); 

CREATE TABLE appliance (
    id INTEGER NOT NULL AUTO_INCREMENT,
    client_id INTEGER NOT NULL,
    name VARCHAR(150) NOT NULL,
    brand VARCHAR(150) NOT NULL,
    energyConsumed BLOB,
    energyConsumedDaytime BLOB,
    energyConsumedNight BLOB,
    UNIQUE (client_id, name, brand),
    PRIMARY KEY (id),
    FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE, 
    ENGINE=InnoDB ENCRYPTION='Y');

CREATE TABLE solarpanel (
    id INTEGER NOT NULL AUTO_INCREMENT,
    client_id INTEGER NOT NULL,
    name VARCHAR(150) NOT NULL,
    brand VARCHAR(150) NOT NULL,
    energyProduced BLOB,
    UNIQUE (client_id, name, brand),
    PRIMARY KEY (id),
    FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE, 
    ENGINE=InnoDB ENCRYPTION='Y');

CREATE TABLE invoice (
    id INTEGER NOT NULL AUTO_INCREMENT,
    client_id INTEGER NOT NULL,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    paymentAmount DECIMAL(20, 2) NOT NULL,
    energyConsumed DECIMAL(25, 2) NOT NULL,
    energyConsumedDaytime DECIMAL(25, 2) NOT NULL,
    energyConsumedNight DECIMAL(25, 2) NOT NULL,
    plan VARCHAR(15) NOT NULL,
    taxes INTEGER NOT NULL,
    UNIQUE (client_id, year, month),
    PRIMARY KEY (id),
    FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE,
    ENGINE=InnoDB ENCRYPTION='Y');

-- backoffice

DROP TABLE IF EXISTS admin;
DROP TABLE IF EXISTS permission;
DROP TABLE IF EXISTS compartment_keys;



CREATE TABLE compartment_keys (
    id INTEGER NOT NULL AUTO_INCREMENT,
    personal_info_key BLOB NOT NULL,
    energy_panel_key BLOB NOT NULL,
    PRIMARY KEY (id),
    ENGINE=InnoDB ENCRYPTION='Y');

CREATE TABLE admin (id INTEGER NOT NULL AUTO_INCREMENT,
    username VARCHAR(150) NOT NULL,
    password VARCHAR(150) NOT NULL,
    token VARCHAR(64) DEFAULT '',
    role VARCHAR(25) NOT NULL, -- ACCOUNT_MANAGER, ENERGY_SYSTEM_MANAGER
    UNIQUE (username),
    PRIMARY KEY (id), 
    ENGINE=InnoDB ENCRYPTION='Y');

CREATE TABLE permission (
    id INTEGER NOT NULL AUTO_INCREMENT,
    role VARCHAR(25) NOT NULL,
    personal_info BOOLEAN NOT NULL,
    energy_panel BOOLEAN NOT NULL,
    UNIQUE (role),
    PRIMARY KEY(id),
    ENGINE=InnoDB ENCRYPTION='Y');