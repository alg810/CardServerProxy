CSP Optimization
----------------

Contents:
  1. Client behaviour
  2. Cardserver behaviour
  3. Proxy configuration
  4. Interpreting transaction flags
  5. Linux/OS-tweaking
  6. JVM tweaking

There are a considerable number of timeout values at play for any given ecm -> cw transaction, this readme will attempt
to identify the most common timing pitfalls and suggest a set of best practices.

It should be said that by far the greatest increase to stability comes from adding a second proxy, compared to that
any other optimizations are insignificant. Even if both proxies are connected to the exact same set of cards, the
second chance it offers to clients (regardless of what the issue was) has a _major_ impact on the perceived stability.
This is of course assuming clients with failover functionality similar to mgcamd, that maintain all configured connections
(with keep-alives) and will perform a seamless switch to the next applicable connection in case of trouble.

NOTE: Most of the tips here are based on ca-systems where the possible delay between the ecm appearing in the TS and
the cw actually changing is fairly large (>5 seconds). For ca-systems with tighter margins you may need other methods.
In most proxy examples, it is assumed that the client can safely wait nearly 9 seconds for its cw reply without
experiencing a freeze.


1. Client behaviour (most of the proxy examples are based on mgcamd, or similar clients).
-----------------------------------------------------------------------------------------

- Identify and configure the ecm timeout* value for each client, and set this as high as possible. 
I.e. if the situation allows waiting for 9 seconds without causing a picture freeze, then set the value to 9 seconds. 
Experiment to determine what the actual maximum is (you can use the LoggingPlugin, see 3. Proxy configuration).
If you have multiple proxies or other alternative connections, set the timeout slightly below the maximum to give the
client a chance to get a reply elsewhere in case of failure.

- Client ecm cache and reconnect/retry behaviour etc, are less important and can usually be safely ignored. 
If possible the client should be configured so that it tries to connect to a previously unconnected server each time 
it sees a new ecm.

- Always keep full client logging enabled when trying to determine the effects of configuration changes.
Usually its a good idea to always have udp logging enabled (with any and all debug options), and send the packages
to whatever desktop machine you're at. Then when troubleshooting is required, all you need to do is start a listener,
for example netcat: nc -l -u -p portnumber
On windows, netcat is available via cygwin (cygwin.com).

* Ecm timeout here refers to the maximum time that the client will wait for a cw response, after sending the ecm request
to the server, before giving up and moving on (typically resulting in a freeze). For example: the "K" value in mg_cfg.


2. Cardserver behaviour (most of the proxy examples assume newcs, but mpcs/oscam or rqcs have also been used).
--------------------------------------------------------------------------------------------------------------

- Ensure that the server does not have any unnecessary limitations imposed on the account used by the proxy.
Rate limits are of course especially harmful and should be removed completely.  

- Avoid configurations where the server attempts to connect back to the client (a newcamd feature). I.e. if the server
allows specifying hostname/port per user account, then make sure you don't. Remove any such elements.

- Ecm cache and emm cache can use reasonable defaults, there is no benefit in modifying them for the sake of the proxy.
Typically the ecm cache would not be used at all, and the emm cache is only there to prevent unecessary time consuming
card communication.

- Priority in the ecm queue is a non-issue when the server only has proxy users, but should be left at default or set
to fifo (or whatever setting causes requests to be processed in the order of arrival).

- Again, keep full logging enabled while troubleshooting. If the server has udp logging, use it to send the log 
events to the same target machine used by the client logging (use a different port though) so you can compare both 
side by side in real time.

- Keep an eye on cpu use on the server, especially if it is running on a small embedded system. If it is maxed out
it is more likely to experience glitches (returning duplicates, timeouts, card communication failures etc). If so it
may be necessary to insert an artifical delay between asynch requests (see default-min-delay in proxy-reference.html).


3. Proxy configuration
----------------------

Always start from scratch with the auto-generated config. Do not add any options until you understand what they do and
you see an actual need from analyzing the logs.

To get a definitive overview of what the proxy does with each config value (and where it does it), you can start java
with the following param: -Dcom.bowman.cardserv.util.tracexmlcfg=true
That will track all access to proxy.xml and when you request it (via the admin section of the status web), dump the
trace to file in the proxy etc dir.

