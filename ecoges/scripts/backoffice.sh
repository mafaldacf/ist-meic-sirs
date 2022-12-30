#
# Network
#
sudo ifconfig enp0s3 192.168.2.2/24 up
sudo ip route add default via 192.168.2.1
sudo /etc/init.d/network-manager force-reload

#
# Firewall
#
sudo iptables -F
sudo iptables -t nat -F
sudo iptables -P FORWARD DROP
sudo iptables -P INPUT DROP
sudo iptables -P OUTPUT DROP

#accept already established connections
sudo iptables -A INPUT -p tcp -m state --state ESTABLISHED -j ACCEPT
sudo iptables -A OUTPUT -p tcp -m state --state ESTABLISHED -j ACCEPT

# admin -> backoffice
sudo iptables -A INPUT -p tcp --sport 1024: --dport 8001 -m state --state NEW -s 192.168.2.3 -j ACCEPT

# backoffice -> db
sudo iptables -A OUTPUT -p tcp --sport 1024: --dport 3306 -m state --state NEW -d 192.168.1.2 -j ACCEPT

# backoffice -> webserver
sudo iptables -A OUTPUT -p tcp --sport 1024: --dport 8000 -m state --state NEW -d 192.168.0.2 -j ACCEPT

# backoffice <-> rbac (on localhost)
sudo iptables -A INPUT -p tcp --sport 1024: --dport 8002 -m state --state NEW -d 127.0.0.1 -j ACCEPT
sudo iptables -A OUTPUT -p tcp --sport 1024: --dport 8002 -m state --state NEW -d 127.0.0.1 -j ACCEPT