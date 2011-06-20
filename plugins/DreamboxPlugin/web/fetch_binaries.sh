#!/bin/sh

BINARIES="dropbearmulti.ppc dropbearmulti.static.ppc dropbearmulti.04x.ppc dropbearmulti.mips dropbearmulti.static.mips dropbearmulti.sh4 dropbearmulti.static.sh4"

for BIN in $BINARIES
do
	echo "Retrieving $BIN ..."
	wget -q http://streamboard.gmc.to/CSP/raw-attachment/wiki/DropBearBins/$BIN
	if [ -e $BIN ]; then
		mv $BIN binaries/
		chmod +x binaries/$BIN
		echo "Done"
	else
		echo "Download failed: $BIN"
		exit 1
	fi
done
