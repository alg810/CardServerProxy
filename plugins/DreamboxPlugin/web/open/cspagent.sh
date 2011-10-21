#!/bin/ash

AGENTV=1.1.2
SKIPSLEEP=true
PIDFILE=/tmp/cspagent.pid
TIMEOUT=10

# Source conf-file
. /var/etc/cspagent.conf

# Some wget versions on dbox crashing with signal 11
# so we use another binary in /var/bin if it exists
if [ -x /var/bin/wget.csp ]; then
  WGET="/var/bin/wget.csp"
else
  WGET="wget"
fi

# Telnet doesn't exist in busybox in some older images, but nc does and works with the same syntax
if [ $(which telnet | wc -l) -le 0 ]; then
  TELNET="nc"
else
  TELNET="telnet"
fi

# Get OSD manager version and type, set the env variable to correct version and echo variable to conf-file
get_osd_manager()
{
  # some images have busybox that recognized the x parameter for ps, some other dont know about this parameter 
  if [ $(ps x | grep neutrino | grep -v grep | wc -l) -ge 1 ] || [ $(ps | grep neutrino | grep -v grep | wc -l) -ge 1 ]
  then                                                                                                    
    OSDTYPE="neutrino"                                                                                    
    if [ $(uname -m | grep ppc | wc -l) -ge 1 ]; then                                                      
      OSDVER=1                                                                                    
      echo "OSDVER=1" >> /var/etc/cspagent.conf            
    else                                                                                                  
      OSDVER=2                                                                                    
      echo "OSDVER=2" >> /var/etc/cspagent.conf                                                           
    fi                                                                                            
    echo "OSDTYPE=neutrino" >> /var/etc/cspagent.conf
  elif [ $(which enigma2 | wc -l) -ge 1 ]
  then
    OSDTYPE="enigma"
    OSDVER=2
    echo "OSDTYPE=enigma" >> /var/etc/cspagent.conf
    echo "OSDVER=2" >> /var/etc/cspagent.conf
  elif [ $(which enigma | wc -l) -ge 1 ]
  then
    OSDTYPE="enigma"
    OSDVER=1
    echo "OSDTYPE=enigma" >> /var/etc/cspagent.conf
    echo "OSDVER=1" >> /var/etc/cspagent.conf
  elif [ -e /root/spark/ywapp.exe ]
  then
    OSDTYPE="spark"
    OSDVER=1
    echo "OSDTYPE=spark" >> /var/etc/cspagent.conf
    echo "OSDVER=1" >> /var/etc/cspagent.conf
  fi
}

get_service()
{
  case $OSDTYPE in
    "enigma")
      # Are there any better ways to get the current sid, besides the enigma web?
      if [ $OSDVER -eq 1 ]; then
        # Enigma1 values in hex
        XML="$($WGET -q -O - http://$OSDUSER:$OSDPASS@127.0.0.1/xml/streaminfo)"
        if [ $? != "0" ]; then
          echo "$(date): cannot get information from enigma1 webinterface" >> /tmp/csperr
        fi
        SID=$(expr "$XML" : ".*<sid> *\([0-9a-h]*\)")
        ONID=$(expr "$XML" : ".*<onid> *\([0-9a-h]*\)")
      elif [ $OSDVER -eq 2 ]; then
        # Enigma2 values in decimal
        XML="$($WGET -q -O - http://$OSDUSER:$OSDPASS@127.0.0.1/web/about)"
        if [ $? != "0" ]; then
          echo "$(date): cannot get information from enigma2 webinterface" >> /tmp/csperr
        fi
        SID=$(expr "$XML" : ".*<e2sid> *\([0-9a-h]*\)")
        ONID=$(expr "$XML" : ".*<e2onid> *\([0-9a-h]*\)")
      fi
      ;;

    "neutrino")
      if [ $OSDVER -eq 1 ] || [ $OSDVER -eq 2 ]; then
        SIDONID="$($WGET -q -O - http://$OSDUSER:$OSDPASS@127.0.0.1/control/getonidsid)"
        if [ $? != "0" ]; then 
          echo "$(date): cannot get current sid from neutrino yweb webinterface" >> /tmp/csperr
        fi 
        SID=$(echo $SIDONID | cut -c 8-11)
        ONID=$(echo $SIDONID | cut -c 4-7)
        # Neutrino returns hex values - csp needs enigma like dec values
        SID=$(printf "%d" 0x$SID)
        ONID=$(printf "%d" 0x$ONID)
      fi
      ;;

    "spark")                            
        ONID=0
      ;;
  esac

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
  elif [ -e /root/spark/ywapp.exe ]; then                           
    BOXTYPE="spark"                       
  elif [ $( grep -i coolstream /proc/cpuinfo | wc -l ) -ge 1 ]; then
    BOXTYPE="coolstream"                                                                          
  elif [ -d /proc/bus/tuxbox/dbox2 ]; then   
    BOXTYPE="dbox2"                       
  else                                    
    if [ -e /etc/model ]; then            
      BOXTYPE=$(cat /etc/model) 
    else                        
      BOXTYPE=$(hostname)                                                                                 
    fi                   
  fi                                                                             
}

