package macroeasy;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Plays steps with {@link Robot}. Returns false when stopped early. */
public final class Player {
    private static final int TAP_MS = 25;
    /** Esc after this is ignored, so a macro step that presses Esc does not stop itself. */
    private static final long ESCAPE_QUIET_NS = 250_000_000L;
    private final Robot robot;
    private final BooleanSupplier stop;
    private volatile long escapeQuietUntil;

    public Player(BooleanSupplier stop) throws java.awt.AWTException {
        this.robot = new Robot();
        this.robot.setAutoDelay(0);
        this.stop = stop;
    }

    /** {@code progress} receives the 1-based round and the 0-based step index. */
    public boolean play(List<Step> steps, int repeats, int intervalMs, BiConsumer<Integer, Integer> progress) {
        int times = Math.max(1, repeats);
        int shared = Math.max(0, intervalMs);
        boolean started = false;
        for (int round = 1; round <= times; round++) {
            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                if (started && step.kind != Step.Kind.WAIT) {
                    int pauseMs = step.gapMs >= 0 ? step.gapMs : shared;
                    if (!pause(pauseMs)) return false;
                }
                if (stopped()) return false;
                if (progress != null) progress.accept(round, i);
                if (!run(step)) return false;
                started = true;
            }
        }
        return !stopped();
    }

    private boolean run(Step step) {
        return switch (step.kind) {
            case CLICK -> click(step);
            case TEXT -> step.paste ? paste(step.text) : type(step.text);
            case KEYS -> chord(step.keys);
            case WAIT -> pause(step.waitMs);
            case FOCUS -> focus(step);
            case SCROLL -> scroll(step);
            case DRAG -> drag(step);
        };
    }

    /**
     * Presses at the start, holds briefly, nudges past the system's drag threshold, glides to the end
     * in moves of a few points and hovers there before releasing. Apps that need to see a drag begin,
     * or a drop target that reacts to the pointer resting over it, accept the drop this way.
     */
    private boolean drag(Step step) {
        if (!pause(40)) return false;
        robot.mouseMove(step.x, step.y);
        if (!pause(80)) return false;
        int mask = mask(step.button);
        boolean done = false;
        try {
            robot.mousePress(mask);
            if (!pause(DRAG_HOLD_MS)) return false;
            int[][] path = dragPath(step.x, step.y, step.x2, step.y2);
            for (int[] point : path) {
                if (stopped()) return false;
                robot.mouseMove(point[0], point[1]);
                if (!pause(DRAG_MOVE_MS)) return false;
            }
            // Rest over the target so it registers the hover, then drop.
            if (!pause(DRAG_HOVER_MS)) return false;
            done = true;
        } finally {
            robot.mouseRelease(mask);
        }
        return done && pause(80);
    }

    private static final int DRAG_HOLD_MS = 150;
    private static final int DRAG_MOVE_MS = 12;
    private static final int DRAG_HOVER_MS = 200;

    /**
     * Points the drag passes through: two small nudges to start it, then steps of about 8 points with
     * a gentle start and finish, ending one point short and then on the target itself.
     */
    static int[][] dragPath(int x, int y, int x2, int y2) {
        double dx = x2 - x;
        double dy = y2 - y;
        double length = Math.hypot(dx, dy);
        java.util.List<int[]> path = new java.util.ArrayList<>();
        if (length < 1) {
            path.add(new int[] {x2, y2});
            return path.toArray(new int[0][]);
        }
        double ux = dx / length;
        double uy = dy / length;
        double sx = x;
        double sy = y;
        for (int nudge : new int[] {2, 5}) {
            if (nudge * 2 >= length) break;
            sx = x + ux * nudge;
            sy = y + uy * nudge;
            path.add(new int[] {(int) Math.round(sx), (int) Math.round(sy)});
        }
        double rest = Math.hypot(x2 - sx, y2 - sy);
        int moves = (int) Math.max(12, Math.min(90, Math.ceil(rest / 8)));
        for (int i = 1; i <= moves; i++) {
            double t = (double) i / moves;
            double eased = t * t * (3 - 2 * t);
            path.add(new int[] {(int) Math.round(sx + (x2 - sx) * eased), (int) Math.round(sy + (y2 - sy) * eased)});
        }
        int[] last = path.get(path.size() - 1);
        if (last[0] != x2 || last[1] != y2) path.add(new int[] {x2, y2});
        path.add(path.size() - 1, new int[] {(int) Math.round(x2 - ux), (int) Math.round(y2 - uy)});
        return path.toArray(new int[0][]);
    }

    private static int mask(int button) {
        return switch (button) {
            case 3 -> InputEvent.BUTTON3_DOWN_MASK;
            case 2 -> InputEvent.BUTTON2_DOWN_MASK;
            default -> InputEvent.BUTTON1_DOWN_MASK;
        };
    }

    /** Pause between wheel notches. Steady pacing lets apps with smooth scrolling keep up, so each run lands in the same place. */
    private static final int NOTCH_MS = 30;
    /** Largest slice of a point scroll and the pause between slices. */
    private static final int POINT_SLICE = 20;
    private static final int POINT_MS = 8;
    /** Wait after the last notch for smooth-scroll animations to finish before the next step. */
    private static final int SCROLL_SETTLE_MS = 220;

    /**
     * On macOS a step measured in points is posted as exact point scrolling, in small even slices, so
     * nothing is accelerated or rounded and every run lands in the same place. Otherwise the wheel
     * rolls one notch at a time at a steady pace. Stop still reacts mid-way either way. Moving the
     * pointer first gives the app time to see what is under it.
     * On macOS {@link Robot#mouseWheel} rolls up for positive values, the opposite of its documentation,
     * so the sign is flipped there.
     */
    private boolean scroll(Step step) {
        if (step.scroll == 0) return true;
        if (step.move) {
            if (!pause(40)) return false;
            robot.mouseMove(step.x, step.y);
            if (!pause(80)) return false;
        }
        if (step.pixels && MacScroll.available()) {
            for (int part : MacScroll.slices(step.scroll, POINT_SLICE)) {
                if (stopped()) return false;
                MacScroll.post(part);
                if (!pause(POINT_MS)) return false;
            }
            return pause(SCROLL_SETTLE_MS);
        }
        int lines = step.scrollLines();
        int sign = (lines < 0) == isMac() ? 1 : -1;
        for (int left = Math.abs(lines); left > 0; left--) {
            if (stopped()) return false;
            robot.mouseWheel(sign);
            if (left > 1 && !pause(NOTCH_MS)) return false;
        }
        return pause(SCROLL_SETTLE_MS);
    }

    private boolean focus(Step step) {
        Windows.raise(step.appName(), step.windowTitle());
        return pause(120);
    }

    private boolean click(Step step) {
        if (!pause(40)) return false;
        robot.mouseMove(step.x, step.y);
        if (!pause(30)) return false;
        int mask = mask(step.button);
        int times = Math.max(1, step.clicks);
        for (int i = 0; i < times; i++) {
            if (stopped()) return false;
            try {
                robot.mousePress(mask);
                pause(TAP_MS);
            } finally {
                robot.mouseRelease(mask);
            }
            if (i + 1 < times && !pause(50)) return false;
        }
        return pause(40);
    }

    private boolean chord(int[] keys) {
        if (keys.length == 0) return true;
        boolean escape = false;
        for (int key : keys) escape |= key == KeyEvent.VK_ESCAPE;
        if (escape) escapeQuietUntil = Long.MAX_VALUE;
        try {
            for (int key : keys) {
                if (stopped()) return false;
                robot.keyPress(key);
                pause(12);
            }
            return pause(TAP_MS);
        } finally {
            for (int i = keys.length - 1; i >= 0; i--) {
                try {
                    robot.keyRelease(keys[i]);
                } catch (IllegalArgumentException ignored) {
                    // Unknown code: nothing held down under that id.
                }
            }
            if (escape) escapeQuietUntil = System.nanoTime() + ESCAPE_QUIET_NS;
        }
    }

    private boolean paste(String text) {
        if (text.isEmpty()) return true;
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable previous = readClipboard(clipboard);
        clipboard.setContents(new StringSelection(text), null);
        if (!pause(60)) {
            restore(clipboard, previous);
            return false;
        }
        int modifier = isMac() ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
        boolean ok = chord(new int[] {modifier, KeyEvent.VK_V});
        pause(Math.min(300, 80 + text.length() / 8));
        restore(clipboard, previous);
        return ok;
    }

    private boolean type(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (stopped()) return false;
            if (!typeChar(text.charAt(i))) return false;
        }
        return true;
    }

    private boolean typeChar(char c) {
        if (c == '\n') return tap(KeyEvent.VK_ENTER);
        if (c == '\t') return tap(KeyEvent.VK_TAB);
        if (c == ' ') return tap(KeyEvent.VK_SPACE);
        boolean shift = false;
        int code = KeyEvent.VK_UNDEFINED;
        char lower = Character.toLowerCase(c);
        if (lower >= 'a' && lower <= 'z') {
            code = KeyEvent.VK_A + (lower - 'a');
            shift = c != lower;
        } else if (c >= '0' && c <= '9') {
            code = KeyEvent.VK_0 + (c - '0');
        } else {
            code = KeyEvent.getExtendedKeyCodeForChar(c);
        }
        if (code == KeyEvent.VK_UNDEFINED) return paste(String.valueOf(c));
        if (!shift) return tap(code);
        robot.keyPress(KeyEvent.VK_SHIFT);
        try {
            return tap(code);
        } finally {
            robot.keyRelease(KeyEvent.VK_SHIFT);
        }
    }

    private boolean tap(int code) {
        robot.keyPress(code);
        pause(TAP_MS);
        robot.keyRelease(code);
        return pause(12);
    }

    /** Sleep in short slices so Stop and the corner failsafe react quickly. */
    public boolean pause(int ms) {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < end) {
            if (stopped()) return false;
            long left = Math.max(1, (end - System.nanoTime()) / 1_000_000L);
            try {
                Thread.sleep(Math.min(40, left));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !stopped();
    }

    private boolean stopped() {
        return stop.getAsBoolean() || cornerAbort() || escapeAbort();
    }

    /** Esc held down in any app stops the macro, except right after a step that pressed Esc itself. */
    private boolean escapeAbort() {
        return System.nanoTime() >= escapeQuietUntil && EscapeKey.down();
    }

    /** The pointer in the top-left corner of any connected screen stops the macro. */
    public static boolean cornerAbort() {
        var info = java.awt.MouseInfo.getPointerInfo();
        if (info == null) return false;
        var point = info.getLocation();
        for (var device : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle screen = device.getDefaultConfiguration().getBounds();
            if (inCorner(point.x, point.y, screen)) return true;
        }
        return false;
    }

    static boolean inCorner(int x, int y, Rectangle screen) {
        return x >= screen.x && y >= screen.y && x <= screen.x + 8 && y <= screen.y + 8;
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static Transferable readClipboard(Clipboard clipboard) {
        try {
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object data = contents.getTransferData(DataFlavor.stringFlavor);
                if (data instanceof String text) return new StringSelection(text);
            }
        } catch (Exception ignored) {
            // Clipboard busy or non-text. Leave it alone on restore.
        }
        return null;
    }

    private static void restore(Clipboard clipboard, Transferable previous) {
        if (previous == null) return;
        try {
            clipboard.setContents(previous, null);
        } catch (IllegalStateException ignored) {
            // Another app owns the clipboard. The pasted text can stay.
        }
    }
}
