ProviderIdentPlugin
-------------------

Extracts provider ident from the ecm payload to help proxy card selection in cases where the client doesn't specify provider.


NOTE:
- Automatically processes all messages with caid 0500 (viaccess) and 0100 (seca) if loaded.
- Requires CSP 0.9.0 or newer.

TODO/SUGGESTIONS:
- Handle other systems where ident matters and is extractable from the ecm payload?

Example config:
---------------
  <plugin class="com.bowman.cardserv.ProviderIdentPlugin" enabled="true" jar-file="provideridentplugin.jar" />
  
Status commands:
----------------
- No commands

Usage example
-------------
- No commands
                             
