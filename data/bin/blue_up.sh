#!/system/bin/sh
# blue-up.sh
adhocpath=/data/data/org.servalproject
adhoclog=$adhocpath/var/adhoc.log

$adhocpath/bin/ifconfig bnep0 192.168.2.254 netmask 255.255.255.0 up >> $adhoclog 2>> $adhoclog
$adhocpath/bin/dnsmasq --resolv-file=$adhocpath/conf/resolv.conf --conf-file=$adhocpath/conf/dnsmasq.conf -i bnep0 >> $adhoclog 2>> $adhoclog
