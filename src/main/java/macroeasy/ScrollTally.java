package macroeasy;

/**
 * Adds up wheel movement into one scroll step. When the system reports the distance in screen
 * points (macOS), the step keeps the exact points; otherwise it keeps lines, fractions included.
 */
final class ScrollTally {
    private boolean active;
    private double total;
    private long points;
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
        points = 0;
        shown = false;
        this.x = x;
        this.y = y;
        this.gapMs = gapMs;
    }

    /** Lines only. Returns the step for the distance so far, or null while it still rounds to zero lines. */
    Step add(double lines) {
        return add(lines, 0);
    }

    /**
     * {@code lines} and {@code points} describe the same movement, positive rolling down.
     * Returns the step for the distance so far, or null while it is still zero.
     */
    Step add(double lines, long points) {
        total += lines;
        this.points += points;
        if (this.points != 0) {
            int capped = (int) Math.max(-1_000_000, Math.min(1_000_000, this.points));
            return Step.scrollPoints(x, y, capped, true).withGap(gapMs);
        }
        int rounded = (int) Math.round(total);
        if (rounded == 0) return null;
        return Step.scroll(x, y, rounded, true).withGap(gapMs);
    }

    void reset() {
        active = false;
        total = 0;
        points = 0;
        shown = false;
    }
}
