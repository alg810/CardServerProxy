#!/bin/ash

NEW_INTERVAL={PARAMS}

if [ -z $NEW_INTERVAL ]; then
  NEW_INTERVAL=30
fi

if [ $NEW_INTERVAL -lt 5 ]; then
  $NEW_INTERVAL=30
fi  

cat /var/etc/cspagent.conf | sed "s/INTERVAL=.*/INTERVAL=$NEW_INTERVAL/" > /var/etc/cspagent.conf.tmp
cat /var/etc/cspagent.conf.tmp
mv /var/etc/cspagent.conf.tmp /var/etc/cspagent.conf
