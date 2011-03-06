#!/bin/ash

AGENTV=1.0.2
SKIPSLEEP=true
PIDFILE=/tmp/cspagent.pid

# Source conf-file
. /var/etc/cspagent.conf

# Some $WGET versions on dbox crashing with signal 11
# so we use another binary in /var/bin if it exists
if [ -x /var/bin/wget.csp ]; then
  WGET="/var/bin/wget.csp"
else
  WGET="wget"
fi

# Telnet doesn't exist in busybox in some older images, but nc does and works with the same syntax
if [ $(busybox | grep telnet | wc -l) -le 0 ]; then
  TELNET="nc"
else
  TELNET="telnet"
fi

# Get Enigma version, set the env variable to correct version and echo variable to conf-file
# if system is dbox2 set ENIGMAV to 3
get_enigma()
{
  if [ $(ps | grep neutrino | grep -v grep | wc -l) -ge 1 ]
  then
    ENIGMAV=3
    echo "ENIGMAV=3" >> /var/etc/cspagent.conf
  elif [ $(which enigma2 | wc -l) -ge 1 ]
  then
    ENIGMAV=2
    echo "ENIGMAV=2" >> /var/etc/cspagent.conf
  elif [ $(which enigma | wc -l) -ge 1 ]
  then
    ENIGMAV=1
    echo "ENIGMAV=1" >> /var/etc/cspagent.conf
  fi
}

get_service()
{
  # Are there any better ways to get the current sid, besides the enigma web?
  if [ $ENIGMAV -eq 1 ]
  then
    # Enigma1 values in hex
    XML="$($WGET -q -O - http://$ENIGMAUSER:$ENIGMAPASS@127.0.0.1/xml/streaminfo)"
    if [ $? != "0" ]; then
      echo "$(date): cannot get information from enigma1 webinterface" >> /tmp/csperr
    fi
    SID=$(expr "$XML" : ".*<sid> *\([0-9a-h]*\)")
    ONID=$(expr "$XML" : ".*<onid> *\([0-9a-h]*\)")
  elif [ $ENIGMAV -eq 2 ]
  then
    # Enigma2 values in decimal
    XML="$($WGET -q -O - http://$ENIGMAUSER:$ENIGMAPASS@127.0.0.1/web/about)"
    if [ $? != "0" ]; then
      echo "$(date): cannot get information from enigma2 webinterface" >> /tmp/csperr
    fi
    SID=$(expr "$XML" : ".*<e2sid> *\([0-9a-h]*\)")
    ONID=$(expr "$XML" : ".*<e2onid> *\([0-9a-h]*\)")
  elif [ $ENIGMAV -eq 3 ]
  then
    # Enigma v3 equals to neutrino
    SIDONID="$($WGET -q -O - http://$ENIGMAUSER:$ENIGMAPASS@127.0.0.1/control/getonidsid)"
    if [ $? != "0" ]; then
      echo "$(date): cannot get information from neutrino yweb webinterface" >> /tmp/csperr
    fi
    SID=$(echo $SIDONID | cut -c 8-11)
    ONID=$(echo $SIDONID | cut -c 4-7)
    # Neutrino returns hex values - csp needs enigma like dec values
    SID=$(printf "%d" 0x$SID)
    ONID=$(printf "%d" 0x$ONID)
  fi

  if [ -z "$SID" ]
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
      BOXTYPE=$(cat /proc/bus/dreambox | sed 'q' | sed 's/type=//')
    else
      BOXTYPE="Unknown"
    fi
  else
    if [ -d /proc/bus/tuxbox/dbox2 ]; then
      BOXTYPE="dbox2"
    elif [ -e /etc/model ]; then
      BOXTYPE=$(cat /etc/model)
    else
      BOXTYPE=$(hostname)
    fi
  fi
}

get_cputype()
{
  case $(uname -m) in
    ppc)
        CPUTYPE="ppc"
    ;;

    mips)
        CPUTYPE="mips"
    ;;

    sh4)
        CPUTYPE="sh4"
    ;;

    *)
        # mips boxes reporting cpu id instead of architecture. e.g. dm800 returns 7401c0
        CPUTYPE="mips"
    ;;
  esac
}

