ClusteredCache (com.bowman.cardserv.ClusteredCache)
---------------------------------------------------

Cache-sharing with the csproxy is a powerful tool that can be used in a number of different ways. Before contemplating
the various possible scenarios, the basic cache model needs to be understood.

Whenever a proxy node receives a new ecm request from a client, it will query the cache to see if this is the first
time this particular request has been received. One of three things can happen:

1. The ecm has been received before, and a cw reply has already been fetched from a card. This causes an instant hit.
2. The ecm has been received before, but the card transaction is still ongoing and the reply not yet available.
   This causes the request to be held in the cache for a maximum of 'max-cache-wait' seconds. As soon as a reply is
   available all threads waiting for it will be released with their own copy of the cw.
3. The ecm has not been seen before, no card transaction is pending for it. This will place the ecm in a list of pending
   requests, and it will be up to the proxy service mapper and connection manager to find a card to handle it.

The cache knows nothing about which ca-system the ecm belongs to, what sid it is for, or who is asking for it. It will
simply check its lists of already handled ecm -> cw pairs, and the list of pending ecm requests and see which (if any)
contains the new ecm. If two different profiles, or multiple services within one profile, happen to use the same ecm's
the cache will score hits. The chances of a false positive are insignificant enough to be safely ignored.

When using remote cache-sharing, all of the above remains pretty much the same. However, whenever something is added
to the list of pending requests or requests with cw replies, the cache will broadcast the new addition to the remote
proxy (or proxies) that it has been configured to talk to. This broadcast can be done in several ways, but all involve
udp communication and standard java object serialization.

Note that in any cache cluster, there can still be occasions when two or more proxies will attempt to query a card for
the same ecm. This will occur when the ecms arrive at both proxies at exactly the same time (which is of course often
the case). Depending on the roundtrip ping time between the proxies, this can be enough to reduce the effectiveness
of the cache significantly. Typically though, you have multiple proxies for the sake of redundancy, you probably want
the same ecm processed in several places so that if one proxy fails to produce a reply in time, another may succeed.

That said, it is possible to achieve a strict synchronization between cache-instances by using the arbitration feature,
which introduces a negotiation procedure for each ecm, to determine which proxy is best suited to handle it (and then
only that proxy will proceed with forwarding, the others will wait). See sync-period below.

NOTE: As of 0.8.13 the ClusteredCache no longer uses default java object serialization for the transport protocol.
The new custom protocol is briefly documented in ClusteredCache.java (it should be about 20-40 times more efficient).

------------------------------------------------------

The following are settings are available, see proxy.xml for separate examples:

<cache-handler class="com.bowman.cardserv.ClusteredCache">

- To use the ClusteredCache as the cache-handler for the proxy, use the class name: com.bowman.cardserv.ClusteredCache
Changing cache-handlers requires a proxy restart.

<cache-config>
  <cw-max-age>19</cw-max-age>
  <max-cache-wait>7</max-cache-wait>
      
- Inherited from DefaultCache (see proxy-reference.html).

  <remote-host>peer.proxy.host.com</remote-host>
  <remote-port>54278</remote-port>

- One way of specifying where to send cache-updates, when there is only a single target proxy.

  <multicast-group>230.2.3.2</multicast-group>
  <multicast-ttl>2</multicast-ttl>
  <remote-port>54278</remote-port>

- Another way to configure targets for cache-updates. Multicast typically only works in a LAN environment.

  <tracker-url>http://cstracker.host.com/list.enc</tracker-url>
  <tracker-key>secretkey</tracker-key>
  <tracker-update>10</tracker-update> <!-- minutes -->

- A third (and perhaps the best) option for configuring multiple targets for cache-updates. The ClusteredCache will
fetch a plain text list of hostnames and portnumbers, and send updates to every entry in the list. The list can be
automatically fetched at regular intervals (or if tracker-update is 0, only when proxy.xml is modified/touched).
This approach allows proxies to be added to a cluster without having to modify the configuration of already existing nodes.
The list must have the following format (# are comments):

# ClusteredCache list file. Syntax: hostname_or_ip:udp_port_nr
proxy1.host.com:54278
proxy2.host.com:54278
192.168.0.3:54275

The list can be stored anywhere, as long as it can be accessed via url (e.g file://, http, https, ftp). 
Optionally, the list can also be blowfish encrypted using the included tool fishenc.jar (found in lib, java -jar fishenc.jar).
If encrypted, tracker-key must be correctly set.
 
  <local-port>54278</local-port>  
  
- The UDP port where this ClusteredCache instance will listen for incoming updates.  
     
  <local-host>csproxy3.host.com</local-host>
  
- The external hostname or IP of this ClusteredCache. This is only needed when using the above tracker setup. The name
should match the one in the tracker list, so the cache instance can identify itself in the list and avoid sending itself
updates.

  <debug>true</debug>

- Set debug to true to enable additional cache information in the status-web. This can impact performance, use with care.

  <hide-names>false</hide-names>

- If configured to send to one or more remote caches, this controls whether the names of the connectors are censored in
the outgoing cache updates (will appear as remote: unknown to the target). Only makes sense when dealing with untrusted
proxy peers.

  <sync-period>0</sync-period> <!-- milliseconds -->
  
- Set this > 0 to enable the strict arbitration procedure. For example if you set it to 200, the ClusteredCache
would use 200 ms for every new (previously unseen ecm) to wait and synchronize with as many other proxies in the cluster
as possible, and determine who is best suited to handle it. Only this proxy would proceed with a forward to a card.
This adds 200 ms to every single transaction, but should ensure that a cluster of proxies will only ask one card in one
proxy, once, for the same ecm. Probably only usable in a cluster where all nodes are fully trusted, and where the
network is reliable with fairly fixed ping times and no congestion.

</cache-config>

------------------------------------------------------

NOTE: It is possible to set up the ClusteredCache without any remote targets (receive-only-mode). If no remote-host/port
is set using any of the various methods then the default behaviour will be to not attempt any sending of updates.
This is useful when creating a cache-only proxy node, receiving updates from multiple other proxies but sending to none.
It can also be used to augment local cards with additional services (which will only be available through the cache).

With 0.9.0, ClusteredCache in receive-only mode is the default in the auto-generated config template. If nothing is
received, it behaves exactly like the DefaultCache.