It will produce output such as this:

/cardserv-proxy/connection-manager
        cannot-decode-wait
                type: Time (defSuffix=s)
                default: '0'
                caller: CwsConnectorManager.configUpdated(CwsConnectorManager.java:63)

Which means that the CwsConnectorManager checks the value of the setting <cannot-decode-wait> on line 63 in the method
configUpdated() in the file CwsConnectorManager.java. It also shows (as of 0.9.0) that this is a time value and that
the default is 0 (disabled in this case), as well as specified in seconds unless otherwise indicated.


- Asynchronous vs synchronous newcamd

With synchronous newcamd communication (default) the proxy will only send one request at a time to each connector, and
await a reply to that before proceeding. In theory this is inefficient, since the newcamd protocol supports multiple
concurrent requests. In synchronous mode any and all delays that occur due to network latency spikes (or other glitches)
or simply other users on the server side, will delay the entire proxy queue for the connector.

In the asynchronous case, the proxy sends all pending requests for the connector immediately to the server as fast as it
will read them. The replies are then matched to requests by the newcamd sequence id present in each message, as they
come back.
This effectively moves the queue handling from the proxy to the server, and should mean a higher throughput as there is
less room for network interference. Not all servers support async, and even those that do (e.g later newcs versions) 
seem to sometimes return unexpected sequence ids. Situations where multiple proxies (or other users) use the same newcs
port seem especially error prone.

As a rule of thumb, if you want to get the maximum capacity from your connectors you should try async mode. Watch the
logs closely for issues related to out of sequence data though (unexpected/unknown replies and subsequent timeouts),
or it could end up doing more harm then good.

If a newcamd-connector points to another csproxy, it should use synchronous mode (as the proxy will assume each client 
is a regular user which will have at most one pending ecm request at any given time). The csp-connector type (0.9.0+) is 
async by default.


- The following timing values are significant:

<connection-manager> / <max-cw-wait>
Set this to the same as the client ecm timeout (e.g 9 seconds) or if in doubt, leave it at the default setting.
Changing it affects what the proxy considers to be one cw validity period, and this in turn affects statistics and 
capacity estimates. 
Under normal circumstances, you never want to set this value below the ecm timeout of the clients (it should be
equal or larger). In general however, it depends on which timeout you wish to be reached first, client or proxy.
It may depend on which clients are in use and how they deal with timeouts.

To help figure out the exact cutoff point for clients (the maximum time they can wait before they experience a freeze),
use the LoggingPlugin and the set-test-delay ctrl command (via the status web). This allows you to experiment by adding
a gradually increasing delay for a specific test-client (use the ip filter). Watch the client logs closely and try to
see at which exact ecm round-trip time the freezes start occuring. This is your max-delay for this particular ca-system.
If the time is very short (<3 seconds) it might not be possible to set the max-cw-wait for the proxy to the same, but
at least you know where the cutoff point is. Values <4 have not been tested with the proxy, but may still work.
NOTE: To get accurate values with test-delay, use a service that is cached (that other clients are currently watching).
Also, make sure the client ecm timeout is increased to a level where it will not interfere before the test (>9 secs).
That means you cannot use clients such as CCcam where the newcamd timeout is locked to 4 seconds and cannot be changed.

<connection-manager> / <congestion-limit>
This allows you to configure the point at which the proxy considers a connector to be congested. The value refers
to the estimated connector queue-length (in seconds). By default it is the same as the <max-cw-wait> but its usually
a good idea to set it slightly below, to force service-mapper and metric handling to move to other connectors sooner.
I.e: if max-cw-wait is 5, set congestion limit to 4 (causing any connector with a queue of >8 to be flagged as congested).
Using this mainly makes sense if you have multiple cards with different metric levels and want to fine-tune when the
higher metric cards start getting used.

