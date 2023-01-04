Instituto Superior Técnico

Master's Degree in Computer Science and Engineering

Network and Computer Security 2022/2023

# EcoGes

This project aims to develop a new system for the electricity provider, Ecoges, which allows clients to monitor the energy cost of household appliances and the energy production of solar panels. The system is also used to calculate the monthly invoices for consumed energy and taxes and manage the energy plan (flat or bi-hourly rate).

The EcoGes application concerns a five-tier system:
- A **public website** for clients to access and update their information.
- A **client** that can access EcoGes through a public website to access and update their personal information, add new appliances and solar panels, and access energy consumption, production, and invoices.
- A **backoffice** in the **internal machine** where employees of can manage the system according to their role in the company: account manager or energy manager.
- A Role-Based Access Control entity (**RBAC**) in the **internal machine** that receives requests from the backoffice regarding employees’ accesses according to their role.
- An **admin machine** where employees can access the back office.
- A **database** to store information regarding clients and internal data.

# Technology Used
- Java programming language
- gRPC: framework of remote procedure calls that supports client and server communication
- Maven: build automation tool for Java projects
- Protobuf: cross-platform data used to serialize structured data
- JUnit: unit testing framework for Java programming language

# Requirements

- Java Developer Kit 19 (JDK 19)
- Maven 3
- MySQL

Install:

    sudo apt update
    sudo apt install mysql-server
    sudo apt install maven

Confirm all versions are correctly installed:

    javac -version
    mvn -version
    mysql -v

# Configure Network and Firewall

All machines will be running on Linux and configured according to the following figure and table:


<img src="network_architecture.png" alt="Machines and Network Architecture" style="height: 500px; width: 600px;"/>



| # Interface | Subnet | Adapter | Adapter Attached To | Adapter Name |
|:---:|:---:|:---:|:---:|:---:|
| __Client Machine__ |||||||||
| 1 | INTERNET (10.0.2.6) | enp0s3 | NatNetwork |  |
| __Firewall Machine__ |
| 1 | INTERNET (10.0.2.4) | enp0s3 | NatNetwork |  |
| 2 | 192.168.0.1 | enp0s3 | Internal Network | sw-0
| 3 | 192.168.1.1 | enp0s8 | Internal Network | sw-1
| 4 | 192.168.2.1 | enp0s9 | Internal Network | sw-2
| __Webserver Machine__ |
| 1 | 192.168.0.2 | enp0s3 | Internal Network | sw-0
| __Database Machine__ |
| 1 | 192.168.1.2 | enp0s3 | Internal Network | sw-1
| __Internal Machine (Backoffice & RBAC)__ |
| 1 | 192.168.2.2 | enp0s3 | Internal Network | sw-2
| __Admin Machine__ |
| 1 | 192.168.2.2 | enp0s3 | Internal Network | sw-2




For each machine, run the following commands:

    cd ecoges/scripts
    chmod 777 <script>.sh


> **TIP: If the script does not run as expected, convert the file to unix format using the `dos2unix` tool and run the script again**
>
>    \> **`dos2unix <script>.sh`**


Firewall machine:
    
    sudo ./firewall.sh

Webserver machine:

    sudo ./webserver.sh

Internal machine (backoffice and RBAC) machine:

    sudo ./backoffice.sh

Database machine:

    sudo ./db.sh

Admin machine:

    sudo ./terminal.sh

# Generate Certificates

Before deploying all machines, you need to generate the certificates that will be used for TLS connections and the departments.

For the TLS, both webserver, backoffice and database ceritificates need to be issued to their IP address.

Change the field `IP.1` in `ecoges/scripts/webserver-domains.ext` to the corresponding IP address (e.g. Firewall public IP `10.0.2.4`) and `IP.2` to its own address `192.168.0.2`
 
    IP.1 = 10.0.2.4
    IP.2 = 192.168.0.2

Change the field `IP.1` in `ecoges/scripts/backoffice-domains.ext` to the corresponding IP address (e.g. `192.168.2.2`)

    IP.1 = 192.168.2.2

