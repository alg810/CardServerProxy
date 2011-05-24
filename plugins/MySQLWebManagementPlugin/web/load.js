
// make a new xsltransformer for this plugin
var xsltTrMusmp = new BowWeb.XsltTransformer("/plugin/mysqlwebmanagementplugin/open/xslt/cws-status-resp.xsl", postProcess);

// add the postProcess function to cs-status.js
pluginsPostProcess.push("mySQLWebManagementPluginPostProcess()");

// add the logout function to cs-status.js
pluginsLogout.push("mySQLWebManagementPluginLogout()");

sections['mysqlusers'] = {
	label: 'MySQLUsers',
	queries: ['proxy-status', 'mysql-users pageNum="0"'],
	repeat: false,
	handler: function(xml) {
		xsltTrMusmp.transform(xml); // ======================================================================================
		
		if (getById('btnEditUser')) getById('btnEditUser').disabled = !(getById('tableMySQLUsers'));
		if (getById('btnDeleteUser')) getById('btnDeleteUser').disabled = !(getById('tableMySQLUsers'));
		
		if (getById('btnAddUser')) {
			getById('btnAddUser').onclick = function() { selectSection('mysqladduser'); }
		}
		
		if (getById('btnEditUser')) {
			getById('btnEditUser').onclick = function() { 
				sections.mysqledituser.queries[1] = 'mysql-edit-user username="' + getSelectedUser() + '"';
				selectSection('mysqledituser');
			}
		}
		
		if (getById('btnDeleteUser')) {
			getById('btnDeleteUser').onclick = function() { executeDeleteUser(getSelectedUser()); }
		}
		
		if (getById('btnDeleteAllUsers')) {
			getById('btnDeleteAllUsers').onclick = function() { executeDeleteAllUsers(); }
		}
		
		if (getById('btnImport')) {
			getById('btnImport').onclick = function() { selectSection('mysqlimport'); }
		}
		
		if (getById('btnProfiles')) {
			getById('btnProfiles').onclick = function() { selectSection('mysqlprofiles'); }
		}
		
		if (getById('tableMySQLUsers')) {
			var rows = document.getElementById('tableMySQLUsers').getElementsByTagName("tr");
			for(var i = 1; i < rows.length; i++) {
				rows[i].onmouseover = function() { this.style.backgroundColor='#adadad'; };
				if (i % 2 == 1) {
					rows[i].onmouseout = function() { this.style.backgroundColor='#ffffff'; };
				} else {
					rows[i].onmouseout = function() { this.style.backgroundColor='#eeeeee'; };
				}
			}
		}
	}
};

sections['mysqladduser'] = {
	label: 'AddUser',
	queries: ['proxy-status', 'mysql-add-user'],
	repeat: false,
	handler: function(xml) {
		xsltTrMusmp.transform(xml); // ======================================================================================

		if (getById('btnAbort')) {
			getById('btnAbort').onclick = function() { selectSection('mysqlusers'); }
		}
		
		additionalFieldHandler();
		
		// attach handlers to ctrl command forms
		var forms = getAllByTag('form');
		for(i = 0; i < forms.length; i++) {
			forms[i].onsubmit = function() {
				executeMySQLCtrlCmd(this.getAttribute("id"), this.elements, true);
				return false;
			};
		}	
	}
};

sections['mysqledituser'] = {
	label: 'EditUser',
	queries: ['proxy-status'],
	repeat: false,
	handler: function(xml) {
		xsltTrMusmp.transform(xml); // ======================================================================================

		if (getById('btnAbort')) {
			getById('btnAbort').onclick = function() { selectSection('mysqlusers'); }
		}
		
		additionalFieldHandler();
		
		// attach handlers to ctrl command forms
		var forms = getAllByTag('form');
		for(i = 0; i < forms.length; i++) {
			forms[i].onsubmit = function() {
				executeMySQLCtrlCmd(this.getAttribute("id"), this.elements, true);
				return false;
			};
		}
	}
};

sections['mysqlimport'] = {
		label: 'Import',
		queries: ['proxy-status', 'mysql-import'],
		repeat: false,
		handler: function(xml) {
			xsltTrMusmp.transform(xml); // ======================================================================================

			additionalFieldHandler();
			
			// attach handlers to ctrl command forms
			var forms = getAllByTag('form');
			for(i = 0; i < forms.length; i++) {
				forms[i].onsubmit = function() {
					executeMySQLCtrlCmd(this.getAttribute("id"), this.elements, true);
					return false;
				};
			}
		}
	};

sections['mysqlprofiles'] = {
	label: 'MySQLProfiles',
	queries: ['proxy-status', 'mysql-profiles'],
	repeat: false,
	handler: function(xml) {
		xsltTrMusmp.transform(xml); // ======================================================================================

		if (getById('selectProfileName')) {
			getById('selectProfileName').onchange = function() { 
				if (getById('btnAddProfile')) getById('btnAddProfile').disabled = !(this.value == "NEW");
				if (getById('btnEditProfile')) getById('btnEditProfile').disabled = (this.value == "NEW");
				if (getById('btnDeleteProfile')) getById('btnDeleteProfile').disabled = (this.value == "NEW");
				if (getById('txtProfileName')) getById('txtProfileName').value = (this.value == "NEW") ? "" : 
					this.value.substring(this.value.indexOf("%") + 1);
				if (getById('txtProfileId')) getById('txtProfileId').value = (this.value == "NEW") ? "" : 
					this.value.substring(0, this.value.indexOf("%"));
			}
		}
		
		if (getById('btnAddProfile')) {
			getById('btnAddProfile').onclick = function() { executeAddProfile(getById('txtProfileName').value); }
		}
		
		if (getById('btnEditProfile')) {
			getById('btnEditProfile').onclick = function() { executeEditProfile(getById('txtProfileId').value, getById('txtProfileName').value); }
		}
		
		if (getById('btnDeleteProfile')) {
			getById('btnDeleteProfile').onclick = function() { executeDeleteProfile(getById('txtProfileId').value); }
		}
		
		if (getById('btnDeleteAllProfiles')) {
			getById('btnDeleteAllProfiles').onclick = function() { executeDeleteAllProfiles(); }
		}
		
	}
};

