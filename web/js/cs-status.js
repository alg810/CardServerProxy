
var timer;
var helper = new BowWeb.AjaxHelper(logout, 'busyImg');
var xsltTr = new BowWeb.XsltTransformer("/xslt/cws-status-resp.xsl", postProcess);
var cmEditor;
var saveLock = false;
var hideInactive = true, toggleDuration = false;
var busy = false;
var profileFilter;
var currentSection;
var autoExec;
var interval = 5000;
var eventGrpVis = {};
var pluginsPostProcess = new Array(); // contains plugins postProcess functions
var pluginsLogout = new Array(); // plugins logout functions

var loginWindow = ['<form action="" onsubmit="executeLogin(); return false;" name="loginForm">',
    '<input type="text" name="userId"/>', '<input type="password" name="password"/>',
    '<input type="submit" value="Log in"/>', '</form>'].join('\n');

var configEditor = ['<div class="cmeditor">', '<textarea id="code" cols="120" rows="40"></textarea></div>',
    '<br /><br /><input type="button" value="Save changes" onclick="saveConfigFile();"/>&nbsp;',
    '<input type="button" value="Revert to saved" onclick="selectSection(\'config\');"/>&nbsp;',
    '&nbsp;<a href="proxy-reference.html" target="_blank">Proxy.xml reference</a>'].join('\n');

window.onload = writeLoginWindow;

/*
  Section definitions:
  queries = status commands to post, or function to run
  repeat = auto repeat every interval millisecs
  handler = optional function for pre/postprocessing and xslt transform
 */

