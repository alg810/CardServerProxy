
// make a new xsltransformer for this plugin
//var xsltTrDbp = new BowWeb.XsltTransformer("/plugin/dreamboxplugin/open/xslt/cws-status-resp.xsl", postProcessHook);
var xsltTrDbp = new BowWeb.XsltTransformer("/plugin/dreamboxplugin/open/xslt/cws-status-resp.xsl", postProcess);
var hideInactiveBoxes = true;
var selectedScript, scriptSelectedFilename, cmdSelectedFilename, cmdlineText, paramsText, tagText;

//add the postProcess function to cs-status.js
pluginsPostProcess.push("dreamboxPluginPostProcess()");

//add the logout function to cs-status.js
pluginsLogout.push("dreamboxPluginLogout()");

// add maintenance section
sections['maintenance'] = {
  label: 'Maintenance',
  queries: ['proxy-status', 'list-boxes hide-inactive="true"', 'installer-details'],
  repeat: true,
  handler: function(xml) {
    if(hideInactiveBoxes) getFirstByTag('boxes', xml).setAttribute('hide-inactive', 'true');

    var boxes = xml.getElementsByTagName('box');
    for(var i = 0; i < boxes.length; i++) { // convert dec to hex, too messy for xslt
      if(boxes[i].getAttribute('sid') != 'N/A') boxes[i].setAttribute('sid', (new Number(boxes[i].getAttribute('sid')).toString(16)));
      if(boxes[i].getAttribute('onid') != 'N/A') boxes[i].setAttribute('onid', (new Number(boxes[i].getAttribute('onid')).toString(16)));
      var cb = getById(boxes[i].getAttribute('id')); // remember check state
      if(cb && cb.checked) boxes[i].setAttribute('checked', 'true');
    }

    xsltTrDbp.transform(xml);

    // add handler for the hide-inactive checkbox
    if(getById('hideInactiveCb')) {
      getById('hideInactiveCb').onclick = function() {
        if(isBusy()) return false;
        sections.maintenance.queries[1] = 'list-boxes hide-inactive="' + this.checked + '"';
        hideInactiveBoxes = this.checked;
        selectSection();
      };
    }
    if(getById('invertSelection')) {
      getById('invertSelection').onclick = invertSelection;
    }

    // handle the task form, if visible
    if(getById('scriptSelector')) {
      var fields = ['scriptSelector', 'paramsInput', 'cmdlineInput', 'tagInput', 'scriptFilenameSelector', 'cmdFilenameSelector'];
      for(i = 0; i < fields.length; i++) setupInputField(fields[i]);
      getById('scriptSelector').onchange = function() {
        selectedScript = this.value;
        busy = false;
      };
      getById('paramsInput').onblur = function() { paramsText = this.value; busy = false; };
      getById("cmdlineInput").onblur = function() { cmdlineText = this.value; busy = false; };
      getById("tagInput").onblur = function() { tagText = this.value; busy = false; };

      if(selectedScript) getById('scriptSelector').value = selectedScript;
      if(cmdlineText) getById('cmdlineInput').value = cmdlineText;
      if(paramsText) getById('paramsInput').value = paramsText;
      if(tagText) getById('tagInput').value = tagText;

      getById('scriptBtn').onclick = executeScript;
      getById('cmdlineBtn').onclick = executeCmdline;
      getById('abortBtn').onclick = executeAbort;
      getById('clearBtn').onclick = executeClear;
      getById('tagBtn').onclick = executeTag;

      if(getById('scriptFilenameSelector')) {
        getById('scriptFilenameSelector').onchange = function() {
          scriptSelectedFilename = this.value;
          busy = false;
        };
        if(scriptSelectedFilename) getById('scriptFilenameSelector').value = scriptSelectedFilename;
        getById('cmdFilenameSelector').onchange = function() {
          cmdSelectedFilename = this.value;
          busy = false;
        };
        if(cmdSelectedFilename) getById('cmdFilenameSelector').value = cmdSelectedFilename;
      }
    }

  }
};

