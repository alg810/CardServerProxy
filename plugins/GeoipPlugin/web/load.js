
// insert some extra code to run after the regular postprocessing for the "sessions" section
var oldHandler = sections.sessions.handler;

sections.sessions.handler = function(xml) {
  oldHandler(xml); // call the regular handler and let it do its thing

  // add a link before the checkbox
  var cb = document.getElementById('hideInactiveCb');
  if(cb) {
    var d = document.createElement("div");
    d.innerHTML = '<strong>Geoip estimated locations: </strong> <a href="/plugin/geoipplugin/googlemap.html" target="gmaps">show map</a>&nbsp;(Using database: ' + dbInfo + ')<br /><br />';
    cb.parentNode.insertBefore(d, cb);    
  }

  // make a lookup table from the raw xml (which contains the proxy-users reply) : username -> city
  var users = xml.getElementsByTagName('user');
  var lookup = {};
  for(var i = 0; i < users.length; i++) {
    lookup[users[i].getAttribute('name')] = users[i].getAttribute('geoip-city');
  }

  // insert city into the first column
  var tbl = document.getElementById('userSessions');  
  if(tbl) { 
    var cells = tbl.getElementsByTagName('td');
    var as;
    for(i = 0; i < cells.length; i++) {
      as = cells[i].getElementsByTagName("a");      
      if(as.length > 0 && lookup[as[0].getAttribute('id')]) {
        // this is the table cell with the href, i.e as[0].innerHTML is the user name, add the city guess
        var city = document.createTextNode(' - ' + lookup[as[0].getAttribute('id')]);
        as[0].parentNode.appendChild(city);
      }
    }
  }
  
}