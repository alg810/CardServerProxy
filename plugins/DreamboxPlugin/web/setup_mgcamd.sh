#!/bin/ash
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
# fallback
FALLBACK="false"
CSP_FALLBACK_HOST="fallback-host.domain.tld"
### end ###

### functions ###
install_pkg() {

	FEEDLIST=$1
	PKG_NAME=$2
	PKG_MANAGER=$3

	if [ ! -e $FEEDLIST ]; then
		echo "Updating feed list from server ..."
		$PKG_MANAGER update
		if [ $? != "0" ]; then
			echo "Cannot get feed list from server ..."
			return 1
		fi
	else
		echo "Feedlist up-to-date ... "
	fi

	if [ $($PKG_MANAGER status $PKG_NAME | grep installed | wc -l) -ge 1 ]; then
		echo "$PKG_NAME allready installed ..."
		return 0
	else
		$PKG_MANAGER install $PKG_NAME
		if [ $? != "0" ]; then
			echo "Cannot install $PKG_NAME with $PKG_MANAGER ... "
			return 1
		else
			echo "$PKG_NAME installed successfully ..."
			return 0
		fi	
	fi
}

fetch_file() {

	GET_FILE=$1
	DEST_FILE=$2
	EXECUTABLE=$3

	wget -q -O /tmp/$GET_FILE http://$CSPHOST:$CSPPORT/open/binaries/$GET_FILE
	if [ $? != "0" ]; then
		echo "Cannot get $GET_FILE from server ..."
		return 1
	else
		mv -f /tmp/$GET_FILE $DEST_FILE

		if [ $EXECUTABLE = "1" ]; then
			chmod +x $DEST_FILE
		fi

		echo "$GET_FILE successfully fetched and copied to $DEST_FILE ..."
		return 0
	fi

}
### end ###

### main ###
if [ $(echo $PARAMS | grep -i kill | wc -l) -ge 1 ]; then
	echo "Killing newcamd.list ..."
	rm -Rf $NCDLIST
	echo "Rebooting system ..."
	reboot
	exit
fi

if [ $(echo $PARAMS | grep -i config | wc -l) -ge 1 ]; then
	OLDIMAGE=$IMAGE
	IMAGE="SkipInstall"
fi

# check if we have an image with GP3 plugin
if [ $(echo $IMAGE | grep -i gp3 | wc -l) -ge 1 ]; then
  IMAGE="Gemini"
  GP3_PLUGIN_INSTALLED="1"
fi

case $IMAGE in
	"Sportster Pro")
		if [ -x /var/emu/mgcamd ]; then
                        echo "Mgcamd allready installed ..."
                        break
                else
			CPUARCH="ppc_old"
			fetch_file "mgcamd.$CPUARCH" "/var/emu/mgcamd" "1"
			if [ $? != "0" ]; then
				break
			else
				# set default cam to mgcamd
				echo "1.35a" > /var/emu/.mgcamd.txt
				touch /var/etc/.mgcamd

				# remove camd2 default
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
			fetch_file "mgcamd.$CPUARCH" "/var/bin/mgcamd_1.35a" "1"
			if [ $? != "0" ]; then
				break
			else
				# install cam script
				echo '#!/bin/ash
CAMNAME="MgCamd 1.35a"
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

exit 0' > /usr/script/mgcamd_1.35a_cam.sh
				chmod +x /usr/script/mgcamd_1.35a_cam.sh

				# set default cam to mgcamd
				if [ $GP3_PLUGIN_INSTALLED = "1" ]; then
					sed -i "s/int=currCamd=.*/int=currCamd=4096/" /etc/enigma2/gemini_plugin.conf
					echo "Mgcamd installed successfully on dreambox with installed GP3 Plugin ..."
				else
					case $OSDVER in
						1)
							sed -i "s/act_cam=.*/act_cam=4096/" /var/tuxbox/config/Gemini.conf
							echo "Mgcamd installed successfully on dreambox with Gemini (E1) image ..."
							;;

						2)
							sed -i "s/int=used_camd=.*/int=used_camd=4096/" /var/etc/gemini2.conf
							echo "Mgcamd installed successfully on dreambox with Gemini (E2) image ..."
							;;
					esac
				fi
			fi
		fi
		;;

	"Dreamelite")
		if [ -x /var/bin/mgcamd_1.35a ]; then
			echo "Mgcamd allready installed ..."
			break
		else
			fetch_file "mgcamd.$CPUARCH" "/var/bin/mgcamd_1.35a" "1"
			if [ $? != "0" ]; then
				break
			else
				# install cam script
				echo '#!/bin/ash
#emuname=Mgcamd 1.35a
#ecminfofile=ecm.info

remove_tmp () {
        rm -rf /tmp/ecm.info /tmp/pid.info /tmp/cardinfo /tmp/mg*
}

case "$1" in
        start)
        remove_tmp
        /var/bin/mgcamd_1.35a &
        sleep 3
        ;;
        stop)
        killall -9 mgcamd_1.35a
        remove_tmp
        sleep 2
        ;;
        *)
        $0 stop
        exit 1
        ;;
esac

