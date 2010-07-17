#!/bin/ash

#Variables populated automatically on download by the DreamboxPlugin
CSPHOST={0}
CSPPORT={1}
CSPUSER={2}
INTERVAL={3}
AGENTV={4}

AGENTURL=http://$CSPHOST:$CSPPORT/open

i=0

generate_conf()
{
	if [ -e /var/etc/cspagent.conf ]
	then
		echo "Config file /var/etc/cspagent.conf already exists. Skipping..."
	else
		echo "

#Proxy DreamboxPlugin host
CSPHOST=$CSPHOST

#Proxy DreamboxPlugin port
CSPPORT=$CSPPORT

#Proxy username
CSPUSER=$CSPUSER

#Enigma httpauth user
ENIGMAUSER=root

#Enigma httpauth password
ENIGMAPASS=dreambox
 
#Interval between script downloads/runs (in seconds)
INTERVAL=$INTERVAL

#Tempfiles, no reason to touch these
OUTFILE=/tmp/runme.sh 
TMPFILE=/tmp/runme.out
" >> /var/etc/cspagent.conf

	fi
}

initd()
{
	if [ -d /etc/init.d ] && [ -w /etc/init.d ] && [ $(mount|grep /dev/root|grep squash|wc -c) -eq 0 ]
	then
		if [ -e /etc/init.d/cspagent ]
		then
			echo "Init.d-script already exists."
		else
			echo "Generating start script in /etc/init.d/..."
			#Generate script for /etc/init.d
			echo "#!/bin/sh

# start/stop cspagent
if ! [ -x /var/bin/cspagent.sh ]; then
	exit 0
fi
case \$1 in
start)
	start-stop-daemon -S -b -x /var/bin/cspagent.sh
	echo 'Starting cspagent...'
	;;
stop)
	killall cspagent.sh
	echo 'Stopping cspagent...'
	;;
*)
	echo '\$1 not found. Try start/stop.'
	;;
esac
exit 0" > /etc/init.d/cspagent

			#Init.d-script done
			chmod +x /etc/init.d/cspagent
			echo "Script generated."
		fi     
		ln -s /etc/init.d/cspagent /etc/rc2.d/S80cspagent
		ln -s /etc/init.d/cspagent /etc/rc3.d/S80cspagent
		ln -s /etc/init.d/cspagent /etc/rc4.d/S80cspagent
		echo "CSP Agent installed. Starting service..."
		/etc/init.d/cspagent start
	else
		echo "Warning: /etc/init.d does not exist or is not writable."
		echo "Trying rcS method instead..."
		rcs
	fi
}

rcs()
{
	if [ -e /etc/init.d/rcS ]
	then
		test=$(cat /etc/init.d/rcS|grep /var/etc/init|wc -c)
		if [ $test = "0" ]
		then
			echo "Error: rcS method failed (no reference to /var/etc/init found)."
		else
			chmod +x /var/bin/cspagent.sh
			echo "/var/bin/cspagent.sh &" > /var/etc/init
			chmod +x /var/etc/init
			echo "CSP Agent installed. Starting service..."
			/var/bin/cspagent.sh &
			echo "Done."
		fi
	fi
			
}

evaluate_method()
{
	initd
}

echo "CSP Agent v$AGENTV installation"
test=1
while [ $test = "1" ]
do
	read -p "Would you like to continue? [y/n] " answer
	echo ""
	if [ $answer = "y" ]
	then
		test=0
		echo "Continuing installation"
		if [ -e /tmp/cspagent.sh ]
		then
			echo "Agent already downloaded. Trying installation."
		else
			echo "Downloading agent..."
			wget -q -O - $AGENTURL/cspagent.sh > /tmp/cspagent.sh
			if [ $? != "0" ]
			then
				rm cspagent.sh
				echo "Failed to get cspagent.sh from $AGENTURL"
				echo "Try again later..."
				exit
			else
				chmod +x cspagent.sh
			fi
		fi
		if [ -w /var/bin ]
		then
			echo "Installing CSP Agent in /var/bin/..."
			mv /tmp/cspagent.sh /var/bin
			echo "Done."
		else
			echo "Unable to install CSP Agent (/var/bin not writable). Exiting..."
			exit
		fi
		generate_conf
		evaluate_method
	elif [ $answer = "n" ]
	then
		echo "Exiting installation"
		exit
	else
		echo "Please answer y or n"
	fi
done