get_cputype()
{
  case $(uname -m) in
    "ppc")
        CPUTYPE="ppc"
    ;;

    "mips")
        CPUTYPE="mips"
    ;;

    "sh4")
        CPUTYPE="sh4"
    ;;

    "armv6l")
        CPUTYPE="arm"
    ;;

    *)
        # mips boxes reporting cpu id instead of architecture. e.g. dm800 returns 7401c0
        CPUTYPE="mips"
    ;;
  esac
}

get_imginfo()
{
  case $OSDTYPE in
    "neutrino")
      if [ $OSDVER -eq 1 ] || [ $OSDVER -eq 2 ]; then
        YWEBOUT=$($WGET -q -O - http://$OSDUSER:$OSDPASS@127.0.0.1/control/version)
        if [ $? != "0" ]; then
          echo "$(date): cannot get image infos from neutrino yweb webinterface" >> /tmp/csperr
        fi
                                                                          
        OIFS=$IFS
        IFS=" "
        IMGGUESS=$(echo $YWEBOUT | grep imagename | sed 's/imagename=//g')
        IMGINFO=$(echo $YWEBOUT | grep version | sed 's/version=//g')
        IFS=$OIFS

        # some images dont have imagename set in yweb version page, so list such images here
        if [ $OSDVER -eq 2 ] && [ -z $IMGGUESS ]; then
          if [ $(cat /etc/issue.net | grep -i bluepeer | wc -l) -ge 1 ]; then
            IMGGUESS="BluePeer"
          fi
        fi
      fi
      ;;

    "enigma")
      # Try to gather additional image characterstics that can be used to figure out which one is installed
      if [ $(grep -i icvs /etc/image-version | wc -l) -ge 1 ]; then
        IMGGUESS="iCVS"
      elif [ $(ps | grep gdaemon | grep -v grep | wc -l) -ge 1 ] || [ -e /etc/gemini_dissociation.txt ]; then
        IMGGUESS="Gemini"
      elif [ $(ps | grep plimgr | grep -v grep | wc -l) -ge 1 ] || $(ps x | grep plimgr | grep -v grep | wc -l) -ge 1 ]; then
        IMGGUESS="PLi"
      elif [ -e /usr/bin/blackholesocker ] || [ $(grep -i dream-elite /etc/image-version | wc -l) -ge 1 ]; then
        IMGGUESS="Dreamelite"
      elif [ $(grep -i newnigma /etc/image-version | wc -l) -ge 1 ]; then
        IMGGUESS="Newnigma2"
      elif [ $(grep -i aaf /etc/imageinfo | wc -l) -ge 1 ]; then
        IMGGUESS="AAF"
      elif [ $(grep -i vti /etc/image-version | wc -l) -ge 1 ]; then
        IMGGUESS="VTi"
      elif [ -e /etc/image-version ]; then
        IMGGUESS=$(grep comment /etc/image-version | sed 's/comment=//g')
      fi

      if [ -e /etc/issue.net ]; then
        IMGINFO=$(sed 'q' /etc/issue.net | sed 's/\*//g')
      fi

      if [ -e /etc/image-version ]; then
        IMGINFO=$(echo $IMGINFO $(cat /etc/image-version))
      fi
      ;;

      "spark")                          
        IMGGUESS="Pingulux"
        IMGINFO=$(cat /etc/stm-release)
      ;;
  esac

  if [ -z "$IMGINFO" ]; then
    IMGINFO="Unknown"
  fi

  if [ -z "$IMGGUESS" ]; then
    IMGGUESS="Unknown"
  fi
}

