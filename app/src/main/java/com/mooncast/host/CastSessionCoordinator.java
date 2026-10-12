package com.mooncast.host;

/** Pure state machine: a tab change cannot transfer a capture grant or outrun host teardown. */
final class CastSessionCoordinator {
    static final int MOONLIGHT = 0, AIRPLAY = 1, BROWSER = 2;
    static final int IDLE = 0, PERMISSION = 1, STARTING = 2, RUNNING = 3, STOPPING = 4;
    private int selected, active = -1, target = -1, phase = IDLE, generation;

    CastSessionCoordinator(int selected) { this.selected = validMode(selected); }
    CastSessionCoordinator(int selected, int active, int target, int phase, int generation) {
        this.selected = validMode(selected);
        this.active = active < 0 ? -1 : validMode(active);
        this.target = target < 0 ? -1 : validMode(target);
        this.phase = phase >= IDLE && phase <= STOPPING ? phase : IDLE;
        this.generation = Math.max(0, generation);
        if (this.phase >= STARTING && this.active < 0) this.phase = IDLE;
    }
    int selected() { return selected; }
    int active() { return active; }
    int target() { return target; }
    int phase() { return phase; }
    int generation() { return generation; }
    boolean busy() { return phase != IDLE; }
    boolean hasHost() { return active >= 0 && phase >= STARTING; }
    int begin() {
        if (busy()) return -1;
        // Activity request codes are bounded; do not recycle within the practical process lifetime.
        if (generation >= 29000) return -1;
        phase = PERMISSION;
        return ++generation;
    }
    boolean accepts(int token, int mode) { return phase == PERMISSION && generation == token && selected == mode; }
    boolean start(int token, int mode) {
        if (!accepts(token, mode)) return false;
        active = selected;
        phase = STARTING;
        return true;
    }
    void connected() { if (phase == STARTING) phase = RUNNING; }
    void restoreHost(int mode) { if (phase == IDLE) { active = selected = validMode(mode); phase = STARTING; } }
    /** True means STOP must be sent; selected changes only after hostGone. */
    boolean select(int mode) {
        mode = validMode(mode);
        if (phase == STOPPING) return false;
        if (mode == selected) return false;
        ++generation; // Any later permission result belongs to the old tab.
        if (hasHost()) { target = mode; phase = STOPPING; return true; }
        selected = mode; active = target = -1; phase = IDLE;
        return false;
    }
    boolean stop() {
        if (phase == STOPPING) return false;
        ++generation;
        if (hasHost()) { phase = STOPPING; return true; }
        phase = IDLE; active = target = -1;
        return false;
    }
    void denied(int token) { if (phase == PERMISSION && token == generation) phase = IDLE; }
    void hostGone() {
        if (target >= 0) selected = target;
        active = target = -1;
        phase = IDLE;
        ++generation;
    }
    private static int validMode(int mode) { return mode == AIRPLAY || mode == BROWSER ? mode : MOONLIGHT; }
}
