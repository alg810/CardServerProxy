#!/bin/ash

# Hack to force dropbear 0.4x to read 'y' from stdin and not the tty
# TODO: adduser doesnt exist in all busybox builds, need an alternative that manipulates /var/etc/passwd directly
chmod -r /dev/tty
adduser -h /tmp -D -H dropbear 2> /dev/null
killall dbclient 2> /dev/null

. /var/etc/cspagent.conf

TUNNELPORT={TUNNELPORT}
SSHDPORT={SSHDPORT}
CPUARCH={CPUARCH}
LOCALPORT={PARAMS}

if [ -z $LOCALPORT ]; then
  LOCALPORT=23
fi

# Download apropriate binary from plugin web
if [ ! -e "/tmp/dropbear/dropbearmulti" ]; then
  mkdir -p /tmp/dropbear
  cd /tmp/dropbear
  echo "Downloading dropbear binary: http://$CSPHOST:$CSPPORT/open/binaries/dropbearmulti.$CPUARCH.gz"
  wget -q http://$CSPHOST:$CSPPORT/open/binaries/dropbearmulti.$CPUARCH.gz -O /tmp/dropbear/dropbearmulti.gz
  if [ ! -e "/tmp/dropbear/dropbearmulti.gz" ]; then
    echo "Failed to download: http://$CSPHOST:$CSPPORT/open/binaries/dropbearmulti.$CPUARCH.gz"
    exit
  fi
  gunzip dropbearmulti.gz
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
su -c "echo y | /tmp/dropbear/dbclient -T -R $TUNNELPORT:localhost:$LOCALPORT -i /tmp/dropbear/dropbearkey.rsa -p $SSHDPORT -l $(cat /var/etc/cspagent.id) $CSPHOST -N -y &" dropbear
