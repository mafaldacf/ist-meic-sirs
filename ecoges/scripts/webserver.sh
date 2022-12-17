#
# Network
#
sudo ifconfig enp0s3 192.168.0.2/24 up
sudo ip route add default via 192.168.0.1
sudo /etc/init.d/network-manager force-reload