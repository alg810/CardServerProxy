Cardservproxy 0.9.1
-------------------

First, in order to avoid the usual confusion about the proxy:
- This is not a cam emulator or card server, it knows next to nothing about ca systems, DVB concepts, iso7816 or the md api.
- It's 100% memory-hogging-java, intended to run on stable servers, not on stb's or desktop pcs (although of course this
  may still be possible).
- It's not for everyone, it could be considered an SDK for building custom sharing solutions or integrating sharing into
  existing communities. If you don't have any experience writing code, the proxy probably isn't for you.
- Yes it's controversial. Used properly a cluster of 2 proxies could handle several thousand clients with a handful of cards,
  bandwidth being the only real limit. NOTE: This assumes complete understanding of all aspects of the newcamd protocol,
  as well as any quirks introduced by the ca-systems you're trying to proxy.
- There are no commercial or "full" versions of the proxy by this author (and there never will be). This is the only one.
  3rd party branches are encouraged, as long as they are free and full source is made available.

Cardservproxy is a scalable proxy primarily for the newcamd protocol, with load balancing and cluster handling built in.
It will keep track of 2 or more cardservers (typically newcs) and accept incoming connections from clients (any client
that support the newcamd or radegast protocols). It removes the need for complex clients or servers, by centralizing all
of the complexity.
It is primarily intended for cable/terrestrial dvb setups, or those where each client only ever connects to one
profile (or at most two). For scenarios where users need to connect to more than two systems, you should probably
look elsewhere for something that does not involve the newcamd protocol.

Features/benefits:
- The proxy hides the servers from the clients. New servers can be added/removed on the fly without affecting traffic
  or having to change the clients configurations.
- The proxy hides the clients from the servers, the servers will only ever have a single very busy user (the proxy)
  from a fixed ip.
- User management is centralized in the proxy, the servers will not have to be updated to add new users. The proxy user
  manager is pluggable and can be connected to any external user database with a minimum of coding.
- Connected servers don't have to have identical card subscriptions, the proxy can keep track of which services will
  decode on which card, and route requests accordingly (assuming clients accurately set the SID in their requests).
- The proxy will use fairly sophisticated load balancing to make sure requests are routed to the least loaded card that
  has the service in question. It will monitor the utilization for each card and give a clear indication when there are
  bottlenecks or excess capacity.
- Multiple providers with different ca-systems can be hosted by the same proxy, by defining separate profiles. Each
  profile is assigned its own listener port(s) and any number of card servers (exactly which ca-systems are used or
  whether it is dvb-s/c/t doesn't matter).
- Caching is centralized to the proxy (ideally the caches of the individual cardservers will never score a hit). This
  means that as long as someone in the same proxy or proxy cluster is watching a service, an infinite number of others
  can watch the same service without causing any extra traffic towards the card servers. Once there are enough cards
  connected to always keep all the providers services in cache at any given moment, the number of users becomes limited
  only by bandwidth.
- The proxy is prepared for integration into existing communities (irc bots, web forums, torrent trackers) and provides
  an example webgui on top of an ajax-friendly generic http/xml query interface (faux-RESTful).
- Multiple proxies can be clustered together by real time cache-sharing. If user a is watching a service on proxy1,
  then user b can watch the same service on proxy2 without causing any requests towards a card server.
- The proxy can be used as a protocol analyzer or general troubleshooting tool, since it decrypts/encrypts newcamd
  and allows user created plugins to be added.
- Nearly all changes to configuration can be performed without restarts, the proxy monitors the config file proxy.xml
  and most other resources.
- As of 0.8.10, new user created connector implementations (protocols) can be added in the same manner as plugins.
- As of 0.9.0, multi-context connectors and sessions are supported, primarily the mgcamd-specific newcamd extensions.
  This makes it feasible to use the proxy for satellite setups where users typically access many profiles at the same
  time (but in general it is still a bad idea to use the proxy for situations like that).
- Full java source is available.

The creator accepts no responsibility or liability for any breach of provider contracts or local laws resulting from the use of this code. :)

