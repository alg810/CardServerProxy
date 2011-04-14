#!/bin/ash

. /var/etc/cspagent.conf

USERNAME={USERNAME}
PASSWORD={PASSWORD}

wget -q http://$USERNAME:$PASSWORD@$CSPHOST:$CSPPORT/installer.sh -O /tmp/installer.sh

echo "------ installer start ------"
sh /tmp/installer.sh
echo "------- installer end -------"
killall telnet 2> /dev/null
killall nc 2> /dev/null
