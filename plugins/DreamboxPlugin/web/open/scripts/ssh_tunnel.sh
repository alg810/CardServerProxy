#!/bin/ash

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

  # Get static binaries for some boxtypes, defaults to dynamic
  case $BOXTYPE in
    dm600pvr|dm7020|mystb)
      BINARY="dropbearmulti.static.$CPUARCH"
      ;;

    *)
      BINARY="dropbearmulti.$CPUARCH"
      ;;
  esac

  # Binaries for dropbear 0.5x rely on libutil, if its missing get 0.4x instead (probably only applies to tuxbox cdk)
  if [ ! -e "/lib/libutil.so.1" ]; then
    BINARY="dropbearmulti.04x.ppc"
    touch /tmp/dropbear.04x
  fi

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

# Generate hostkey in order to use non-interactive public key auth (plugin sshd will accept any key but a valid key file has to exist)
if [ ! -e "/tmp/dropbear/dropbearkey.rsa" ]; then
  /tmp/dropbear/dropbearkey -t rsa -f /tmp/dropbear/dropbearkey.rsa
fi

if [ ! -e "/tmp/dropbear/dropbearkey.rsa" ]; then
  echo "Failed to generate host key."
  rm -rf /tmp/dropbear
  exit
fi

chmod +r /tmp/dropbear/dropbearkey.rsa

# Connect using the boxid as username
if [ -e "/tmp/dropbear.04x" ]; then
  echo "Using dropbear 0.4x workaround..."
  # No -y arg i 0.4x, and if dropbear can read from /dev/tty then it will not accept the "y" response on stdin, so make it unreadable for non-root users and create one to run as
  chmod -r /dev/tty
  adduser -h /tmp -D -H dropbear 2> /dev/null
  echo "Running: echo y | /tmp/dropbear/dbclient -T -R $TUNNELPORT:localhost:$LOCALPORT -i /tmp/dropbear/dropbearkey.rsa -p $SSHDPORT -l $(cat /var/etc/cspagent.id) $CSPHOST &"
  su -c "echo y | /tmp/dropbear/dbclient -T -R $TUNNELPORT:localhost:$LOCALPORT -i /tmp/dropbear/dropbearkey.rsa -p $SSHDPORT -l $(cat /var/etc/cspagent.id) $CSPHOST &" dropbear
else
  echo "Running: /tmp/dropbear/dbclient -T -R $TUNNELPORT:localhost:$LOCALPORT -i /tmp/dropbear/dropbearkey.rsa -p $SSHDPORT -l $(cat /var/etc/cspagent.id) $CSPHOST -N -y &"
  /tmp/dropbear/dbclient -T -R $TUNNELPORT:localhost:$LOCALPORT -i /tmp/dropbear/dropbearkey.rsa -p $SSHDPORT -l $(cat /var/etc/cspagent.id) $CSPHOST -N -y &
fi