function additionalFieldHandler() {
	if(getById('chkbxAllProfiles')) {
		getById('chkbxAllProfiles').onclick = function(){
			var inputs = getAllByName('chkbxProfiles');
			for(var i = 0; i < inputs.length; i++) inputs[i].checked = this.checked;
		}
	}

	var inputs = getAllByTag('input', getById('profiles'));
	for(var i = 0; i < inputs.length; i++) {
		if(inputs[i].type == 'checkbox' && inputs[i].name == 'chkbxProfiles') {
			inputs[i].onclick = function(){
				var allChecked = true;
				var inputs = getAllByName('chkbxProfiles');
				for(var i = 0; i < inputs.length; i++) {
					if(inputs[i].type == 'checkbox') allChecked = allChecked && inputs[i].checked;
				}
				getById('chkbxAllProfiles').checked = allChecked;
			};
		}
	}

	// password color helper
	if (getById('inputPassword') && getById('inputPasswordRetyped')) {
		getById('inputPassword').onblur = verifyPassword;
		getById('inputPasswordRetyped').onblur = verifyPassword;
	}

	if (getById('btnReset')) {
		getById('btnReset').onclick = function() {
			if (getById('inputPasswordRetyped')) {
				getById('inputPasswordRetyped').style.backgroundColor = "";
			}
		}
	}
}

/* ############################################################################################ */
/* extended for profiles, reset and abort buttons												*/
/* ############################################################################################ */

function executeMySQLCtrlCmd(cmd, elements, includeProfiles) {
	var sessionId = getCookie('sessionId');
	if(sessionId != '') {
		var xml = '<cws-command-req ver="1.0">\n';
		xml += '<session session-id="' + sessionId + '"/>\n';
		xml += '<command command="' + cmd + '"';
		for(var i = 0; i < elements.length; i++) {
			if(elements[i].type == 'hidden' && elements[i].name == 'confirm' && elements[i].value == 'true')
				if(!confirm('Execute ' + cmd + ' command?')) return;
			if(elements[i].type != 'submit' && elements[i].type != 'reset' && elements[i].id != 'btnAbort') {  	  
				if(elements[i].type != 'checkbox') {
					xml += ' ' + elements[i].name + '="' + elements[i].value + '"';
				}
			}
		}
		if (includeProfiles) {
			xml += ' profile_ids="';
			var profiles = getSelectedProfileIds();
			for (var i = 0; i < profiles.length; i++) {
				xml += profiles[i] + ' ';
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

/* ############################################################################################ */
/* ctrl command elements																		*/
/* ############################################################################################ */

	function createElement(name, value) {
		var newElement = document.createElement('input');
		newElement.name = arguments[0];
		newElement.value = arguments[1];
		return newElement;
	}

	function createConfirmElement() {
		var newElement = createElement('confirm', 'true');
		newElement.type = 'hidden';
		return newElement;
	}

/* ############################################################################################ */
/* user ctrl commands																			*/
/* ############################################################################################ */

	function executeDeleteUser(userName) {
		executeCtrlCmd('mysql-delete-user', new Array(createConfirmElement(), createElement('username',arguments[0])), true);
	}

	function executeDeleteAllUsers() {
		executeCtrlCmd('mysql-delete-all-users', new Array(createConfirmElement()), true);
	}

/* ############################################################################################ */
/* profile ctrl commands																		*/
/* ############################################################################################ */

	function executeAddProfile(profileName) {
		executeCtrlCmd('mysql-add-profile', new Array(createElement('profilename',arguments[0])), true);
	}

	function executeEditProfile(id, profileName) {
		executeCtrlCmd('mysql-edit-profile', new Array(createElement('id',arguments[0]), createElement('profilename',arguments[1])), true);
	}

	function executeDeleteProfile(id) {
		executeCtrlCmd('mysql-delete-profile', new Array(createConfirmElement(), createElement('id',arguments[0])), true);
	}

	function executeDeleteAllProfiles() {
		executeCtrlCmd('mysql-delete-all-profiles', new Array(createConfirmElement()), true);
	}

/* ############################################################################################ */
/* some universal functions																		*/
/* ############################################################################################ */

	function getAllByName(name) {
		return document.getElementsByName(name);
	}

	function getSelectedUser() {
		var inputs = getAllByName('rdSelectedUser');
		for(var i = 0; i < inputs.length; i++) {
			if (inputs[i].checked) {
				return inputs[i].id;
			}
		}
	}

	function getSelectedProfileIds() {
		var inputs = getAllByName('chkbxProfiles');
		var ids = new Array();
		for(var i = 0; i < inputs.length; i++) {
			if(inputs[i].checked) ids.push(inputs[i].id);
		}
		return ids;
	}

	function verifyPassword() {
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

	function selectPage(pageNum) {
		sections.mysqlusers.queries[1] = 'mysql-users pageNum="' + pageNum + '"';
		clickSection('mysqlusers');
	}
	
/* ############################################################################################ */
/* plugin registration																			*/
/* ############################################################################################ */

	function mySQLWebManagementPluginPostProcess() {
		if(getCookie('isAdmin') == 'true') {
			var newLink = document.createElement('a'); // create a new link for this section in the menu
			newLink.href = '#';
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