var sections = {
  events: {
    label: 'Events',
    queries: ['proxy-status', 'ca-profiles', 'error-log', 'file-log', 'user-warning-log', 'profile'],
    repeat: true,
    handler: function(xml) {
      // format cws event timestamps to avoid having to do it with xslt :P
      var events = getAllByTag('event', xml);
      for(var i = 0; i < events.length; i++) fixTimeStamp(events[i], 'timestamp');

      // format user warning timestamps + handle the grouping of warnings by timestamp (truncated to minute)
      var ecms = getAllByTag('ecm', xml);
      for(i = 0; i < ecms.length; i++) {
        fixTimeStamp(ecms[i], 'timestamp');
        var tsStr = ecms[i].getAttribute('timestamp');
        tsStr = tsStr.substring(0, tsStr.length - 3).replace(/ /g, '_');
        if(eventGrpVis[tsStr]) ecms[i].setAttribute('display', BowWeb.getLayerVis(tsStr, 'none'));
        else ecms[i].setAttribute('display', 'none');
      }

      xsltTr.transform(xml); // =======================================================================================

      // add profile filter handler (and stop auto updating while filter dropdown is open)
      if(getById('profileFilter')) {
        getById('profileFilter').onchange = function() {
          if('ALL' == this.value) profileFilter = null;
          else profileFilter = this.value;
          busy = false;
          selectSection();
        }
        getById('profileFilter').onfocus = function() { busy = true; }
        getById('profileFilter').onblur = function() { busy = false; }
        if(profileFilter != null) getById('profileFilter').value = profileFilter;
      }

      // add handlers to toggle visibility of the event groups
      var anchors = getAllByTag('a');
      for(i = 0; i < anchors.length; i++) {
        if(anchors[i].id == 'openhref') {
          var layerId = anchors[i].href;
          layerId = layerId.substring(layerId.lastIndexOf('/') + 1);
          eventGrpVis[layerId] = getById(layerId).style.display;
          anchors[i].href = 'javascript:BowWeb.toggleVisibility("' + layerId + '");';
        }
      }

      // add clear button handlers
      if(getById('clearEventsBtn')) getById('clearEventsBtn').onclick = function() {
        executeCtrlCmd('clear-events', new Array());
      };
      if(getById('clearWarningsBtn')) getById('clearWarningsBtn').onclick = function() {
        executeCtrlCmd('clear-warnings', new Array());
      };
      if(getById('clearFileLogBtn')) getById('clearFileLogBtn').onclick = function() {
        executeCtrlCmd('clear-file-log', new Array());
      };

      document.title = 'CSP Status: ' + getFirstByTag('proxy-status', xml).getAttribute('name');
    }
  },
  channels: {
    label: 'Channels',
    queries: ['proxy-status', 'ca-profiles', 'watched-services', 'linked-services', 'profile'],
    repeat: false,
    handler: function(xml) {
      var services = getAllByTag('service', xml);
      for(var i = 0; i < services.length; i++) // add hex format id, too messy for xslt
        services[i].setAttribute('hex-id', (new Number(services[i].getAttribute('id')).toString(16)));

      xsltTr.transform(xml); // =======================================================================================

      // add profile filter handler
      if(getById('profileFilter')) {
        getById('profileFilter').onchange = function() {
          if('ALL' == this.value) profileFilter = null;
          else profileFilter = this.value;
          selectSection();
        }
        if(profileFilter != null) getById('profileFilter').value = profileFilter;
      }

      // add handlers for buttons/checkboxes
      if(getById('checkAllCb')) {
        getById('checkAllCb').onchange = function() {
          var inputs = getById('content').getElementsByTagName('input'); // all input elements in content div
          for(var i = 0; i < inputs.length; i++) {
            if(inputs[i].id == 'checkAllCb' || inputs[i].id == 'enigma2Cb') continue;
            if(inputs[i].type == 'checkbox') inputs[i].checked = getById('checkAllCb').checked;
          }
        }
        getById('createChannelFileBtn').onclick = createBouquetFile;
      }

      if(getById('showAllServices')) {
        getById('showAllServices').onclick = function() {
          sections.channels.queries = ['proxy-status', 'ca-profiles', 'watched-services', 'all-services', 'profile'];
          selectSection();
          sections.channels.queries = ['proxy-status', 'ca-profiles', 'watched-services', 'profile'];
        }
      }
    }
  },
  status: {
    label: 'Status',
    queries: ['proxy-status', 'cache-status', 'proxy-plugins', 'ca-profiles', 'cws-connectors'],
    repeat: true,
    handler: function(xml) {
      var status = getFirstByTag('proxy-status', xml);
      fixTimeStamp(status, 'started');

      // ensure visibility state is remembered for each connector by adding display attrib to xml before transform
      var cache = getFirstByTag('cache-status', xml);
      cache.setAttribute('display', BowWeb.getLayerVis('toggle-' + cache.getAttribute('type'), 'none'));
      var connectors = getAllByTag('connector', xml);
      for(var i = 0; i < connectors.length; i++) {
        connectors[i].setAttribute('display', BowWeb.getLayerVis('toggle-' + connectors[i].getAttribute('name'), 'none'));
        fixTimeStamp(connectors[i], 'connected');
        fixTimeStamp(connectors[i], 'disconnected');
      }
      var profiles = getAllByTag('profile', xml);
      for(i = 0; i < profiles.length; i++)
        profiles[i].setAttribute('display', BowWeb.getLayerVis('toggle-profile' + profiles[i].getAttribute('name'), 'none'));
      var plugins = getAllByTag('plugin', xml);
      for(i = 0; i < plugins.length; i++)
        plugins[i].setAttribute('display', BowWeb.getLayerVis('toggle-plugin' + plugins[i].getAttribute('name'), 'none'));

      var services = getAllByTag('service', xml);
      for(i = 0; i < services.length; i++) // add hex format id, too messy for xslt
        services[i].setAttribute('hex-id', (new Number(services[i].getAttribute('id')).toString(16)));      

      xsltTr.transform(xml); // =======================================================================================

      // attach visibility handlers to links
      var anchors = getAllByTag('a');
      for(i = 0; i < anchors.length; i++) {
        if(anchors[i].id == 'openhref') {
          var layerId = anchors[i].href;
          layerId = 'toggle-' + layerId.substring(layerId.lastIndexOf('/') + 1);
          anchors[i].href = 'javascript:BowWeb.toggleVisibility("' + layerId + '");';
        }
      }
    }
  },
  sessions: {
    label: 'Sessions',
    queries: ['proxy-status', 'ca-profiles', 'proxy-users hide-inactive="true"'],
    repeat: true,
    handler: function(xml) {
      if(hideInactive) getFirstByTag('proxy-users', xml).setAttribute('hide-inactive', 'true');
      if(toggleDuration) getFirstByTag('proxy-users', xml).setAttribute('toggle-duration', 'true');

      var sessions = getAllByTag('session', xml);
      for(var i = 0; i < sessions.length; i++) {
        if(toggleDuration) sessions[i].removeAttribute('duration');
        else sessions[i].removeAttribute("last-zap");
      }

      xsltTr.transform(xml); // =======================================================================================

      // add handler for the hide-inactive checkbox
      if(getById('hideInactiveCb')) {
        getById('hideInactiveCb').onclick = function() {
          if(isBusy()) return false;
          sections.sessions.queries[2] = 'proxy-users hide-inactive="' + this.checked + '"';
          hideInactive = this.checked;
          selectSection();
        }
      }

      // add handler for the hide-inactive checkbox
      if(getById('toggleDuration')) {
        getById('toggleDuration').onclick = function() {
          if(isBusy()) return false;
          toggleDuration = !toggleDuration;
          selectSection();
        }
      }

    }
  },
  seen: {
    label: 'Seen',
    queries: ['proxy-status', 'last-seen', 'ctrl-commands group="Internal" name="remove-seen"'],
    repeat: false,
    handler: function(xml) {
      // format timestamps
      var entries = getAllByTag('entry', xml);
      for(var i = 0; i < entries.length; i++) {
        fixTimeStamp(entries[i], 'last-login');
        fixTimeStamp(entries[i], 'last-seen');
      }
      xsltTr.transform(xml); // =======================================================================================
      // attach handlers to ctrl command forms
      var forms = getAllByTag('form');
      for(i = 0; i < forms.length; i++) {
        forms[i].onsubmit = function() {
          executeCtrlCmd(this.getAttribute("id"), this.elements);
          return false;
        };
      }
    }
  },
  failures: {
    label: 'Failures',
    queries: ['proxy-status', 'login-failures', 'ctrl-commands group="Internal" name="remove-failed"'],
    repeat: false,
    handler: function(xml) {
      // format timestamps
      var entries = getAllByTag('entry', xml);
      for(var i = 0; i < entries.length; i++) {
        fixTimeStamp(entries[i], 'last-failure');
        fixTimeStamp(entries[i], 'first-failure');
      }
      xsltTr.transform(xml); // =======================================================================================
      // attach handlers to ctrl command forms
      var forms = getAllByTag('form');
      for(i = 0; i < forms.length; i++) {
        forms[i].onsubmit = function() {
          executeCtrlCmd(this.getAttribute("id"), this.elements);
          return false;
        };
      }
    }
  },
  admin: {
    label: 'Admin',
    queries: ['proxy-status', 'ctrl-commands'],
    repeat: false,
    handler: function(xml) {
      xsltTr.transform(xml); // =======================================================================================
      // attach handlers to ctrl command forms
      var forms = getAllByTag('form');
      for(var i = 0; i < forms.length; i++) {
        forms[i].onsubmit = function() {
          executeCtrlCmd(this.getAttribute("id"), this.elements);
          return false;
        };
      }
    }
  },
  config: {
    label: 'Config',
    queries: writeConfigEditor,
    repeat: false
  }
}

