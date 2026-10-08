package macroeasy;

import java.awt.event.KeyEvent;
import java.util.Arrays;

/** One macro action. Fields unused by a kind stay at their defaults. */
public final class Step {
    public enum Kind { CLICK, TEXT, KEYS, WAIT, FOCUS, SCROLL }

    public final Kind kind;
    public final int x;
    public final int y;
    public final int button;
    public final int clicks;
    public final String text;
    public final boolean paste;
    public final int[] keys;
    public final int waitMs;
    /** Wheel lines for SCROLL. Positive rolls down, negative rolls up. */
    public final int scroll;
    /** Whether SCROLL moves the pointer to x, y before rolling. */
    public final boolean move;
    /** Pause before this step, in milliseconds. Negative means the shared interval. */
    public final int gapMs;

    private Step(Kind kind, int x, int y, int button, int clicks,
                 String text, boolean paste, int[] keys, int waitMs,
                 int scroll, boolean move, int gapMs) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.button = button;
        this.clicks = clicks;
        this.text = text == null ? "" : text;
        this.paste = paste;
        this.keys = keys == null ? new int[0] : keys;
        this.waitMs = waitMs;
        this.scroll = scroll;
        this.move = move;
        this.gapMs = gapMs;
    }

    public static Step click(int x, int y, int button, int clicks) {
        return new Step(Kind.CLICK, x, y, button, Math.max(1, clicks), "", true, null, 0, 0, false, -1);
    }

    public static Step text(String text, boolean paste) {
        return new Step(Kind.TEXT, 0, 0, 1, 1, text, paste, null, 0, 0, false, -1);
    }

    public static Step keys(int[] keys) {
        return new Step(Kind.KEYS, 0, 0, 1, 1, "", true, Arrays.copyOf(keys, keys.length), 0, 0, false, -1);
    }

    public static Step wait(int ms) {
        return new Step(Kind.WAIT, 0, 0, 1, 1, "", true, null, Math.max(0, ms), 0, false, -1);
    }

    /** Bring an already open application window to the front. */
    public static Step focus(String app, String window) {
        String title = window == null ? "" : window;
        return new Step(Kind.FOCUS, 0, 0, 1, 1, (app == null ? "" : app) + "\n" + title, true, null, 0, 0, false, -1);
    }

    /** Roll the mouse wheel. {@code lines} > 0 rolls down; {@code move} points the mouse at x, y first. */
    public static Step scroll(int x, int y, int lines, boolean move) {
        return new Step(Kind.SCROLL, x, y, 1, 1, "", true, null, 0, lines, move, -1);
    }

    public Step withGap(int gapMs) {
        return new Step(kind, x, y, button, clicks, text, paste, keys, waitMs, scroll, move, gapMs);
    }

    public String appName() {
        int split = text.indexOf('\n');
        return split < 0 ? text : text.substring(0, split);
    }

    public String windowTitle() {
        int split = text.indexOf('\n');
        return split < 0 ? "" : text.substring(split + 1);
    }

    public String describe() {
        String label = switch (kind) {
            case CLICK -> clickLabel();
            case TEXT -> "Digitar  " + clip(text.replace('\n', '↵'));
            case KEYS -> "Atalho  " + keyLabel(keys);
            case WAIT -> "Esperar  " + waitMs + " ms";
            case FOCUS -> focusLabel();
            case SCROLL -> scrollLabel();
        };
        return gapMs >= 0 && kind != Kind.WAIT ? label + "    " + gapMs + " ms" : label;
    }

    private String scrollLabel() {
        String label = "Rolar " + (scroll < 0 ? "para cima" : "para baixo") + "  " + Math.abs(scroll);
        return move ? label + "  em " + x + ", " + y : label;
    }

    private String focusLabel() {
        String title = windowTitle();
        return title.isBlank() ? "Janela  " + appName() : "Janela  " + appName() + "  —  " + clip(title);
    }

    private String clickLabel() {
        String which = switch (button) {
            case 3 -> "direito";
            case 2 -> "meio";
            default -> "esquerdo";
        };
        String times = clicks > 1 ? "duplo " : "";
        return "Clique " + times + which + "  " + x + ", " + y;
    }

    public String encode() {
        String body = switch (kind) {
            case CLICK -> "CLICK " + x + " " + y + " " + button + " " + clicks;
            case TEXT -> "TEXT " + (paste ? "P " : "K ") + escape(text);
            case KEYS -> "KEYS " + join(keys);
            case WAIT -> "WAIT " + waitMs;
            case FOCUS -> "FOCUS " + escape(text);
            case SCROLL -> "SCROLL " + scroll + " " + x + " " + y + " " + (move ? 1 : 0);
        };
        return body + "|g" + gapMs;
    }

    public static Step parse(String line) {
        if (line == null || line.isBlank() || line.startsWith("MACROEASY")) return null;
        int gap = -1;
        int mark = line.lastIndexOf("|g");
        if (mark >= 0 && line.substring(mark + 2).matches("-?\\d+")) {
            gap = Integer.parseInt(line.substring(mark + 2));
            line = line.substring(0, mark);
        }
        String[] p = line.split(" ", 5);
        try {
            Step step = switch (p[0]) {
                case "CLICK" -> click(
                        Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                case "TEXT" -> text(unescape(line.substring(7)), line.charAt(5) == 'P');
                case "KEYS" -> keys(parseKeys(p[1]));
                case "WAIT" -> wait(Integer.parseInt(p[1]));
                case "FOCUS" -> focusFrom(unescape(line.substring(6)));
                case "SCROLL" -> scroll(
                        Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                        Integer.parseInt(p[1]), !"0".equals(p[4]));
                default -> null;
            };
            return step == null ? null : step.withGap(gap);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Step focusFrom(String value) {
        int split = value.indexOf('\n');
        if (split < 0) return focus(value, "");
        return focus(value.substring(0, split), value.substring(split + 1));
    }

    static String keyLabel(int[] keys) {
        if (keys.length == 0) return "—";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) sb.append(" + ");
            sb.append(switch (keys[i]) {
                case KeyEvent.VK_META -> "⌘";
                case KeyEvent.VK_CONTROL -> "Ctrl";
                case KeyEvent.VK_ALT -> "Alt";
                case KeyEvent.VK_SHIFT -> "Shift";
                default -> KeyEvent.getKeyText(keys[i]);
            });
        }
        return sb.toString();
    }

    private static String clip(String value) {
        return value.length() > 48 ? value.substring(0, 48) + "…" : value;
    }

    private static String join(int[] keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(keys[i]);
        }
        return sb.toString();
    }

    private static int[] parseKeys(String raw) {
        String[] parts = raw.split(",");
        int[] keys = new int[parts.length];
        for (int i = 0; i < parts.length; i++) keys[i] = Integer.parseInt(parts[i]);
        return keys;
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "");
    }

    static String unescape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char n = value.charAt(++i);
                sb.append(n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
