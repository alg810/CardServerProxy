package com.bowman.cardserv.util;

import com.bowman.xml.XMLEncoder;

/**
 * Created by IntelliJ IDEA.
 * User: bowman
 * Date: Feb 6, 2008
 * Time: 8:01:50 PM 
 */
public class XmlStringBuffer {

  private StringBuffer sb;
  private String contents;

  public XmlStringBuffer() {
    this.sb = new StringBuffer();
  }

  public XmlStringBuffer(StringBuffer sb) {
    this.sb = sb;
  }

  public XmlStringBuffer appendElement(String name) {
    return appendElement(name, new String[0], new String[0], false);
  }

  public XmlStringBuffer appendElement(String name, String attrib, int value) {
    return appendElement(name, new String[] {attrib}, new String[] {String.valueOf(value)}, false);
  }

  public XmlStringBuffer appendElement(String name, String attrib, String value) {
    return appendElement(name, new String[] {attrib}, new String[] {value}, false);
  }

  public XmlStringBuffer appendElement(String name, String attrib, String value, boolean close) {
    return appendElement(name, new String[] {attrib}, new String[] {value}, close);
  }

  public XmlStringBuffer appendElement(String name, String[] attribs, String[] values, boolean close) {
    sb.append('<').append(name);
    if(attribs != null && attribs.length > 0) {
      for(int i = 0; i < attribs.length; i++) {
        if(values.length > i && values[i] != null) appendAttr(attribs[i], values[i]);
      }
    }
    if(close) sb.append(" /");    
    sb.append(">\n");
    return this;
  }

  public XmlStringBuffer appendAttr(String name, boolean value) {
    return appendAttr(name, String.valueOf(value));
  }

  public XmlStringBuffer appendAttr(String name, int value) {
    return appendAttr(name, String.valueOf(value));
  }

  public XmlStringBuffer appendAttr(String name, long value) {
    return appendAttr(name, String.valueOf(value));
  }

  public XmlStringBuffer appendAttr(String name, String value) {
    if(value == null) return this;
    if(sb.charAt(sb.length() - 1) == '\n') sb.deleteCharAt(sb.length() - 1);
    if(sb.charAt(sb.length() - 1) == '>') sb.deleteCharAt(sb.length() - 1);
    if(sb.charAt(sb.length() - 1) == '/') sb.deleteCharAt(sb.length() - 1);
    if(sb.charAt(sb.length() - 1) == ' ') sb.deleteCharAt(sb.length() - 1);
    sb.append(' ').append(name).append("=\"").append(XMLEncoder.encode(value)).append('"');
    return this;
  }

  public XmlStringBuffer appendText(String text) {
    sb.append(XMLEncoder.encode(text));
    return this;
  }

  public XmlStringBuffer appendCdata(String text) {
    sb.append("<![CDATA[").append(text).append("]]>");
    return this;
  }

  public XmlStringBuffer closeElement() {
    sb.append(" />\n");
    return this;
  }

  public XmlStringBuffer closeElement(String name) {
    sb.append("</").append(name).append(">\n");
    return this;
  }

  public XmlStringBuffer endElement(boolean close) {
    if(close) return closeElement();
    sb.append(">\n");
    return this;
  }

  public String toString() {
    if(contents == null) return sb.toString();
    else return contents;
  }

  public void setContents(String contents) {
    this.contents = contents;
  }
}