get_imginfo()
{
  # Try to gather additional image characterstics that can be used to figure out which one is installed
  if [ $(grep iCVS /var/etc/image-version | wc -l) -ge 1 ]
  then                                                                                                                                    
    IMGGUESS="iCVS" 
  elif [ $(ps | grep gdaemon | grep -v grep | wc -l) -ge 1 ] || [ -e /etc/gemini_dissociation.txt ]
  then
    IMGGUESS="Gemini"
  elif [ $(ps | grep plimgr | grep -v grep | wc -l) -ge 1 ]
  then
    IMGGUESS="PLi"
  elif [ $(ps | grep neutrino | grep -v grep | wc -l) -ge 1 ]
  then
    IMGGUESS=$($WGET -q -O - http://$ENIGMAUSER:$ENIGMAPASS@127.0.0.1/control/version | grep imagename | cut -c 11-)
    if [ $? != "0" ]; then
      echo "$(date): cannot get image info from neutrino yweb webinterface" >> /tmp/csperr
    fi
  elif [ $(ps | grep blackholesocker | grep -v grep | wc -l) -ge 1 ] || [ -e /usr/bin/blackholesocker ]
  then
    IMGGUESS="Dreamelite"
  elif [ $(grep -i newnigma /proc/version | wc -l) -ge 1 ]
  then       
    IMGGUESS="Newnigma2"
  elif [ $(grep -i aaf /etc/imageinfo | wc -l) -ge 1 ]
  then
    IMGGUESS="AAF"
  else
    IMGGUESS="Unknown"
  fi

  # Check if GP3 Plugin exists and add marker to Image Guess
  if [ -e /etc/enigma2/gemini_plugin.conf ]
  then
    IMGGUESS=$IMGGUESS"+GP3"
  fi

  if [ $(ps | grep neutrino | grep -v grep | wc -l) -ge 1 ]
  then
    IMGINFO=$($WGET -q -O - http://$ENIGMAUSER:$ENIGMAPASS@127.0.0.1/control/version | grep version | cut -c 9-)
    if [ $? != "0" ]; then
      echo "$(date): cannot get image info from neutrino yweb webinterface" >> /tmp/csperr
    fi
  else
    if [ -e /etc/issue.net ]
    then
      IMGINFO=$(sed 'q' /etc/issue.net | sed 's/\*//g')
    fi

    if [ -e /etc/image-version ]
    then
      IMGINFO=$(echo $IMGINFO $(cat /etc/image-version))
    fi
  fi

  if [ -z "$IMGINFO" ]
  then
    IMGINFO="Unknown"
  fi
}

# Configure Enigma version
if [ -z $ENIGMAV ]
then
  get_enigma
fi

# Save csp agent pid
echo $$ > $PIDFILE

while [ 1 ]; do

  # Source conf-file in case it changed
  . /var/etc/cspagent.conf

  if [ $INTERVAL -lt 5 ]; then
    $INTERVAL=300
  fi
  if [ -z $SKIPSLEEP ]; then
    sleep $INTERVAL
  fi
  SKIPSLEEP=
  
  if [ -e /var/etc/cspagent.id ]; then
    # Set Box-ID
    BOXID=$(cat /var/etc/cspagent.id)
    UPTIME=$(uptime)
    
    # Return ip from tun/wlan interface if configured
    if [ $(ifconfig | grep tun | wc -l) -ge 1 ]; then
      DEVICE=$(ifconfig | grep tun | awk {'print $1'})
      IP=$(expr "$(ifconfig $DEVICE | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
    elif [ $(ifconfig | grep wlan0 | wc -l) -ge 1 ]; then
      IP=$(expr "$(ifconfig wlan0 | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
    else
      IP=$(expr "$(ifconfig eth0 | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
    fi
    
    SCRIPTURL=http://$CSPHOST:$CSPPORT/checkin
    
    get_service
    
    # Get script to run from proxy
    $WGET --header "csp-agent-version: $AGENTV" --header "csp-local-ip: $IP" --header "csp-sid: $SID" --header "csp-onid: $ONID" --header "csp-uptime: $UPTIME" --header "csp-iv: $INTERVAL" --header "csp-boxid: $BOXID" -O - $SCRIPTURL > $OUTFILE 2> /tmp/csplog

    sleep 1
    
    # Update log file if needed
    if [ -s /tmp/csplog ]; then
      CSPLOG=$(cat /tmp/csplog)
      if [ $(echo $CSPLOG | grep 403 | wc -l) -eq 1 ]; then
        # HTTP 403 = boxid was likely invalid, delete to trigger a new login
        rm /var/etc/cspagent.id
        rm /tmp/csplog
        SKIPSLEEP=true
      fi
    fi
    
    # Make sure the script exists
    test -s $OUTFILE || rm -f $OUTFILE
    if [ -f $OUTFILE ]; then
      # Make downloaded script executable
      chmod +x $OUTFILE   

      # If nc was used to pipe output, it doesn't terminate when the pipe is broken - cleanup previous
      if [ $TELNET = "nc" ]; then
        killall nc 2> /dev/null
      fi

      # Send all output from script to the proxy
      exec $OUTFILE 2>&1 | $TELNET $CSPHOST $CSPPORT 2> /tmp/csplog &
      sleep 1
      
      # Remove script
      rm -f $OUTFILE

      sleep 1
      SKIPSLEEP=true
    fi
  else
    # Id-file doesn't exist, give the proxy enough unique info to create one...
    HWADDR=$(expr "$(ifconfig eth0 | grep HW)" : ".*HWaddr *\([0-9A-F:]*\)")
    # Return ip from wlan interface if configured
    # We generate unique id with this values, so we dont take care of tun devices
    if [ $(ifconfig | grep wlan0 | wc -l) -ge 1 ]; then
      IP=$(expr "$(ifconfig wlan0 | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
    else
      IP=$(expr "$(ifconfig eth0 | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
    fi
    TIME=$(date +%s)
    KERNVERSION=$(cat /proc/version)
    FIRSTURL=http://$CSPHOST:$CSPPORT/login

    get_cputype
    get_boxtype
    get_imginfo
    
    $WGET --header "csp-agent-version: $AGENTV" --header "csp-local-ip: $IP" --header "csp-kernel-version: $KERNVERSION" --header "csp-uname-m: $CPUTYPE" --header "csp-img-guess: $IMGGUESS" --header "csp-img-info: $IMGINFO" --header "csp-user: $CSPUSER" --header "csp-seed: $TIME" --header "csp-boxtype: $BOXTYPE" --header "csp-iv: $INTERVAL" --header "csp-enigma-version: $ENIGMAV" --header "csp-mac: $HWADDR" -O - $FIRSTURL > /var/etc/cspagent.id
    if [ $? != "0" ]; then
      echo "$(date): Unable to obtain a box id from csp at $FIRSTURL ... try to get new box id in next interval" >> /tmp/csperr
      rm /var/etc/cspagent.id
    fi
  fi
done
