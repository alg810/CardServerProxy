#!/bin/sh

mkdir -p tmp
rm -rf tmp/*
cd tmp

# Use an old binary to ensure no unmet dependencies on older 500-images
echo "Retrieving dropbear 0.48 ppc binary..."
wget -q http://downloads.pli-images.org/iolite/plugins/dropbear.tar.gz
if [ -e dropbear.tar.gz ]; then
  tar xzf dropbear.tar.gz
  mv var/bin/dropbearmulti ../dropbearmulti.ppc
  gzip ../dropbearmulti.ppc
  mv ../dropbearmulti.ppc.gz ../binaries/
  rm dropbear.tar.gz
  rm -rf var
  echo "Done"
else
  echo "Download failed: dropbear.tar.gz"
  exit 1
fi

echo "Retrieving dropbear 0.48 mips binary..."
wget -q http://downloads.pli-images.org/feeds/jade3/dm800/dropbear_0.48.1-r1_mipsel.ipk
if [ -e dropbear_0.48.1-r1_mipsel.ipk ]; then
  ar x dropbear_0.48.1-r1_mipsel.ipk
  tar xf data.tar.gz
  mv usr/sbin/dropbearmulti ../dropbearmulti.mips
  gzip ../dropbearmulti.mips
  mv ../dropbearmulti.mips.gz ../binaries/
  rm -rf *
  echo "Done"
else
  echo "Download failed: dropbear_0.48.1-r1_mipsel.ipk"
  exit 1
fi