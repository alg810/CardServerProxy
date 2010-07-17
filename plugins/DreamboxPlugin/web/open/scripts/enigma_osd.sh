#!/bin/sh

. /var/etc/cspagent.conf

MSG="{PARAMS}"

if [ -z "$MSG" ]; then
  echo "No message specified."
  exit
else
  MSG="Message from $CSPHOST:%0A$MSG"
  MSG=$(echo "$MSG" | sed 's/[ ]/%20/g')
fi

if [ $ENIGMAV -eq 1 ]; then
  wget -q http://$ENIGMAUSER:$ENIGMAPASS@127.0.0.1/cgi-bin/message?message=$MSG -O -
else
	# TODO: verify/fix url and format for enigma2
  wget -q http://$ENIGMAUSER:$ENIGMAPASS@127.0.0.1/web/message?text=$MSG&type=1&timeout= -O -
fi
