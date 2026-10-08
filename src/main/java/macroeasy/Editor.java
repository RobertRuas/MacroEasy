package macroeasy;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;

/** Small modal editors for each step kind. */
public final class Editor {
    private static final int CANCEL = 0;
    private static final int OK = 1;
    private static final int PICK = 2;
    private static final int CAPTURE = 3;

    private Editor() {}

    public static Step click(Window owner, Step current) {
        JTextField x = field(current == null ? "" : Integer.toString(current.x));
        JTextField y = field(current == null ? "" : Integer.toString(current.y));
        JComboBox<String> button = new JComboBox<>(new String[] {"Esquerdo", "Direito", "Meio"});
        JComboBox<String> clicks = new JComboBox<>(new String[] {"1", "2"});
        if (current != null) {
            button.setSelectedIndex(switch (current.button) {
                case 3 -> 1;
                case 2 -> 2;
                default -> 0;
            });
            clicks.setSelectedIndex(current.clicks > 1 ? 1 : 0);
        }
        JButton mark = new JButton("Marcar na tela");
        GapEditor gap = new GapEditor(current);
        JPanel form = column(
                row(new JLabel("X"), x, new JLabel("Y"), y, mark),
                row(new JLabel("Botão"), button, new JLabel("Cliques"), clicks),
                gap.panel());
        JDialog dialog = dialog(owner, current == null ? "Novo clique" : "Editar clique");
        int[] action = {CANCEL};
        mark.addActionListener(e -> {
            action[0] = PICK;
            dialog.setVisible(false);
        });
        JButton ok = accept(dialog, action, () -> {
            if (parse(x) == null || parse(y) == null) {
                warn(dialog, "Informe X e Y com números inteiros, ou marque na tela.");
                return false;
            }
            return true;
        });
        mount(dialog, form, ok);
        while (true) {
            dialog.setVisible(true);
            if (action[0] == PICK) {
                action[0] = CANCEL;
                var point = ScreenPicker.pick();
                if (point != null) {
                    x.setText(Integer.toString(point.x));
                    y.setText(Integer.toString(point.y));
                }
                continue;
            }
            dialog.dispose();
            if (action[0] != OK) return null;
            int which = switch (button.getSelectedIndex()) {
                case 1 -> 3;
                case 2 -> 2;
                default -> 1;
            };
            return Step.click(parse(x), parse(y), which, clicks.getSelectedIndex() + 1).withGap(gap.value());
        }
    }

    public static Step text(Window owner, Step current) {
        JTextArea area = new JTextArea(current == null ? "" : current.text, 5, 36);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JCheckBox typed = new JCheckBox("Digitar tecla a tecla (sem colar)");
        typed.setSelected(current != null && !current.paste);
        String pasteKey = System.getProperty("os.name", "").toLowerCase().contains("mac") ? "⌘V" : "Ctrl+V";
        GapEditor gap = new GapEditor(current);
        JPanel form = column(
                new JLabel("O texto é colado com " + pasteKey + ". Acentos funcionam."),
                new JScrollPane(area),
                typed,
                gap.panel());
        JDialog dialog = dialog(owner, current == null ? "Novo texto" : "Editar texto");
        int[] action = {CANCEL};
        JButton ok = accept(dialog, action, () -> true);
        mount(dialog, form, ok);
        dialog.setVisible(true);
        dialog.dispose();
        if (action[0] != OK) return null;
        return Step.text(area.getText(), !typed.isSelected()).withGap(gap.value());
    }

