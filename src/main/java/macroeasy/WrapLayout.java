package macroeasy;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** A FlowLayout that wraps its buttons onto more rows when the window is narrow, instead of hiding them. */
final class WrapLayout extends FlowLayout {
    private Dimension laidOut;

    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    /** When the real width changes the row count, ask the parents to make room for the new height. */
    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        Dimension now = preferredLayoutSize(target);
        if (!now.equals(laidOut)) {
            boolean first = laidOut == null;
            laidOut = now;
            if (!first || target.getHeight() < now.height) SwingUtilities.invokeLater(target::revalidate);
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return size(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = size(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            Container container = target;
            while (container.getSize().width == 0 && container.getParent() != null) {
                container = container.getParent();
            }
            targetWidth = container.getSize().width;
            if (targetWidth == 0) targetWidth = Integer.MAX_VALUE;
            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontal = insets.left + insets.right + hgap * 2;
            int maxWidth = targetWidth - horizontal;
            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            for (int i = 0; i < target.getComponentCount(); i++) {
                Component m = target.getComponent(i);
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth + d.width > maxWidth) {
                    addRow(dim, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) rowWidth += hgap;
                rowWidth += d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            addRow(dim, rowWidth, rowHeight);
            dim.width += horizontal;
            dim.height += insets.top + insets.bottom + vgap * 2;
            Container scroll = (Container) SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scroll != null && target.isValid()) dim.width -= hgap + 1;
            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) dim.height += getVgap();
        dim.height += rowHeight;
    }
}
