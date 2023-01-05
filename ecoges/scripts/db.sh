#
# Network
#
sudo ifconfig enp0s3 192.168.1.2/24 up
sudo ip route add default via 192.168.1.1
sudo /etc/init.d/network-manager force-reload

#
# Setup Database
#

cd ../databaseTLS

sudo mkdir -p /etc/mysql/databaseTLS
sudo cp ca.crt /etc/mysql/databaseTLS/ca.crt
sudo cp database.crt /etc/mysql/databaseTLS/database.crt
sudo cp database.key /etc/mysql/databaseTLS/database.key

sudo chmod +r /etc/mysql/databaseTLS/ca.crt
sudo chmod +r /etc/mysql/databaseTLS/database.crt
sudo chmod +r /etc/mysql/databaseTLS/database.key

