/**
 * bowweb.js: Web utility functions and classes, using global symbol "BowWeb" as a namespace object
 **/

if(!window.console){ window.console = {log: function(str){} }; }

var BowWeb = {};

BowWeb.testSessionCookie = function() {
  document.cookie = "testSessionCookie=Enabled";
  return BowWeb.getCookieValue("testSessionCookie") == "Enabled";
}

BowWeb.writeSessionCookie = function(cookieName, cookieValue) {
  if (BowWeb.testSessionCookie()) {
    document.cookie = escape(cookieName) + "=" + escape(cookieValue) + "; path=/";
    return true;
  }
  else return false;
}

BowWeb.getCookieValue = function(cookieName) {
  var exp = new RegExp(escape(cookieName) + "=([^;]+)");
  if (exp.test(document.cookie + ";")) {
    exp.exec(document.cookie + ";");
    return unescape(RegExp.$1);
  }
  else return false;
}

BowWeb.writeLayer = function(id, content) {
  if(document.getElementById(id)) document.getElementById(id).innerHTML = content;
}

BowWeb.getVisibility = new Array();

BowWeb.getLayerVis = function(id, defaultVal) {
  for (var i = 0; i < BowWeb.getVisibility.length; i++) {
    if (BowWeb.getVisibility[i][0] == id) {
      return BowWeb.getVisibility[i][1];
    }
  }
  BowWeb.getVisibility[BowWeb.getVisibility.length] = new Array(id, defaultVal);
  return defaultVal;
}

BowWeb.setLayerVis = function(id, setVal) {
  var foundVal = false;
  for (var i = 0; i < BowWeb.getVisibility.length; i++) {
    if (BowWeb.getVisibility[i][0] == id) {
      BowWeb.getVisibility[i][1] = setVal;
      foundVal = true;
    }
  }
  if (!foundVal) BowWeb.getVisibility[BowWeb.getVisibility.length] = new Array(id, setVal);
}

BowWeb.toggleVisibility = function(id) {
  var el = document.getElementById(id);
  el.style.display = (el.style.display == 'block') ? 'none' : 'block';
  BowWeb.setLayerVis(id, el.style.display);
}

BowWeb.fixTimestamps = function(xml, tagName) {
  var elements = xml.getElementsByTagName(tagName);
  for(var i = 0; i < elements.length; i++)
    elements[i].setAttribute('timestamp', BowUtil.formatDateTime(elements[i].getAttribute('timestamp')));
}

BowWeb.getXmlRequest = function() {
  var req;
  if (window.XMLHttpRequest) {
    req = new XMLHttpRequest()
  } else if (window.ActiveXObject) {// code for IE
    var MsXml = new Array('MSXML2.XMLHTTP.5.0', 'MSXML2.XMLHTTP.4.0', 'MSXML2.XMLHTTP.3.0', 'MSXML2.XMLHTTP', 'Microsoft.XMLHTTP');
    for (var i = 0; i < MsXml.length; i++) {
      try {
        req = new ActiveXObject(MsXml[i]);
        break;
        // Only breaks if successful
      } catch (e) {
        e = null;
      }
    }
  }
  return req;
}

/**
 * XsltTransformer class
 **/

BowWeb.XsltTransformer = function(stylesheet, postTransformFunc) {
  if (typeof stylesheet == "string") stylesheet = this.loadXslt(stylesheet);
  this.stylesheet = stylesheet;
  this.postTransformFunc = postTransformFunc;
  if (typeof XSLTProcessor != "undefined") {
    this.processor = new XSLTProcessor();
    this.processor.importStylesheet(this.stylesheet);
  }
}

BowWeb.XsltTransformer.prototype.setPostFunc = function(postTransformFunc) {
  this.postTransformFunc = postTransformFunc;
}

BowWeb.XsltTransformer.prototype.loadXslt = function(name) {
  if (this.requestXslt == null) this.requestXslt = BowWeb.getXmlRequest();
  this.requestXslt.open("GET", name, false);
  this.requestXslt.send(null);
  return this.requestXslt.responseXML;
}

