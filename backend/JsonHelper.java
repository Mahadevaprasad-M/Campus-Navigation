/* =====================================================================
 * FILE:    JsonHelper.java
 * PROJECT: Campus Navigation System
 * COLLEGE: Government Engineering College, Chamarajanagar
 * PURPOSE: Small helper to build JSON strings without any library.
 *          We only need three things:
 *            1. escape()  - make a text safe for JSON
 *            2. str()     - wrap a text in double quotes
 *            3. obj()     - build an object { "key": value, ... }
 *          We use a simple StringBuilder and String.replace().
 * ===================================================================== */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class JsonHelper {

    /* -----------------------------------------------------------------
     * 1. ESCAPE TEXT
     * -----------------------------------------------------------------
     * JSON needs certain characters to be "escaped".
     * Example: a double quote inside a string must become \" .
     * This method replaces all such characters.
     * ----------------------------------------------------------------- */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }

        String result = text;

        // Order matters: backslash first, then others.
        result = result.replace("\\", "\\\\");   // \   ->  \\
        result = result.replace("\"", "\\\"");   // "   ->  \"
        result = result.replace("\n", "\\n");    // newline  ->  \n
        result = result.replace("\r", "\\r");    // carriage return  ->  \r
        result = result.replace("\t", "\\t");    // tab  ->  \t
        result = result.replace("\b", "\\b");    // backspace  ->  \b
        result = result.replace("\f", "\\f");    // form feed  ->  \f

        return result;
    }

    /* -----------------------------------------------------------------
     * 2. WRAP A STRING IN QUOTES
     * ----------------------------------------------------------------- */
    public static String str(String text) {
        if (text == null) {
            return "null";
        }
        return "\"" + escape(text) + "\"";
    }

    /* -----------------------------------------------------------------
     * 3. CONVERT OTHER TYPES
     * ----------------------------------------------------------------- */

    public static String num(int value) {
        return Integer.toString(value);
    }

    public static String num(long value) {
        return Long.toString(value);
    }

    public static String num(double value) {
        // Avoid scientific notation for simple cases.
        if (value == Math.floor(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    public static String bool(boolean value) {
        if (value) {
            return "true";
        }
        return "false";
    }

    /* -----------------------------------------------------------------
     * 4. CONVERT ANY OBJECT TO JSON
     * -----------------------------------------------------------------
     * We check the type and call the right helper.
     * This is a beginner-friendly "if-else ladder".
     * ----------------------------------------------------------------- */
    public static String value(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String) {
            return str((String) obj);
        }
        if (obj instanceof Integer) {
            return num(((Integer) obj).intValue());
        }
        if (obj instanceof Long) {
            return num(((Long) obj).longValue());
        }
        if (obj instanceof Double) {
            return num(((Double) obj).doubleValue());
        }
        if (obj instanceof Float) {
            return num(((Float) obj).doubleValue());
        }
        if (obj instanceof Boolean) {
            return bool(((Boolean) obj).booleanValue());
        }
        if (obj instanceof ArrayList) {
            return array((ArrayList) obj);
        }
        if (obj instanceof HashMap) {
            return obj((HashMap) obj);
        }
        if (obj instanceof LinkedHashMap) {
            return obj((LinkedHashMap) obj);
        }
        // Fallback: convert to String.
        return str(obj.toString());
    }

    /* -----------------------------------------------------------------
     * 5. BUILD A JSON OBJECT  { "key": value, ... }
     * -----------------------------------------------------------------
     * We accept a LinkedHashMap so the order of keys is preserved
     * (LinkedHashMap remembers the insertion order).
     * ----------------------------------------------------------------- */
    public static String obj(LinkedHashMap<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for (String key : map.keySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;

            sb.append(str(key));
            sb.append(":");
            sb.append(value(map.get(key)));
        }

        sb.append("}");
        return sb.toString();
    }

    // A second obj() for plain HashMap, in case caller uses it.
    public static String obj(HashMap<String, Object> map) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<String, Object>();
        for (String key : map.keySet()) {
            copy.put(key, map.get(key));
        }
        return obj(copy);
    }

    /* -----------------------------------------------------------------
     * 6. BUILD A JSON ARRAY  [ value, value, ... ]
     * ----------------------------------------------------------------- */
    public static String array(ArrayList list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(value(list.get(i)));
        }

        sb.append("]");
        return sb.toString();
    }

    /* -----------------------------------------------------------------
     * 7. PRETTY PRINT  (optional, for debugging)
     * -----------------------------------------------------------------
     * Adds newlines and spaces so the output is readable in a browser.
     * Very simple version - just indents after { and [ , and before
     * closing } or ].
     * ----------------------------------------------------------------- */
    public static String pretty(String compact) {
        if (compact == null || compact.length() == 0) {
            return compact;
        }

        StringBuilder out = new StringBuilder();
        int indent = 0;
        boolean insideString = false;

        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);

            if (c == '"' && (i == 0 || compact.charAt(i - 1) != '\\')) {
                insideString = !insideString;
                out.append(c);
                continue;
            }

            if (insideString) {
                out.append(c);
                continue;
            }

            if (c == '{' || c == '[') {
                out.append(c);
                indent = indent + 1;
                out.append("\n");
                out.append(spaces(indent * 2));
            } else if (c == '}' || c == ']') {
                indent = indent - 1;
                if (indent < 0) {
                    indent = 0;
                }
                out.append("\n");
                out.append(spaces(indent * 2));
                out.append(c);
            } else if (c == ',') {
                out.append(c);
                out.append("\n");
                out.append(spaces(indent * 2));
            } else if (c == ':') {
                out.append(": ");
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }

    // Helper: return a string with N spaces.
    private static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }
}