sections['boxdetails'] = {
  label: 'Box-details',
  queries: ['proxy-status', 'box-details'],
  repeat: true,
  handler: function(xml) {
    var box = getFirstByTag('box-details', xml);
    if(box) {
      box.setAttribute('display', BowWeb.getLayerVis('toggle-' + box.getAttribute('id'), 'none'));
      fixTimeStamp(box, 'created');
      fixTimeStamp(box, 'last-checkin');
    }

    var ops = getAllByTag('op', xml);
    if(ops) {
      for(var i = 0; i < ops.length; i++) {
        ops[i].setAttribute('display', BowWeb.getLayerVis('toggle-' + ops[i].getAttribute('id'), 'none'));
        fixTimeStamp(ops[i], 'start');
        fixTimeStamp(ops[i], 'stop');
      }
    }

    xsltTrDbp.transform(xml);

    // attach visibility handlers to links
    var anchors = getAllByTag('a');
    for(i = 0; i < anchors.length; i++) {
      if(anchors[i].id == 'openhref') {
        var layerId = anchors[i].href;
        layerId = 'toggle-' + layerId.substring(layerId.lastIndexOf('/') + 1);
        anchors[i].href = 'javascript:BowWeb.toggleVisibility("' + layerId + '");';
      }
    }

    // attach handlers to ctrl command forms
    var forms = getAllByTag('form');
    for(i = 0; i < forms.length; i++) {
      forms[i].onsubmit = function() {
        executeCtrlCmd(this.getAttribute("id"), this.elements);
        return false;
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

function dreamboxPluginPostProcess() {
  var newLink = document.createElement('a'); // create a new link for this section in the menu
  newLink.href = '#';
  newLink.id = 'maintenance';
  newLink.appendChild(document.createTextNode('Maintenance'));
  var adminLink = document.getElementById('admin');
  adminLink.parentNode.insertBefore(newLink, adminLink);
  adminLink.parentNode.insertBefore(document.createTextNode(' '), adminLink);
}

function dreamboxPluginLogout() {
  hide(getById('maintenance'));
}

function selectBox(id) {
  sections.boxdetails.queries[1] = 'box-details id="' + id + '"';
  clickSection('boxdetails');
}

function getSelectedBoxes() {
  var table = getById('boxes');
  var inputs = getAllByTag('input', table);
  var ids = [];
  for(var i = 0; i < inputs.length; i++) {
    if(inputs[i].checked) ids.push(inputs[i].id);
  }
  return ids;
}

function uncheckAllBoxes() {
  var table = getById('boxes');
  var inputs = getAllByTag('input', table);
  for(var i = 0; i < inputs.length; i++) inputs[i].checked = false;
}

function executeSetOperations(cmd, params, boxes, filename) {
  var fname = '';
  if(filename && filename != 'None') fname = ' filename="' + filename + '"';
  var xml = '<set-operations operation="' + cmd + '" params="' + params + '"' + fname + ' include="true">\n';
  for(var i = 0; i < boxes.length; i++) xml += '<box id="' + boxes[i] + '"/>\n';
  xml += '</set-operations>\n';
  executeStatusCmd(xml, processOperationsResult);
}

function executeSetTag(boxes, tag) {
  if(!tag) tag = '';
  var xml = '<set-tag tag="' + tag + '" include="true">\n';
  for(var i = 0; i < boxes.length; i++) xml += '<box id="' + boxes[i] + '"/>\n';
  xml += '</set-tag>\n';
  executeStatusCmd(xml, processOperationsResult);
}

function processOperationsResult(xmlReply) {
  uncheckAllBoxes();
  selectSection();
}

function executeScript() {
  var boxes = getSelectedBoxes();
  if(boxes.length > 0) {
    if(!selectedScript) selectedScript = getById('scriptSelector').value;
    if(!scriptSelectedFilename && getById('scriptFileNameSelector')) scriptSelectedFilename = getById('scriptFilenameSelector').value;
    if(confirm('Run script "' + selectedScript + "' on " + boxes.length + " box(es)?"))
      executeSetOperations('script:' + selectedScript, getById('paramsInput').value, boxes, scriptSelectedFilename);
  }
}

function executeCmdline() {
  var boxes = getSelectedBoxes();
  if(boxes.length > 0) {
    if(!cmdSelectedFilename && getById('cmdFilenameSelector')) cmdSelectedFilename = getById('cmdFilenameSelector').value;
    if(confirm('Run command line "' + cmdlineText + "' on " + boxes.length + " box(es)?"))
      executeSetOperations('cmd:' + cmdlineText, '', boxes, cmdSelectedFilename);
  }
}

function executeTag() {
  var boxes = getSelectedBoxes();
  if(boxes.length > 0) executeSetTag(boxes, tagText);
}

function executeAbort() {
  var table = getById('boxes');
  var inputs = getAllByTag('input', table);
  var boxes = [];
  for(var i = 0; i < inputs.length; i++) boxes.push(inputs[i].id);
  if(boxes.length > 0) executeSetOperations('', '', boxes);
}

function executeClear() {
  if(confirm('Clear history for all boxes?'))
    executeCtrlCmd('clear-history', []);
}

function invertSelection() {
  var table = getById('boxes');
  var inputs = getAllByTag('input', table);
  for(var i = 0; i < inputs.length; i++) inputs[i].checked = !inputs[i].checked;
}