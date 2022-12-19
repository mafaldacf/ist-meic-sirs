#!/bin/bash

# Clean everything

rm -f ../webserver/src/main/resources/webserver.jks
rm -f ../backoffice/src/main/resources/backoffice.jks

# Webserver

keytool -genkeypair -alias webserver -keyalg RSA -keysize 4096 -validity 365 -keystore webserver.jks -storetype PKCS12 -storepass webserver -dname "CN=webserver, OU=Webserver, O=SIRS, L=Lisbon, ST=Portugal, C=PT"

keytool -export -alias webserver -keystore webserver.jks -storepass webserver -rfc -file webserver.crt

mv webserver.jks ../webserver/src/main/resources/

# Backoffice

keytool -genkeypair -alias backoffice -keyalg RSA -keysize 4096 -validity 365 -keystore backoffice.jks -storetype PKCS12 -storepass backoffice -dname "CN=backoffice, OU=Backoffice, O=SIRS, L=Lisbon, ST=Portugal, C=PT"

keytool -genkeypair -alias accountManagement -keyalg RSA -keysize 4096 -validity 365 -keystore backoffice.jks -storetype PKCS12 -storepass backoffice -dname "CN=accountManagement, OU=Backoffice, O=SIRS, L=Lisbon, ST=Portugal, C=PT"

keytool -genkeypair -alias energyManagement -keyalg RSA -keysize 4096 -validity 365 -keystore backoffice.jks -storetype PKCS12 -storepass backoffice -dname "CN=energyManagement, OU=Backoffice, O=SIRS, L=Lisbon, ST=Portugal, C=PT"

# Import webserver certificate to backoffice java key store

keytool -importcert -file webserver.crt -keystore backoffice.jks -alias webserver -noprompt -storepass backoffice
rm webserver.crt

mv backoffice.jks ../backoffice/src/main/resources/