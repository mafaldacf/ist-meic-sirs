Instituto Superior Técnico

Master's Degree in Computer Science and Engineering

Network and Computer Security 2022/2023

# EcoGes

This project aims to develop a new system for the Electricity provider, which allows clients to have a minute-by-minute update on their energy consumption and production. With this new system, the user can monitor the energy cost of every household appliance or how much energy the solar panels produce by logging into the EcoGes website. The system is also used to manage every user's contract and calculate the monthly invoices regarding the consumed energy, the energy plan (flat rate or bi-hourly rate), and taxes.

The EcoGes application concerns a five-tier system:
- A set of clients that can access EcoGes through a public website.
- A public website for clients to access energy consumption information and update their personal data.
- An internal private back office where employees of EcoGes can manage the system according to their role in the company (marketing, account manager, technical assistant, or system manager).
- A set of internal machines where employees can access the back office.
- A database server to store persistent information regarding clients and the organization’s internal data.

## Technology Used
- Java programming language
- gRPC: framework of remote procedure calls that supports client and server communication
- Maven: build automation tool for Java projects
- Protobuf: cross-platform data used to serialize structured data
- JUnit: unit testing framework for Java programming language

## Requirements

- Java Developer Kit 19 (JDK 19)
- Maven 3
- MySQL ?

Install:

    sudo apt update
    sudo apt install mysql-server
    sudo apt install maven

Confirm all versions are correctly installed:

    javac -version
    mvn -version
    mysql -v

All virtual machines will be running on Linux.

## Configure network and firewall

TODO

### Firewall

The firewall is a machine set between the external network and the DMZ. It is used as a reverse proxy.

### Webserver

The webserver is a machine in the DMZ.


### Backoffice

The backoffice is a machine in the internal network.

### Database

The backoffice is a machine in the internal network.

### Admin

An admin is a machine in the internal network that sends requests to the backoffice

### Client

A client is a machine in the external network that sends requests to the webserver

## Generate certificates

Before deploying all machines, both webserver and backoffice ceritifcate need to be issued to their IP address in the `IP.1` field in `ecoges/keyscerts/webserver-domains.ext` and `ecoges/keyscerts/backoffice-domains.ext`, respectivelly. (e.g. webserver: `192.168.1.0`, backoffice: `192.168.2.0`). The content of both files correspond to the following:

    authorityKeyIdentifier=keyid,issuer
    basicConstraints=CA:FALSE
    keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
    subjectAltName = @alt_names
    [alt_names]
    DNS.1 = localhost
    IP.1 = <IP address>

To generate the certificates, simply run the shell script:

    cd ecoges/keyscerts
    ./script.sh

## Compile and Run

For each machine, compile and run the project:

    cd ecoges
    mvn clean compile install -DskipTests

Run webserver on port `<serverPort>` (e.g. `8000`):

    cd ecoges/webserver
    mvn exec:java -Dexec.args="<serverPort>"

Run client to communicate with webserver on `<serverHost>` (e.g. `localhost` for development; `192.168.1.0`) and port `<serverPort>` (e.g. `8000`):

    cd ecoges/client
    mvn exec:java -Dexec.args="<serverHost> <serverPort>"

Run backoffice on port `<serverPort>` (e.g. `8001`):

    cd ecoges/backoffice
    mvn exec:java -Dexec.args="<serverPort>"

Run admin to communicate with backoffice on `<serverHost>` (e.g. `localhost` for development; `192.168.2.0`) and port `<serverPort>` (e.g. `8001`):

    cd ecoges/admin
    mvn exec:java -Dexec.args="<serverHost> <serverPort>"