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
    private final Robot robot;
    private final BooleanSupplier stop;

    public Player(BooleanSupplier stop) throws Exception {
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
        };
    }

    private boolean focus(Step step) {
        Windows.raise(step.appName(), step.windowTitle());
        return pause(120);
    }

    private boolean click(Step step) {
        if (!pause(40)) return false;
        robot.mouseMove(step.x, step.y);
        if (!pause(30)) return false;
        int mask = switch (step.button) {
            case 3 -> InputEvent.BUTTON3_DOWN_MASK;
            case 2 -> InputEvent.BUTTON2_DOWN_MASK;
            default -> InputEvent.BUTTON1_DOWN_MASK;
        };
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
        return stop.getAsBoolean() || cornerAbort();
    }

    public static boolean cornerAbort() {
        var info = java.awt.MouseInfo.getPointerInfo();
        if (info == null) return false;
        var point = info.getLocation();
        Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        return point.x <= screen.x + 8 && point.y <= screen.y + 8;
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
