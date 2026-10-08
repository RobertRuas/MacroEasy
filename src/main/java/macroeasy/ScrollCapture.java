package macroeasy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Floating notice shown while the real mouse wheel is recorded. The ticks are
 * summed into one scroll step. Recording ends after a short pause, with Esc, or
 * with the Cancelar button.
 */
final class ScrollCapture {
    private static final long IDLE_MS = 1200;

    private ScrollCapture() {}

    /** Blocks until done. Returns the step, or null when cancelled, empty or unavailable. */
    static Step capture(Window owner) {
        JDialog notice = new JDialog(owner, "", JDialog.ModalityType.APPLICATION_MODAL);
        notice.setUndecorated(true);
        notice.setAlwaysOnTop(true);
        notice.setAutoRequestFocus(false);
        notice.setFocusableWindowState(false);
        notice.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        List<Step> seen = new ArrayList<>();
        boolean[] cancelled = {false};
        long[] lastTick = {0};
        String[] error = {null};
        Recorder[] recorder = {null};
        Rectangle[] bounds = {null};

        Recorder.Out out = new Recorder.Out() {
            @Override
            public void add(Step step) {
                synchronized (seen) {
                    if (isEscape(step)) {
                        cancelled[0] = true;
                        recorder[0].stop();
                        return;
                    }
                    if (step.kind != Step.Kind.SCROLL) return;
                    seen.add(step);
                    lastTick[0] = System.nanoTime();
                }
            }

            @Override
            public void replaceLast(Step step) {
                synchronized (seen) {
                    if (step.kind != Step.Kind.SCROLL) return;
                    if (seen.isEmpty()) seen.add(step);
                    else seen.set(seen.size() - 1, step);
                    lastTick[0] = System.nanoTime();
                }
            }
        };
        recorder[0] = new Recorder(out, () -> bounds[0]);

        JLabel text = new JLabel("Gire a roda do mouse onde quer rolar. Pare 1 segundo para terminar.");
        text.setFont(text.getFont().deriveFont(Font.BOLD, 12f));
        JLabel hint = new JLabel("Esc cancela.");
        hint.setFont(hint.getFont().deriveFont(11f));
        Color muted = UIManager.getColor("Label.disabledForeground");
        hint.setForeground(muted == null ? new Color(130, 130, 130) : muted);
        JButton cancel = new JButton("Cancelar");
        cancel.setFocusable(false);
        cancel.addActionListener(e -> {
            cancelled[0] = true;
            recorder[0].stop();
        });
        JPanel lines = new JPanel(new BorderLayout(0, 2));
        lines.setOpaque(false);
        lines.add(text, BorderLayout.NORTH);
        lines.add(hint, BorderLayout.SOUTH);
        JPanel panel = new JPanel(new BorderLayout(14, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 12));
        panel.add(lines, BorderLayout.CENTER);
        panel.add(cancel, BorderLayout.EAST);
        notice.setContentPane(panel);
        notice.pack();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        notice.setLocation(screen.x + (screen.width - notice.getWidth()) / 2,
                screen.y + screen.height - notice.getHeight() - 88);
        bounds[0] = new Rectangle(notice.getX() - 12, notice.getY() - 12,
                notice.getWidth() + 24, notice.getHeight() + 24);

        Thread listener = new Thread(() -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            error[0] = recorder[0].run();
            SwingUtilities.invokeLater(notice::dispose);
        }, "macroeasy-scroll-capture");
        listener.setDaemon(true);
        Thread idle = new Thread(() -> {
            while (listener.isAlive()) {
                long tick;
                synchronized (seen) {
                    tick = lastTick[0];
                }
                if (tick != 0 && (System.nanoTime() - tick) / 1_000_000L >= IDLE_MS) {
                    recorder[0].stop();
                    return;
                }
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "macroeasy-scroll-idle");
        idle.setDaemon(true);
        listener.start();
        idle.start();
        notice.setVisible(true);

        if (error[0] != null) {
            JOptionPane.showMessageDialog(owner, error[0], "MacroEasy", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        synchronized (seen) {
            if (cancelled[0] || seen.isEmpty()) return null;
            int total = 0;
            for (Step step : seen) total += step.scroll;
            if (total == 0) return null;
            Step first = seen.get(0);
            return Step.scroll(first.x, first.y, total, true);
        }
    }

    private static boolean isEscape(Step step) {
        return step.kind == Step.Kind.KEYS && step.keys.length == 1 && step.keys[0] == KeyEvent.VK_ESCAPE;
    }
}
