#!/bin/ash

AGENTV=0.3
SKIPSLEEP=true

#Source conf-file
. /var/etc/cspagent.conf

#Get Enigma version, set the env variable to correct version and echo variable to conf-file
get_enigma()
{
  if [ $(ps | grep enigma2 | wc -l) -eq 1 ] && [ $(cat /var/etc/cspagent.conf | grep ENIGMAV | wc -l) -ne 1 ]
  then
    ENIGMAV=1
    echo "ENIGMAV=1" >> /var/etc/cspagent.conf
  else
    ENIGMAV=2
    echo "ENIGMAV=2" >> /var/etc/cspagent.conf
  fi
}

get_service()
{
  #Are there any better ways to get the current sid, besides the enigma web?
  if [ $ENIGMAV -eq 1 ]
  then
    #Enigma1 values in hex
    XML="$(wget http://$ENIGMAUSER:$ENIGMAPASS@127.0.0.1/xml/streaminfo -O - 2> /tmp/csperr)"
    SID=$(expr "$XML" : ".*<sid> *\([0-9a-h]*\)")
    ONID=$(expr "$XML" : ".*<onid> *\([0-9a-h]*\)")
  else
    #Enigma2 values in decimal
    XML="$(wget http://$ENIGMAUSER:$ENIGMAPASS@127.0.0.1/web/about -O - 2> /tmp/csperr)"
    SID=$(expr "$XML" : ".*<e2sid> *\([0-9a-h]*\)")
    ONID=$(expr "$XML" : ".*<e2onid> *\([0-9a-h]*\)")
  fi  
  if [ -z $SID ]
  then
    SID="N/A"
  fi
}

get_boxtype()
{
  if [ "$(hostname)" = "dreambox" ]
  then
    if [ -e /proc/bus/dreambox ]
    then
      BOXTYPE=$(cat /proc/bus/dreambox | head -n1 | sed 's/type=//')
    else
      BOXTYPE="Unknown"
    fi
  else
    BOXTYPE=$(hostname)
  fi
}

get_cputype()
{
  if [ "$(uname -m)" = "ppc" ]
  then
    CPUTYPE="ppc"
  else
    CPUTYPE="mips"
  fi
}

get_imginfo()
{
  #Try to gather additional image characterstics that can be used to figure out which one is installed
  if [ $(ps | grep gdaemon | wc -l) -gt 1 ]
  then
    IMGGUESS="Gemini"
  elif [ $(ps | grep plimgr | wc -l) -gt 1 ]
  then
    IMGGUESS="PLi"
  elif [ $(ps | grep emustarter | wc -l) -gt 1 ]
  then
    IMGGUESS="LT"
  else
    IMGGUESS="Unknown"
  fi

  if [ -e /etc/issue.net ]
  then
    IMGINFO=$(head -n1 /etc/issue.net | sed 's/\*//g')
  fi
  if [ -e /etc/image-version ]
  then
    IMGINFO=$(echo $IMGINFO $(cat /etc/image-version))
  fi

  if [ -z "$IMGINFO" ]
  then
    IMGINFO="Unknown"
  fi
}

#Configure Enigma version
if [ -z $ENIGMAV ]
then
  get_enigma
fi

while [ 1 ]; do

  #Source conf-file in case it changed
  . /var/etc/cspagent.conf

  if [ $INTERVAL -lt 5 ]; then
    $INTERVAL=300
  fi
  if [ -z $SKIPSLEEP ]; then
    sleep $INTERVAL
  fi
  SKIPSLEEP=
  
  if [ -e /var/etc/cspagent.id ]; then
    #Set Box-ID
    BOXID=$(cat /var/etc/cspagent.id)
    UPTIME=$(uptime)
    IP=$(expr "$(ifconfig eth0 | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
    SCRIPTURL=http://$CSPHOST:$CSPPORT/checkin
    
    get_service
    
    #Get script to run from proxy
    wget --header "csp-agent-version: $AGENTV" --header "csp-local-ip: $IP" --header "csp-sid: $SID" --header "csp-onid: $ONID" --header "csp-uptime: $UPTIME" --header "csp-iv: $INTERVAL" --header "csp-boxid: $BOXID" -O - $SCRIPTURL > $OUTFILE 2> /tmp/csplog

    sleep 1
    
    #Update log file if needed
    if [ -s /tmp/csplog ]; then
      CSPLOG=$(cat /tmp/csplog)
      if [ $(echo $CSPLOG | grep 403 | wc -l) -eq 1 ]; then
        #HTTP 403 = boxid was likely invalid, delete to trigger a new login
        rm /var/etc/cspagent.id
        rm /tmp/csplog
        SKIPSLEEP=true
      else
        #Unknown error, save
        CSPLOG="$(date +%f) $CSPLOG"
        echo $CSPLOG >> /var/log/cspagent.log
        rm /tmp/csplog
      fi
    fi
    
    #Make sure the script exists
    test -s $OUTFILE || rm -f $OUTFILE
    if [ -f $OUTFILE ]; then
      #Make downloaded script executable
      chmod +x $OUTFILE   

      #Send all output from script to the proxy
      exec $OUTFILE 2>&1 | telnet $CSPHOST $CSPPORT 2> /tmp/csplog &
      
      #Update log file if needed
      if [ -s /tmp/csplog ]; then
        CSPLOG="$(date +%f) $(cat /tmp/csplog)"
        echo $CSPLOG >> /var/log/cspagent.log
        rm /tmp/csplog
      fi
      
      #Remove script
      rm -f $OUTFILE

      sleep 1
      SKIPSLEEP=true
    fi
  else
    #Id-file doesn't exist, give the proxy enough unique info to create one...
    HWADDR=$(expr "$(ifconfig eth0 | grep HW)" : ".*HWaddr *\([0-9A-F:]*\)")
    IP=$(expr "$(ifconfig eth0 | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
    TIME=$(date +%s)
    KERNVERSION=$(cat /proc/version)
    MACHINE=$(uname -m)
    FIRSTURL=http://$CSPHOST:$CSPPORT/login

    get_boxtype
    get_imginfo
    
    wget --header "csp-agent-version: $AGENTV" --header "csp-local-ip: $IP" --header "csp-kernel-version: $KERNVERSION" --header "csp-uname-m: $MACHINE" --header "csp-img-guess: $IMGGUESS" --header "csp-img-info: $IMGINFO" --header "csp-user: $CSPUSER" --header "csp-seed: $TIME" --header "csp-boxtype: $BOXTYPE" --header "csp-iv: $INTERVAL" --header "csp-enigma-version: $ENIGMAV" --header "csp-mac: $HWADDR" -O - $FIRSTURL > /var/etc/cspagent.id 2> /tmp/csperr
    if [ -s /tmp/csperr ]; then
      rm /var/etc/cspagent.id
      echo "Unable to obtain a box id from csp at $FIRSTURL:\n"$(cat /tmp/csperr)
      echo "Try again later..."
      exit
    fi
  fi
done