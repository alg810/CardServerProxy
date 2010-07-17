
// make a new xsltransformer for this plugin
var xsltTrDbp = new BowWeb.XsltTransformer("/plugin/dreamboxplugin/open/xslt/cws-status-resp.xsl", postProcessHook);
var hideInactiveBoxes = true;
var selectedScript, cmdlineText, paramsText;

// hook the postprocess function for the regular xsltransformer so we can add stuff
xsltTr.setPostFunc(postProcessHook);

// add maintenance section
sections['maintenance'] = {
  label: 'Maintenance',
  queries: ['proxy-status', 'list-boxes hide-inactive="true"', 'installer-details'],
  repeat: true,
  handler: function(xml) {
    if(hideInactiveBoxes) getFirstByTag('boxes', xml).setAttribute('hide-inactive', 'true');

    var boxes = xml.getElementsByTagName('box');
    for(var i = 0; i < boxes.length; i++) { // convert dec to hex, too messy for xslt
      boxes[i].setAttribute('sid', (new Number(boxes[i].getAttribute('sid')).toString(16)));
      boxes[i].setAttribute('onid', (new Number(boxes[i].getAttribute('onid')).toString(16)));
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
      getById('scriptSelector').onchange = function() {
        selectedScript = this.value;
        busy = false;
      };
      getById('scriptSelector').onfocus = function() { busy = true; };
      getById('scriptSelector').onblur = function() { busy = false; };
      getById('paramsInput').onfocus = function() { busy = true; };
      getById('paramsInput').onblur = function() { paramsText = this.value; busy = false; };
      getById("cmdlineInput").onfocus = function() { busy = true; };
      getById("cmdlineInput").onblur = function() { cmdlineText = this.value; busy = false; };

      if(selectedScript) getById('scriptSelector').value = selectedScript;
      if(cmdlineText) getById('cmdlineInput').value = cmdlineText;
      if(paramsText) getById('paramsInput').value = paramsText;

      getById('scriptBtn').onclick = executeScript;
      getById('cmdlineBtn').onclick = executeCmdline;
      getById('abortBtn').onclick = executeAbort;
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

function postProcessHook() {
  var newLink = document.createElement('a'); // create a new link for this section in the menu
  newLink.href = '#';
  newLink.id = 'maintenance';
  newLink.appendChild(document.createTextNode('Maintenance'));
  var adminLink = document.getElementById('admin');
  adminLink.parentNode.insertBefore(newLink, adminLink);
  adminLink.parentNode.insertBefore(document.createTextNode(' '), adminLink);
  
  postProcess(); // call back to the regular handling
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

function executeSetOperations(cmd, params, boxes) {
  var xml = '<set-operations operation="' + cmd + '" params="' + params + '" include="true">\n';
  for(var i = 0; i < boxes.length; i++) xml += '<box id="' + boxes[i] + '"/>\n';
  xml += '</set-operations>\n';
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
    if(confirm('Run script "' + selectedScript + "' on " + boxes.length + " box(es)?"))
      executeSetOperations('script:' + selectedScript, getById('paramsInput').value, boxes);
  }
}

function executeCmdline() {
  var boxes = getSelectedBoxes();
  if(boxes.length > 0) {
    if(confirm('Run command line "' + cmdlineText + "' on " + boxes.length + " box(es)?"))
      executeSetOperations('cmd:' + cmdlineText, '', boxes);
  }
}

function executeAbort() {
  var table = getById('boxes');
  var inputs = getAllByTag('input', table);
  var boxes = [];
  for(var i = 0; i < inputs.length; i++) boxes.push(inputs[i].id);
  if(boxes.length > 0) executeSetOperations('', '', boxes);
}

function invertSelection() {
  var table = getById('boxes');
  var inputs = getAllByTag('input', table);
  for(var i = 0; i < inputs.length; i++) inputs[i].checked = !inputs[i].checked;
}