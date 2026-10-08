package macroeasy;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

/** Small line icons drawn in the current text color. */
final class Icons {
    private Icons() {}

    static ImageIcon play() {
        return icon(g -> g.fillPolygon(new int[] {5, 5, 13}, new int[] {3, 13, 8}, 3));
    }

    static ImageIcon record() {
        return icon(g -> g.fillOval(4, 4, 8, 8));
    }

    static ImageIcon stop() {
        return icon(g -> g.fillRoundRect(4, 4, 8, 8, 2, 2));
    }

    static ImageIcon click() {
        return icon(g -> {
            g.drawLine(4, 3, 8, 13);
            g.drawLine(4, 3, 12, 7);
            g.drawLine(8, 13, 12, 7);
        });
    }

    static ImageIcon text() {
        return icon(g -> {
            g.drawLine(3, 4, 13, 4);
            g.drawLine(3, 8, 13, 8);
            g.drawLine(3, 12, 10, 12);
        });
    }

    static ImageIcon keys() {
        return icon(g -> {
            g.drawRoundRect(2, 4, 12, 8, 3, 3);
            g.drawLine(6, 7, 6, 9);
            g.drawLine(10, 7, 10, 9);
        });
    }

    static ImageIcon clock() {
        return icon(g -> {
            g.drawOval(3, 3, 10, 10);
            g.drawLine(8, 8, 8, 5);
            g.drawLine(8, 8, 11, 9);
        });
    }

    static ImageIcon window() {
        return icon(g -> {
            g.drawRoundRect(2, 3, 12, 10, 2, 2);
            g.drawLine(2, 6, 14, 6);
        });
    }

    static ImageIcon scroll() {
        return icon(g -> {
            g.drawLine(8, 3, 8, 13);
            g.drawLine(5, 6, 8, 3);
            g.drawLine(8, 3, 11, 6);
            g.drawLine(5, 10, 8, 13);
            g.drawLine(8, 13, 11, 10);
        });
    }

    static ImageIcon edit() {
        return icon(g -> g.drawLine(3, 13, 13, 3));
    }

    static ImageIcon copy() {
        return icon(g -> {
            g.drawRoundRect(5, 3, 8, 8, 2, 2);
            g.drawRoundRect(3, 5, 8, 8, 2, 2);
        });
    }

    static ImageIcon trash() {
        return icon(g -> {
            g.drawLine(4, 5, 12, 5);
            g.drawLine(6, 3, 10, 3);
            g.drawRoundRect(4, 5, 8, 8, 1, 1);
        });
    }

    static ImageIcon up() {
        return icon(g -> {
            g.drawLine(4, 10, 8, 5);
            g.drawLine(8, 5, 12, 10);
        });
    }

    static ImageIcon down() {
        return icon(g -> {
            g.drawLine(4, 6, 8, 11);
            g.drawLine(8, 11, 12, 6);
        });
    }

    static ImageIcon plus() {
        return icon(g -> {
            g.drawLine(8, 3, 8, 13);
            g.drawLine(3, 8, 13, 8);
        });
    }

    static ImageIcon folder() {
        return icon(g -> {
            g.drawLine(3, 6, 6, 6);
            g.drawLine(6, 6, 8, 4);
            g.drawRoundRect(2, 6, 12, 7, 2, 2);
        });
    }

    static ImageIcon save() {
        return icon(g -> {
            g.drawRoundRect(3, 2, 10, 12, 2, 2);
            g.drawLine(5, 2, 5, 6);
            g.drawLine(5, 6, 11, 6);
        });
    }

    static ImageIcon ok() {
        return icon(g -> {
            g.drawLine(3, 8, 6, 12);
            g.drawLine(6, 12, 13, 4);
        });
    }

    static ImageIcon pending() {
        return icon(g -> g.drawOval(3, 3, 10, 10));
    }

    static ImageIcon macro() {
        return icon(g -> {
            g.drawRoundRect(3, 2, 10, 12, 2, 2);
            g.drawLine(5, 6, 11, 6);
            g.drawLine(5, 9, 11, 9);
        });
    }

    static ImageIcon of(Step.Kind kind) {
        return switch (kind) {
            case CLICK -> click();
            case TEXT -> text();
            case KEYS -> keys();
            case WAIT -> clock();
            case FOCUS -> window();
            case SCROLL -> scroll();
        };
    }

    private static ImageIcon icon(Consumer<Graphics2D> paint) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Color color = UIManager.getColor("Label.foreground");
        g.setColor(color == null ? new Color(35, 35, 35) : color);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        paint.accept(g);
        g.dispose();
        return new ImageIcon(image);
    }
}
