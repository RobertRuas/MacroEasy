package macroeasy;

/** Adds up wheel movement, fractions of a line included, into one scroll step. */
final class ScrollTally {
    private boolean active;
    private double total;
    private int x;
    private int y;
    private int gapMs;
    /** Whether the step was already handed out, so later movement replaces it. */
    boolean shown;

    /** Whether {@code lines} belongs to the open step: same direction and soon enough after the last movement. */
    boolean continues(double lines, long sinceLastMs, long mergeMs) {
        return active && sinceLastMs < mergeMs && Math.signum(total) == Math.signum(lines);
    }

    void start(int x, int y, int gapMs) {
        active = true;
        total = 0;
        shown = false;
        this.x = x;
        this.y = y;
        this.gapMs = gapMs;
    }

    /** Returns the step for the distance so far, or null while it still rounds to zero lines. */
    Step add(double lines) {
        total += lines;
        int rounded = (int) Math.round(total);
        if (rounded == 0) return null;
        return Step.scroll(x, y, rounded, true).withGap(gapMs);
    }

    void reset() {
        active = false;
        total = 0;
        shown = false;
    }
}
