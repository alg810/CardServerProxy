#!/bin/ash
#
# CSP installer for Agent v1.x.x
#

#Variables populated automatically on download by the DreamboxPlugin
CSPHOST={0}
CSPPORT={1}
CSPUSER={2}
INTERVAL={3}
AGENTV={4}
#
AGENTURL=http://$CSPHOST:$CSPPORT/open
#
i=0

# get own busybox binary for pingulux spark os, as default binary dont know about bash functions O_o
if [ ! -e /root/plugin/bin/busybox ] && [ -e /root/spark/ywapp.exe ]; then
	echo "output: busybox binary running here was not compatible with CSP Agent ... getting our own binary from server ..."
	if [ ! -e /root/plugin/bin ]; then
		mkdir /root/plugin/bin
	fi

	if [ ! -e /root/plugin/var/etc ]; then
		mkdir /root/plugin/var/etc
	fi
	wget -q -O /root/plugin/bin/busybox $AGENTURL/binaries/busybox.sh4
	if [ $? != 0 ]; then
		echo "output: failed to get cspagent.sh from $AGENTURL/open/binaries/busybox.sh4 ... please start installer again ..."
		exit
	else
		chmod +x /root/plugin/bin/busybox
		sed -i "2iln -sf /root/plugin/bin/busybox /bin/ash" /root/autorun.sh
		ln -sf /root/plugin/bin/busybox /bin/ash
		echo "output: busybox binary installed successfully ... starting up installer in new shell ..."
		/root/plugin/bin/busybox ash $0
		exit
	fi
fi

# make some symlinks if /var was tmpfs
if [ $(mount | grep /var | grep tmpfs | wc -l) -ge 1 ]; then
	if [ ! -h /var/bin ]; then
		ln -s /usr/bin /var/bin
	fi

	if [ ! -h /var/etc ]; then
		ln -s /etc /var/etc
	fi
fi

# create /var/bin if it not exists
if [ ! -d /var/bin ]; then
  mkdir /var/bin
fi

check_running_agent()
{

	if [ $(ps | grep cspagent.sh | grep -v grep | wc -l) -ge 1 ]; then
		echo "output: CSP Agent allready running. Skipping ..."
	else
		echo "output: CSP Agent not running. Trying to start ..."
		/var/bin/cspagent.sh &
		echo "output: Done."
	fi

}

generate_conf()
{
	if [ -e /var/etc/cspagent.conf ]; then
		echo "output: Config file /var/etc/cspagent.conf already exists. Skipping..."
	else
		echo "

#Proxy DreamboxPlugin host
CSPHOST=$CSPHOST

#Proxy DreamboxPlugin port
CSPPORT=$CSPPORT

#Proxy username
CSPUSER=$CSPUSER

#Osd httpauth user
OSDUSER=root

#Osd httpauth password" >> /var/etc/cspagent.conf

if [ $(ps | grep neutrino | grep -v grep | wc -l) -ge 1 ]; then
  if [ $(uname -m | grep ppc | wc -l) ge 1 ]; then
    echo "OSDPASS=dbox2" >> /var/etc/cspagent.conf
  else
    echo "OSDPASS=neutrino" >> /var/etc/cspagent.conf
  fi
else
  echo "OSDPASS=dreambox" >> /var/etc/cspagent.conf
fi

echo "
#Interval between script downloads/runs (in seconds)
INTERVAL=$INTERVAL

#Tempfiles, no reason to touch these
OUTFILE=/tmp/runme.sh 
TMPFILE=/tmp/runme.out
" >> /var/etc/cspagent.conf

	fi
}

evaluate_method()
{
	if [ -e /root/spark/ywapp.exe ]; then
		pingulux_spark
		return 0
	fi

	if [ -d /etc/init.d ] && [ -w /etc/init.d ] && [ $(mount | grep /dev/root | grep squash | wc -c) -eq 0 ]; then
		echo "output: Generating start script in /etc/init.d/..."

		#Generate script for /etc/init.d
		echo '#!/bin/sh

# make some symlinks if /var was tmpfs
if [ $(mount | grep /var | grep tmpfs | wc -l) -ge 1 ]; then
        if [ ! -h /var/bin ]; then
                ln -s /usr/bin /var/bin
        fi

        if [ ! -h /var/etc ]; then
                ln -s /etc /var/etc
        fi
fi

# check for loopback interface configuration
if [ $(ifconfig lo | grep UP | wc -l) -le 0 ] || \
   [ $(ifconfig lo | grep 127.0.0.1 | wc -l) -le 0 ]; then
                ifconfig lo 127.0.0.1
fi

# start/stop cspagent
if [ ! -x /var/bin/cspagent.sh ]; then
	exit 0
fi

case $1 in
start)
	/var/bin/cspagent.sh &
	echo "Starting cspagent..."
	;;
stop)
	killall cspagent.sh
	echo "Stopping cspagent..."
	;;
*)
	echo "$1 not found. Try start/stop."
	;;
