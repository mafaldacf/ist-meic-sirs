#!/bin/bash

# Clean previous keys and certificates
rm -rf *.crt *.csr *.key *.srl *.p12 *.keystore
rm ../tlscerts/*
rm ../webserver/src/main/resources/*
rm ../backoffice/src/main/resources/*

# Generate CA key and certificate
openssl genrsa -out ca.key
openssl req -new -x509 -days 365 -key ca.key -out ca.crt -subj "/C=PT/CN=CA"


# ------------------------------------------------------------------------------------------
# ------------------------------------ TLS CERTIFICATES ------------------------------------
# ------------------------------------------------------------------------------------------


createTLSCertificates() { # Arguments: $1 -> name
    openssl genrsa -out $1.key
    openssl pkcs8 -topk8 -nocrypt -in $1.key -out $1.pem
    openssl req -new -key $1.key -out $1.csr -subj "/C=PT/ST=Lisbon/L=Lisbon/O=IST/CN=localhost"
    openssl x509 -req -days 365 -in $1.csr -CA ca.crt -CAkey ca.key -extfile $1-domains.ext -set_serial 01 -out $1.crt
    rm $1.csr
    mv $1.key $1.crt $1.pem ../tlscerts/
}

createTLSCertificates webserver
createTLSCertificates backoffice
createTLSCertificates database

cp ca.crt ../tlscerts/

# ------------------------------------------------------------------------------------------
# -------------------------------- DEPARTMENTS CERTIFICATES --------------------------------
# ------------------------------------------------------------------------------------------


createKeyStores() { # Arguments: $1 -> name; $2 -> unit
    openssl genrsa -out $2.key
    openssl req -new -key $2.key -out $2.csr -subj "/CN=$1/OU=$2/O=IST/L=Lisbon/ST=Portugal/C=PT"
    openssl x509 -req -days 365 -in $2.csr -CA ca.crt -CAkey ca.key -set_serial 01 -out $2.crt
    rm $2.csr

    openssl pkcs12 -password pass:$1 -export -in $2.crt -inkey $2.key -out $2.p12 -name $2 -CAfile ca.crt -caname root
    keytool -importkeystore -deststorepass $1 -destkeypass $1 -destkeystore $1.keystore -srckeystore $2.p12 -srcstoretype PKCS12 -srcstorepass $1 -alias $2
    rm $2.p12 $2.key $2.crt
}

createKeyStores webserver webserver
createKeyStores backoffice accountmanagement
createKeyStores backoffice energyManagement

createTrustedStores() { # Arguments: $1 -> name $2 -> alias
    keytool -import -trustcacerts -file ca.crt -alias $2 -keystore $1.truststore -storepass $1 -noprompt
}

createTrustedStores webserver ca
createTrustedStores backoffice ca

mv webserver.keystore webserver.truststore ../webserver/src/main/resources/
mv backoffice.keystore backoffice.truststore ../backoffice/src/main/resources/

echo ""
echo "done!"

rm ca.key ca.crt