BowWeb.XsltTransformer.prototype.transform = function(node, element) {
  if (!element) element = document.body;
  if (typeof element == "string") element = document.getElementById(element);
  if (this.processor) {
    var fragment = this.processor.transformToFragment(node, document);
    element.innerHTML = (new XMLSerializer()).serializeToString(fragment);
  } else if ("transformNode" in node) {
    element.innerHTML = node.transformNode(this.stylesheet);    
  } else {
    throw "XSLT is not supported in this browser";
  }
  if(this.postTransformFunc) this.postTransformFunc(element);
  return element;
};

/**
 * AjaxHelper class
 **/

BowWeb.AjaxHelper = function(errorHandler, loadingImg) {
  this.loadingImg = loadingImg;
  this.errorHandler = errorHandler;
  this.request = BowWeb.getXmlRequest();
}

BowWeb.AjaxHelper.prototype.executeRequest = function(url, xml, processFunc) {
  var request = this.request;

  if (request == null || (request.readyState != 0 && request.readyState != 4))
    this.request = request = BowWeb.getXmlRequest(); // other request in progress, ignore

  if (this.loadingImg && document.getElementById(this.loadingImg))
    document.getElementById(this.loadingImg).style.visibility = 'visible';

  url = location.protocol + '//' + location.host + url;
  try {
    var caller = this; // make a reference accessible from within the readystatechange handler
    request.open("POST", url);
    request.setRequestHeader('Content-type', 'text/xml');

    request.onreadystatechange = function() {
      try {
        if(request.readyState == 4) {
          if(request.status == 200) {
            if(caller.loadingImg && document.getElementById(caller.loadingImg))
              document.getElementById(caller.loadingImg).style.visibility = 'hidden';
            var type = request.getResponseHeader('Content-type');
            if(type && type.indexOf('text/xml') > -1) {
              var error = request.responseXML.getElementsByTagName('error');
              if(error && error.length > 0) caller.errorOccured(error[0].getAttribute('description'));
              else {
                caller.lastResponse = request.responseText;
                caller.lastSize = request.getResponseHeader('Content-length');
                processFunc(request.responseXML, request.responseText);
              }
            } else caller.errorOccured('Server error' + (request.responseText ? ':\n' + request.responseText : ''));
          } else {
            caller.errorOccured('HTTP Connection error' + (request.status == 0 ? '' : ': ' + request.status + '\n' + request.responseText));
          }
        }
      } catch (e) {
        // caller.errorOccured('Internal error: ' + e);
        caller.errorOccured('Connection lost (refused or timed out).\n\n' + e);
      }
    }    
    request.send(xml);
    this.lastRequest = xml;
  } catch(e) {
    this.errorOccured('Connection error: ' + e);
  }
}

BowWeb.AjaxHelper.prototype.errorOccured = function(e) {
  if(timer) clearTimeout(timer);
  if(this.loadingImg && document.getElementById(this.loadingImg)) document.getElementById(this.loadingImg).style.visibility = 'hidden';
  if(typeof e != 'string') msg = "Uncaught error: " + e.message;
  else msg = e;
  console.log(msg);
  if(this.errorHandler) this.errorHandler(msg);
  this.request = null;
}

BowWeb.AjaxHelper.prototype.fetchText = function(url, processFunc) {
  var request = this.request;

  if (request == null || (request.readyState != 0 && request.readyState != 4))
    this.request = request = BowWeb.getXmlRequest(); // other request in progress, ignore

  if (this.loadingImg && document.getElementById(this.loadingImg))
    document.getElementById(this.loadingImg).style.visibility = 'visible';

  url = location.protocol + '//' + location.host + url;
  try {
    var caller = this;
    request.open("GET", url);
    request.onreadystatechange = function() {
      try {
        if(request.readyState == 4) {
          if(request.status == 200) {          
            processFunc(request.responseText);
            if(caller.loadingImg && document.getElementById(caller.loadingImg))
              document.getElementById(caller.loadingImg).style.visibility = 'hidden';
          } else {
            caller.errorOccured('HTTP error: ' + request.status + '\n' + request.responseText);
          }
        }
      } catch (e) {
        caller.errorOccured('Internal error: ' + e);
      }
    }
    request.send(null);
  } catch(e) {
    this.errorOccured('Connection error: ' + e);
  }
}





