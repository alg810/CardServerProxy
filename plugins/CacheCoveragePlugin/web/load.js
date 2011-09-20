
// make a new xsltransformer for this plugin
var xsltTrCcp = new BowWeb.XsltTransformer("/plugin/cachecoverageplugin/open/xslt/cws-status-resp.xsl", postProcess);

var hideExpiredEntries = true, hideLocalSources = true, showMissingEntries = false, sidInHex = true, tidInHex = true;
var sourceFilter = '';
var excludedKeys = {};

//add the postProcess function to cs-status.js
pluginsPostProcess.push("cacheCoveragePluginPostProcess()");

//add the logout function to cs-status.js
pluginsLogout.push("cacheCoveragePluginLogout()");

// add maintenance section
sections['cache'] = {
  label: 'Cache',
  queries: ['proxy-status', 'cache-status', 'cache-contents hide-expired="true"', 'cache-sources hide-local="true"'],
  repeat: true,
  handler: function(xml) {
    if(hideExpiredEntries) getFirstByTag('cache-contents', xml).setAttribute('hide-expired', 'true');
    if(showMissingEntries) getFirstByTag('cache-contents', xml).setAttribute('show-missing', 'true');
    if(hideLocalSources) getFirstByTag('cache-sources', xml).setAttribute('hide-local', 'true');
    if(sourceFilter != '') getFirstByTag('cache-contents', xml).setAttribute('source-filter', sourceFilter);

    var services = xml.getElementsByTagName('service');
    for(var i = 0; i < services.length; i++) { // convert dec to hex for display, too messy for xslt
      if(sidInHex)
        if(services[i].getAttribute('id') != 'N/A') services[i].setAttribute('id', '0x' + (new Number(services[i].getAttribute('id')).toString(16)));
      if(tidInHex)
        if(services[i].getAttribute('tid')) services[i].setAttribute('tid', '0x' + (new Number(services[i].getAttribute('tid')).toString(16)));
    }

    var contexts = xml.getElementsByTagName('cache-context');
    for(i = 0; i < contexts.length; i++) {
      if(excludedKeys[contexts[i].getAttribute('key')]) {
        contexts[i].setAttribute("excluded", 'true');
      }
    }

    var cache = getFirstByTag('cache-status', xml);
    cache.setAttribute('display', BowWeb.getLayerVis('toggle-' + cache.getAttribute('type'), 'none'));

    xsltTrCcp.transform(xml);

    // attach visibility handlers to links
    var anchors = getAllByTag('a');
    for(i = 0; i < anchors.length; i++) {
      if(anchors[i].id == 'openhref') {
        var layerId = anchors[i].href;
        layerId = 'toggle-' + layerId.substring(layerId.lastIndexOf('/') + 1);
        anchors[i].href = 'javascript:BowWeb.toggleVisibility("' + layerId + '");';
      } else if(anchors[i].id == 'filterhref') {
        anchors[i].href = 'javascript:toggleFilter("' + anchors[i].href.toUpperCase() + '");';
      }
    }

    var inputs = getAllByTag('input');
    for(i = 0; i < inputs.length; i++) {
      if(inputs[i].id == 'toggleCb') {
        inputs[i].onchange = function() {
          if(isBusy()) return false;
          var key = this.name;
          if(!this.checked) excludedKeys[key] = 'true';
          else delete excludedKeys[key];
          updateCacheQuery();
          selectSection();
        };
      }
    }

    // add handler for the hide-inactive checkbox
    if(getById('hideExpiredCb')) {
      getById('hideExpiredCb').onclick = function() {
        if(isBusy()) return false;
        hideExpiredEntries = this.checked;
        updateCacheQuery();
        selectSection();
      };
    }
    if(getById('showMissingCb')) {
      getById('showMissingCb').onclick = function() {
        if(isBusy()) return false;
        showMissingEntries = this.checked;
        updateCacheQuery();
        selectSection();
      };
    }
    if(getById('hideLocalCb')) {
      getById('hideLocalCb').onclick = function() {
        if(isBusy()) return false;
        sections.cache.queries[3] = 'cache-sources hide-local="' + this.checked + '"';
        hideLocalSources = this.checked;
        selectSection();
      };
    }

  }
};

function setupInputField(idStr) {
  if(getById(idStr)) {
    getById(idStr).onfocus = function() { busy = true; };
    getById(idStr).onblur = function() { busy = false; };
  }
}

function toggleFilter(sourceStr) {
  if(sourceFilter == sourceStr) sourceFilter = '';
  else sourceFilter = sourceStr;
  updateCacheQuery();
  selectSection();
}

function toggleTidDisplay() {
  tidInHex = !tidInHex;
  selectSection();
}

function toggleSidDisplay() {
  sidInHex = !sidInHex;
  selectSection();
}

function updateCacheQuery() {
  var keys = new Array();
  for(var key in excludedKeys) keys.push(key);
  sections.cache.queries[2] = 'cache-contents show-missing="' + showMissingEntries + '" hide-expired="' + hideExpiredEntries + '" source-filter="' + sourceFilter + '"';
  if(keys.length > 0) sections.cache.queries[2] += ' exclude-keys="' + keys.join(",") + '"';
}

function cacheCoveragePluginPostProcess() {
	if(getCookie('isAdmin') == 'true') {
    var newLink = document.createElement('a'); // create a new link for this section in the menu
    newLink.href = '#';
    newLink.id = 'cache';
    newLink.appendChild(document.createTextNode('Cache'));
    var adminLink = document.getElementById('admin');
    adminLink.parentNode.insertBefore(newLink, adminLink);
    adminLink.parentNode.insertBefore(document.createTextNode(' '), adminLink);
  }
}

function cacheCoveragePluginLogout() {
  hide(getById('cache'));
}
