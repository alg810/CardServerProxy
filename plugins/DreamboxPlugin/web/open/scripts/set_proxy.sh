#!/bin/ash
NEWPROXY="{PARAMS}"

edit_config()
{ 
  if [ $# -eq 2 ]; then
    echo "Switching to $NEWPROXY ..."
    sed -i "s#CSPHOST=.*#CSPHOST=$1#" /var/etc/cspagent.conf
    sed -i "s#CSPPORT=.*#CSPPORT=$2#" /var/etc/cspagent.conf
    echo "Config updated"
  else
    echo "No target host specified (usage: hostname portnr)"
    exit
  fi
}

edit_config $NEWPROXY