exit 0' > /usr/script/mgcamd_135a_em.sh
				chmod +x /usr/script/mgcamd_135a_em.sh

				# set default cam to mgcamd
				echo "mgcamd_135a" > emuactive
				echo "Mgcamd installed successfully on dreambox with Dreamelite image ..."
			fi
		fi
		;;

	"Newnigma2")
		install_pkg "/var/lib/opkg/softcammipsel" "newnigma2-camd-mgcamd1-35a" "opkg"
		if [ $? != "0" ]; then
			break
		else
			# set default cam to mgcamd
			ln -sf /usr/script/mgcamd1.35.emu /etc/rc3.d/S98emustart
			echo "Mgcamd installed successfully on dreambox with Newnigma2 image ..."
		fi
		;;

	"AAF")
		install_pkg "/usr/lib/ipkg/cross" "enigma2-plugin-emus-mgcamd.v1.35" "ipkg"
		if [ $? != "0" ]; then
			break
		else
			sed -i 's/emu=.*/emu=mgcamd_1.35\.emu/' /etc/init.d/autostart/start-config
			echo "Mgcamd installed successfully on ufs912 with AAF image ..."
		fi
		;;

	"VTi")
		install_pkg "/var/lib/ipkg/VTI" "enigma2-plugin-cams-mgcamd.1.35" "ipkg"
		if [ $? != "0" ]; then
			break
		else
			ln -sf /usr/script/mgcamd_1.35_cam.sh /etc/init.d/current_cam.sh
			echo "Mgcamd installed successfully on Vu+ with VTi image ..."
		fi
		;;

        "Pingulux")
                # create plugin dirs
                if [ ! -e /root/plugin/var ]; then
                        mkdir /root/plugin/var
                fi

                if [ ! -e /root/plugin/var/bin ]; then
                        mkdir /root/plugin/var/bin
                fi

                if [ ! -e /root/plugin/var/keys ]; then
                        mkdir /root/plugin/var/keys
                fi

                fetch_file "mgcamd.$CPUARCH" "/root/plugin/var/bin" "1"

                # set default cam to mgcamd
                echo -e '<?xml version="1.0"?>\n<BOOT_CAMID CURRENT_CAMID="mgcamd.sh4" />' > /root/plugin/var/bin/plugin.xml
                echo "Mgcamd installed successfully on Pingulux with Spark image ..."
                ;;

        "NG-NeutrinoHD"|"NG-Neutrino-HD")
                fetch_file "mgcamd.$CPUARCH" "/var/bin/mgcamd" "1"
                if [ $? != "0" ]; then
                        echo "Mgcamd installation failed ..."
                else
                        echo "Mgcamd installed successfully on Coolstream with NG-NeutrinoHD image ..."
                fi
                touch /var/etc/.mgcamd
                ;;

	"SkipInstall")
		echo "Config only parameter set ... will skip installation ..."
		IMAGE=$OLDIMAGE
		;;

	*)
		echo "No supported image found ... config only ..."
		;;
esac

# generate new newcamd.list
echo "Generate newcamd.list ..."
if [ "$FALLBACK" = "true" ]; then
  echo -e "CWS_KEEPALIVE = $NCDKEEPALIVE\nCWS = $CSPHOST $NCDEXTPORT $USERNAME $PASSWORD $DESKEY wan server1\nCWS = $CSP_FALLBACK_HOST $NCDEXTPORT $USERNAME $PASSWORD $DESKEY wan fallback1" > $NCDLIST
else
  echo -e "CWS_KEEPALIVE = $NCDKEEPALIVE\nCWS = $CSPHOST $NCDEXTPORT $USERNAME $PASSWORD $DESKEY wan server1" > $NCDLIST
fi

# generate ignore.list
echo "Generate ignore.list ..."
echo -e "X: { 09 C4 }\nX: { 09 C7 }\nX: { 09 AF }\nX: { 18 11 }\nX: { 18 15 }\nX: { 18 36 }" > $NCDIGNORE

# generate priority.list
echo "Generate priority.list ..."
echo -e "X: { 0B 00 }\nX: { 17 20 }\nX: { 17 22 }\nX: { 18 30 }" > $NCDPRIO

# get SoftCam.Key and AutoRoll.Key from Server
KEYFILES="SoftCam.Key AutoRoll.Key"
for KEYFILE in $KEYFILES
do
	fetch_file "$KEYFILE" "/var/keys/$KEYFILE" "0"
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
K: { 07 }
Q: { 120 }
P: { 00 }" >> $NCDCFG

case $OSDTYPE in
        "neutrino")
                if [ $OSDVER -eq 1 ]; then
                        echo "O: { 01 } root dbox2" >> $NCDCFG
                fi
                ;;

        "enigma")
                if [ $OSDVER -eq 1 ]; then
                        echo "O: { 02 } root dreambox" >> $NCDCFG
                else
                        echo "O: { 00 } root password" >> $NCDCFG
                fi
                ;;

        "spark")
                echo "O: { 00 } root root" >> $NCDCFG
                ;;

        *)
                echo "O: { 00 } root password" >> $NCDCFG
                ;;
esac

echo "S: { 00 } 80
L: { 02 } 172.16.1.1 28007 /tmp/mgcamd.log
E: { 15 }
H: { 07 }
R: { 04 }
D: { 00 }" >> $NCDCFG

case $BOXTYPE in
	"ufs912")
		if [ "$IMAGE" = "AAF" ]; then
			echo "B: { 11 }" >> $NCDCFG
		else
			echo "B: { 08 }" >> $NCDCFG
		fi
		;;

        "spark")
                echo "B: { 11 }" >> $NCDCFG
                ;;

	*)
		echo "B: { 00 }" >> $NCDCFG
		;;
esac

echo "F: { 00 }" >> $NCDCFG

# reboot box if requested
if [ $(echo $PARAMS | grep -i reboot | wc -l) -ge 1 ]; then
	reboot
fi

# flush buffers
sync
