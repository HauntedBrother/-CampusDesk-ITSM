package edu.university.itsm.reporting;
import java.util.*;
public final class Csv {
    private Csv() {}
    /** RFC 4180 quoting plus spreadsheet formula neutralization for untrusted text. */
    public static String row(Object... values){return Arrays.stream(values).map(Csv::cell).collect(java.util.stream.Collectors.joining(","))+"\r\n";}
    private static String cell(Object value){String text=value==null?"":value.toString();String leading=text.stripLeading();if(!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0))>=0)text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
}
