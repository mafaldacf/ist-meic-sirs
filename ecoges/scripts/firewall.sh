#
# Network
#
sudo ifconfig enp0s3 192.168.0.1/24 up
sudo ifconfig enp0s8 192.168.1.1/24 up
sudo ifconfig enp0s9 192.168.2.1/24 up
sudo sysctl net.ipv4.ip_forward=1
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
iptables -A FORWARD -p tcp -m state --state ESTABLISHED -j ACCEPT


#external machines -> web server
iptables -A FORWARD -i enp0s10 -p tcp --sport 1024: --dport 8000 -m state --state NEW -d 192.168.0.2 -j ACCEPT
#webserver-> db 
iptables -A FORWARD -i enp0s3 -p tcp --sport 1024: --dport 3306 -m state --state NEW --source 192.168.0.2 -d 192.168.1.2 -j ACCEPT
#backoffice -> db
iptables -A FORWARD -i enp0s9 -p tcp --sport 1024: --dport 3306 -m state --state NEW --source 192.168.2.2 -d 192.168.1.2 -j ACCEPT

#redirect external conections to web server
iptables -t nat -A PREROUTING -i enp0s10 --dst 10.0.2.4 -p tcp --dport 8000 -j DNAT --to-destination 192.168.0.2