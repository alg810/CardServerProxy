CacheCoveragePlugin
-------------------

This plugin attempts to aid in achieving 100% cache coverage for selected profiles. It monitors the cache contents and
analyzes cw continuity for each service (this can't currently be used with systems that always zero out one cw).
Cache contributions from both local client sessions and remote ClusteredCache nodes are tracked per service.

It can detect (by looking at the cw overlaps) when there a multiple ecm sources for the same service. This typically
occurs when the same exact service is available on different transponders, or different terrestrial transmitters
separated geographically.
Average variance from the expected cw interval is measured (typically amounts to the average ecm processing time), and
continuity errors (gaps) are counted.

Data is collected for all encountered cache contexts (networkid-caid combinations) even if there is no corresponding local
profile, allowing any ClusteredCache setup to be analyzed. Backlogs of all cw's are kept up to 1 minute and can be
viewed manually for each service entry.
Additionally, the time offset for each cw is measured (counted from the first encountered cw in the validity period),
allowing the cw change distribution over time within one period to be estimated. I.e showing which services have cw
switchovers at the same time.

NOTE:
- Adds a 'Cache' page to the status web (admin users only). Mouseover column titles for explanations.
- Requires CSP 0.9.1RC r192 or newer.

- Default cw validity is assumed at 10 secs, for profiles/contexts that have something else manual config is required.

- Continuity error counter is reset if a gap occurs that is > 1 minute. 0 errors can only be expected if there are
  clients in the share permanently parked on the service (or dedicated cache feeder clients).

- Only onid/caid/sid is part of the cache metadata, details like tid are assumed based on loaded service files.
  In some situations info from service files cannot be correct (e.g regionally differing transponder ids in cable or
  terrestrial networks).

- If nodes in the cluster have differing onid/profile setups, cws will end up randomly distributed among them and no
  continuity detection will be possible. Recorded remote profiles will remain forever, so any cluster where nodes do not
  agree on profiles (or where one node has profiles with onid 0000) will cause the plugin to leak memory.

...


TODO/SUGGESTIONS:
- This is unfinished and experimental.

Example config:
---------------
  <plugin class="com.bowman.cardserv.CacheCoveragePlugin" enabled="true" jar-file="cachecoverageplugin.jar">
    <plugin-config>
      <analyze-overwrites>false</analyze-overwrites> <!-- keep all contested cws in memory for manual inspection (will leak over time) -->
      <cache-context network-id="22f1" ca-id="0500" interval="20"/> <!-- context/profile has expected cw interval different from 10 secs-->
    </plugin-config>
  </plugin>
  
Status commands:
----------------
- cache-contents: Show aggregated cache stats for all known contexts (services in cache, per context).
    optional parameters: hide-expired (true/false, exclude service entries that are older than their context cw validity)
                         show-missing (true/false, include service entries parsed from e1/e2 files, that aren't in cache)
                         source-filter (only display entries which have this reported source address string)
                         exclude-keys (exclude contents of selected cache contexts, comma separated list of keys)

- cache-sources: Show stats per cache data source address.
    optional parameters: hide-local (true/false, exclude local sources).

- service-backlog: Show transaction backlog and current sources for selected service (last 60s).

- transponder-list: Show services per transponder for locally known profiles (as parsed from services file).
    mandatory parameters: profile (name of local profile)
    optional parameters: tid (transponder id)

Usage example
-------------
http://proxy.host.com/xmlHandler?command=cache-contents&hide-expired=false&show-missing=true
                             
