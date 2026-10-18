package macroeasy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.JSpinner;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class MacroEasy extends JFrame {
    private final Preferences prefs = Preferences.userNodeForPackage(MacroEasy.class);
    private final DefaultListModel<Step> model = new DefaultListModel<>();
    private final JList<Step> list = new JList<>(model);
    private final JLabel status = new JLabel(" ");
    private static final int SHORTCUT = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
    private static final KeyStroke KEY_RUN = key(KeyEvent.VK_R, SHORTCUT);
    private static final KeyStroke KEY_RECORD = key(KeyEvent.VK_R, SHORTCUT | KeyEvent.SHIFT_DOWN_MASK);
    private static final KeyStroke KEY_STOP = key(KeyEvent.VK_ESCAPE, 0);
    private static final KeyStroke KEY_OPEN = key(KeyEvent.VK_O, SHORTCUT);
    private static final KeyStroke KEY_SAVE = key(KeyEvent.VK_S, SHORTCUT);
    private static final KeyStroke KEY_NEW_MACRO = key(KeyEvent.VK_N, SHORTCUT);
    private static final KeyStroke KEY_CLICK = key(KeyEvent.VK_1, SHORTCUT);
    private static final KeyStroke KEY_TEXT = key(KeyEvent.VK_2, SHORTCUT);
    private static final KeyStroke KEY_KEYS = key(KeyEvent.VK_3, SHORTCUT);
    private static final KeyStroke KEY_WAIT = key(KeyEvent.VK_4, SHORTCUT);
    private static final KeyStroke KEY_WINDOW = key(KeyEvent.VK_5, SHORTCUT);
    private static final KeyStroke KEY_SCROLL = key(KeyEvent.VK_6, SHORTCUT);
    private static final KeyStroke KEY_DRAG = key(KeyEvent.VK_7, SHORTCUT);
    private static final KeyStroke KEY_UNDO = key(KeyEvent.VK_Z, SHORTCUT);
    private static final KeyStroke KEY_REDO = key(KeyEvent.VK_Z, SHORTCUT | KeyEvent.SHIFT_DOWN_MASK);
    private static final KeyStroke KEY_EDIT = key(KeyEvent.VK_E, SHORTCUT);
    private static final KeyStroke KEY_DUPLICATE = key(KeyEvent.VK_D, SHORTCUT);
    private static final KeyStroke KEY_DELETE = key(KeyEvent.VK_BACK_SPACE, SHORTCUT);
    private static final KeyStroke KEY_UP = key(KeyEvent.VK_UP, SHORTCUT);
    private static final KeyStroke KEY_DOWN = key(KeyEvent.VK_DOWN, SHORTCUT);

    private static final KeyStroke KEY_SIDEBAR = key(KeyEvent.VK_B, SHORTCUT);

    private final JButton run = button("Executar", Icons.play(Color.WHITE), KEY_RUN);
    private final JButton record = button("Gravar", Icons.record(), KEY_RECORD);
    private final JButton stop = button("Parar", Icons.stop(), KEY_STOP);
    private final JButton addStep = button("Adicionar passo  ▾", Icons.plus());
    private final JButton more = iconButton(Icons.menu(), "Mais opções");
    private final JButton toggleSide = iconButton(Icons.sidebar(), "Mostrar ou ocultar as macros (" + hint(KEY_SIDEBAR) + ")");
    private final javax.swing.JCheckBoxMenuItem timingItem = new javax.swing.JCheckBoxMenuItem("Gravar as pausas reais");
    private JComponent side;
    private final JSpinner repeats = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JSpinner delay = new JSpinner(new SpinnerNumberModel(2000, 0, 600_000, 100));
    private final JSpinner interval = new JSpinner(new SpinnerNumberModel(200, 0, 600_000, 50));
    private final AtomicBoolean stopFlag = new AtomicBoolean();
    private final History history = new History();
    private JMenuItem undoItem;
    private JMenuItem redoItem;

    private final DefaultListModel<Path> macros = new DefaultListModel<>();
    private final JList<Path> macroList = new JList<>(macros);
    private JButton removeMacro;
    private Path file;
    private boolean dirty;
    private boolean loadingLibrary;
    private boolean running;
    private boolean recording;

    boolean busy() {
        return running || recording;
    }
    private Recorder recorder;
    private JWindow hud;
    private JButton hudButton;
    private volatile Rectangle hudHit;

    private MacroEasy() {
        super("MacroEasy");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImage(icon());
        installIcon();
        build();
        bindKeys();
        loadSession();
        pack();
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        placeWindow();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });
    }

    private static final int MIN_WIDTH = WindowSize.MIN_WIDTH;
    private static final int MIN_HEIGHT = WindowSize.MIN_HEIGHT;

    /** Opens where it was last closed, or at a size that leaves room for other windows on this screen. */
    private void placeWindow() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle saved = new Rectangle(prefs.getInt("winX", 0), prefs.getInt("winY", 0),
                prefs.getInt("winW", 0), prefs.getInt("winH", 0));
        if (saved.width >= MIN_WIDTH && saved.height >= MIN_HEIGHT && onScreen(saved)) {
            setBounds(saved);
            return;
        }
        setSize(WindowSize.defaultSize(screen.width, screen.height));
        setLocationRelativeTo(null);
    }

    private static boolean onScreen(Rectangle bounds) {
        for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle visible = device.getDefaultConfiguration().getBounds().intersection(bounds);
            if (visible.width >= 120 && visible.height >= 80) return true;
        }
        return false;
    }

    private void rememberWindow() {
        if ((getExtendedState() & (Frame.ICONIFIED | Frame.MAXIMIZED_BOTH)) != 0) return;
        Rectangle bounds = getBounds();
        prefs.putInt("winX", bounds.x);
        prefs.putInt("winY", bounds.y);
        prefs.putInt("winW", bounds.width);
        prefs.putInt("winH", bounds.height);
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "MacroEasy");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            installIcon();
            if (!Theme.flat()) lightenFonts();
            MacroEasy app = new MacroEasy();
            app.setEnabled(false);
            app.setVisible(true);
            Updater.start(app, () -> Permissions.ensure(app, () -> app.setEnabled(true)));
        });
    }

    /**
     * One bar on top (run, record, stop, the three numbers, the options menu), the macros on the left,
     * the steps in the middle with their actions on the selected row, and one button to add a step below.
     */
    private void build() {
        run.setFont(run.getFont().deriveFont(Font.BOLD));
        run.setBackground(Theme.accent());
        run.setForeground(Color.WHITE);
        run.putClientProperty("JButton.buttonType", "roundRect");
        run.setToolTipText("Focar o aplicativo alvo durante o atraso, depois executar  (" + hint(KEY_RUN) + ")");
        stop.setToolTipText("Interrompe a execução  (Esc)");
        record.setToolTipText("Grava cliques, arrastos, teclas e rolagem  (" + hint(KEY_RECORD) + ")");
        repeats.setToolTipText("Quantas vezes repetir a lista");
        delay.setToolTipText("Milissegundos para focar o aplicativo alvo antes de começar");
        interval.setToolTipText("Pausa entre cada ação, em milissegundos. Um passo pode ter um intervalo próprio");
        ((JSpinner.DefaultEditor) repeats.getEditor()).getTextField().setColumns(2);
        ((JSpinner.DefaultEditor) delay.getEditor()).getTextField().setColumns(4);
        ((JSpinner.DefaultEditor) interval.getEditor()).getTextField().setColumns(4);

        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        StepList.install(list, action -> {
            switch (action) {
                case EDIT -> editSelected();
                case DUPLICATE -> duplicateSelected();
                case DELETE -> deleteSelected();
                case UP -> move(-1);
                case DOWN -> move(1);
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateEnabled();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !e.isConsumed()
                        && StepList.actionAt(list, e.getX(), e.getY()) == null) editSelected();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) stepMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) stepMenu(e);
            }
        });

        run.addActionListener(e -> start());
        record.addActionListener(e -> record());
        stop.addActionListener(e -> halt());
        addStep.addActionListener(e -> addMenu().show(addStep, 0, addStep.getHeight() + 2));
        more.addActionListener(e -> {
            javax.swing.JPopupMenu menu = moreMenu();
            menu.show(more, more.getWidth() - menu.getPreferredSize().width, more.getHeight() + 2);
        });
        toggleSide.addActionListener(e -> toggleSidebar());
        timingItem.setSelected(prefs.getBoolean("recordTiming", false));
        timingItem.setToolTipText("Cada passo gravado guarda o tempo que passou desde a ação anterior");
        timingItem.getModel().addItemListener(e -> prefs.putBoolean("recordTiming", timingItem.isSelected()));

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.add(row(toggleSide, run, record, stop,
                field("Vezes", repeats),
                field("Atraso", delay),
                field("Intervalo", interval)), BorderLayout.CENTER);
        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 3));
        corner.setOpaque(false);
        corner.add(more);
        top.add(corner, BorderLayout.EAST);
        top.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        status.setFont(status.getFont().deriveFont(11f));
        status.setForeground(Theme.muted());
        status.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 2));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        bottom.add(addStep, BorderLayout.WEST);
        bottom.add(status, BorderLayout.CENTER);

        Color line = Theme.line();
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(line));

        JPanel editor = new JPanel(new BorderLayout());
        editor.add(top, BorderLayout.NORTH);
        editor.add(scroll, BorderLayout.CENTER);
        editor.add(bottom, BorderLayout.SOUTH);

        side = sidebar(line);
        side.setVisible(prefs.getBoolean("sidebar", true));
        JPanel root = new JPanel(new BorderLayout(10, 0));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(side, BorderLayout.WEST);
        root.add(editor, BorderLayout.CENTER);
        setContentPane(root);
        if (MAC) setJMenuBar(menu());
        updateEnabled();
    }

    private JComponent sidebar(Color line) {
        JLabel title = new JLabel("MACROS");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 10.5f));
        title.setForeground(Theme.muted());
        macroList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        macroList.setFixedCellHeight(28);
        macroList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> source, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(source, value, index, selected, false);
                String name = ((Path) value).getFileName().toString();
                if (name.endsWith(".macro")) name = name.substring(0, name.length() - 6);
                label.setIcon(Icons.macro());
                label.setIconTextGap(8);
                label.setText(name);
                label.setToolTipText(name);
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return label;
            }
        });
        macroList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || loadingLibrary) return;
            Path selected = macroList.getSelectedValue();
            if (selected == null || selected.equals(file) || running || recording) return;
            loadMacro(selected);
        });
        JButton create = iconButton(Icons.plus(), "Nova macro  (" + hint(KEY_NEW_MACRO) + ")");
        removeMacro = iconButton(Icons.trash(), "Excluir a macro");
        create.addActionListener(e -> createMacro());
        removeMacro.addActionListener(e -> deleteMacro());
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        tools.setOpaque(false);
        tools.add(create);
        tools.add(removeMacro);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(3, 4, 6, 0));
        header.add(title, BorderLayout.WEST);
        header.add(tools, BorderLayout.EAST);
        JScrollPane saved = new JScrollPane(macroList);
        saved.setBorder(BorderFactory.createLineBorder(line));
        saved.setPreferredSize(new Dimension(160, 200));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(header, BorderLayout.NORTH);
        panel.add(saved, BorderLayout.CENTER);
        return panel;
    }

    private void toggleSidebar() {
        side.setVisible(!side.isVisible());
        prefs.putBoolean("sidebar", side.isVisible());
        revalidate();
    }

    /** The seven kinds of step, with their shortcuts. */
    private javax.swing.JPopupMenu addMenu() {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.add(item("Clique", Icons.click(), KEY_CLICK, this::newClick));
        menu.add(item("Arrastar e soltar", Icons.drag(), KEY_DRAG, this::newDrag));
        menu.add(item("Rolagem", Icons.scroll(), KEY_SCROLL, this::newScroll));
        menu.addSeparator();
        menu.add(item("Texto", Icons.text(), KEY_TEXT, this::newText));
        menu.add(item("Atalho de teclado", Icons.keys(), KEY_KEYS, this::newKeys));
        menu.addSeparator();
        menu.add(item("Espera", Icons.clock(), KEY_WAIT, this::newWait));
        menu.add(item("Trazer janela à frente", Icons.window(), KEY_WINDOW, this::newWindow));
        return menu;
    }

    /** Right click on a step. */
    private void stepMenu(MouseEvent e) {
        if (busy()) return;
        int index = list.locationToIndex(e.getPoint());
        if (index >= 0) list.setSelectedIndex(index);
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        boolean any = list.getSelectedIndex() >= 0;
        int selected = list.getSelectedIndex();
        menu.add(item("Editar", Icons.edit(), KEY_EDIT, this::editSelected)).setEnabled(any);
        menu.add(item("Duplicar", Icons.copy(), KEY_DUPLICATE, this::duplicateSelected)).setEnabled(any);
        menu.add(item("Excluir", Icons.trash(), KEY_DELETE, this::deleteSelected)).setEnabled(any);
        menu.addSeparator();
        menu.add(item("Subir", Icons.up(), KEY_UP, () -> move(-1))).setEnabled(selected > 0);
        menu.add(item("Descer", Icons.down(), KEY_DOWN, () -> move(1)))
                .setEnabled(any && selected < model.getSize() - 1);
        menu.addSeparator();
        javax.swing.JMenu add = new javax.swing.JMenu("Adicionar passo");
        add.setIcon(Icons.plus());
        for (Component part : addMenu().getComponents()) {
            if (part instanceof JMenuItem entry) add.add(entry);
            else add.addSeparator();
        }
        menu.add(add);
        menu.show(list, e.getX(), e.getY());
    }

    /** Files, history, recording option and help, in the ☰ button. */
    private javax.swing.JPopupMenu moreMenu() {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        boolean idle = !busy();
        menu.add(item("Nova macro", Icons.plus(), KEY_NEW_MACRO, this::createMacro)).setEnabled(idle);
        menu.add(item("Abrir…", Icons.folder(), KEY_OPEN, this::open)).setEnabled(idle);
        menu.add(item("Salvar", Icons.save(), KEY_SAVE, this::save)).setEnabled(idle);
        menu.addSeparator();
        menu.add(item("Desfazer", null, KEY_UNDO, this::undo)).setEnabled(idle && history.canUndo());
        menu.add(item("Refazer", null, KEY_REDO, this::redo)).setEnabled(idle && history.canRedo());
        menu.addSeparator();
        menu.add(item(side.isVisible() ? "Ocultar as macros" : "Mostrar as macros", Icons.sidebar(), KEY_SIDEBAR,
                this::toggleSidebar));
        javax.swing.JCheckBoxMenuItem timing = new javax.swing.JCheckBoxMenuItem(timingItem.getText());
        timing.setModel(timingItem.getModel());
        timing.setToolTipText(timingItem.getToolTipText());
        menu.add(timing);
        menu.addSeparator();
        menu.add(item("Sobre o MacroEasy", null, null, () -> About.show(this)));
        return menu;
    }

    /** A menu item that shows its shortcut on the right without registering it twice. */
    private static JMenuItem item(String text, javax.swing.Icon icon, KeyStroke stroke, Runnable action) {
        JMenuItem item = new JMenuItem(text, icon);
        if (stroke != null) {
            item.setText("<html><table cellpadding=0 cellspacing=0><tr><td width=150>" + text
                    + "</td><td style='padding-left:12px;color:" + hex(Theme.muted()) + "'>" + hint(stroke)
                    + "</td></tr></table></html>");
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** The macOS menu bar at the top of the screen. It takes no room in the window. */
    private JMenuBar menu() {
        JMenuBar bar = new JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu("Arquivo");
        fileMenu.add(item("Nova macro", null, null, this::createMacro));
        fileMenu.add(item("Abrir…", null, null, this::open));
        fileMenu.add(item("Salvar", null, null, this::save));
        bar.add(fileMenu);
        javax.swing.JMenu editMenu = new javax.swing.JMenu("Editar");
        undoItem = item("Desfazer", null, null, this::undo);
        redoItem = item("Refazer", null, null, this::redo);
        editMenu.add(undoItem);
        editMenu.add(redoItem);
        bar.add(editMenu);
        javax.swing.JMenu stepMenu = new javax.swing.JMenu("Passo");
        stepMenu.add(item("Clique", null, null, this::newClick));
        stepMenu.add(item("Arrastar e soltar", null, null, this::newDrag));
        stepMenu.add(item("Rolagem", null, null, this::newScroll));
        stepMenu.addSeparator();
        stepMenu.add(item("Texto", null, null, this::newText));
        stepMenu.add(item("Atalho de teclado", null, null, this::newKeys));
        stepMenu.addSeparator();
        stepMenu.add(item("Espera", null, null, this::newWait));
        stepMenu.add(item("Trazer janela à frente", null, null, this::newWindow));
        bar.add(stepMenu);
        javax.swing.JMenu recordMenu = new javax.swing.JMenu("Gravação");
        recordMenu.add(timingItem);
        bar.add(recordMenu);
        javax.swing.JMenu view = new javax.swing.JMenu("Visualizar");
        view.add(item("Mostrar ou ocultar as macros", null, null, this::toggleSidebar));
        bar.add(view);
        javax.swing.JMenu help = new javax.swing.JMenu("Ajuda");
        help.add(item("Sobre o MacroEasy", null, null, () -> About.show(this)));
        bar.add(help);
        return bar;
    }

    private void bindKeys() {
        bind("run", KEY_RUN, this::start);
        bind("record", KEY_RECORD, this::record);
        bind("stop", KEY_STOP, this::halt);
        bind("open", KEY_OPEN, this::open);
        bind("save", KEY_SAVE, this::save);
        bind("newMacro", KEY_NEW_MACRO, this::createMacro);
        bind("newClick", KEY_CLICK, this::newClick);
        bind("newText", KEY_TEXT, this::newText);
        bind("newKeys", KEY_KEYS, this::newKeys);
        bind("newWait", KEY_WAIT, this::newWait);
        bind("newWindow", KEY_WINDOW, this::newWindow);
        bind("newScroll", KEY_SCROLL, this::newScroll);
        bind("newDrag", KEY_DRAG, this::newDrag);
        bind("undo", KEY_UNDO, this::undo);
        bind("redo", KEY_REDO, this::redo);
        bind("sidebar", KEY_SIDEBAR, this::toggleSidebar);
        if (!MAC) bind("redoY", key(KeyEvent.VK_Y, SHORTCUT), this::redo);
        bind("edit", KEY_EDIT, this::editSelected);
        bind("dup", KEY_DUPLICATE, this::duplicateSelected);
        bind("deleteStep", KEY_DELETE, this::deleteSelected);
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete");
        list.getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                deleteSelected();
            }
        });
        bind("up", KEY_UP, () -> move(-1));
        bind("down", KEY_DOWN, () -> move(1));
    }

    private void newClick() {
        if (!busy()) insert(Editor.click(this, null));
    }

    private void newText() {
        if (!busy()) insert(Editor.text(this, null));
    }

    private void newKeys() {
        if (!busy()) insert(Editor.keys(this, null));
    }

    private void newWait() {
        if (!busy()) insert(Editor.wait(this, null));
    }

    private void newWindow() {
        if (!busy()) insert(Editor.focus(this, null));
    }

    private void newScroll() {
        if (!busy()) insert(Editor.scroll(this, null));
    }

    private void newDrag() {
        if (!busy()) insert(Editor.drag(this, null));
    }

    /** Saves the list as it is now so the next change can be undone. */
    private void remember() {
        history.record(snapshot());
    }

    private void undo() {
        if (busy()) return;
        restore(history.undo(snapshot()));
    }

    private void redo() {
        if (busy()) return;
        restore(history.redo(snapshot()));
    }

    private void restore(List<Step> steps) {
        if (steps == null) return;
        int selected = list.getSelectedIndex();
        model.clear();
        for (Step step : steps) model.addElement(step);
        if (!model.isEmpty()) list.setSelectedIndex(Math.max(0, Math.min(selected, model.getSize() - 1)));
        changed();
    }

    private void bind(String name, KeyStroke stroke, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    private void start() {
        if (running || model.isEmpty()) return;
        List<Step> steps = snapshot();
        int times = number(repeats, 1);
        int wait = number(delay, 0);
        int between = number(interval, 0);
        if (recording) return;
        running = true;
        stopFlag.set(false);
        updateEnabled();
        showHud("Parar");
        Thread worker = new Thread(() -> runSteps(steps, times, wait, between), "macroeasy");
        worker.setDaemon(true);
        worker.start();
    }

    private void runSteps(List<Step> steps, int times, int waitMs, int intervalMs) {
        String end = "Concluído";
        try {
            Player player = new Player(stopFlag::get);
            if (waitMs > 0) {
                SwingUtilities.invokeLater(() -> setStatus("Começa em " + waitMs + " ms — foque o aplicativo alvo"));
                if (!player.pause(waitMs)) {
                    end = "Interrompido";
                    return;
                }
            }
            SwingUtilities.invokeLater(() -> setExtendedState(Frame.ICONIFIED));
            if (!player.pause(280) || !player.play(steps, times, intervalMs, (round, index) ->
                    SwingUtilities.invokeLater(() -> setStatus(
                            "Passo " + (index + 1) + " de " + steps.size() + "  ·  vez " + round + " de " + times)))) {
                end = "Interrompido";
            }
        } catch (java.awt.AWTException | SecurityException ex) {
            end = "permission";
        } catch (RuntimeException ex) {
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            end = "Erro durante a execução: " + reason;
        } finally {
            String message = end;
            SwingUtilities.invokeLater(() -> finish(message));
        }
    }

    private void finish(String message) {
        running = false;
        hideHud();
        if (!isDisplayable()) return;
        setExtendedState(Frame.NORMAL);
        if ("permission".equals(message)) {
            JOptionPane.showMessageDialog(this, MAC
                            ? "O sistema bloqueou o controle do mouse e do teclado.\n"
                            + "Abra Ajustes do Sistema → Privacidade e Segurança → Acessibilidade\n"
                            + "e permita o Java, o Terminal ou o MacroEasy."
                            : "Não foi possível controlar o mouse e o teclado nesta sessão.\n"
                            + "No Linux, a sessão precisa ser X11 (Wayland bloqueia o controle).",
                    "MacroEasy", JOptionPane.WARNING_MESSAGE);
            setStatus(MAC ? "Sem permissão de acessibilidade." : "Sem controle do mouse e do teclado.");
        } else if (message.startsWith("Erro")) {
            JOptionPane.showMessageDialog(this, message, "MacroEasy", JOptionPane.WARNING_MESSAGE);
            setStatus(message);
        } else {
            setStatus(message);
        }
        updateEnabled();
        toFront();
    }

    private void insert(Step step) {
        if (step == null || running) return;
        remember();
        int selected = list.getSelectedIndex();
        int index = selected < 0 ? model.getSize() : selected + 1;
        model.add(index, step);
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
        changed();
    }

    private void editSelected() {
        int index = list.getSelectedIndex();
        if (index < 0 || running) return;
        Step current = model.get(index);
        Step next = switch (current.kind) {
            case CLICK -> Editor.click(this, current);
            case TEXT -> Editor.text(this, current);
            case KEYS -> Editor.keys(this, current);
            case WAIT -> Editor.wait(this, current);
            case FOCUS -> Editor.focus(this, current);
            case SCROLL -> Editor.scroll(this, current);
            case DRAG -> Editor.drag(this, current);
        };
        if (next == null) return;
        remember();
        model.set(index, next);
        list.setSelectedIndex(index);
        changed();
    }

    private void duplicateSelected() {
        int index = list.getSelectedIndex();
        if (index < 0 || running) return;
        Step copy = Step.parse(model.get(index).encode());
        if (copy == null) return;
        remember();
        model.add(index + 1, copy);
        list.setSelectedIndex(index + 1);
        list.ensureIndexIsVisible(index + 1);
        changed();
    }

    private void deleteSelected() {
        int index = list.getSelectedIndex();
        if (index < 0 || running) return;
        remember();
        model.remove(index);
        if (!model.isEmpty()) list.setSelectedIndex(Math.min(index, model.getSize() - 1));
        changed();
    }

    private void move(int delta) {
        int index = list.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= model.getSize() || running) return;
        remember();
        Step step = model.remove(index);
        model.add(target, step);
        list.setSelectedIndex(target);
        list.ensureIndexIsVisible(target);
        changed();
    }

    private void open() {
        if (running || recording) return;
        JFileChooser chooser = chooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        rememberDirectory(path);
        try {
            Path stored = importMacro(path);
            refreshMacros();
            selectMacro(stored);
            loadMacro(stored);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Não foi possível abrir o arquivo.", "MacroEasy",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean save() {
        if (running) return false;
        if (file == null) return saveAs();
        if (!persist()) return false;
        setStatus("Salvo.");
        return true;
    }

    private boolean saveAs() {
        JFileChooser chooser = chooser();
        chooser.setCurrentDirectory(library().toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
        Path path = chooser.getSelectedFile().toPath();
        String name = path.getFileName().toString();
        if (!name.endsWith(".macro")) name = name + ".macro";
        file = library().resolve(name);
        if (!persist()) return false;
        refreshMacros();
        selectMacro(file);
        setStatus("Salvo.");
        return true;
    }

    private void quit() {
        if (running) stopFlag.set(true);
        if (recording && recorder != null) recorder.stop();
        persist();
        rememberWindow();
        hideHud();
        dispose();
    }

    private void changed() {
        if (persist()) idleStatus();
        updateEnabled();
    }

    private void loadSession() {
        repeats.setValue(prefs.getInt("repeats", 1));
        delay.setValue(storedDelay());
        interval.setValue(prefs.getInt("intervalMs", 200));
        repeats.addChangeListener(e -> prefs.putInt("repeats", number(repeats, 1)));
        delay.addChangeListener(e -> prefs.putInt("delayMs", number(delay, 2000)));
        interval.addChangeListener(e -> prefs.putInt("intervalMs", number(interval, 200)));
        String stored = prefs.get("file", "");
        if (!stored.isBlank()) file = Path.of(stored);
        try {
            Files.createDirectories(library());
            if (macros.isEmpty()) refreshMacros();
            if (file == null || !Files.isRegularFile(file) || !file.startsWith(library())) {
                Path session = sessionFile();
                if (Files.isRegularFile(session)) {
                    file = library().resolve(nextName() + ".macro");
                    Files.copy(session, file);
                } else if (macros.isEmpty()) {
                    file = library().resolve("Macro 1.macro");
                    MacroFile.save(file, List.of());
                } else {
                    file = macros.get(0);
                }
            }
        } catch (IOException ex) {
            setStatus("Não foi possível preparar a pasta Documentos.");
        }
        refreshMacros();
        if (file != null && Files.isRegularFile(file)) loadMacro(file);
        else idleStatus();
        updateEnabled();
    }

    private static Path library() {
        return Library.root();
    }

    private void refreshMacros() {
        loadingLibrary = true;
        try {
            macros.clear();
            Library.list(library()).forEach(macros::addElement);
            selectMacro(file);
        } catch (IOException ex) {
            setStatus("Não foi possível ler as macros em Documentos.");
        } finally {
            loadingLibrary = false;
        }
    }

    private void selectMacro(Path path) {
        if (path == null) return;
        for (int i = 0; i < macros.size(); i++) {
            if (macros.get(i).equals(path)) {
                macroList.setSelectedIndex(i);
                macroList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private void loadMacro(Path path) {
        try {
            List<Step> steps = MacroFile.load(path);
            file = path;
            history.clear();
            model.clear();
            for (Step step : steps) model.addElement(step);
            if (!model.isEmpty()) list.setSelectedIndex(0);
            dirty = false;
            prefs.put("file", path.toString());
            refreshTitle();
            storeSession();
            idleStatus();
            updateEnabled();
        } catch (IOException ex) {
            setStatus("Não foi possível abrir a macro.");
        }
    }

    private void createMacro() {
        if (running || recording) return;
        String name = JOptionPane.showInputDialog(this, "Nome da macro", nextName());
        if (name == null) return;
        name = name.trim().replaceAll("[\\\\/:*?\"<>|]", "");
        if (name.isBlank()) return;
        if (name.toLowerCase().endsWith(".macro")) name = name.substring(0, name.length() - 6);
        Path path = library().resolve(name + ".macro");
        if (Files.exists(path)) {
            JOptionPane.showMessageDialog(this, "Já existe uma macro com esse nome.", "MacroEasy",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            MacroFile.save(path, List.of());
            file = path;
            history.clear();
            model.clear();
            dirty = false;
            refreshMacros();
            selectMacro(path);
            refreshTitle();
            idleStatus();
            updateEnabled();
        } catch (IOException ex) {
            setStatus("Não foi possível criar a macro.");
        }
    }

    private void deleteMacro() {
        if (running || recording || file == null) return;
        String name = file.getFileName().toString();
        if (name.endsWith(".macro")) name = name.substring(0, name.length() - 6);
        int choice = JOptionPane.showConfirmDialog(this, "Excluir a macro “" + name + "”?",
                "MacroEasy", JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION) return;
        int index = macroList.getSelectedIndex();
        try {
            Files.deleteIfExists(file);
            file = null;
            history.clear();
            refreshMacros();
            if (macros.isEmpty()) {
                model.clear();
                refreshTitle();
                idleStatus();
                updateEnabled();
                return;
            }
            loadMacro(macros.get(Math.min(Math.max(index, 0), macros.size() - 1)));
            selectMacro(file);
        } catch (IOException ex) {
            setStatus("Não foi possível excluir a macro.");
        }
    }

    private Path importMacro(Path path) throws IOException {
        return Library.importInto(library(), path);
    }

    private String nextName() {
        return Library.nextName(library());
    }

    private boolean persist() {
        try {
            Files.createDirectories(library());
            if (file == null || !file.startsWith(library())) file = library().resolve(nextName() + ".macro");
            boolean known = false;
            for (int i = 0; i < macros.size(); i++) {
                if (macros.get(i).equals(file)) known = true;
            }
            MacroFile.save(file, snapshot());
            dirty = false;
            prefs.put("file", file.toString());
            refreshTitle();
            storeSession();
            if (!known) refreshMacros();
            return true;
        } catch (IOException ex) {
            setStatus("Não foi possível salvar em Documentos.");
            return false;
        }
    }

    private void storeSession() {
        try {
            Path session = sessionFile();
            Files.createDirectories(session.getParent());
            MacroFile.save(session, snapshot());
            prefs.putBoolean("dirty", dirty);
            prefs.put("file", file == null ? "" : file.toString());
        } catch (IOException ex) {
            setStatus("Não foi possível guardar o rascunho.");
        }
    }

    private List<Step> snapshot() {
        List<Step> steps = new ArrayList<>(model.getSize());
        for (int i = 0; i < model.getSize(); i++) steps.add(model.getElementAt(i));
        return steps;
    }

    private void updateEnabled() {
        boolean idle = !running && !recording;
        run.setEnabled(idle && !model.isEmpty());
        record.setEnabled(idle);
        stop.setEnabled(running || recording);
        addStep.setEnabled(idle);
        list.setEnabled(idle);
        macroList.setEnabled(idle);
        if (removeMacro != null) removeMacro.setEnabled(idle && file != null);
        if (undoItem != null) undoItem.setEnabled(idle && history.canUndo());
        if (redoItem != null) redoItem.setEnabled(idle && history.canRedo());
    }

    private void idleStatus() {
        int count = model.getSize();
        if (count == 0) {
            setStatus("Nenhum passo. Adicione um clique, texto, atalho, espera, janela, rolagem ou arrasto.");
        } else {
            setStatus(count + (count == 1 ? " passo. " : " passos. ")
                    + "Para interromper: Esc, Parar, ou o mouse no canto superior esquerdo.");
        }
    }

    private void refreshTitle() {
        String name = file == null ? "rascunho" : file.getFileName().toString();
        setTitle("MacroEasy — " + name + (dirty ? " •" : ""));
    }

    private void setStatus(String text) {
        if (isDisplayable() || status.getParent() != null) status.setText(text);
    }

    private void showHud(String label) {
        if (hud == null) {
            hudButton = button("Parar", Icons.stop());
            hudButton.setFont(hudButton.getFont().deriveFont(Font.BOLD, 12f));
            hudButton.addActionListener(e -> halt());
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            panel.add(hudButton);
            hud = new JWindow();
            hud.setAutoRequestFocus(false);
            hud.setAlwaysOnTop(true);
            hud.add(panel);
        }
        hudButton.setText(label);
        hud.pack();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        hud.setLocation(screen.x + screen.width - hud.getWidth() - 24,
                screen.y + screen.height - hud.getHeight() - 88);
        hud.setVisible(true);
        cacheHud();
    }

    private void cacheHud() {
        if (hud != null && hud.isShowing()) {
            java.awt.Point origin = hud.getLocationOnScreen();
            hudHit = new Rectangle(origin.x - 12, origin.y - 12, hud.getWidth() + 24, hud.getHeight() + 24);
        }
    }

    private void halt() {
        stopFlag.set(true);
        if (recording && recorder != null) recorder.stop();
    }

    private void record() {
        if (running || recording) return;
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            JOptionPane.showMessageDialog(this, "A gravação está disponível no macOS.", "MacroEasy",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        remember();
        recording = true;
        stopFlag.set(false);
        updateEnabled();
        boolean timing = prefs.getBoolean("recordTiming", false);
        setStatus(timing
                ? "Gravando cliques, arrastos, teclas e rolagem, com as pausas reais."
                : "Gravando cliques, arrastos, teclas e rolagem. O intervalo entre eles fica no campo Intervalo.");
        recorder = new Recorder(new Recorder.Out() {
            @Override
            public void add(Step step) {
                SwingUtilities.invokeLater(() -> appendRecorded(step, false));
            }

            @Override
            public void replaceLast(Step step) {
                SwingUtilities.invokeLater(() -> appendRecorded(step, true));
            }
        }, () -> hudHit, timing);
        showHud("Parar gravação");
        setExtendedState(Frame.ICONIFIED);
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            String error = recorder.run();
            SwingUtilities.invokeLater(() -> finishRecord(error));
        }, "macroeasy-record");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * With {@code replaceLast}, the most recent step of the same kind is updated: a double click,
     * or a longer scroll. A drag replaces the click recorded when the button went down.
     * The replaced step keeps its pause.
     */
    private void appendRecorded(Step step, boolean replaceLast) {
        if (replaceLast) {
            Step.Kind kind = step.kind == Step.Kind.DRAG ? Step.Kind.CLICK : step.kind;
            for (int i = model.getSize() - 1; i >= 0 && i >= model.getSize() - 3; i--) {
                if (model.get(i).kind == kind) {
                    if (step.gapMs < 0) step = step.withGap(model.get(i).gapMs);
                    model.set(i, step);
                    list.setSelectedIndex(i);
                    list.ensureIndexIsVisible(i);
                    persist();
                    return;
                }
            }
        }
        model.addElement(step);
        int index = model.getSize() - 1;
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
        persist();
    }

    private void finishRecord(String error) {
        recording = false;
        recorder = null;
        hudHit = null;
        hideHud();
        if (!isDisplayable()) return;
        setExtendedState(Frame.NORMAL);
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "MacroEasy", JOptionPane.WARNING_MESSAGE);
            setStatus(error);
        } else {
            setStatus("Gravação concluída.");
        }
        updateEnabled();
        toFront();
    }

    private int storedDelay() {
        int millis = prefs.getInt("delayMs", -1);
        if (millis >= 0) return millis;
        int previous = prefs.getInt("delay", 2);
        return previous <= 60 ? previous * 1000 : previous;
    }

    private void hideHud() {
        if (hud != null) hud.setVisible(false);
    }

    private JFileChooser chooser() {
        JFileChooser chooser = new JFileChooser();
        String dir = prefs.get("dir", "");
        if (!dir.isBlank()) chooser.setCurrentDirectory(Path.of(dir).toFile());
        if (file != null) chooser.setSelectedFile(file.toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Macro (*.macro)", "macro"));
        return chooser;
    }

    private void rememberDirectory(Path path) {
        Path parent = path.getParent();
        if (parent != null) prefs.put("dir", parent.toString());
    }

    private static Path sessionFile() {
        return Library.sessionFile();
    }

    private static int number(JSpinner spinner, int fallback) {
        try {
            spinner.commitEdit();
        } catch (ParseException ignored) {
            return fallback;
        }
        return ((Number) spinner.getValue()).intValue();
    }

    private static JPanel row(Component... parts) {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 3));
        panel.setOpaque(false);
        for (Component part : parts) panel.add(part);
        return panel;
    }

    /** A small number field with a short caption to the left. */
    private static JPanel field(String caption, JComponent editor) {
        JLabel label = new JLabel(caption);
        label.setFont(label.getFont().deriveFont(11.5f));
        label.setForeground(Theme.muted());
        label.setToolTipText(editor.getToolTipText());
        JPanel box = new JPanel(new BorderLayout(5, 0));
        box.setOpaque(false);
        box.add(label, BorderLayout.WEST);
        box.add(editor, BorderLayout.CENTER);
        box.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        return box;
    }

    private static JButton button(String text, javax.swing.Icon icon) {
        return style(new JButton(text, icon));
    }

    /** A button whose shortcut is in its tooltip, so the caption stays short. */
    private static JButton button(String text, javax.swing.Icon icon, KeyStroke stroke) {
        JButton button = style(new JButton(text, icon));
        button.setToolTipText(text + "  (" + hint(stroke) + ")");
        return button;
    }

    /** A square button with only an icon. */
    private static JButton iconButton(javax.swing.Icon icon, String tip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tip);
        button.setFocusPainted(false);
        button.setMargin(new Insets(4, 4, 4, 4));
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        return button;
    }

    private static JButton style(JButton button) {
        button.setMargin(new Insets(3, 8, 3, 10));
        button.setIconTextGap(6);
        button.setFocusPainted(false);
        return button;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    /** Human label for a shortcut: ⌘⇧R on the Mac, Ctrl+Shift+R elsewhere. */
    static String hint(KeyStroke stroke) {
        int mods = stroke.getModifiers();
        StringBuilder sb = new StringBuilder();
        if ((mods & KeyEvent.CTRL_DOWN_MASK) != 0) sb.append(MAC ? "⌃" : "Ctrl+");
        if ((mods & KeyEvent.ALT_DOWN_MASK) != 0) sb.append(MAC ? "⌥" : "Alt+");
        if ((mods & KeyEvent.SHIFT_DOWN_MASK) != 0) sb.append(MAC ? "⇧" : "Shift+");
        if ((mods & KeyEvent.META_DOWN_MASK) != 0) sb.append("⌘");
        sb.append(switch (stroke.getKeyCode()) {
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_BACK_SPACE -> "⌫";
            case KeyEvent.VK_DELETE -> "Del";
            default -> KeyEvent.getKeyText(stroke.getKeyCode());
        });
        return sb.toString();
    }

    private static void lightenFonts() {
        for (String key : new String[] {
                "Label.font", "Button.font", "List.font", "TextField.font",
                "Menu.font", "MenuItem.font", "Spinner.font", "ComboBox.font"}) {
            Font font = UIManager.getFont(key);
            if (font != null) UIManager.put(key, font.deriveFont(Math.max(11f, font.getSize2D() - 2f)));
        }
    }

    static Image icon() {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(28, 28, 28));
        g.fillRoundRect(2, 2, 60, 60, 16, 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        g.drawString("M", 16, 46);
        g.dispose();
        return image;
    }

    static void installIcon() {
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(icon());
            }
        } catch (UnsupportedOperationException ignored) {
            // Dock icon stays at the runtime default.
        }
    }
}