function setHref(elem, href) {
  if(elem) elem.href = href;
}

function postProcess() { // always executed after each xslt transform

  // execute the registered plugin postProcess functions
  if(getCookie('sessionId') && currentSection != 'config') {
	  for(var i = 0; i < pluginsPostProcess.length; i++) {
	    eval(pluginsPostProcess[i]);
	  }
  }
	  
  setHref(getById('logout'), 'javascript:logout();');
  setHref(getById('viewXml'), 'javascript:viewXml();');

  var menu = getById('subheader').getElementsByTagName('a');
  for(var i = 0; i < menu.length; i++) {
    if(sections[menu[i].id]) {
      menu[i].href = 'javascript:clickSection("' + menu[i].id + '");';
    }
  }

  var isAdmin = getCookie('isAdmin');
  if(isAdmin == 'true') {
    show(getById('admin'));
    show(getById('config'));
    hrefWrapInner(getById('jvm'), "/xmlHandler?command=system-properties");
    hrefWrapInner(getById('tc'), "/xmlHandler?command=system-threads");
    sections.failures.queries = ['proxy-status', 'login-failures', 'ctrl-commands group="Internal" name="remove-failed"'];
    sections.seen.queries = ['proxy-status', 'last-seen', 'ctrl-commands group="Internal" name="remove-seen"'];
  } else {
    hide(getById('admin'));
    hide(getById('config'));
    sections.failures.queries = ['proxy-status', 'login-failures'];
    sections.seen.queries = ['proxy-status', 'last-seen'];
  }

  if(sections[currentSection] && sections[currentSection].repeat) show(getById('autoPollDiv'));
  else hide(getById('autoPollDiv'));
  if(getById('autoPollCb')) {
    getById('autoPollCb').onchange = function() {
      if(this.checked) selectSection();
    };
  }

  if(autoExec) {
    var section = autoExec;
    autoExec = null;
    selectSection(section);
  }
}

function stopCmEditor() {
  if(cmEditor != null) {
    clearTimeout(cmEditor.documentScan);
    cmEditor = null;
  }
}

function postXml(xml, handlerFunc) {
  xml = '<?xml version="1.0" encoding="UTF-8"?>\n' + xml;
  helper.executeRequest('/xmlHandler?module=rmi', xml, handlerFunc);
}

