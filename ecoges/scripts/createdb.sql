DROP DATABASE IF EXISTS clientdb;
CREATE DATABASE clientdb;

-- webserver

DROP TABLE IF EXISTS clientdb.client;
DROP TABLE IF EXISTS clientdb.appliance;
DROP TABLE IF EXISTS clientdb.solarpanel;
DROP TABLE IF EXISTS clientdb.invoice;
DROP TABLE IF EXISTS clientdb.compartment_keys;

CREATE TABLE clientdb.client (id INTEGER NOT NULL AUTO_INCREMENT,
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
    salt BLOB, -- password hash
    iv BLOB, -- initialization vector using in AES encryption with CBC mode
    UNIQUE (email), 
    PRIMARY KEY (id)); 

CREATE TABLE clientdb.appliance (
    id INTEGER NOT NULL AUTO_INCREMENT,
    client_id INTEGER NOT NULL,
    name VARCHAR(150) NOT NULL,
    brand VARCHAR(150) NOT NULL,
    energyConsumed BLOB,
    energyConsumedDaytime BLOB,
    energyConsumedNight BLOB,
    iv BLOB, -- initialization vector using in AES encryption with CBC mode
    UNIQUE (client_id, name, brand),
    PRIMARY KEY (id),
    FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE);

CREATE TABLE clientdb.solarpanel (
    id INTEGER NOT NULL AUTO_INCREMENT,
    client_id INTEGER NOT NULL,
    name VARCHAR(150) NOT NULL,
    brand VARCHAR(150) NOT NULL,
    energyProduced BLOB,
    iv BLOB, -- initialization vector using in AES encryption with CBC mode
    UNIQUE (client_id, name, brand),
    PRIMARY KEY (id),
    FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE);

CREATE TABLE clientdb.invoice (
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
    iv BLOB, -- initialization vector using in AES encryption with CBC mode
    UNIQUE (client_id, year, month),
    PRIMARY KEY (id),
    FOREIGN KEY (client_id) REFERENCES client(id) ON DELETE CASCADE);

CREATE TABLE clientdb.compartment_keys (
    id INTEGER NOT NULL AUTO_INCREMENT,
    personal_info_key BLOB NOT NULL,
    energy_panel_key BLOB NOT NULL,
    PRIMARY KEY (id));

-- backoffice

DROP TABLE IF EXISTS clientdb.admin;

CREATE TABLE clientdb.admin (id INTEGER NOT NULL AUTO_INCREMENT,
    username VARCHAR(150) NOT NULL,
    password VARCHAR(150) NOT NULL,
    token VARCHAR(64) DEFAULT '',
    role VARCHAR(25) NOT NULL, -- ACCOUNT_MANAGER, ENERGY_MANAGER
    UNIQUE (username),
    PRIMARY KEY (id));