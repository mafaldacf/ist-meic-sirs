#!/bin/sh

# Clean previous keys and certificates

rm -rf *.crt *.pem *.csr *.key *.srl


# Generate CA key and certificate

openssl genrsa -out ca.key
openssl req -new -x509 -days 365 -key ca.key -out ca.crt -subj "/C=PT/CN=CA"


# Generate webserver key and certificate signing request

openssl genrsa -out webserver.key
openssl pkcs8 -topk8 -nocrypt -in webserver.key -out webserver.pem
openssl req -new -key webserver.key -out webserver.csr -subj "/C=PT/ST=Lisbon/L=Lisbon/O=IST/CN=localhost"

# Generate backoffice key and certificate signing request

openssl genrsa -out backoffice.key
openssl pkcs8 -topk8 -nocrypt -in backoffice.key -out backoffice.pem
openssl req -new -key backoffice.key -out backoffice.csr -subj "/C=PT/ST=Lisbon/L=Lisbon/O=IST/CN=localhost"

# Sign webserver certificate

openssl x509 -req -days 365 -in webserver.csr -CA ca.crt -CAkey ca.key -extfile webserver-domains.ext -set_serial 01 -out webserver.crt

# Sign backoffice certificate

openssl x509 -req -days 365 -in backoffice.csr -CA ca.crt -CAkey ca.key -extfile backoffice-domains.ext -set_serial 01 -out backoffice.crt

echo ""
echo "Done."