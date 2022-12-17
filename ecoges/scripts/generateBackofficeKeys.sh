#!/bin/bash

cd backoffice/src/main/resources

keytool -genkeypair -alias backoffice -keyalg RSA -keysize 4096 -validity 365 -keystore backoffice.jks -storetype PKCS12 -storepass backoffice -dname "CN=energyManagement, OU=Backoffice, O=SIRS, L=Lisbon, ST=Portugal, C=PT"