The proxy prefers a sun JRE (1.4.2 or later) but others like JamVM _may_ work. If you're interested in getting the
proxy to run properly under gcj/gij or any other non-sun jre, contact me on efnet (be prepared to make source changes).
As of 0.9.0 the proxy will not start with non-sun jvms, unless you explicit force it to by adding the cmd line argument:
-Dcom.bowman.cardserv.allowanyjvm=true

To get started read the example configs and the proxy-reference.html, + the changelog and cardproxy.sh start script.

INSTALL NOTES:

- Mainly tested on sun jre 1.4.2 and 1.5 (but the latest java6 release should of course also work fine). 
  No installation is necessary, get the self extracting dist and unpack wherever.
  Both are available here: http://java.sun.com/javase/downloads/previous.jsp (sun previous releases)
  Which one works best will depend on your environment. Later versions will probably work as well.

- The cardproxy.sh start script is just an example, you will likely need to edit it. If you have multiple jre's or jdk's
  installed then put the full path to the one you intend to use in the line that starts java. If it fails for no
  apparent reason then check the log/cardserv-sysout.log where all stdout and stderr output should end up.

- To start without the script (on any OS): cd <csproxy dir>; java -jar lib/cardservproxy.jar

- If you start the proxy with no proxy.xml configuration file in the config dir, a skeleton config will be generated.

- When running under windows, use the prepared java service wrapper setup (jsw-win32.zip) to install the
  proxy as a proper nt service (kept running regardless of user login/logout and started automatically at boot).
  Make sure all the jsw files are in a dir called jsw in the proxy home dir, and set silent="true" for the logging
  in proxy.xml or you'll have duplicate logs. Also make sure you can successfully start the proxy from cmdline before
  you try it as a service.
  NOTE: Do not try to run the jsw wrapper.exe manually, it will only work when started by the installed service entry.
  NOTE: It is of course also possible to start the proxy as a normal java app, without the script (see above)
  or even with the script, using cygwin bash: http://www.cygwin.com


CONFIGURATION NOTES: (checklist for a basic setup)

- Define ca-profiles, one for each provider/vendor/card-type (yes if two providers happen to use the same ca-system, two
  separate profiles are still required). Ca-profiles can be thought of as virtual cardservers, and will seem to the
  clients like single cards (with a potentially infinite capacity). If you have an enigma1 services file (dreambox)
  you can fetch that and place it where the proxy can read it and point it out in the profile definition. This will
  get you friendly readable names for each service rather than just service id. Multiple profiles can read from the
  same services file if you specify a provider string as a filter (see proxy-reference.html).

- Define newcamd/radegast listen-ports as needed for each profile (always use newcamd if you have a choice). Usually
  only one port is required per profile (or two if you need both newcamd and radegast listeners), but it is now
  possible to have an arbitrary number of listen ports for the same profile, complete with their own accept/deny lists
  and other protocol-specific settings.

