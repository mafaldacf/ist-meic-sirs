#!/bin/bash

#
# Script to automatically run project units
#

databaseHost="192.168.1.2"
databasePort="3306"

publicWebserverHost="10.0.2.4" # firewall's public IP
webserverHost="192.168.0.2"
webserverPort="8000"

backofficeHost="192.168.2.2"
backofficePort="8001"

rbacHost="localhost" # backoffice will have RBAC on its localhost
rbacPort="8002"

# Usage example ./run.sh -dev webserver
usage() {
    echo "Usage: ./run.sh [-dev] <unit>"
    echo "Available <unit> values: webserver, backoffice, rbac, client, admin"
    echo "The -dev flag is optional and, if enable, all units will be run on development mode (localhost)"
    exit 1
}

# Check if exactly one or two arguments were provided
if [ $# -ne 1 ] && [ $# -ne 2 ]; then
    echo "Error: No arguments were provided"
    usage
fi

# Prepare arguments
if [ "$#" -eq 1 ] && ([ $1 = "webserver" ] || [ $1 = "backoffice" ] || [ $1 = "rbac" ] || [ $1 = "client" ] || [ $1 = "admin" ])
  then
    unit=$1
elif [ "$#" -eq 2 ] && [ $1 = "-dev" ]
  then
    databaseHost="localhost"
    publicWebserverHost="localhost"
    webserverHost="localhost"
    backofficeHost="localhost"
    unit=$2
else
  echo "Invalid arguments."
  usage
fi

# Run units
if [ $unit = "webserver" ]
  then
    cd webserver
    mvn exec:java -Dexec.args="$webserverPort $databaseHost $databasePort"

elif [ $unit = "backoffice" ]
  then
    cd backoffice
    mvn exec:java -Dexec.args="$backofficePort $webserverHost $webserverPort $databaseHost $databasePort $rbacHost $rbacPort"

# RBAC arguments: <serverPort>
elif [ $unit = "rbac" ]
  then
    cd rbac
    mvn exec:java -Dexec.args="$rbacPort"

elif [ $unit = "client" ]
  then
    cd client
    mvn exec:java -Dexec.args="$publicWebserverHost $webserverPort"

elif [ $unit = "admin" ]
  then
    cd admin
    mvn exec:java -Dexec.args="$backofficeHost $backofficePort"
fi
