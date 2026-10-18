package macroeasy;

import java.awt.Dimension;

/** How big the main window opens, so it leaves room for the app the macro drives. */
final class WindowSize {
    static final int MIN_WIDTH = 820;
    static final int MIN_HEIGHT = 500;

    private WindowSize() {}

    /** About two thirds of the screen, never smaller than the minimum and never larger than needed. */
    static Dimension defaultSize(int screenWidth, int screenHeight) {
        int width = Math.max(MIN_WIDTH, Math.min(980, screenWidth * 2 / 3));
        int height = Math.max(MIN_HEIGHT, Math.min(600, screenHeight * 2 / 3));
        return new Dimension(width, height);
    }
}
