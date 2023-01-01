Instituto Superior Técnico

Master's Degree in Computer Science and Engineering

Network and Computer Security 2022/2023

# Deploy Machines

For each machine, compile and run the project:

    cd ecoges
    mvn clean compile install -DskipTests


Run each module for each machine by providing the following possible values to `<module>` field: **webserver**, **backoffice**, **rbac**, **client** or **admin**. If desired, modules can be run on localhost by setting the development environment `-dev` before specifying the module.

    cd ecoges
    sudo chmod 777 run.sh
    ./run.sh <module>

# Capture and Analyze Network Traffic

If machines are deployed on Linux, you can analyze the packets that are being exchanged between the servers, clients and database.

For example, to verify that messages sent from the webserver and backoffice to the database are being encrypted and the content of the queries can't be disclosed, run the following tool on the **database** machine:

    sudo tcpdump -XX -X -i enp0s3

To capture all the traffic of the system, run the following tool  on the **firewall** machine. Alternatively, the interface can be specified:

    sudo tcpdump -XX -X

# Demo Walkthrough

## Client

[CLIENT] Attempt to register with weak password and then successfully registers with strong one

    >>> ClientMain <<<

    Please select one option:
    1. Register
    2. Login
    0. Exit
    > 1

    Enter your name: client
    Enter your email: client-email
    Enter your strong password (10 to 30 characters and at least: 1 uppercase, 1 lowercase, 1 digit and 1 special character): weakpw
    The entered password is not strong (10 to 30 characters and at least: 1 uppercase, 1 lowercase, 1 digit and 1 special character)
    Enter your strong password again: myStrong1Password?
    Enter your address: address
    Enter your bank account IBAN: iban
    Select one energy plan:
    1. Flat Rate
    2. Bi-Hourly Rate
    0. Cancel
    > 1

    Successfully registered with email 'client-email'. Please login.


[CLIENT] Login

    Please select one option:
    1. Register
    2. Login
    0. Exit
    > 2

    Enter your email: client-email
    Enter your password: myStrong1Password?
    Login successful.
    Welcome, client!


[CLIENT] Check personal info

    Please select one option:
    1. Check personal info
    2. Check energy panel
    3. Check invoices
    4. Add appliance
    5. Add solar panel
    6. Update address
    7. Update plan
    0. Logout
    > 1
    Name: client
    Email: client-email
    Address: address
    IBAN: iban
    Energy Plan: FLAT_RATE


[CLIENT] Add appliance

    Please select one option:
    1. Check personal info
    2. Check energy panel
    3. Check invoices
    4. Add appliance
    5. Add solar panel
    6. Update address
    7. Update plan
    0. Logout
    > 4
    Insert the name for the new appliance: a1
    Insert the brand for the new appliance: b1
    Successfully added new appliance 'a1'.


[CLIENT] Add solar panel

    Please select one option:
    1. Check personal info
    2. Check energy panel
    3. Check invoices
    4. Add appliance
    5. Add solar panel
    6. Update address
    7. Update plan
    0. Logout
    > 5
    Insert the name for the new solar panel: s1
    Insert the brand for the new solar panel: b1
    Successfully added new solar panel 's1'.


[CLIENT] Check energy panel (note that values may change)

    Please select one option:
    1. Check personal info
    2. Check energy panel
    3. Check invoices
    4. Add appliance
    5. Add solar panel
    6. Update address
    7. Update plan
    0. Logout
    > 2
    Total Energy Consumed: 45.34506 kWh
    Total Energy Consumed Daytime: 27.878149 kWh
    Total Energy Consumed Night: 17.46691 kWh
    Total Energy Produced: 20.98211 kWh
    Appliances Consumption:
            a1 (b1) > Total: 45.34506 kWh, Daytime: 27.878149 kWh, Night: 17.46691 kWh
    Solar Panels Production:
            s1 (b1) > Total: 20.98211 kWh


[CLIENT] Check invoices (note that values may change)

    Please select one option:
    1. Check personal info
    2. Check energy panel
    3. Check invoices
    4. Add appliance
    5. Add solar panel
    6. Update address
    7. Update plan
    0. Logout
    > 3
    Available invoices:
        Sep 2023 > Taxes: 25%, Plan: FLAT_RATE, Energy Consumed: Total = 0.0 kWh, Daytime = 0.0 kWh, Night = 0.0 kWh, Payment Amount: 0.0 euros
        Oct 2023 > Taxes: 25%, Plan: FLAT_RATE, Energy Consumed: Total = 45.35 kWh, Daytime = 27.88 kWh, Night = 17.47 kWh, Payment Amount: 10.2 euros


[CLIENT] Update address

    Please select one option:
    1. Check personal info
    2. Check energy panel
    3. Check invoices
    4. Add appliance
    5. Add solar panel
    6. Update address
    7. Update plan
    0. Logout
    > 6
    Insert a new address: newaddress
    Successfully updated address.

[CLIENT] Update plan

    Please select one option:
    1. Check personal info
    2. Check energy panel
    3. Check invoices
    4. Add appliance
    5. Add solar panel
    6. Update address
    7. Update plan
    0. Logout
    > 7
    Select one energy plan:
    1. Flat Rate
    2. Bi-Hourly Rate
    0. Cancel
    > 2
    Successfully updated plan.


