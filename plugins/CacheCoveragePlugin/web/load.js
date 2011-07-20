
// make a new xsltransformer for this plugin
var xsltTrCcp = new BowWeb.XsltTransformer("/plugin/cachecoverageplugin/open/xslt/cws-status-resp.xsl", postProcess);

var hideExpiredEntries = true, hideLocalSources = false;
var sourceFilter = '';

//add the postProcess function to cs-status.js
pluginsPostProcess.push("cacheCoveragePluginPostProcess()");

//add the logout function to cs-status.js
pluginsLogout.push("cacheCoveragePluginLogout()");

// add maintenance section
sections['cache'] = {
  label: 'Cache',
  queries: ['proxy-status', 'cache-status', 'cache-contents hide-expired="true"', 'cache-sources hide-local="false"'],
  repeat: true,
  handler: function(xml) {
    if(hideExpiredEntries) getFirstByTag('cache-contents', xml).setAttribute('hide-expired', 'true');
    if(hideLocalSources) getFirstByTag('cache-sources', xml).setAttribute('hide-local', 'true');
    if(sourceFilter != '') getFirstByTag('cache-contents', xml).setAttribute('source-filter', sourceFilter);

    var services = xml.getElementsByTagName('service');
    for(var i = 0; i < services.length; i++) { // convert dec to hex for display, too messy for xslt
      if(services[i].getAttribute('id') != 'N/A') services[i].setAttribute('id', (new Number(services[i].getAttribute('id')).toString(16)));
      if(services[i].getAttribute('tid')) services[i].setAttribute('tid', (new Number(services[i].getAttribute('tid')).toString(16)));
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

    // add handler for the hide-inactive checkbox
    if(getById('hideExpiredCb')) {
      getById('hideExpiredCb').onclick = function() {
        if(isBusy()) return false;
        sections.cache.queries[2] = 'cache-contents hide-expired="' + this.checked + '" source-filter="' + sourceFilter + '"';
        hideExpiredEntries = this.checked;
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
  sections.cache.queries[2] = 'cache-contents hide-expired="' + hideExpiredEntries + '" source-filter="' + sourceFilter + '"';
  selectSection();
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
