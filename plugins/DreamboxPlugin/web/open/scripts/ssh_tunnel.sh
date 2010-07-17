#!/bin/ash

# changed for dropbear 0.5x packed with upx
killall dbclient 2> /dev/null

. /var/etc/cspagent.conf

TUNNELPORT={TUNNELPORT}
SSHDPORT={SSHDPORT}
CPUARCH={CPUARCH}
LOCALPORT={PARAMS}
BOXTYPE={BOXTYPE}

if [ -z $LOCALPORT ]; then
  LOCALPORT=23
fi

# Download apropriate binary from plugin web
if [ ! -e "/tmp/dropbear/dropbearmulti" ]; then

  # get static binaries for some boxtypes, defaults to dynamic
  case $BOXTYPE in
    dm600pvr|dm7020|mystb)
      BINARY="dropbearmulti.static.$CPUARCH"
      ;;

    *)
      BINARY="dropbearmulti.$CPUARCH"
      ;;
  esac

  mkdir -p /tmp/dropbear
  cd /tmp/dropbear
  echo "Downloading dropbear binary: http://$CSPHOST:$CSPPORT/open/binaries/$BINARY"
  wget -q http://$CSPHOST:$CSPPORT/open/binaries/$BINARY -O /tmp/dropbear/dropbearmulti
  if [ ! -e "/tmp/dropbear/dropbearmulti" ]; then
    echo "Failed to download: http://$CSPHOST:$CSPPORT/open/binaries/$BINARY"
    exit
  fi
  chmod +x dropbearmulti
fi

# Create symlinks
ln -fs /tmp/dropbear/dropbearmulti /tmp/dropbear/dropbear
ln -fs /tmp/dropbear/dropbearmulti /tmp/dropbear/dbclient
ln -fs /tmp/dropbear/dropbearmulti /tmp/dropbear/dropbearkey
ln -fs /tmp/dropbear/dropbearmulti /tmp/dropbear/dropbearconvert
ln -fs /tmp/dropbear/dropbearmulti /tmp/dropbear/scp

# Generate hostkey in order to use non-interactive public key auth (plugin sshd will accept any key)
if [ ! -e "/tmp/dropbear/dropbearkey.rsa" ]; then
  /tmp/dropbear/dropbearkey -t rsa -f /tmp/dropbear/dropbearkey.rsa
fi

if [ ! -e "/tmp/dropbear/dropbearkey.rsa" ]; then
  echo "Failed to generate host key."
  exit
fi

chmod +r /tmp/dropbear/dropbearkey.rsa
echo "Running: /tmp/dropbear/dbclient -T -R $TUNNELPORT:localhost:$LOCALPORT -i /tmp/dropbear/dropbearkey.rsa -p $SSHDPORT -l $(cat /var/etc/cspagent.id) $CSPHOST -N -y &"

# Connect using the boxid as username
/tmp/dropbear/dbclient -T -R $TUNNELPORT:localhost:$LOCALPORT -i /tmp/dropbear/dropbearkey.rsa -p $SSHDPORT -l $(cat /var/etc/cspagent.id) $CSPHOST -N -y &