<cache-handler> / <cache-config> / <max-cache-wait>
If card capacity is a problem, you want to keep this at a level that is as high as possible but still allows room for
one retry before the max-cw-wait is reached.
I.e. if max-cw-wait is 9, and the average response time for your profiles is around 2 secs, set the max-cache-wait to 6
or 7 secs.
This should result in a behaviour where a timeout in the cache means the client can still receive a reply before
reaching its own timeout. Use the transaction flags to determine whether this is actually the case (see below).

Cache timeouts occur when an expected reply never arrives, either because the server that the request was sent to
became disconnected or overloaded, or because a remote cache that indicated it was going to provide the reply failed to
do so (or did but it got lost in transit). It is normal to see more cache timeouts in a proxy setup that uses the
clustered cache.

For most situations, you want a max-cache-wait that is just slightly above the worst case processing time (if its
2500 ms, you might set 3000 ms as the cache wait). This ensures that you only get cache timeouts when actual problems
occur, not because normal processing is taking longer than usual (due to peak load or network latency).
As of 0.9.0, it is possible to configure max-cache-wait with a percentage string, e.g "50%". This allows for different
wait times depending on the max-cw-wait in effect for the profile. If you have multiple profiles with significantly
different max-cw-wait times then you should definately use a percentage setting.


- These are less important but deserve mention:

<connection-manager> / <reconnect-interval>
This only determines how often the proxy will attempt to reconnect to disconnected servers. It can be reduced to
10-15 seconds (but avoid lower values) to minimize downtime due to shaky connections when there are few cards available.
Keep in mind that whenever a connection is lost one immediate retry is performed, so its usually best to leave this
at 30-60 seconds. Setting too low may cause connectors to never reconnect.

<connection-manager> / <default-keepalive-interval>
If there are nat-routers or some equivalent between proxy and server, tcp's that are idle for more than a set time
may end up getting disconnected. Setting this value to slightly below that idle timeout will ensure they stay up. 
E.g: 59 seconds. Note that no keep-alives are sent as long as there is traffic on a server connection, so
there is no danger of flooding it with unnecessary messages.


- Don't touch these unless you're experimenting with some really radical cache-sharing setups:

<connection-manager> / <cannot-decode-wait>
<cache-handler> / <cache-config> / <cw-max-age>


- For very large shares changing these may be required (see proxy-reference.html):

<ca-profiles> / <max-threads>
<ca-profiles> / <sesion-timeout>
<connection-manager> / <default-max-queue> (also available as <max-queue> set per connector)
<connection-manager> / <default-min-delay> (also available as <min-delay> set per connector)


- For proxy troubleshooting, make sure you have debug="true" for all profiles, and logging configured as follows:

  <logging log-ecm="true" log-emm="false" log-zapping="false" hide-ip-addresses="false">
    <log-file>log/cardserv.log</log-file>
    <log-level>FINE</log-level>
    <silent>true</silent>
    <debug>true</debug>
    <warning-threshold bad-flags="YNTSOGXWD-" max-delay="6000"/>
    <event-threshold min-count="1"/>
  </logging>

In general this is a reasonable troubleshooting default, but of course depending on what you're looking for it may need
changing. Under normal operation you want to use level INFO (or WARNING), and it may make sense to only log transactions
exceeding the client ecm timeout limit as warnings. I.e. use something like:
<warning-threshold bad-flags="YTSAQGXWD-" max-delay="8000"/>

For each transaction that meets the warning criteria, a more detailed time log will be made available, showing exactly
what the wait time was spent on. 
- Cache wait: The time the request was locked in the cache, waiting for another transaction forwarding to a card.
- Send queue: Time spent waiting in line to be sent to a card (usually 0 in async mode as everything is sent immediately).
- Server wait: Time spent waiting for the server to reply. For normal (cache miss) transactions this should be longest.
- Client writeback: Time spent sending the reply back to the client. Typically 0-1 ms, unless there is a network issue.


4. Interpreting transaction flags
---------------------------------

Normal traffic flags:         
 F = Normal forward to CWS
 C = Cache hit (local)
 R = Cache hit (received from remote cache)
 I = Instant cache hit (no waiting at all in cache, both request and reply already available)
 1 = This was the first transaction performed by a new session
 Z = SID changed (compared to previous transaction = user zap, can also indicate multiple tuners or users in one session if it occurs every time)