Change the field `IP.1` in `ecoges/scripts/database-domains.ext` to the corresponding IP address (e.g. `192.168.1.2`)

    IP.1 = 192.168.1.2

To generate the certificates, simply run the script:

> **TIP: If the script does not run as expected, convert the file to unix format using the `dos2unix` tool and run the script again**
>
>    \> **`dos2unix generateCertificates.sh`**

    cd ecoges/scripts
    ./generateCertificates.sh

# Set Up Database

    cd ecoges/scripts

Add `clientdb` schema:

    sudo mysql
        source createdb.sql

Create an user for webserver and backoffice with hosts `192.168.0.2` and `192.168.2.2`, respectively (alternatively, can use `localhost` during development) and add privileges:

        CREATE USER 'ecoges'@'192.168.0.2' IDENTIFIED BY 'admin';
        GRANT ALL PRIVILEGES ON clientdb.* TO 'ecoges'@'192.168.0.2';

        CREATE USER 'ecoges'@'192.168.2.2' IDENTIFIED BY 'admin';
        GRANT ALL PRIVILEGES ON clientdb.* TO 'ecoges'@'192.168.2.2';

Change `bind-address` field to desired `<databaseHost>` of database (e.g. `192.168.1.2`):

    exit
    sudo vim /etc/mysql/mysql.conf.d/mysqld.cnf
        bind-address = 192.168.1.2


To configure TLS, copy keys and certificates:

    cd SIRS/ecoges
    sudo mkdir /etc/mysql/databaseTLS
    sudo cp databaseTLS/ca.crt /etc/mysql/databaseTLS/ca.crt
    sudo cp databaseTLS/database.crt /etc/mysql/databaseTLS/database.crt
    sudo cp databaseTLS/database.key /etc/mysql/databaseTLS/database.key

> **TIP: make sure these keys and certificates have root permissions, otherwise, MySQL won't be able to use SSL!**
>
>    \> **`chmod 777 /etc/mysql/databaseTLS/ca.crt`**
>    \> **`chmod 777 /etc/mysql/databaseTLS/database.crt`**
>    \> **`chmod 777 /etc/mysql/databaseTLS/database.key`**

Append the following content to `/etc/mysql/my.cnf` file:

    sudo vim /etc/mysql/my.cnf
        [mysqld]
        ssl=1
        ssl-cipher=DHE-RSA-AES256-SHA
        ssl-ca=/etc/mysql/databaseTLS/ca.crt
        ssl-cert=/etc/mysql/databaseTLS/database.crt
        ssl-key=/etc/mysql/databaseTLS/database.key

Verify if everything is ok, having the following field values: `have_openssl` = `YES`:

    sudo service mysql restart
    sudo mysql
        show variables like '%ssl%';
    exit


# Run JUnit Tests

To run JUnit tests, make sure the database server is running on **localhost** and servers are able to open a connection with it:

    cd ecoges
    mvn test

# Compile and Run

For each machine, compile and run the project:

    cd ecoges
    mvn clean compile install -DskipTests

## Alternative 1

To simplify the task, we created a script that runs each module automatically.

Run each module for each machine by providing the following possible values to `<module>` field: **webserver**, **backoffice**, **rbac**, **client** or **admin**. If desired, modules can be run on localhost by setting the development environment `-dev` before specifying the module.

    cd ecoges
    sudo chmod 777 run.sh
    ./run.sh <module>

## Alternative 2

To run each machine, you can simply provide the maven commands manually and provide any desired arguments.

Run **webserver**:

    cd ecoges/webserver
    mvn exec:java -Dexec.args="<serverPort> <databaseHost> <databasePort>"

Run **backoffice**:

    cd ecoges/backoffice
    mvn exec:java -Dexec.args="<serverPort> <databaseHost> <databasePort> <rbacHost> <rbacPort>"

Run **rbac**:

    cd ecoges/rbac
    mvn exec:java -Dexec.args="<serverPort>"

Run **client**:

    cd ecoges/client
    mvn exec:java -Dexec.args="<publicWebserverHost> <serverPort>"

Run **admin**:

    cd ecoges/admin
    mvn exec:java -Dexec.args="<serverHost> <serverPort>"