#!/bin/bash

## uncomment this for large csp installations
#JVM_PARAMS="-Xmx512m -Dsun.net.inetaddr.ttl=0"

## otherwise use this
JVM_PARAMS="-Dsun.net.inetaddr.ttl=0"

## to enable running under other jvms besides sun:
#JVM_PARAMS="-Dcom.bowman.cardserv.allowanyjvm=true -Dnetworkaddress.cache.ttl=0"

case "`uname -s`" in

  'CYGWIN'*)
    SYSTEM="Cygwin"
  ;;

  'Linux')
    SYSTEM="Linux"
  ;;

  'OSF1')
    SYSTEM="Tru64"
  ;;

  'SunOS')
    SYSTEM="Solaris"
  ;;

  *)
    SYSTEM="Unknown"
  ;;

esac

PID_FILE=cardservproxy.pid

serverpid() {
  if [ -f $PID_FILE ]; then
    if [ "$SYSTEM" = "Cygwin" ]; then
      PID=`cat $PID_FILE`
      if [ "x"$PID != "x" ]; then
        if [ -n "`ps | grep $PID`" ]; then
          cat $PID_FILE
          return
        fi
      fi
    else
      if [ "$SYSTEM" = "Solaris" ]; then
        if [ -n "`cat $PID_FILE | xargs ps -p | tail +2`" ]; then
          cat $PID_FILE
          return
        fi
      else
        if [ -n "`cat $PID_FILE | xargs ps | tail -n +2`" ]; then
          cat $PID_FILE
          return
        fi
      fi
    fi
  fi
  echo -n 0
}

echoresult() {
  echo -n " "
  $MOVE_TO_COL
  echo -n "[  "
  echo -n $1
  echo "  ]"
  shift
  if [ "$#" != "0" ] ; then echo "$1" ; fi
}

case "$1" in

  'start')
    echo -n "Starting CardServProxy:"
    if [ "`serverpid`" != "0" ]; then
      echoresult FAILED "An instance of the server is already running"
      exit 1
    fi
    java $JVM_PARAMS -jar lib/cardservproxy.jar > log/cardserv-sysout.log 2>&1 &
    echo $! > $PID_FILE
    sleep 3
    ERR=`cat log/cardserv-sysout.log | grep '[Ee]rror\|[Ee]xception\|[Ff]ailed\|not found'`
    if [ "$ERR" ]; then
      echoresult FAILED
      cat log/cardserv-sysout.log
      OP=`serverpid`
      if [ $OP != "0" ]; then        
        kill $OP              
      fi
      rm $PID_FILE
      exit
    else
      echoresult OK
    fi
  ;;

  'stop')
    echo -n "Killing Proxy:"
    OP=`serverpid`
    if [ $OP != "0" ]; then
      rm $PID_FILE
      kill $OP
      echoresult OK
    else
      echoresult FAILED "Cannot determine pid"
    fi
  ;;

  'dump')
    echo -n "Sending SIGQUIT:"
    OP=`serverpid`
    if [ $OP != "0" ]; then
      kill -3 $OP
      echoresult OK
    else
      echoresult FAILED "Cannot determine pid"
    fi
  ;;

  'status')
    OP=`serverpid`
    if [ $OP = "0" ]; then
      echo "Proxy is stopped"
      exit 1
    else
      echo "Proxy (pid $OP) is running..."
      exit 0
    fi
  ;;

  *)
    echo "Usage: $0 {start|stop|status|dump}"
    exit 1
  ;;

esac

exit $?




