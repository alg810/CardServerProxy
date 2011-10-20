#!/bin/sh

DROPBEARBINS="dropbearmulti.ppc dropbearmulti.static.ppc dropbearmulti.04x.ppc dropbearmulti.mips dropbearmulti.arm dropbearmulti.static.mips dropbearmulti.sh4 dropbearmulti.static.sh4"
BUSYBOXBINS="busybox.sh4"

get_bin_from_trac() {

	BIN=$1
	TYPE=$2

	echo -n "Retrieving $BIN ... "
	wget -q http://streamboard.gmc.to/CSP/raw-attachment/wiki/$TYPE/$BIN
	if [ -e $BIN ]; then
		mv $BIN binaries/
		chmod +x binaries/$BIN
		echo "Done"
	else
		echo "Download failed: $BIN"
		exit 1
	fi
}

for BIN in $DROPBEARBINS
do
	get_bin_from_trac $BIN DropBearBins
done

for BIN in $BUSYBOXBINS
do
	get_bin_from_trac $BIN BusyBoxBins
done
