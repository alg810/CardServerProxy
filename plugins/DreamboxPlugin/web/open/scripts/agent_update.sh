#!/bin/ash

. /var/etc/cspagent.conf

USERNAME={USERNAME}
PASSWORD={PASSWORD}

wget -q http://$USERNAME:$PASSWORD@$CSPHOST:$CSPPORT/installer.sh -O /tmp/installer.sh

echo "------ installer start ------"
sh /tmp/installer.sh
echo "------ installer end ------"
exit 0