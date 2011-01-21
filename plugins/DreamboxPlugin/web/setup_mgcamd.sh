#!/bin/sh
#
# template for remote mgcamd installation/configuration over csp agent.
# this will not work out of the box. You have to edit some var's and place mgcamd and keys to the right places.
#

# params substituted by csp
SSHDPORT="{SSHDPORT}"
CPUARCH="{CPUARCH}"
USERNAME="{USERNAME}"
PASSWORD="{PASSWORD}"
IMAGE="{IMAGE}"
BOXTYPE="{BOXTYPE}"
PARAMS="{PARAMS}"

# Source conf-file if exists
if [ -e /var/etc/cspagent.conf ]; then
	. /var/etc/cspagent.conf
else
	echo "CSP Agent config not found ... exit now ..."
	exit 1
fi

### config ###
# mgcamd files
NCDLIST="/var/keys/newcamd.list"
NCDCFG="/var/keys/mg_cfg"
NCDPRIO="/var/keys/priority.list"
NCDIGNORE="/var/keys/ignore.list"
# csp ports and keys
NCDEXTPORT="12345"
NCDKEEPALIVE="60"
DESKEY="11 22 33 44 55 66 77 88 99 AA BB CC DD EE"
### end ###

### init scripts for boxes ###
GEMINI_CAM='CAMNAME="MgCamd 1.35a"
USERNAME="MgCamd 1.35a"
START_TIME=4
STOPP_TIME=2
CAMID=4096
INFOFILE="ecm.info"
INFOFILELINES=
# end

remove_tmp () {
  rm -rf /tmp/*.info* /tmp/*.tmp* /tmp/*mgcamd*
}

case "$1" in
  start)
  echo "[SCRIPT] $1: $CAMNAME"
  remove_tmp
  /usr/bin/mgcamd_1.35a &
  ;;
  stop)
  echo "[SCRIPT] $1: $CAMNAME"
  killall -9 mgcamd_1.35a
  remove_tmp
  ;;
  *)
  $0 stop
  exit 0
  ;;
esac

exit 0'
### end ###

### functions ###

install_opkg() {

	FEEDLIST=$1
	OPKG_NAME=$2

	if [ ! -e /var/lib/opkg/$FEEDLIST ]; then
		echo "Updating feed list from server ..."
		opkg update
		if [ $? != "0" ]; then
			echo "Cannot get feed list from server ..."
			return 1
		fi
	else
		echo "Feedlist up-to-date ... "
	fi

	if [ $(opkg status $OPKG_NAME | grep installed | wc -l) -ge 1 ]; then
		echo "Mgcamd pkg ($OPKG_NAME) allready installed ..."
		return 0
	else
		opkg install $OPKG_NAME
		if [ $? != "0" ]; then
			echo "Cannot install pkg ($OPKG_NAME) with opkg ... "
			return 1
		else
			echo "Pkg ($OPKG_NAME) installed successfully ..."
			return 0
		fi	
	fi
}

### end ###

### main ###
if [ "$PARAMS" = "kill" ]; then
	echo "Killing newcamd.list ..."
	rm -Rf $NCDLIST
	echo "Rebooting system ..."
	reboot
	exit
fi

if [ "$PARAMS" = "config" ]; then
	IMAGE="SkipInstall"
fi

# check if we have an image with GP3 plugin, so we use plugin functions for cam installation
if [ $(echo $IMAGE | grep GP3 | wc -l) -ge 1 ]; then
  IMAGE="GeminiPlugin3"
fi

case $IMAGE in
	"Sportster Pro")
		if [ -x /var/emu/mgcamd ]; then
                        echo "Mgcamd allready installed ..."
                        break
                else
			CPUARCH="ppc_old"
			wget -q -O /tmp/mgcamd http://$CSPHOST:$CSPPORT/open/binaries/mgcamd.$CPUARCH
			if [ $? != "0" ]; then
				echo "Cannot get mgcamd binary from server ..."
			else
				mv /tmp/mgcamd /var/emu/mgcamd
				chmod +x /var/emu/mgcamd
				echo "1.35a" > /var/emu/.mgcamd.txt
				touch /var/etc/.mgcamd
				rm -Rf /var/etc/.camd2
				echo "Mgcamd installed successfully on dbox2 with sportster image ..."
			fi
		fi
		;;

	"Gemini")
		if [ -x /var/bin/mgcamd_1.35a ]; then
			echo "Mgcamd allready installed ..."
			break
		else

			if [ $ENIGMAV -eq 1 ]; then
				wget -q -O /var/bin/mgcamd_1.35a http://$CSPHOST:$CSPPORT/open/binaries/mgcamd.$CPUARCH
				if [ $? != "0" ]; then
					echo "Cannot get mgcamd binary from server ..."
				else	
					chmod +x /var/bin/mgcamd_1.35a

					# install cam script
					echo $GEMINI_CAM > /usr/script/mgcamd_1.35a_cam.sh
					chmod +x /usr/script/mgcamd_1.35a_cam.sh
	
					# set default cam to mgcamd
					cat /var/tuxbox/config/Gemini.conf | sed "s/act_cam=.*/act_cam=4096/" > /tmp/Gemini.conf.new
					mv /var/tuxbox/config/Gemini.conf /var/tuxbox/config/Gemini.conf.bak
					mv /tmp/Gemini.conf.new /var/tuxbox/config/Gemini.conf
	
					echo "Mgcamd installed successfully on dreambox with Gemini (E1) image ..."
				fi
			else
				wget -q -O /var/bin/mgcamd_1.35a http://$CSPHOST:$CSPPORT/open/binaries/mgcamd.$CPUARCH
				if [ $? != "0" ]; then
					echo "Cannot get mgcamd binary from server ..."
				else
					chmod +x /var/bin/mgcamd_1.35a
	
					# install cam script
					echo $GEMINI_CAM > /usr/script/mgcamd_1.35a_cam.sh
					chmod +x /usr/script/mgcamd_1.35a_cam.sh
	
					# set default cam to mgcamd
					cat /var/etc/gemini2.conf | sed "s/int=used_camd=.*/int=used_camd=4096/" > /tmp/gemini2.conf.new
					mv /var/etc/gemini2.conf /var/etc/gemini2.conf.bak
					mv /tmp/gemini2.conf.new /var/etc/gemini2.conf
	
					echo "Mgcamd installed successfully on dreambox with Gemini (E2) image ..."
				fi
			fi
		fi
		;;

	"Newnigma2")
		install_opkg "softcammipsel" "newnigma2-camd-mgcamd1-35a"
		if [ $? != "0" ]; then
			break;
		fi
		;;

	"iCVS")
		# TODO 
		# - automatically GP3 Plugin installation
		# - reinstall CSP agent to get rid of new Image Guess
		# - requeue an setup_mgcamd task
		;;

	"GeminiPlugin3")
		install_opkg "gemini-mipsel" "gp-cam-mgcamd-1.35a"
		if [ $? != "0" ]; then
			break;
		else
			# set default cam to mgcamd
			echo "Set Mgcamd as default cam ..."
			cat /etc/enigma2/gemini_plugin.conf | sed "s/int=currCamd=.*/int=currCamd=4096/" > /tmp/gemini_plugin.conf.new
			mv /etc/enigma2/gemini_plugin.conf /etc/enigma2/gemini_plugin.conf.bak
			mv /tmp/gemini_plugin.conf.new /etc/enigma2/gemini_plugin.conf
			rm -Rf /etc/enigma2/gemini_plugin.conf.bak
		fi
		;;

	"SkipInstall")
		echo "Config only parameter set ... will skip installation ..."
		;;

	*)
		echo "No supported image found ... generating only config ..."
		;;
