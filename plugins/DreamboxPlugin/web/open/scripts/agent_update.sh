#!/bin/ash

. /var/etc/cspagent.conf

USERNAME={USERNAME}
PASSWORD={PASSWORD}

wget -q http://$USERNAME:$PASSWORD@$CSPHOST:$CSPPORT/installer.sh -O /tmp/installer.sh


echo "------ installer start ------"
if [ -e /root/plugin/bin/busybox ] && [ -e /root/spark/ywapp.exe ]; then
  /root/plugin/bin/busybox ash /tmp/installer.sh
else
  sh /tmp/installer.sh
fi
echo "------- installer end -------"
killall telnet 2> /dev/null
killall nc 2> /dev/null