    public static Step keys(Window owner, Step current) {
        int[] captured = current == null ? null : current.keys;
        JLabel shown = new JLabel(captured == null ? "Pressione o atalho" : Step.keyLabel(captured));
        shown.setHorizontalAlignment(SwingConstants.CENTER);
        shown.setFont(shown.getFont().deriveFont(Font.BOLD, 16f));
        shown.setBorder(BorderFactory.createEmptyBorder(18, 12, 18, 12));
        JDialog dialog = dialog(owner, current == null ? "Novo atalho" : "Editar atalho");
        int[] action = {CANCEL};
        int[][] held = {captured};
        KeyEventDispatcher dispatcher = event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED || !dialog.isActive()) return false;
            int code = event.getKeyCode();
            boolean plainEscape = code == KeyEvent.VK_ESCAPE
                    && !event.isMetaDown() && !event.isControlDown()
                    && !event.isAltDown() && !event.isShiftDown();
            if (plainEscape) {
                action[0] = CANCEL;
                dialog.setVisible(false);
                return true;
            }
            if (isModifier(code)) return true;
            List<Integer> keys = new ArrayList<>(5);
            if (event.isMetaDown()) keys.add(KeyEvent.VK_META);
            if (event.isControlDown()) keys.add(KeyEvent.VK_CONTROL);
            if (event.isAltDown()) keys.add(KeyEvent.VK_ALT);
            if (event.isShiftDown()) keys.add(KeyEvent.VK_SHIFT);
            keys.add(code);
            held[0] = keys.stream().mapToInt(Integer::intValue).toArray();
            shown.setText(Step.keyLabel(held[0]));
            return true;
        };
        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focus.addKeyEventDispatcher(dispatcher);
        JButton ok = accept(dialog, action, () -> {
            if (held[0] == null || held[0].length == 0) {
                warn(dialog, "Pressione a tecla ou a combinação antes de usar.");
                return false;
            }
            return true;
        });
        GapEditor gap = new GapEditor(current);
        JPanel form = column(new JLabel("Clique na janela e pressione a combinação."), shown, gap.panel());
        try {
            mount(dialog, form, ok);
            dialog.setVisible(true);
        } finally {
            focus.removeKeyEventDispatcher(dispatcher);
            dialog.dispose();
        }
        if (action[0] != OK) return null;
        return Step.keys(held[0]).withGap(gap.value());
    }

    public static Step focus(Window owner, Step current) {
        java.util.List<Windows.Item> items = new java.util.ArrayList<>(Windows.list());
        if (current != null && current.kind == Step.Kind.FOCUS) {
            boolean present = false;
            for (Windows.Item item : items) {
                if (item.app.equals(current.appName()) && item.title.equals(current.windowTitle())) present = true;
            }
            if (!present) items.add(0, new Windows.Item(current.appName(), current.windowTitle()));
        }
        if (items.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(owner,
                    "Nenhuma janela de outro aplicativo está aberta.", "MacroEasy",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        JList<Windows.Item> choices = new JList<>(items.toArray(Windows.Item[]::new));
        choices.setVisibleRowCount(Math.min(12, items.size()));
        if (current != null) {
            for (int i = 0; i < choices.getModel().getSize(); i++) {
                Windows.Item item = choices.getModel().getElementAt(i);
                if (item.app.equals(current.appName()) && item.title.equals(current.windowTitle())) {
                    choices.setSelectedIndex(i);
                }
            }
        }
        JDialog dialog = dialog(owner, current == null ? "Trazer janela à frente" : "Editar janela");
        int[] action = {CANCEL};
        JButton ok = accept(dialog, action, () -> {
            if (choices.getSelectedValue() == null) {
                warn(dialog, "Escolha uma janela.");
                return false;
            }
            return true;
        });
        choices.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && choices.getSelectedValue() != null) ok.doClick();
            }
        });
        GapEditor gap = new GapEditor(current);
        mount(dialog, column(new JLabel("Escolha uma janela que já está aberta."), new JScrollPane(choices), gap.panel()), ok);
        dialog.setVisible(true);
        dialog.dispose();
        if (action[0] != OK) return null;
        Windows.Item item = choices.getSelectedValue();
        return Step.focus(item.app, item.title).withGap(gap.value());
    }

    public static Step scroll(Window owner, Step current) {
        JComboBox<String> direction = new JComboBox<>(new String[] {"Para baixo", "Para cima"});
        int lines = current == null ? 5 : Math.max(1, Math.abs(current.scroll));
        JSpinner amount = new JSpinner(new SpinnerNumberModel(lines, 1, 10_000, 1));
        ((JSpinner.DefaultEditor) amount.getEditor()).getTextField().setColumns(4);
        if (current != null && current.scroll < 0) direction.setSelectedIndex(1);
        boolean placed = current != null && current.move;
        JCheckBox move = new JCheckBox("Levar o mouse antes para");
        move.setSelected(placed);
        JTextField x = field(placed ? Integer.toString(current.x) : "");
        JTextField y = field(placed ? Integer.toString(current.y) : "");
        JButton mark = new JButton("Marcar na tela");
        Runnable sync = () -> {
            x.setEnabled(move.isSelected());
            y.setEnabled(move.isSelected());
            mark.setEnabled(move.isSelected());
        };
        sync.run();
        move.addActionListener(e -> sync.run());
        JButton capture = new JButton("Gravar rolagem");
        capture.setToolTipText("Gire a roda do mouse de verdade; a direção, as linhas e a posição são preenchidas");
        GapEditor gap = new GapEditor(current);
        JPanel form = column(
                new JLabel("Gira a roda do mouse onde o ponteiro estiver."),
                row(new JLabel("Direção"), direction, new JLabel("Linhas"), amount, capture),
                row(move, new JLabel("X"), x, new JLabel("Y"), y, mark),
                gap.panel());
        JDialog dialog = dialog(owner, current == null ? "Nova rolagem" : "Editar rolagem");
        int[] action = {CANCEL};
        mark.addActionListener(e -> {
            action[0] = PICK;
            dialog.setVisible(false);
        });
        capture.addActionListener(e -> {
            action[0] = CAPTURE;
            dialog.setVisible(false);
        });
        JButton ok = accept(dialog, action, () -> {
            try {
                amount.commitEdit();
            } catch (ParseException ex) {
                warn(dialog, "Informe quantas linhas rolar.");
                return false;
            }
            if (move.isSelected() && (parse(x) == null || parse(y) == null)) {
                warn(dialog, "Informe X e Y com números inteiros, ou marque na tela.");
                return false;
            }
            return true;
        });
        mount(dialog, form, ok);
        while (true) {
            dialog.setVisible(true);
            if (action[0] == PICK) {
                action[0] = CANCEL;
                var point = ScreenPicker.pick();
                if (point != null) {
                    x.setText(Integer.toString(point.x));
                    y.setText(Integer.toString(point.y));
                }
                continue;
            }
            if (action[0] == CAPTURE) {
                action[0] = CANCEL;
                Step got = ScrollCapture.capture(owner);
                if (got != null) {
                    direction.setSelectedIndex(got.scroll < 0 ? 1 : 0);
                    amount.setValue(Math.abs(got.scroll));
                    move.setSelected(true);
                    x.setText(Integer.toString(got.x));
                    y.setText(Integer.toString(got.y));
                    sync.run();
                }
                continue;
            }
            dialog.dispose();
            if (action[0] != OK) return null;
            int count = ((Number) amount.getValue()).intValue();
            int signed = direction.getSelectedIndex() == 1 ? -count : count;
            boolean at = move.isSelected();
            return Step.scroll(at ? parse(x) : 0, at ? parse(y) : 0, signed, at).withGap(gap.value());
        }
    }

    public static Step wait(Window owner, Step current) {
        int initial = current == null ? 300 : current.waitMs;
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, 0, 600_000, 50));
        JDialog dialog = dialog(owner, current == null ? "Nova espera" : "Editar espera");
        int[] action = {CANCEL};
        JButton ok = accept(dialog, action, () -> {
            try {
                spinner.commitEdit();
                return true;
            } catch (ParseException ex) {
                warn(dialog, "Informe o tempo em milissegundos.");
                return false;
            }
        });
        mount(dialog, row(new JLabel("Milissegundos"), spinner), ok);
        dialog.setVisible(true);
        dialog.dispose();
        if (action[0] != OK) return null;
        return Step.wait(((Number) spinner.getValue()).intValue());
    }

    /** Optional pause before one step. Unchecked means the shared interval. */
    private static final class GapEditor {
        private final JCheckBox own = new JCheckBox("Intervalo próprio deste passo");
        private final JSpinner ms = new JSpinner(new SpinnerNumberModel(200, 0, 600_000, 50));

        GapEditor(Step current) {
            int gap = current == null ? -1 : current.gapMs;
            own.setSelected(gap >= 0);
            if (gap >= 0) ms.setValue(gap);
            ms.setEnabled(own.isSelected());
            ((JSpinner.DefaultEditor) ms.getEditor()).getTextField().setColumns(5);
            own.addActionListener(e -> ms.setEnabled(own.isSelected()));
        }

        JPanel panel() {
            return row(own, ms, new JLabel("ms"));
        }

        int value() {
            try {
                ms.commitEdit();
            } catch (ParseException ignored) {
                // The last committed number stays in the spinner.
            }
            return own.isSelected() ? ((Number) ms.getValue()).intValue() : -1;
        }
    }

    private static JDialog dialog(Window owner, String title) {
        JDialog dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);
        return dialog;
    }

    private static JButton accept(JDialog dialog, int[] action, java.util.function.BooleanSupplier valid) {
        JButton ok = new JButton("Usar");
        ok.addActionListener(e -> {
            if (!valid.getAsBoolean()) return;
            action[0] = OK;
            dialog.setVisible(false);
        });
        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(e -> {
            action[0] = CANCEL;
            dialog.setVisible(false);
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                action[0] = CANCEL;
                dialog.setVisible(false);
            }
        });
        return ok;
    }

    private static void mount(JDialog dialog, JComponent form, JButton ok) {
        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(e -> {
            dialog.dispatchEvent(new java.awt.event.WindowEvent(dialog, java.awt.event.WindowEvent.WINDOW_CLOSING));
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(ok);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(dialog.getOwner());
    }

    private static JTextField field(String value) {
        JTextField field = new JTextField(value, 6);
        return field;
    }

    private static Integer parse(JTextField field) {
        try {
            return Integer.valueOf(field.getText().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void warn(JDialog dialog, String message) {
        JOptionPane.showMessageDialog(dialog, message, "MacroEasy", JOptionPane.WARNING_MESSAGE);
    }

    private static boolean isModifier(int code) {
        return code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL
                || code == KeyEvent.VK_ALT || code == KeyEvent.VK_META;
    }

    private static JPanel row(JComponent... parts) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (JComponent part : parts) panel.add(part);
        return panel;
    }

    private static JPanel column(JComponent... parts) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) panel.add(Box.createVerticalStrut(8));
            parts[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(parts[i]);
        }
        return panel;
    }
}
