
// make a new xsltransformer for this plugin
var xsltTrMusmp = new BowWeb.XsltTransformer("/plugin/mysqlwebmanagementplugin/open/xslt/cws-status-resp.xsl", postProcess);

// add the postProcess function to cs-status.js
pluginsPostProcess.push("mySQLWebManagementPluginPostProcess()");

//add the logout function to cs-status.js
pluginsLogout.push("mySQLWebManagementPluginLogout()");

sections['mysqlusers'] = {
    label: 'MySQLUsers',
    queries: ['proxy-status', 'mysql-database-details', 'mysql-users'],
    repeat: false,
    handler: function(xml) {
    	xsltTrMusmp.transform(xml); // ======================================================================================	
    }
};

sections['adduser'] = {
    label: 'AddUsers',
    queries: ['proxy-status', 'mysql-add-user'],
    repeat: false,
    handler: function(xml) {
    	xsltTrMusmp.transform(xml); // ======================================================================================
    	
        // attach handlers to ctrl command forms
        var forms = getAllByTag('form');
        for(i = 0; i < forms.length; i++) {
          forms[i].onsubmit = function() {
        	executeMySQLCtrlCmd(this.getAttribute("id"), this.elements, new Boolean(true));
            return false;
          };
        }
        
        attachPasswordFieldHandler();
        
    }
};

sections['edituser'] = {
	    label: 'EditUsers',
	    queries: ['proxy-status', 'mysql-select-user'],
	    repeat: false,
	    handler: function(xml) {
	    	xsltTrMusmp.transform(xml); // ======================================================================================
	    	
	        // attach handlers to ctrl command forms
	        var forms = getAllByTag('form');
	        for(i = 0; i < forms.length; i++) {
	          forms[i].onsubmit = function() {
	        	executeMySQLCtrlCmd(this.getAttribute("id"), this.elements, new Boolean(true));
	            return false;
	          };
	        }
	        
	        attachPasswordFieldHandler();
	        	        
	        if(getById('loadUserBtn')) {
	            getById('loadUserBtn').onclick = function() {
	              sections.edituser.queries = ['proxy-status', 'mysql-select-user', 'mysql-edit-user username="' + getById('selectUsername').value + '"'];
	              selectSection();
	              sections.edituser.queries = ['proxy-status', 'mysql-select-user'];
	            }
	        }
	    }
	};

sections['deleteuser'] = {
    label: 'DeleteUsers',
	queries: ['proxy-status', 'mysql-delete-user'],
	repeat: false,
	handler: function(xml) {
		xsltTrMusmp.transform(xml); // ======================================================================================
	    	
	    // attach handlers to ctrl command forms
	    var forms = getAllByTag('form');
	    for(i = 0; i < forms.length; i++) {
	      forms[i].onsubmit = function() {
	    	executeMySQLCtrlCmd(this.getAttribute("id"), this.elements, new Boolean(false));
	        return false;
	      };
	    }   
	}
};

sections['profiles'] = {
    label: 'Profiles',
	queries: ['proxy-status', 'mysql-profiles'],
	repeat: false,
	handler: function(xml) {
		xsltTrMusmp.transform(xml); // ======================================================================================
    	
	    // attach handlers to ctrl command forms
	    var forms = getAllByTag('form');
	    for(i = 0; i < forms.length; i++) {
	      forms[i].onsubmit = function() {
	    	executeMySQLCtrlCmd(this.getAttribute("id"), this.elements);
	        return false;
	      };
	    }
	}
};

function attachPasswordFieldHandler() {
    // password color helper
    if (getById('inputPassword') && getById('inputPasswordRetyped')) {
    	// first password field
    	getById('inputPassword').onblur = function() {
    		if ((getById('inputPassword').value == "") && (getById('inputPasswordRetyped').value == "")) {
    			getById('inputPasswordRetyped').style.backgroundColor = "";
    		} else {
        		if (getById('inputPassword').value != getById('inputPasswordRetyped').value) {
        			getById('inputPasswordRetyped').style.backgroundColor = "#CD3700";
        		} else {
        			getById('inputPasswordRetyped').style.backgroundColor = "#A2CD5A";
        		}
    		}
    	}
    	
    	// second password field
    	getById('inputPasswordRetyped').onblur = function() {
    		if ((getById('inputPassword').value == "") && (getById('inputPasswordRetyped').value == "")) {
    			getById('inputPasswordRetyped').style.backgroundColor = "";
    		} else {
        		if (getById('inputPassword').value != getById('inputPasswordRetyped').value) {
        			getById('inputPasswordRetyped').style.backgroundColor = "#CD3700";
        		} else {
        			getById('inputPasswordRetyped').style.backgroundColor = "#A2CD5A";
        		}
    		}
    	}
    }
    
    if (getById('btnReset')) {
    	getById('btnReset').onclick = function() {
    		if (getById('inputPasswordRetyped')) {
    			getById('inputPasswordRetyped').style.backgroundColor = "";
    		}
    	}
    }
}

function executeMySQLCtrlCmd(cmd, elements, includeProfiles) {
  var sessionId = getCookie('sessionId');
  if(sessionId != '') {
    var xml = '<cws-command-req ver="1.0">\n';
    xml += '<session session-id="' + sessionId + '"/>\n';
    xml += '<command command="' + cmd + '"';
    for(var i = 0; i < elements.length; i++) {
      if(elements[i].type == 'hidden' && elements[i].name == 'confirm' && elements[i].value == 'true')
        if(!confirm('Execute ' + cmd + ' command?')) return;
      if(elements[i].type != 'submit' && elements[i].type != 'reset') {  	  
        if(elements[i].type != 'checkbox') {
          xml += ' ' + elements[i].name + '="' + elements[i].value + '"';
        }
      }
    }
    if (includeProfiles) {
		xml += ' profiles="';
		var all = 'false';
	    for(var i = 0; i < elements.length; i++) {
	    	if (elements[i].type == 'checkbox' && elements[i].name == 'profiles' && elements[i].value == 'ALL' && elements[i].checked) {
	    		all = 'true';
	    	} else {
	    	if(elements[i].type == 'checkbox' && elements[i].name == 'profiles') {
	    		if (all == 'true' || elements[i].checked) {
	    			xml += elements[i].value + ' ';
	    		}
	    	}
	    	}
	    }
    	xml += '"';
    }
    xml += '/>\n';
    xml += '</cws-command-req>';
    postXml(xml, processCtrlCmd);
  } else {
    writeLoginWindow('Login required');
  }
}

function mySQLWebManagementPluginPostProcess() {
	if(getCookie('isAdmin') == 'true') {
		var newLink = document.createElement('a'); // create a new link for this section in the menu
		newLink.href = '#';
		//newLink.href = 'javascript:clickSection("mysqlusers");';
		newLink.id = 'mysqlusers';
		newLink.appendChild(document.createTextNode('MySQL-Users'));
		var adminLink = document.getElementById('admin');
		adminLink.parentNode.insertBefore(newLink, adminLink);
		adminLink.parentNode.insertBefore(document.createTextNode(' '), adminLink);
	}
}

function mySQLWebManagementPluginLogout() {
	hide(getById('mysqlusers'));
}