function executeStatusCmd(xml, handlerFunc, noLogin) {
  if(busy) return true;
  var sessionId = getCookie('sessionId');
  if(sessionId != '' || noLogin) {
    if(sessionId != '') xml = '<session session-id="' + sessionId + '"/>\n' + xml;
    xml = '<cws-status-req ver="1.0">\n' + xml + '</cws-status-req>';
    postXml(xml, handlerFunc);
    return true;
  } else {
    writeLoginWindow('Login required');
    return false;
  }
}

function executeCtrlCmd(cmd, elements) {
  var sessionId = getCookie('sessionId');
  if(sessionId != '') {
    var xml = '<cws-command-req ver="1.0">\n';
    xml += '<session session-id="' + sessionId + '"/>\n';
    xml += '<command command="' + cmd + '"';
    for(var i = 0; i < elements.length; i++) {
      if(elements[i].type == 'hidden' && elements[i].name == 'confirm' && elements[i].value == 'true')
        if(!confirm('Execute ' + cmd + ' command?')) return;
      if(elements[i].type != 'submit') {
        if(elements[i].type == 'checkbox') xml += ' ' + elements[i].name + '="' + elements[i].checked + '"';
        else {
          if(!elements[i].value) return; // consider everything else mandatory
          xml += ' ' + elements[i].name + '="' + elements[i].value + '"';
        }
      }
    }
    xml += '/>\n';
    xml += '</cws-command-req>';
    postXml(xml, processCtrlCmd);
  } else {
    writeLoginWindow('Login required');
  }
}

function processCtrlCmd(xmlReply) {
  var result = getFirstByTag('cmd-result', xmlReply);
  var msg = 'Command: ' + result.getAttribute("command") + '\nSuccess: ' + result.getAttribute('success') + '\n';
  msg += 'Message: ';
  if(result.firstChild) msg += result.firstChild.nodeValue;
  alert(msg);
  selectSection();
}

function repeatStatusCmd(xml, handlerFunc, repeatFuncName, delay) {
  if(executeStatusCmd(xml, handlerFunc)) {
    if(!delay) delay = interval;
    timer = setTimeout(repeatFuncName, delay);
  }
}

function writeLoginWindow(message) {
  var sessionId = getCookie('sessionId');
  if(!sessionId) {
    if(typeof message != 'string') message = '';
    hide(getById('admin'));
    hide(getById('config'));
    BowWeb.writeLayer('content', message + loginWindow);
    BowWeb.writeLayer('headerInfoLeft', '<strong>CSP</strong>');
    postProcess();
  } else {
    var functionName = getCookie('functionName');
    if(!functionName) selectSection('events');
    else eval(functionName);
  }
}

function validateLogin(xmlReply) {
  var status = getFirstByTag('status', xmlReply);
  if(status.getAttribute('state') == 'loggedIn') {
    var cookieWritten = setCookie('sessionId', status.getAttribute('session-id'));
    setCookie('isAdmin', status.getAttribute('super-user'));
    if(cookieWritten) selectSection('events');
    else writeLoginWindow('Enable cookies.');
  } else {
    writeLoginWindow('Invalid user name or password.');
  }
}

function executeLogin() {
  var userId = document.loginForm.userId.value;
  var password = document.loginForm.password.value;
  var xml = '<cws-login>\n';
  xml += '<user name="' + userId + '" password="' + password + '"/>\n';
  xml += '</cws-login>\n';
  executeStatusCmd(xml, validateLogin, true);
}

function getStatusXml(queries) {
  var xml = '';
  for(var q in queries) {
    if('profile' == queries[q]) {
      if(profileFilter != null) xml += '<profile>' + profileFilter + '</profile>';
    } else xml += '<' + queries[q] + ' include="true"/>\n';
  }
  return xml;
}

function isBusy() {
  return !isHidden(getById('busyImg'));
}

function clickSection(name) {
  busy = false;
  if(getById('autoPollCb')) getById('autoPollCb').checked = true;
  if(isBusy()) {
    autoExec = name;
    return;
  }
  selectSection(name);
}

