Cardservproxy 0.9.0 additions/changes
-------------------------------------

The 0.9.0 release introduces a number of changes that require attention if attempting an upgrade.

Some guidelines:

- Don't attempt to use an existing config. Start from scratch (with no proxy.xml) and use the generated config template.
  Allow the default plugins to remain unless you find good reason to do otherwise.

- Since the max connections limit is now per profile and not global:
  If you had max-connections specified per user before, remove all these attributes and re-add them only as needed.
  The default max-connections value users are given when connecting to a profile depends on the number of ports you add.

- Don't assume you need to manually specify details like card-data for newcamd ports anymore, try everything with the
  simplest possible config first and if it doesn't work - use the LoggingPlugin to figure out whats going on and only
  add manual config where needed. If you specify provider-idents for a profile there should no longer be any need to
  do that in card-data or as filter strings for service file parsing.
  Avoid all manual allow/block/can-decode/cannot-decode lists until you're absolutely sure they're required (and you
  understand why).

- Services file parsing has been modified for all file types, check proxy-reference.html for details. The network-id
  you set for the profile is now taken into account in most situations, unless overriden by a set of filter strings.
  NOTE: The "provider" attribute has been renamed to "filter". Also the strings are now comma-separated, to allow for
  names with spaces. There is no need to filter on provider idents when these are specified for the profile.

- As the proxy now makes use of newcamd extensions like including ca-id and provider-ident in the ecm requests, it will
  attempt to validate all newcamd headers and block those clients that put other unwanted information in these fields.
  Clients that do this will have all their requests blocked (flags BE). This includes the original newcamd client.
  To turn off the validation and handle these clients as in earlier proxy versions, use the no-validation element:
  <newcamd listen-port="11234"><no-validation>true</no-validation></newcamd>
  NOTE: Don't turn off validation for all ports, rather create separate alternate ports specifically for legacy clients.

- To prevent bug reports and confusion from users not realising which jvm they're using, the proxy will no longer start
  with non-sun jvms. If you're absolutely sure, you can override this by adding the following to the java cmd line:
  -Dcom.bowman.cardserv.allowanyjvm=true


Extended service mapping (provider idents and custom-ids/chids optionally included in mapping):
-------------------------
Provider idents may now be significant in the service mapping (sid is no longer the only factor). By default the
proxy will assume that any profile that has multiple idents associated with it (either because it contains cards that
have ident lists other than 00 00 00, or because the idents have been manually specified using the provider idents
attribute) actually needs the idents taken into account when forwarding to card. It will automatically set the flag
"require-provider-match" to true for such profiles.
If you're using a system that has multiple providers on the cards, but they are not significant for decode success,
you will have to manually set require-provider-match="false" for all profiles with that system.

A profile that requires provider match will result in service mapping that includes both sid and ident in every entry.
I.e mappings will be for sid:ident (e.g 0000:000000) instead of just sid. Most newcamd clients do not specify the
provider ident in the ecm requests, but some ca-systems include the ident in the ecm payload. 0.9.0 contains a plugin
capable of extracting this information for viaccess and seca.
For other systems, it is possible to end up with multiple mappings for the same service (one with sid alone and one
or more sid:ident combinations) since some clients include ident and some don't. Those that don't will likely have
a higher failure rate.

In addition to provider idents, a custom field exists to handle system-specific artefacts like the irdeto-chid.
It works in the same way (included in the mappings) and can co-exist with provider ident should a system appear where
both are signficant. This would result in mappings like sid:customid:ident or sid:ident:customid.
The proxy doesn't pick up on any custom-ids by default, plugins are needed to find and identify such info (and one is
included that does this for irdeto).

To see the maps in detail, access /xmlHandler?command=export-services in the status-web.


Multi-context ports and connectors:
-----------------------------------
0.9.0 introduces connectors and ports that combine multiple profiles (multiple different traffic types) within one
context. There are two examples so far:

1. The proprietary csp-connect protocol, that uses the builtin httpd to accept incoming connections and csp-connectors to
   define outgoing ones.
2. Mgcamd/newcs newcamd protocol extensions, with an extended-newcamd port for incoming and chameleon-connectors for
   for outgoing.

These are not bound to any profile, rather they're listed under the new "*" context (implying multiple or all profiles).
With these protocols it is possible to figure out where a given ecm request belongs based on the contents of each
message (so no - or at least less - information has to be derived from the context/port that the message was received on).

For the mgcamd/newcs extensions every message must contain both caid and provider ident, and if there is a profile
defined that has the matching caid/ident the message will be handled within that profile.
If there are multiple matching profiles, it becomes necessary to configure which ones should be considered for matching.
In case of the extended-newcamd port, this is achieved by using the exclude-profiles element (listing the names of those
profiles that should not be matched). So a limitation of the mgcamd/newcs extensions is that it doesn't work
if there are multiple cards involved that have the exact same caid+ident (but incompatible traffic/different vendors).
NOTE: To specify a list of profiles for the chameleon-connectors the config is inverted, so you use the profiles
element to select which profiles should be matched (there is no exclude-profiles here).

For csp-connect, network-id is added to be able to deal even with cases where multiple identical systems co-exist.
Hence there is no need for an exclude-profiles list for the csp-connect port (which is the status web httpd port).
However, you may still want to exclude some profiles when making an outgoing connection (i.e you're only interested
in obtaining a subset of what is available on the remote proxy). For this reason the csp-connector has an exclude-
profiles list where you can set the unwanted ones.
NOTE: When making a csp-connection, the remote profiles are not shown by name (as names are local). The protocol
uses network-id combined with ca-id, and only if a local profile with the same combination exists will a match be
made. Remotely available profiles with no local equivalent end up under "unmapped data" in the status web.
The exclude-profiles lists allows you to force a network-id + ca-id combination into the unmapped section even when
such a profile exists locally (by listing the name of the local profile).

It is possible to specify manual can-decode-services/cannot-decode-services lists even for csp/chameleon connectors,
but as they have no profile association you have to indicate which profile the lists are for, i.e:
<cannot-decode-services profile="name">00ab 00cf 0123</cannot-decode-services>
<cannot-decode-services profile="other">0dce feb1 0a23 0523</cannot-decode-services>

- When defining a csp-connector to another proxy that has a redundant setup (2 mirrored proxies), you can use the same
  config for both by adding the url-backup element to point to the secondary proxy node:

    <csp-connector name="othercluster" enabled="true">
      <url>https://primary.otherproxy.com:9443</url>
      <url-backup>https://backup.otherproxy.com:9443</url-backup>
      ...

  This actually creates 2 connectors, othercluster and othercluster-backup (no difference from defining both manually).
  
  NOTE: If receiving incoming csp-connections from a redudant setup, you still need separate users for each node in the
  remote cluster. Otherwise the ip-check will prevent all but one from connecting.


AU-users:
---------

Previous versions allowed the same user to be placed in the au-users list for several newcamd connectors even when
they were in the same profile, but as the client can only see one card per connection such configs would eventually
fail (only one of the connectors would receive the correct emms). In 0.9.0 this now causes a ConfigException.

Additionally, the status web will now show which connector emms are forwarded to for each au-user session, in the
sessions view (emm column). Changing the au-users lists for connectors will now trigger automatic kicks of any
existing sessions by the affected users, forcing the clients to reconnect and see the altered (no longer anonymous)
card-data.

NOTE: It is still possible that a client can get confused about which emms have been forwarded already, so to make sure
changes to au-users lists take effect and no messages are missed, it may still be necessary to restart the client.

