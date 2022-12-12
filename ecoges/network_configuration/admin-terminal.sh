#
# Network
#

# Change for each terminal
sudo ifconfig enp0s3 192.168.2.3/24 up
sudo ip route add default via 192.168.2.1
sudo /etc/init.d/network-manager force-reload