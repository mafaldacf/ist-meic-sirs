#
# Network
#
sudo ifconfig enp0s3 192.168.1.2/24 up
sudo ip route add default via 192.168.1.1
sudo /etc/init.d/network-manager force-reload

