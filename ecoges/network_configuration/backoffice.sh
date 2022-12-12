#
# Network
#
sudo ifconfig enp0s3 192.168.2.2/24 up
sudo ip route add default via 192.168.2.1
sudo /etc/init.d/network-manager force-reload

#
# Firewall
#
iptables -F
iptables -t nat -F
iptables -P FORWARD DROP
iptables -P INPUT DROP
iptables -P OUTPUT DROP

#accept already established connections
iptables -A INPUT -p tcp -m state --state ESTABLISHED -j ACCEPT
iptables -A OUTPUT -p tcp -m state --state ESTABLISHED -j ACCEPT

# internal machines -> backoffice
iptables -A INPUT -p tcp --sport 1024: --dport 8001 -m state --state NEW -s 192.168.2.3 -j ACCEPT

# backoffice -> db
iptables -A OUTPUT -p tcp --sport 1024: --dport 3306 -m state --state NEW -d 192.168.1.2 -j ACCEPT





