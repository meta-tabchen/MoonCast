package org.json;
/** Network probing is outside these callback tests; accidental invocation must fail. */
public final class JSONObject {
    public JSONObject(String value) { throw new UnsupportedOperationException("Probe not part of NSD callback tests"); }
    public String optString(String k, String d) { throw new UnsupportedOperationException(); }
    public String optString(String k) { throw new UnsupportedOperationException(); }
    public int optInt(String k) { throw new UnsupportedOperationException(); }
    public boolean optBoolean(String k) { throw new UnsupportedOperationException(); }
    public boolean getBoolean(String k) { throw new UnsupportedOperationException(); }
    public boolean has(String k) { throw new UnsupportedOperationException(); }
}
