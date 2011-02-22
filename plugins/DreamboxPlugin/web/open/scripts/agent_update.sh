#!/bin/ash

. /var/etc/cspagent.conf

wget -q http://$CSPHOST:$CSPPORT/open/cspagent.sh -O /var/bin/cspagent.sh

killall cspagent.sh
killall telnet
killall nc
/var/bin/cspagent.sh &