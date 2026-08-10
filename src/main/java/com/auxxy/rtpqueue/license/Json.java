package com.auxxy.rtpqueue.license;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tiny JSON reader/writer with no external dependencies.
 *
 * <p>The writer reproduces JavaScript's {@code JSON.stringify} byte-for-byte for
 * the value types the license server sends, which is what lets the plugin
 * rebuild the exact bytes that were signed with Ed25519.</p>
 *
 * MADE BY AUXXY
 */
public final class Json {

    private Json() {
    }

    /* ------------------------------------------------------------ parsing */

    /** Parses a JSON object into a map. Values are String, Double, Boolean, Map, List or null. */
    public static Map<String, Object> parseObject(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.readValue();
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object at the top level");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object readValue() {
            skipWhitespace();
            if (i >= s.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private void expect(String literal) {
            if (!s.startsWith(literal, i)) {
                throw new IllegalArgumentException("Malformed JSON near index " + i);
            }
            i += literal.length();
        }

        // TreeMap keeps keys sorted, which is exactly the order the signature uses.
        private Map<String, Object> readObject() {
            Map<String, Object> map = new TreeMap<>();
            i++; // {
            skipWhitespace();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return map;
            }
            while (i < s.length()) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                if (s.charAt(i) != ':') {
                    throw new IllegalArgumentException("Expected ':' at index " + i);
                }
                i++;
                map.put(key, readValue());
                skipWhitespace();
                char c = s.charAt(i);
                i++;
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at index " + (i - 1));
                }
            }
            throw new IllegalArgumentException("Unterminated JSON object");
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            i++; // [
            skipWhitespace();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (i < s.length()) {
                list.add(readValue());
                skipWhitespace();
                char c = s.charAt(i);
                i++;
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at index " + (i - 1));
                }
            }
            throw new IllegalArgumentException("Unterminated JSON array");
        }

        private String readString() {
            if (s.charAt(i) != '"') {
                throw new IllegalArgumentException("Expected a string at index " + i);
            }
            i++;
            StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char esc = s.charAt(i++);
                switch (esc) {
                    case '"':  out.append('"');  break;
                    case '\\': out.append('\\'); break;
                    case '/':  out.append('/');  break;
                    case 'b':  out.append('\b'); break;
                    case 'f':  out.append('\f'); break;
                    case 'n':  out.append('\n'); break;
                    case 'r':  out.append('\r'); break;
                    case 't':  out.append('\t'); break;
                    case 'u':
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Bad escape \\" + esc);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Double readNumber() {
            int start = i;
            while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            return Double.valueOf(s.substring(start, i));
        }
    }

    /* ---------------------------------------------------------- writing */

    /**
     * Serialises a map with keys in sorted order and no whitespace, matching
     * {@code JSON.stringify(obj, Object.keys(obj).sort())} on the server.
     */
    public static String canonical(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : new TreeMap<>(map).entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, e.getKey());
            sb.append(':');
            writeValue(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Number) {
            sb.append(number((Number) value));
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) value;
            sb.append(canonical(m));
        } else if (value instanceof List) {
            sb.append('[');
            List<?> list = (List<?>) value;
            for (int k = 0; k < list.size(); k++) {
                if (k > 0) {
                    sb.append(',');
                }
                writeValue(sb, list.get(k));
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    /**
     * Renders numbers the way JavaScript does: integral doubles lose the ".0".
     *
     * <p>Values at or above 1e21 would need JavaScript's exponent formatting to
     * match exactly. The license server only ever signs small integers
     * (timestamps, counts), so that range is unreachable in practice.</p>
     */
    private static String number(Number n) {
        double d = n.doubleValue();
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e21) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    /** Escapes exactly as JSON.stringify does - notably leaving non-ASCII untouched. */
    private static void writeString(StringBuilder sb, String value) {
        sb.append('"');
        for (int k = 0; k < value.length(); k++) {
            char c = value.charAt(k);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** Convenience readers that tolerate missing or mistyped fields. */
    public static String string(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    public static boolean bool(Map<String, Object> map, String key) {
        return Boolean.TRUE.equals(map.get(key));
    }

    public static long number(Map<String, Object> map, String key, long fallback) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }
}
