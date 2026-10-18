package macroeasy;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import java.awt.Color;
import java.awt.Insets;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.swing.UIManager;

/** Modern flat look (FlatLaf), light or dark like the system. Falls back to the system look. */
final class Theme {
    private static boolean flat;
    private static boolean dark;

    private Theme() {}

    static void install() {
        String os = System.getProperty("os.name", "").toLowerCase();
        dark = systemDark(os);
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollPane.smoothScrolling", true);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("PopupMenu.borderCornerRadius", 8);
        UIManager.put("ToolTip.arc", 8);
        try {
            if (os.contains("mac")) {
                System.setProperty("apple.awt.application.appearance", "system");
                flat = dark ? FlatMacDarkLaf.setup() : FlatMacLightLaf.setup();
            } else {
                flat = dark ? FlatDarkLaf.setup() : FlatLightLaf.setup();
            }
        } catch (RuntimeException | LinkageError ex) {
            flat = false;
        }
        if (flat) softSelection();
        if (!flat) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Keep the cross-platform look if the system one is unavailable.
            }
        }
    }

    /** A light tint of the accent for selected rows, so the dark icons and text stay readable on it. */
    private static void softSelection() {
        Color background = UIManager.getColor("List.background");
        Color text = UIManager.getColor("List.foreground");
        if (background == null || text == null) return;
        Color tint = blend(accent(), background, dark ? 0.35f : 0.18f);
        UIManager.put("List.selectionBackground", tint);
        UIManager.put("List.selectionForeground", text);
        UIManager.put("List.selectionInactiveBackground", tint);
        UIManager.put("List.selectionInactiveForeground", text);
    }

    static Color blend(Color top, Color base, float amount) {
        return new Color(
                Math.round(top.getRed() * amount + base.getRed() * (1 - amount)),
                Math.round(top.getGreen() * amount + base.getGreen() * (1 - amount)),
                Math.round(top.getBlue() * amount + base.getBlue() * (1 - amount)));
    }

    static boolean flat() {
        return flat;
    }

    static boolean dark() {
        return dark;
    }

    /** The highlight color of the theme, used for the Executar button. */
    static Color accent() {
        Color color = UIManager.getColor("Component.accentColor");
        if (color == null) color = UIManager.getColor("Button.default.background");
        return color == null ? new Color(0, 122, 255) : color;
    }

    /** Text that should step back: hints, counters, the status line. */
    static Color muted() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color == null ? new Color(130, 130, 130) : color;
    }

    static Color line() {
        Color color = UIManager.getColor("Component.borderColor");
        if (color == null) color = UIManager.getColor("Separator.foreground");
        return color == null ? new Color(200, 200, 200) : color;
    }

    private static boolean systemDark(String os) {
        try {
            if (os.contains("mac")) return output("defaults", "read", "-g", "AppleInterfaceStyle").contains("Dark");
            if (os.contains("win")) {
                return output("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme").matches("(?s).*AppsUseLightTheme\\s+REG_DWORD\\s+0x0\\b.*");
            }
            String gtk = System.getenv("GTK_THEME");
            return gtk != null && gtk.toLowerCase().contains("dark");
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String output(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "";
        }
        return new String(process.getInputStream().readAllBytes());
    }
}
