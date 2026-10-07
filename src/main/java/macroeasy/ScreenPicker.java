package macroeasy;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;
import javax.swing.JPanel;

/** Full-screen overlay. A click records the point; Esc cancels. */
public final class ScreenPicker {
    private ScreenPicker() {}

    public static Point pick() {
        Rectangle all = new Rectangle();
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice device : env.getScreenDevices()) {
            all = all.union(device.getDefaultConfiguration().getBounds());
        }
        final Rectangle bounds = all;
        JDialog dialog = new JDialog((java.awt.Frame) null, true);
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);
        dialog.setFocusableWindowState(true);
        dialog.setBounds(bounds);
        Point[] chosen = {null};
        Point[] cursor = {null};
        var pointer = MouseInfo.getPointerInfo();
        if (pointer != null) cursor[0] = pointer.getLocation();
        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(0, 0, 0, 72));
                g.fillRect(0, 0, getWidth(), getHeight());
                if (cursor[0] == null) return;
                int x = cursor[0].x - bounds.x;
                int y = cursor[0].y - bounds.y;
                g.setColor(Color.WHITE);
                g.drawLine(x, 0, x, getHeight());
                g.drawLine(0, y, getWidth(), y);
                g.setFont(getFont().deriveFont(Font.BOLD, 12f));
                String label = cursor[0].x + ", " + cursor[0].y + "    clique marca  ·  Esc cancela";
                int width = g.getFontMetrics().stringWidth(label);
                int tx = Math.min(Math.max(8, x + 16), Math.max(8, getWidth() - width - 16));
                int ty = Math.min(Math.max(24, y - 12), Math.max(24, getHeight() - 12));
                g.setColor(new Color(0, 0, 0, 180));
                g.fillRoundRect(tx - 6, ty - 16, width + 12, 24, 8, 8);
                g.setColor(Color.WHITE);
                g.drawString(label, tx, ty);
            }
        };
        canvas.setOpaque(false);
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        canvas.setFocusable(true);
        MouseAdapter move = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                aim(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                aim(e);
            }

            private void aim(MouseEvent e) {
                cursor[0] = e.getLocationOnScreen();
                canvas.repaint();
            }
        };
        canvas.addMouseMotionListener(move);
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                chosen[0] = e.getLocationOnScreen();
                dialog.dispose();
            }
        });
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) dialog.dispose();
            }
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                canvas.requestFocusInWindow();
            }
        });
        if (env.getDefaultScreenDevice().isWindowTranslucencySupported(
                GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
            dialog.setBackground(new Color(0, 0, 0, 0));
        }
        dialog.setContentPane(canvas);
        dialog.setVisible(true);
        return chosen[0];
    }
}