[CLIENT] Logout

    Please select one option:
    1. Check personal info
    2. Check energy panel
    3. Check invoices
    4. Add appliance
    5. Add solar panel
    6. Update address
    7. Update plan
    0. Logout
    > 0
    Logged out.

## Admin - Account Manager

[ADMIN-AM] Register as Account Manager

    >>> AdminMain <<<
    Setting up server connection on localhost:8001

    Please select one option:
    1. Register
    2. Login
    0. Exit
    > 1
    Enter your username: admin-AM
    Enter your strong password (10 to 30 characters and at least: 1 uppercase, 1 lowercase, 1 digit and 1 special character): myStrong1Password?AM
    Select one energy plan:
    1. Account Manager
    2. Energy System Manager
    0. Cancel
    > 1
    Successfully registered with username 'admin-AM'. Please login.


[ADMIN-AM] Attempt to login with wrong password

    Please select one option:
    1. Register
    2. Login
    0. Exit
    > 2
    Enter your username: admin-AM
    Enter your password: wrong-password
    INVALID_ARGUMENT: Wrong password.
    Could not login into the system. Please try again.


[ADMIN-AM] Login

    Please select one option:
    1. Register
    2. Login
    0. Exit
    > 2
    Enter your username: admin-AM
    Enter your password: myStrong1Password?AM
    Login successful.
    Welcome, admin-AM!


[ADMIN-AM] List Clients

    You are signed in as ACCOUNT_MANAGER
    Please select one option:
    1. List Clients
    2. Check Client Personal Info
    3. Check Client Energy Panel
    0. Logout
    > 1
    Name: client, Email: client-email


[ADMIN-AM] Check personal info of client

    You are signed in as ACCOUNT_MANAGER
    Please select one option:
    1. List Clients
    2. Check Client Personal Info
    3. Check Client Energy Panel
    0. Logout
    > 2
    Enter the client email: client-email
    Name: client
    Email: email
    Address: newaddress
    IBAN: iban
    Energy plan: BI_HOURLY_RATE


[ADMIN-AM] Attempt to check energy panel of client

    You are signed in as ACCOUNT_MANAGER
    Please select one option:
    1. List Clients
    2. Check Client Personal Info
    3. Check Client Energy Panel
    0. Logout
    > 3
    Enter the client email: client-email
    PERMISSION_DENIED: Permission denied for role 'ACCOUNT_MANAGER'.


[ADMIN-AM] Logout

    You are signed in as ACCOUNT_MANAGER
    Please select one option:
    1. List Clients
    2. Check Client Personal Info
    3. Check Client Energy Panel
    0. Logout
    > 0
    Logged out.

## Admin - Energy Manager

[ADMIN-EM] Attempt to register account that already exists

    Please select one option:
    1. Register
    2. Login
    0. Exit
    > 1
    Enter your username: admin-AM
    Enter your strong password (10 to 30 characters and at least: 1 uppercase, 1 lowercase, 1 digit and 1 special character): myStrong1Password?EM
    Select one energy plan:
    1. Account Manager
    2. Energy System Manager
    0. Cancel
    > 2
    ALREADY_EXISTS: Admin with username 'admin-AM' already exists.
    Could not register in the system. Please try again.

[ADMIN-EM] Register as Energy Manager

    Please select one option:
    1. Register
    2. Login
    0. Exit
    > 1
    Enter your username: admin-EM
    Enter your strong password (10 to 30 characters and at least: 1 uppercase, 1 lowercase, 1 digit and 1 special character): myStrong1Password?EM
    Select one energy plan:
    1. Account Manager
    2. Energy System Manager
    0. Cancel
    > 2
    Successfully registered with username 'admin-EM'. Please login.


[ADMIN-EM] Login

    Please select one option:
    1. Register
    2. Login
    0. Exit
    > 2
    Enter your username: admin-EM
    Enter your password: myStrong1Password?EM
    Login successful.
    Welcome, admin-EM!


[ADMIN-EM] Attempt to check personal info of client

    You are signed in as ACCOUNT_MANAGER
    Please select one option:
    1. List Clients
    2. Check Client Personal Info
    3. Check Client Energy Panel
    0. Logout
    > 2
    Enter the client email: client-email
    PERMISSION_DENIED: Permission denied for role 'ENERGY_MANAGER'.


[ADMIN-EM] Check energy panel of client (note that values may change)

    You are signed in as ACCOUNT_MANAGER
    Please select one option:
    1. List Clients
    2. Check Client Personal Info
    3. Check Client Energy Panel
    0. Logout
    > 3
    Enter the client email: client-email
    Total Energy Consumed: 45.34506 kWh
    Total Energy Consumed Daytime: 27.878149 kWh
    Total Energy Consumed Night: 17.46691 kWh
    Total Energy Produced: 20.98211 kWh
    Appliances Consumption:
            a1 (b1) > Total: 45.34506 kWh, Daytime: 27.878149 kWh, Night: 17.46691kWh
    Solar Panels Production:
            s1 (b1) > Total: 20.0 kWh


[ADMIN-EM] Logout

    You are signed in as ACCOUNT_MANAGER
    Please select one option:
    1. List Clients
    2. Check Client Personal Info
    3. Check Client Energy Panel
    0. Logout
    > 0
    Logged out.