function selectSection(name) {
  if(name == null) name = currentSection; // refreshing currentsection in response to explicit request/click
  else if(getById('autoPollCb') && !getById('autoPollCb').checked) return; // only autorefresh if checked 
  if(sections[name]) { // section exists
    if(cmEditor && name == 'config') if(!confirm("Changes will be lost, proceed?")) return;
    stopCmEditor();
    currentSection = name;
    saveLock = false;
    if(timer) clearTimeout(timer);
    if(!sections[name].handler) sections[name].handler = function(xml) { xsltTr.transform(xml); };
    var funcName = 'selectSection("' + name + '");'
    setCookie('functionName', funcName);
    if(typeof sections[name].queries == 'function') {
      sections[name].queries(); // exec directly
    } else {
      var xml = getStatusXml(sections[name].queries); // post as xml
      if(sections[name].repeat) repeatStatusCmd(xml, sections[name].handler, funcName, interval);
      else executeStatusCmd(xml, sections[name].handler);
    }
  }
}

function writeConfigEditor() {
  var sessionId = getCookie('sessionId');
  if(sessionId != '') {
    BowWeb.writeLayer('content', configEditor);
    helper.fetchText('/xmlHandler?command=fetch-cfg', processConfigLoad);
  } else {
    writeLoginWindow('Login required');
  }
}

function processConfigLoad(xmlReply) {
  var textarea = getById('code');
  if(textarea == null) return;
  if(cmEditor == null) {
    cmEditor = new CodeMirror(CodeMirror.replace(textarea), {
      parserfile: "parsexml.js",
      linesPerPass: 45,
      path: "js/cm/",
      stylesheet: "js/cm/xmlcolors.css",
      width: "1100px",
      height: "500px",
      content: xmlReply
    });
  } else {
    cmEditor.setCode(xmlReply);
  }
  postProcess();
}

function saveConfigFile() {
  if(saveLock) alert('Saving in already in progress.');
  else {
    if(cmEditor != null) {
      if(!confirm("Save changes?")) return;
      saveLock = true;
      var xml = cmEditor.getCode();
      helper.executeRequest('/cfgHandler', xml, processConfigSave);
    }
  }
}

function processConfigSave(xmlReply) {
  var msg = getFirstByTag('cfg-result', xmlReply)
  if(!msg) alert(xmlReply.getAttribute('message'));
  else alert(msg.getAttribute('message'));
  saveLock = false;
}

function createBouquetFile() {
  var items = getAllByTag("input");
  var xml = '<cws-bouquet include="true">\n';
  for(var i = 0, n = items.length; i < n; i++) { // post all selected checkbox sids
    if(items[i].id == 'checkAllCb') continue;
    if(items[i].id == 'enigma2Cb') {
      if(items[i].checked) xml += '<file format="enigma2"/>\n';
      else xml += '<file format="enigma1"/>\n';
      continue;
    }
    if((items[i].type == 'checkbox' && items[i].checked)) {
      xml += '<service id="' + items[i].name + '"/>\n';
    }
  }
  xml += '</cws-bouquet>\n';
  executeStatusCmd(xml, processBouquetFile);
}

function processBouquetFile(xmlReply) {
  if(getFirstByTag('cws-bouquet', xmlReply).getAttribute('url') != '') {
    document.location.pathname = getFirstByTag('cws-bouquet', xmlReply).getAttribute('url');
  } else {
    alert('Error when creating bouquet-file');
  }
}

function logout() {
  for(var i = 0; i < pluginsLogout.length; i++) {
    eval(pluginsLogout[i]);
  }
  autoExec = null;
  profileFilter = null;
  currentSection = null;
  busy = false;
  clearTimeout(timer);
  hide(getById('busyImg'));
  hide(getById('logOut'));
  setCookie('sessionId', '');
  setCookie('isAdmin', '');
  writeLoginWindow('');
}

function show(elem) {
  if(elem) elem.style.visibility = 'visible';
}

function hide(elem) {
  if(elem) elem.style.visibility = 'hidden';
}

function isHidden(elem) {
  if(!elem) return false;
  else return elem.style.visibility == 'hidden'
}

function setCookie(name, value) {
  return BowWeb.writeSessionCookie(name, value);
}

function getCookie(name) {
  return BowWeb.getCookieValue(name);
}

function getById(id) {
  return document.getElementById(id);
}

function getAllByTag(id, elem) {
  if(!elem) return document.getElementsByTagName(id);
  else return elem.getElementsByTagName(id);
}

function getFirstByTag(id, elem) {
  var all = getAllByTag(id, elem);
  return all[0];
}

function hrefWrapInner(elem, href) {
  if(elem) elem.innerHTML = '<a target="_blank" href="' + href + '">' + elem.innerHTML + '</a>';
}

function fixTimeStamp(elem, attribName) {
  if(elem && elem.getAttribute(attribName)) {
    elem.setAttribute(attribName, BowUtil.formatDateTime(elem.getAttribute(attribName)));
  }
}

function viewXml() {
  alert(helper.lastRequest);
  alert(helper.lastSize);
}