get_ipinfo()
{
  # Get IP addresses from eth/wlan/tun interfaces
  if [ $(ifconfig | grep tun | wc -l) -ge 1 ]; then
    DEVICE=$(ifconfig | grep tun | awk {'print $1'})
    IP=$(expr "$(ifconfig $DEVICE | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
  elif [ $(ifconfig | grep wlan0 | wc -l) -ge 1 ]; then
    IP=$(expr "$(ifconfig wlan0 | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
  elif [ $(ifconfig | grep ath0 | wc -l) -ge 1 ]; then
    IP=$(expr "$(ifconfig ath0 | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
  else
    IP=$(expr "$(ifconfig eth0 | grep "inet addr:")" : ".*inet addr: *\([0-9.]*\)")
  fi
}

timeout()
{
  PID=$1

  # Start timeout watchdog in a subshell
  (sleep $TIMEOUT ; echo "$(date): Timeout ... Sending SIGTERM to process $PID ..." >> /tmp/csperr ; kill $PID 2> /dev/null) &
  TPID=$!

  # Wait for process to be finished
  wait $PID

  # Kill timeout watchdog
  kill $TPID 2> /dev/null
}

# Configure OSD manager version
if [ -z "$OSDTYPE" ] || [ -z "$OSDVER" ]; then
  get_osd_manager
  sleep 1
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

    # Get additional informations
    get_service
    get_ipinfo
    
    # Get script to run from proxy
    SCRIPTURL=http://$CSPHOST:$CSPPORT/checkin
    $WGET --header "csp-agent-version: $AGENTV" --header "csp-local-ip: $IP" --header "csp-sid: $SID" --header "csp-onid: $ONID" --header "csp-uptime: $UPTIME" --header "csp-iv: $INTERVAL" --header "csp-boxid: $BOXID" -O - $SCRIPTURL > $OUTFILE 2> /tmp/csplog &
    timeout $!
    
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
      sleep 2
      
      # Remove script
      rm -f $OUTFILE
      sleep 1

      SKIPSLEEP=true
    fi
  else
    # Id-file doesn't exist, give the proxy enough unique info to create one...
    HWADDR=$(expr "$(ifconfig eth0 | grep HW)" : ".*HWaddr *\([0-9A-F:]*\)")
    TIME=$(date +%s)
    KERNVERSION=$(cat /proc/version)

    # Get additional informations
    get_cputype
    get_boxtype
    get_imginfo
    get_ipinfo
    
    # First connect to CSP - send infos in http headers and get Box-ID
    FIRSTURL=http://$CSPHOST:$CSPPORT/login
    $WGET --header "csp-agent-version: $AGENTV" --header "csp-local-ip: $IP" --header "csp-kernel-version: $KERNVERSION" --header "csp-uname-m: $CPUTYPE" --header "csp-img-guess: $IMGGUESS" --header "csp-img-info: $IMGINFO" --header "csp-user: $CSPUSER" --header "csp-seed: $TIME" --header "csp-boxtype: $BOXTYPE" --header "csp-iv: $INTERVAL" --header "csp-osd-type: $OSDTYPE" --header "csp-osd-version: $OSDVER" --header "csp-mac: $HWADDR" -O - $FIRSTURL > /var/etc/cspagent.id 2> /tmp/csplog &
    timeout $!

    if [ $? != "0" ]; then
      echo "$(date): Unable to obtain a box id from csp at $FIRSTURL ... try to get new box id in next interval" >> /tmp/csperr
    fi
  fi
done
