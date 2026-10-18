package macroeasy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * Rows of the step list: number, icon, what the step does and its own interval as a chip.
 * The selected row also shows edit, duplicate, delete, up and down as small icons that can be clicked.
 */
final class StepList {
    enum Action { EDIT, DUPLICATE, DELETE, UP, DOWN }

    interface Handler {
        void act(Action action);
    }

    static final int ROW_HEIGHT = 34;
    private static final int SLOT = 26;
    private static final int RIGHT_INSET = 8;
    private static final String[] TIPS = {"Editar", "Duplicar", "Excluir", "Subir", "Descer"};

    private StepList() {}

    static void install(JList<Step> list, Handler handler) {
        list.setFixedCellHeight(ROW_HEIGHT);
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!list.isEnabled() || e.getButton() != MouseEvent.BUTTON1) return;
                Action action = actionAt(list, e.getX(), e.getY());
                if (action == null) return;
                e.consume();
                handler.act(action);
            }
        });
        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Action action = actionAt(list, e.getX(), e.getY());
                list.setToolTipText(action == null ? null : TIPS[action.ordinal()]);
            }
        });
    }

    /** The inline icon under the point on the selected row, or null. */
    static Action actionAt(JList<Step> list, int x, int y) {
        int index = list.locationToIndex(new java.awt.Point(x, y));
        if (index < 0 || index != list.getSelectedIndex()) return null;
        Rectangle cell = list.getCellBounds(index, index);
        if (cell == null || !cell.contains(x, y)) return null;
        return actionAt(cell.x + cell.width - x);
    }

    /** Which icon sits {@code fromRight} points left of the row's right edge. */
    static Action actionAt(int fromRight) {
        int offset = fromRight - RIGHT_INSET;
        if (offset < 0) return null;
        int slot = offset / SLOT;
        Action[] all = Action.values();
        return slot < all.length ? all[all.length - 1 - slot] : null;
    }

    private static final class Renderer extends JPanel implements ListCellRenderer<Step> {
        private final JLabel number = new JLabel();
        private final JLabel text = new JLabel();
        private final JLabel chip = new JLabel();
        private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        private final JLabel[] icons = new JLabel[5];
        private boolean selected;
        private Color selectedColor;

        Renderer() {
            super(new BorderLayout(8, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, RIGHT_INSET));
            number.setHorizontalAlignment(SwingConstants.RIGHT);
            number.setPreferredSize(new Dimension(22, ROW_HEIGHT));
            text.setIconTextGap(8);
            chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.line(), 1, true),
                    BorderFactory.createEmptyBorder(1, 6, 1, 6)));
            Icon[] shapes = {Icons.edit(), Icons.copy(), Icons.trash(), Icons.up(), Icons.down()};
            actions.setOpaque(false);
            for (int i = 0; i < icons.length; i++) {
                icons[i] = new JLabel(shapes[i]);
                icons[i].setHorizontalAlignment(SwingConstants.CENTER);
                icons[i].setPreferredSize(new Dimension(SLOT, ROW_HEIGHT));
                actions.add(icons[i]);
            }
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, (ROW_HEIGHT - 20) / 2));
            right.setOpaque(false);
            right.add(chip);
            JPanel east = new JPanel(new BorderLayout());
            east.setOpaque(false);
            east.add(right, BorderLayout.CENTER);
            east.add(actions, BorderLayout.EAST);
            add(number, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(east, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Step> list, Step step, int index,
                                                      boolean isSelected, boolean focus) {
            selected = isSelected;
            selectedColor = list.getSelectionBackground();
            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            Color soft = isSelected ? fg : Theme.muted();
            number.setText(Integer.toString(index + 1));
            number.setForeground(soft);
            number.setFont(list.getFont().deriveFont(11f));
            text.setText(step.label());
            text.setIcon(Icons.of(step.kind));
            text.setForeground(fg);
            text.setFont(list.getFont());
            boolean ownGap = step.gapMs >= 0 && step.kind != Step.Kind.WAIT;
            chip.setVisible(ownGap);
            chip.setText(ownGap ? step.gapMs + " ms" : "");
            chip.setForeground(soft);
            chip.setFont(list.getFont().deriveFont(11f));
            actions.setVisible(isSelected && list.isEnabled());
            icons[3].setEnabled(index > 0);
            icons[4].setEnabled(index < list.getModel().getSize() - 1);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (selected && selectedColor != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(selectedColor);
                g2.fillRoundRect(3, 2, getWidth() - 6, getHeight() - 4, 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }

        @Override
        public Color getBackground() {
            Color color = UIManager.getColor("List.background");
            return color == null ? super.getBackground() : color;
        }
    }
}
