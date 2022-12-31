#!/bin/bash

# Clean previous keys and certificates
rm -rf *.crt *.csr *.key *.srl *.p12 *.keystore
rm -rf ../tlscerts/*
rm -rf ../webserver/src/main/resources/*
rm -rf ../backoffice/src/main/resources/*
rm -rf ../client/src/main/resources/*
rm -rf ../admin/src/main/resources/*
rm -rf ../rbac/src/main/resources/*


# ------------------------------------------------------------------------------------------
# --------------------------------- CA KEY AND CERTIFICATE ---------------------------------
# ------------------------------------------------------------------------------------------

openssl genrsa -out ca.key
openssl req -new -x509 -days 365 -key ca.key -out ca.crt -subj "/C=PT/CN=CA"


# ------------------------------------------------------------------------------------------
# -------------------------------- DATABASE TLS CERTIFICATE --------------------------------
# ------------------------------------------------------------------------------------------

openssl genrsa -out database.key
openssl req -new -key database.key -out database.csr -subj "/C=PT/ST=Lisbon/L=Lisbon/O=IST/CN=localhost"
openssl x509 -req -days 365 -in database.csr -CA ca.crt -CAkey ca.key -extfile database-domains.ext -set_serial 01 -out database.crt

rm database.csr
mv database.key database.crt database.pem ../databaseTLS/
cp ca.crt ../databaseTLS/


# ------------------------------------------------------------------------------------------
# ------------------------- GENERAL PURPOSE KEYS AND CERTIFICATES --------------------------
# ------------------------------------------------------------------------------------------


createKeyStores() { # Arguments: $1 -> name; $2 -> unit
    openssl genrsa -out $2.key
    openssl req -new -key $2.key -out $2.csr -subj "/CN=$1/OU=$2/O=IST/L=Lisbon/ST=Portugal/C=PT"
    openssl x509 -req -days 365 -in $2.csr -CA ca.crt -CAkey ca.key -extfile $1-domains.ext -set_serial 01 -out $2.crt
    rm $2.csr

    openssl pkcs12 -password pass:mypass$1 -export -in $2.crt -inkey $2.key -out $2.p12 -name $2 -CAfile ca.crt -caname root
    keytool -importkeystore -deststorepass mypass$1 -destkeypass mypass$1 -destkeystore $1.keystore -srckeystore $2.p12 -srcstoretype PKCS12 -srcstorepass mypass$1 -alias $2
    rm $2.p12 $2.key
}

createKeyStores webserver webserver
createKeyStores backoffice backoffice
createKeyStores backoffice accountmanagement
createKeyStores backoffice energyManagement
createKeyStores rbac rbac

createTrustedStores() { # Arguments: $1 -> name $2 -> trusted entity
    keytool -import -trustcacerts -file $2.crt -alias $2 -keystore $1.truststore -storepass mypass$1 -noprompt
}

createTrustedStores webserver ca
createTrustedStores webserver rbac
createTrustedStores backoffice ca
createTrustedStores backoffice webserver
createTrustedStores backoffice rbac
createTrustedStores client ca
createTrustedStores client webserver
createTrustedStores admin ca
createTrustedStores admin backoffice
createTrustedStores rbac ca

mv webserver.keystore webserver.truststore ../webserver/src/main/resources/
mv backoffice.keystore backoffice.truststore ../backoffice/src/main/resources/
mv rbac.keystore rbac.truststore ../rbac/src/main/resources/
mv client.truststore ../client/src/main/resources/
mv admin.truststore ../admin/src/main/resources/

# ------------------------------------------------------------------------------------------
# -------------------- SELF-SIGNED CERTIFICATES FOR TESTING PURPOSES -----------------------
# ------------------------------------------------------------------------------------------

createSelfSignedCertificates() { # Arguments: $1 -> name; $2 -> unit
    openssl genrsa -out $2.key
    openssl req -new -key $2.key -out $2.csr -subj "/CN=$1/OU=$2/O=IST/L=Lisbon/ST=Portugal/C=PT"
    openssl x509 -req -days 365 -in $2.csr -signkey $2.key -set_serial 01 -out $2.crt
    rm $2.csr

    openssl pkcs12 -password pass:mypass$1 -export -in $2.crt -inkey $2.key -out $2.p12 -name $2 -CAfile ca.crt -caname root
    keytool -importkeystore -deststorepass mypass$1 -destkeypass mypass$1 -destkeystore $1.keystore -srckeystore $2.p12 -srcstoretype PKCS12 -srcstorepass mypass$1 -alias $2
    rm $2.p12 $2.key
}

createSelfSignedCertificates webserver-tests tests
mv webserver-tests.keystore ../webserver/src/test/resources/

echo ""
echo "done!"

rm ca.key *.crt