- Define cws-connectors for each cardserver that the proxy should connect to. Use one newcamd-connector or
  radegast-connector for each card, and again newcamd is prefered if you have the option (and there would be no point
  in connecting to the same card twice with different protocols, don't try it).
  Specify which profile each connector belongs to. If the server doesn't always contain the same card then you can omit
  the profile, but then you need to be sure that ca-id is correctly specified in each ca-profile. If you have
  multiple proxies accessing the same cardserver (or other clients on the side connecting directly to a cardserver)
  make sure they all use different accounts in the server.

- Define user accounts that the clients can use to access the proxy profiles. Alternatively, plug in your own user-
  manager that makes use of an existing database. As of 0.9.0, max-connections is per profile, so usually this doesn't
  need to be configured (1 per profile is enough).

- Check all general options in proxy.xml against the proxy-reference.html docs. The defaults in the provided example
  configs are not necessarily reasonable, you will eventually have to understand what most of the settings mean.
  As of 0.8.1 you can start the proxy without proxy.xml, and a skeleton example will be generated for you (in ./config).

- Set the log level to info or fine, switch debug on, along with log-ecm and log-emm. Watch the logs as you start the
  proxy and make sure it is able to connect properly to all defined servers, then see what happens when clients connect
  to the ca-profiles. Once you get everything working smoothly, you can switch off debug and use info or warning level.

---

Some important caveats for the automatic SERVICE MAPPING:

- Make sure the newcs user for the proxy doesn't have any rate limit set, since this would be immediately exceeded and
  result in cannot decode replies which would screw up the service mapping. Avoid connecting to the same account with
  multiple proxies or proxies + other clients, use one account for each connection.

- There is a very limited tolerance for bad ecms. If a card connector fails to decode 2 ecms in a row for service X
  (for whatever reason) the proxy will assume service X is no longer available on that card and stop using it for that.
  If one client sends bad ecms that result in cannot decode replies when this shouldn't happen, the service
  mapper may remove services from card connectors incorrectly (which affects all users).
  For users with erratic clients that cause problems like these, try setting map-exclude="true" and the proxy will not
  draw any mapping conclusions based on this user's ecms. Likewise if you have any untrusted users, you probably want
  them excluded from the mapping to avoid deliberate sabotage.
  NOTE: There is no point in setting map-exclude for ALL users, that would amount to disabling service mapping entirely.

- If you're having problems with bad ecms or other artefacts causing the service map to incorrectly remove services
  from card connectors, try auto-reset-threshold="1" for the affected profile. This will cause lost services to be
  found again more or less immediately, but will generate a lot of unnecessary probing if clients try to watch services
  that really can't be decoded on any card. Using the retry-lost-services setting for all profiles with mapping enabled
  is recommended. Use block-services to list all services known not to decode anywhere, to reduce unecessary probing.

- The proxy has no way to determine whether the contents of an ecm is valid or appropriate for the profile. It will
  assume that everything received on a given profile port is intended for the provider/vendor of that profile.
  It's essential that profiles are set up correctly and all clients know which port to use, since if card connectors or
  clients from multiple providers are mixed within one profile the service mapping will become seriously confused or
  fail altogether.
  If you see services appearing and disappearing at random from the card connectors then this is likely your problem.
  As of 0.8.11 there is a transaction flag 'M' that will be set if cache hits indicate that clients are sending ecms
  to the wrong profile port. In 0.8.13 such 'M' transactions will be blocked by default, to avoid misleading clients.

- Clients that do not (or cannot) specify correct serviceid in each request, will only work well in profiles where all
  cards are identical (can decode the same services) or where there is only one card. Future versions may improve on
  this. If you have hw clients like alex-cs, cardlink.nl or lce that cannot know the sid, you probably want to create a
  separate profile for them, and include only cards with (roughly) the same sids available.
  As of 0.8.12 there is a broadcast-missing-sid setting for the mapper section in the config, if this is true then every
  ecm request without sid will be broadcasted to all connectors in the profile. The client will still only get the reply
  from the connector chosen by the load-balancing, but in case that choice was wrong and the client does a retry, then
  the CW should be available in the cache.
  As of 0.9.0, sending the same ecm (without sid) multiple times to the same connector is avoided by a timed blacklist.

- If you face recurring issues with the automatic service mapping, manually specifying service lists for each connector
  is possible. This is achieved by using elements can-decode-sids and cannot-decode-sids per connector config (see proxy-
  reference.html). A better long term solution is to track down the cause of the auto mapping problems.

- 0.9.0 includes other criteria besides sid in the service mappings. If clients send provider-ident (regular newcamd
  does not) and the profile is set to require-provider-match="true", mappings will be made using sid + ident.
  For some systems where ident is included in the ecm payload, a ProviderIdentPlugin is provided to extract and make
  use of this when clients don't set anything in the newcamd header. There is also a totally arbitrary custom-id that
  can be included in mappings, if a specific ca-system has additional complicating factors. One such example is the
  irdeto chid (extracted from the ecm payload by the IrdetoPlugin, also included).

- 0.9.0 adds a status-command for troubleshooting the maps and assisting in creation of manual sid lists if it turns out
  to be needed (but figuring out whats confusing the auto-discovery and reporting it is usually a better course of action).
  Access /xmlHandler?command=export-services to get the current state of the maps. Append &format=hex to get plain lists
  of hex tokens that can be pasted into the config.
