package org.json;

import java.util.ArrayList;
import java.util.List;

/** Minimal deterministic JSON boundary adapter for primitive message arguments only.
 * This is not an Android JSON compatibility test. The actual PhoneMessages codec,
 * resource selection, nesting, formatting, bounds and routing run unchanged. */
public final class JSONArray {
    private final List<Object> values = new ArrayList<>();
    public JSONArray() {}
    public JSONArray(String json) throws JSONException {
        Parser input = new Parser(json);
        input.expect('[');
        if (!input.consume(']')) {
            do { values.add(input.value()); } while (input.consume(','));
            input.expect(']');
        }
        input.whitespace();
        if (input.at != json.length()) throw new JSONException("Trailing JSON data");
    }
    public JSONArray put(Object value) {
        if (!(value instanceof String || value instanceof Number || value instanceof Boolean) && value != null)
            throw new IllegalArgumentException("Only primitive test values are supported");
        values.add(value); return this;
    }
    public int length() { return values.size(); }
    public Object get(int index) throws JSONException {
        if (index < 0 || index >= values.size()) throw new JSONException("Array index");
        return values.get(index);
    }
    public int getInt(int index) throws JSONException {
        Object value = get(index);
        if (!(value instanceof Number)) throw new JSONException("Expected number");
        return ((Number)value).intValue();
    }
    @Override public String toString() {
        StringBuilder text = new StringBuilder("[");
        for (Object value : values) {
            if (text.length() > 1) text.append(',');
            if (!(value instanceof String)) { text.append(value); continue; }
            text.append('"');
            for (char c : ((String)value).toCharArray()) {
                if (c == '"' || c == '\\') text.append('\\').append(c);
                else if (c < 32) text.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
                else text.append(c);
            }
            text.append('"');
        }
        return text.append(']').toString();
    }
    private static final class Parser {
        final String text; int at;
        Parser(String text) { this.text = text; }
        void whitespace() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
        boolean consume(char c) {
            whitespace();
            if (at < text.length() && text.charAt(at) == c) { at++; return true; }
            return false;
        }
        void expect(char c) throws JSONException { if (!consume(c)) throw new JSONException("Expected " + c); }
        Object value() throws JSONException {
            whitespace();
            if (at == text.length()) throw new JSONException("Missing value");
            if (text.charAt(at) == '"') return string();
            int start = at;
            while (at < text.length() && text.charAt(at) != ',' && text.charAt(at) != ']') at++;
            String word = text.substring(start, at).trim();
            if (word.equals("null")) return null;
            if (word.equals("true") || word.equals("false")) return Boolean.valueOf(word);
            try {
                if (word.contains(".") || word.contains("e") || word.contains("E")) return Double.valueOf(word);
                return Long.valueOf(word);
            } catch (NumberFormatException failure) { throw new JSONException("Invalid primitive"); }
        }
        String string() throws JSONException {
            expect('"'); StringBuilder value = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') return value.toString();
                if (c != '\\') { value.append(c); continue; }
                if (at == text.length()) throw new JSONException("Truncated escape");
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw new JSONException("Truncated Unicode escape");
                        try { value.append((char)Integer.parseInt(text.substring(at, at + 4), 16)); }
                        catch (NumberFormatException failure) { throw new JSONException("Invalid Unicode escape"); }
                        at += 4;
                    }
                    default -> throw new JSONException("Invalid escape");
                }
            }
            throw new JSONException("Unterminated string");
        }
    }
}