esac

# generate new newcamd.list
echo "Generate newcamd.list ..."
echo -e "CWS_KEEPALIVE = $NCDKEEPALIVE\nCWS = $CSPHOST $NCDEXTPORT $USERNAME $PASSWORD $DESKEY wan server1" > $NCDLIST

# generate ignore.list
echo "Generate ignore.list ..."
echo -e "X: { 09 C4 }\nX: { 09 C7 }\nX: { 09 AF }\nX: { 18 11 }\nX: { 18 15 }\nX: { 18 36 }" > $NCDIGNORE

# generate priority.list
#echo "Generate priority.list ..."
#echo -e "X: { 0B 00 }\nX: { 17 20 }\nX: { 17 22 }\nX: { 18 30 }" > $NCDPRIO

# get SoftCam.Key and AutoRoll.Key from Server
KEYFILES="SoftCam.Key AutoRoll.Key"
for KEYFILE in $KEYFILES
do
	echo "Getting $KEYFILE from Server ..."
	wget -q -O /tmp/$KEYFILE http://$CSPHOST:$CSPPORT/open/binaries/$KEYFILE
	if [ $? != "0" ]; then
		echo "Cannot get $KEYFILE from server ..."
	else
		if [ -e /var/keys/$KEYFILE ]; then
			echo "Found old $KEYFILE ... deleting ..."
			rm -Rf /var/keys/$KEYFILE
		fi
		mv /tmp/$KEYFILE /var/keys/$KEYFILE
		echo "$KEYFILE installed successfully ... "
	fi
done

# generate mg_cfg
if [ -e $NCDCFG ]; then
	echo "Mgcamd config allready exists ... delete old config ..."
	rm -Rf $NCDCFG
fi

echo "Generate mg_cfg ..."
echo "M: { 01 }
C: { 01 }
A: { 03 }
U: { 01 } 0x12c0
T: { 00 }
G: { 01 }
N: { 07 } 5 30
K: { 10 }
Q: { 600 }
P: { 00 } " >> $NCDCFG

case $ENIGMAV in
	1)
		echo "O: { 02 } root dreambox" >> $NCDCFG
		;;

	3)
		echo "O: { 01 } root dbox2" >> $NCDCFG
		;;
		
	*)
		echo "O: { 00 } root dreambox" >> $NCDCFG
		;;
esac

echo "S: { 00 } 80
L: { 02 } 172.16.1.1 28007 /tmp/mgcamd.log
E: { 15 }
H: { 07 }
R: { 04 }
D: { 00 }
B: { 00 }
F: { 00 }" >> $NCDCFG

# flush buffers
sync