esac
exit 0' > /etc/init.d/cspagent

		#Init.d-script done
		chmod +x /etc/init.d/cspagent
		echo "output: Script generated."

		if [ -e /etc/imageinfo ] && [ $(grep -i aaf /etc/imageinfo | wc -l) -ge 1 ]; then
	            echo "output: AAF Image detected."
        	    echo "output: Using AAF method..."
	            aaf_image
                elif [ $(cat /etc/issue.net | grep -i bluepeer | wc -l) -ge 1 ]; then
	            echo "output: BluePeer Image detected."
        	    echo "output: Using BluePeer method..."
                    blue_peer
		else
		    RUNLEVELS="2 3 4"
		    echo "output: Linking start script to runlevel $RUNLEVELS"

		    for i in $RUNLEVELS
		    do
			    if [ ! -e /etc/rc$i.d/S80cspagent ]; then
				    ln -s /etc/init.d/cspagent /etc/rc$i.d/S80cspagent
			    else
				    echo "output: Link to runlevel $i allready exists..."
			    fi
		    done
		fi

		echo "output: CSP Agent installed. Starting service..."
		/etc/init.d/cspagent start

	elif [ $(grep "Sportster Pro" /etc/issue.net | wc -l) -ge 1 ]; then
		echo "output: Sportster Image detected."
		echo "output: Using sportster method..."
		dbox2_sportster
	else
		echo "output: Warning: /etc/init.d does not exist or is not writable."
		echo "output: Trying rcS method instead..."
		rcs
	fi
}

rcs()
{
	if [ -e /etc/init.d/rcS ]; then
		if [ $(cat /etc/init.d/rcS | grep /var/etc/init | wc -c) -eq 0 ]; then
			echo "output: Error: rcS method failed (no reference to /var/etc/init found)."
		else
			if [ $(grep cspagent /var/etc/init | wc -l) -le 0 ]; then
				chmod +x /var/bin/cspagent.sh
				echo "/var/bin/cspagent.sh &" >> /var/etc/init
				chmod +x /var/etc/init
				echo "output: CSP Agent installed ..."
			else
				echo "output: CSP Agent allready installed. Skipping ..."
			fi
			check_running_agent
		fi
	fi
			
}

dbox2_sportster()
{
	if [ -e /var/etc/init.d/user.start_script ]; then
		if [ $(grep cspagent /var/etc/init.d/user.start_script | wc -l) -le 0 ]; then
			echo -e "# short sleep until neutrino and nhttpd are alive\nsleep 60\n/var/bin/cspagent.sh &" >> /var/etc/init.d/user.start_script
			echo "output: CSP Agent installed ..."
		else
			echo "output: CSP Agent allready installed. Skipping ..."
		fi
		check_running_agent
	else
		echo "output: Error: user.start_script not found. Skipping ..."
	fi
}

blue_peer()
{
	if [ -e /etc/init.d/cspagent ]; then
		if [ -e /etc/init.d/S99cspagent ]; then
			echo "output: /etc/init.d/S99cspagent allready exists ... Skipping ..."
		else
			ln -sf /etc/init.d/cspagent /etc/init.d/S99cspagent
			echo "output: CSP Agent start script now active at system startup ..."
		fi
	else
        	echo "output: Error: /etc/init.d/cspagent not found. Skipping ..."
	fi
}

aaf_image()
{
	if [ -e /etc/init.d/autostart/start.sh ]; then
		if [ $(grep -i csp /etc/init.d/autostart/start.sh | wc -l) -le 0 ]; then
			insert_line=$(grep -n startEmu\(\) /etc/init.d/autostart/start.sh | sed 's/[^0-9]//g')
        	        let insert_line++
                	sed -i "$insert_line i/etc/init.d/cspagent start" /etc/init.d/autostart/start.sh
	                echo "output: CSP Agent start script added to startEmu() in start.sh ..."
		else
                	echo "output: CSP Agent allready installed. Skipping ..."
            	fi
        else
        	echo "output: Error: /etc/init.d/autostart/start.sh not found. Skipping ..."
        fi
}

pingulux_spark()
{
	if [ -e /root/autorun.sh ];then
		if [ $(grep -i csp /root/autorun.sh | wc -l) -le 0 ]; then
			insert_line=$(grep -n ywapp.exe /root/autorun.sh | sed 's/[^0-9]//g')
			let insert_line--
			sed -i "$insert_line i/var/bin/cspagent.sh\ \&" /root/autorun.sh
			echo "output: CSP Agent added to autorun.sh ..."
		else
			echo "output: CSP Agent allready installed. Skipping ..."
		fi
		check_running_agent
	else
		echo "output: Error: /root/autorun.sh not found. Skipping ..."
	fi
}

echo "output: CSP Agent v$AGENTV installation"
if [ -e /var/bin/cspagent.sh ]; then
	echo "output: Agent already installed. Delete old files."
	killall cspagent.sh 2> /dev/null
	rm -Rf /var/bin/cspagent.sh
	rm -Rf /var/etc/cspagent.id
	rm -Rf /var/etc/cspagent.conf
fi

if [ -e /tmp/cspagent.sh ]; then
	echo "output: Agent already downloaded. Trying installation."
else
	echo "output: Downloading agent..."
	wget -q -O - $AGENTURL/cspagent.sh > /tmp/cspagent.sh
	if [ $? != "0" ]; then
		rm cspagent.sh
		echo "Failed to get cspagent.sh from $AGENTURL" >> /tmp/csperr
		echo "Will exit now (`date`)" >> /tmp/csperr
		exit
	fi
fi

if [ -w /var/bin ]; then
	echo "output: Installing CSP Agent in /var/bin/..."
	mv /tmp/cspagent.sh /var/bin
	chmod +x /var/bin/cspagent.sh
	echo "output: Done."
else
	echo "output: Unable to install CSP Agent (/var/bin not writable). Exiting..."
	exit
fi

generate_conf
evaluate_method
