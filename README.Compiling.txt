Compiling CSP
-------------

The proxy is pure java and can be compiled on any platform that has a java sdk available.

You'll need the following:
- The Java SE sdk and compiler (j2sdk) 1.6 or later: http://developers.sun.com/downloads/new.jsp
NOTE: As of 0.8.10 java 1.6 is required to compile everything, but 1.4 is still enough to run the proxy.
Sun only provides downloads for linux/win/solaris, osx users look to apple: http://developer.apple.com/java/download/
- Apache ant (xml makefile system) 1.6 or later: http://ant.apache.org/bindownload.cgi
- If you're on windows, you should consider using cygwin to get a proper bash shell: http://www.cygwin.com
- If you plan to change or fix something, a basic understanding of java concepts will help:
http://java.sun.com/docs/books/tutorial/

The proxy comes with project files for Intellij IDEA 7, but you don't need this to compile (its a commercial product):
http://www.jetbrains.com/idea/index.html
NOTE: There is now a open source community version as well, which works perfectly with the csp project files.

To make sure ant uses the correct java compiler, set your JAVA_HOME so that it points to your sun j2sdk install dir, e.g:
$ export JAVA_HOME=/usr/lib/jvm/java-6-sun-1.6.0.15/

Once j2sdk + ant is installed (and their respective /bin directories added to PATH) you can build the proxy using the
following procedure:

$ tar xvzf cardservproxy-0.8.13-src.tar.gz
$ cd cardservproxy-src
$ ant

This runs the default build target "tar-app" in the build.xml file. This target compiles all classes, creates
the jar/war files and finally tars the installation directory structure.
Each output file ends up in ./dist

NOTE: tar-app will attempt to build the example plugins (which may have dependencies). If you don't want these then
run ant with: -Dskip-plugins=true

--------------------------------------------------------------------------------------------------------------------

The source distribution is arranged as follows:

build - Temp directory for the installation distribution (dir structure for cardservproxy.tar.gz).
classes - Temp directory for compiled .class files.
config - Example configs and reference.
dist - Temp directory for generated distribution files.
lib - Dependency jars needed to compile (but not to run, you'll need to copy the jars from dist to lib to do that).
      NOTE: Source for these dependencies is available on request (go find bowman on efnet or bow on freenode).
etc - Misc resources.
src - Java source files and other resources that will end up in cardservproxy.jar.
trtest - Files needed for the executable fishenc.jar (manifest only, all classes are in cardservproxy.jar).
web - Files for the status web monitoring gui (see README.HttpXmlApi.txt for details). Ends up in cs-status.war.
plugins - project dirs for the example plugins (each one laid out roughly like the main dir for the proxy source).

jsw-win32.zip - Template for a java-service-wrapper windows setup (running java and the proxy as windows service).
CardServProxy.ipr CardServProxy.iml - Intellij IDEA project and module files.
build.xml - Apache ant makefile.

NOTE: While the java source may seem somewhat organized at first glance, it's really a mess. It's the result of years
of organic growth (with contributions from many people) with little or no testing or oversight. Don't assume anything
in there is well thought through, if something seems broken - it probably is.