Service mapper flags:
 N = Cannot decode (service mapper says service not on any card, or service blocked)
 P = Service mapper didn't know status for this SID on one or more cards (may have triggered probing if accepted by connectors)
 2 = Triggered broadcast to additional cards besides the selected one (broadcast-missing-sid or redundant-forwarding in use)
 + = Caused an addition to the service map (found service, after probing/successful decode)
 - = Caused a removal from the service map (lost service, after repeated failed decodes)
 
Flags indicating possible problems/recovery situations:
 B = Blocked by exceeded limits or by filters (plugins)
 M = Ca-id mismatch. The ecm reply didn't have the same ca-id as the request, indicates some clients are sending ecms to the wrong ports.
 E = Client got an empty reply (cannot-decode received from CWS, or from the proxy itself with situation N
 Y = Forward retry performed (first chosen CWS disconnected during the transaction, but alternatives exist)
 A = Abort when forwarding (CWS connection was closed before forward, because of other request timeouts or by the network/server)
 T = Timeout when forwarding (no response from CWS within time limit, i.e max-cw-wait)
 O = Timeout while waiting in cache (waited for max-cache-wait, but no reply was reported to the cache)
 Q = Abort while waiting in cache (the forward the cache was waiting for failed, either locally or remotely)
 G = Congestion when forwarding (time was > max-cw-wait/2, but forward still performed)
 X = Cache hit after failed forward (situation <strong>Y</strong>, but reply became available in cache so no need for new forward)
 
Internal/debugging flags:
 S = Timeout in send queue (when trying to forward to connector, should normally not occur)
 W = Triggered cannot-decode-wait (would have been situation <strong>N</strong>, but waiting for remote cache paid off)
 H = Caused an internal failed rechecking of the cache, represents an attempt to recover from an immediately preceeding problem
 U = Interrupt in the no-sid-delay sleep, a request without sid was delayed but disconnected during the delay
 D = The user session disconnected before it could receive the reply (likely reached the client ecm timeout)


Transaction flags are listed in the order in which they were set internally by csp (before 0.8.7 it was alphabetical).
I.e. "OFD" reads as: cache timeout occured (O), so attempted forward to card (which succeeded - F), but when the
reply from the forward was returned the client had already disconnected (D).
Note that many flags can occur at virtually the same time, so the order isn't necessarily chronological in all cases.

Examples of common transactions (with the flags ordered as of before 0.8.7):

"FPXZ" - User zapped (Z) to a service that the service mapper lacked knowledge for on one or more cards, so 
probes were sent to multiple cards (P). Not knowing where to find the service, the proxy forwarded to the least loaded
card in the profile (F). This card failed to provide a reply, but after the failure a reply was found in the cache (X).
I.e. one of the other probes did get a successful reply.

"+FPZ" - Same as above, except the least loaded first choice was able to provide a reply, and the service mapper used
this information to update the mapped status for this card (+).

"FO" - A cache timeout occured (O, remote cache update may have failed to arrive in time. There was time left before
max-cw-wait, so a forward was performed (and suceeded, F).

"CIZ" - User zapped (Z) to a service already present in cache (C), and immediately received a reply with no waiting (I).
"IRZ" - Same except the cache entry came from a remote cache.

"FOZ" - The first transaction for a new client connection (Z) resulted in a cache timeout (O) followed by a forward (F).
Normal if it occurs mainly in connection with proxy restarts (local or remote), before all cards have been connected.

"ENZ" - User zapped (Z) to a blocked service causing the proxy to immediately reply with an empty cannot-decode (EN).

"+NPZ" - Same as above, except the service wasn't blocked, only marked as non-existant on all cards by the service
mapper. This one request meant the auto-reset-threshold was exceeded, so probes were sent (P) and the first one got
a successful reply, causing the mapper to change the status on that card from cannot decode to can decode (+).

"-EFZ" - User zapped (Z) to a channel believed to exist on a card, but when it was forwarded the server replied with a
cannot decode (EF) and this was the 2nd such failure for this card, so the service mapper removed the channel (-).

"EFT" - A forward (F) timed out waiting for the reply to be returned from the server (T), and there was no time left
for retries so an empty (cannot decode, E) reply was sent back to the client. 

This scenario can be fairly common and would show something like this in the standard log, for a situation where the 
reply was just a little too late:

WARNING -> NewcamdCws[cwsname:profile] <- Timeout waiting for ecm reply, discarding request and returning empty 
  (1 failures) - ECM B9328A35 - [16] - Waited: 8241ms
                                 ^^ sequence id
INFO -> NewcamdCws[cwsname:profile] <- Message received, cancelling timeout-state

WARNING -> NewcamdCws[cwsname:profile] <- No request found for reply: 16 (DCW, ServiceName profile:sid)     
                                                                      ^^ sequence id
I.e. when the reply is finally received, the request is already deleted and the client has been given a failure reply.


5. Linux/OS-tweaking
--------------------

As of 0.8.11 the proxy will use sun java6 features to keep track of used and available unix file descriptors (file
handles). This is a common source of problems, as each tcp socket uses at least one and the default maximum limit per
user is commonly 1024. If exceeded there would be java exception stacktraces in the logs with messages such as
"too many open files".

You can check this by logging in as the user who will be running the proxy and doing:
  $ ulimit -n
  1024

To increase the limit you typically have to edit /etc/security/limits.conf and add lines like these:
  proxyuser        soft    nofile           8192
  proxyuser        hard    nofile           8192

After a reboot ulimit -n output should show 8192 when logging in as proxyuser. If running sun java6, the currently used 
and available filedescriptors will be visible in the status web (as part of the proxy-status xml api command) shown as:
  FD: [used/maximum]

If not using sun java6, you can still get some idea how many handles are used by the proxy using lsof for the proxy pid.
For example:
  $ /usr/sbin/lsof -p `cat cardservproxy.pid` | wc -l
  181

Note that this value will tend to be higher by a fixed number than the one reported by the sun jvm, presumably because
lsof also lists some file handles that don't count against the per-user quota. 
I.e. when lsof shows 181, FD would show 141 (the actual number that will cause problems when it reaches the limit).

Depending on OS and JVM, there might also be other limits hit (such as noproc, number of processes/threads).


6. JVM tweaking
---------------

As mentioned elsewhere the proxy only works properly with real sun java (and was only tested under 1.4.2 and 1.6).
To verify that you're using the correct one, check the status web. It should mention HotSpot if it is sun, i.e:
[Java HotSpot(TM) Server VM1.6.0_03]
Server or client doesn't matter much, and neither does 64/32 bit (although 64 will tend to use significantly more heap).
If it turns out to be any other jvm implementation you're likely to run into subtle and sporadic problems.
If you've found another that does seem to run the proxy well enough under load, and you can prove it: let me know.
As of 0.9.0, a super user can click on the jvm name in the status web to trigger the status command 'system-properties',
which shows all the jvm information as xml.

The Heap usage displayed on the status web shows the amount of memory currently in use (within this jvm instance, it
says nothing about the rest of the system) and the total allocated by the jvm so far, i.e:
[12503k/57536k] means ~12 megs in use and ~57 peak allocation. It does not imply that 57 is the maximum available to
the jvm, once it is reached it will probably be able to allocate more.
If it is unable to do so you'll start to get OutOfMemoryError in your logs (hopefully, it may just fail silently).
The maximum heap the jvm is allowed to allocate by default depend on the jvm version and os.
To increase it, add the following parameter to the line that starts java (in cardproxy.sh if you use that script):
-Xmx256m
That would allow the heap to grow up to 256 megs, which should be more than enough for most use cases.
If you know how much your setup usually ends up using, you can give the jvm that much to start with, by also adding:
-Xms100m (for example, this will allocate 100 megs immediately on startup).

The next value 'TC' is for thread count. This will normally be a fixed number around 15, + 2 for every connector, + 1
for every listen port, + 1 for every client session. Use it to spot problems. As long as none of the above changes,
the thread count shouldn't either. If it does then something is going on that you should look into.
As of 0.9.0, clicking on the count will show a list of the actual threads. All threads in the proxy core use very
descriptive names so it should be immediately apparent what each one does.

