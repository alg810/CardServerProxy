#!/bin/sh

BINARIES="dropbearmulti.ppc dropbearmulti.static.ppc dropbearmulti.mips dropbearmulti.static.mips"

for BIN in $BINARIES
do
	echo "Retrieving $BIN ..."
	wget -q http://streamboard.gmc.to:8011/raw-attachment/wiki/DropBearBins/$BIN
	if [ -e $BIN ]; then
		mv $BIN binaries/
		chmod +x binaries/$BIN
		echo "Done"
	else
		echo "Download failed: $BIN"
		exit 1
	fi
done
