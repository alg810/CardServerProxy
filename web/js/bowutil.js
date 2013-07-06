/**
 * bowutil.js: Misc utility functions, using global symbol "BowUtil" as a namespace object
 **/

var BowUtil = {};

BowUtil.formatDateTime = function(dateStr) {
  var date = new Date(dateStr);
  return BowUtil.formatDate(date) + " " + BowUtil.formatTime(date);
}

BowUtil.formatDate = function(date) {
  return date.getFullYear() + "-" + BowUtil.padString(date.getMonth() + 1, 2) + "-" + BowUtil.padString(date.getDate(), 2);
}

BowUtil.formatTime = function(date) {
  return "" + BowUtil.padString(date.getHours(), 2) + ":" + BowUtil.padString(date.getMinutes(), 2) + ":" + BowUtil.padString(date.getSeconds(), 2);  
}

BowUtil.padString = function(s, len) {
  s = "" + s;
  while(s.length < len) s = "0" + s;
  return s;
}




