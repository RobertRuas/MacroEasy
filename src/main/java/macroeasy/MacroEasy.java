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
import java.util.Comparator;
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
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class MacroEasy extends JFrame {
    private final Preferences prefs = Preferences.userNodeForPackage(MacroEasy.class);
    private final DefaultListModel<Step> model = new DefaultListModel<>();
    private final JList<Step> list = new JList<>(model);
    private final JLabel status = new JLabel(" ");
    private final JButton run = button("Executar", Icons.play());
    private final JButton record = button("Gravar", Icons.record());
    private final JButton stop = button("Parar", Icons.stop());
    private final JButton edit = button("Editar", Icons.edit());
    private final JButton duplicate = button("Duplicar", Icons.copy());
    private final JButton delete = button("Excluir", Icons.trash());
    private final JButton up = button("Subir", Icons.up());
    private final JButton down = button("Descer", Icons.down());
    private final JSpinner repeats = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JSpinner delay = new JSpinner(new SpinnerNumberModel(2000, 0, 600_000, 100));
    private final JSpinner interval = new JSpinner(new SpinnerNumberModel(200, 0, 600_000, 50));
    private final AtomicBoolean stopFlag = new AtomicBoolean();
    private final int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

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
        setMinimumSize(new Dimension(1040, 480));
        setSize(1120, 560);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "MacroEasy");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Keep the cross-platform look if the system one is unavailable.
            }
            installIcon();
            lightenFonts();
            Permissions.ensure(() -> {
                MacroEasy app = new MacroEasy();
                app.setEnabled(false);
                app.setVisible(true);
                Updater.start(app);
            });
        });
    }

    private void build() {
        run.setFont(run.getFont().deriveFont(Font.BOLD));
        run.setToolTipText("Focar o aplicativo alvo durante o atraso, depois executar");
        stop.setToolTipText("Interrompe a execução");
        up.setToolTipText("Subir");
        down.setToolTipText("Descer");
        repeats.setToolTipText("Quantas vezes repetir a lista");
        record.setToolTipText("Grava cliques e teclas. As pausas usam o intervalo");
        delay.setToolTipText("Milissegundos para focar o aplicativo alvo antes de começar");
        interval.setToolTipText("Pausa entre cada ação. Um passo pode ter um intervalo próprio");
        ((JSpinner.DefaultEditor) repeats.getEditor()).getTextField().setColumns(3);
        ((JSpinner.DefaultEditor) delay.getEditor()).getTextField().setColumns(5);
        ((JSpinner.DefaultEditor) interval.getEditor()).getTextField().setColumns(5);

        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(30);
        list.setFont(list.getFont().deriveFont(12f));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> source, Object value, int index,
                                                          boolean selected, boolean focus) {
                Step step = (Step) value;
                JLabel label = (JLabel) super.getListCellRendererComponent(source, value, index, selected, focus);
                label.setIcon(Icons.of(step.kind));
                label.setIconTextGap(8);
                label.setText((index + 1) + "    " + step.describe());
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return label;
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateEnabled();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });

        run.addActionListener(e -> start());
        record.addActionListener(e -> record());
        stop.addActionListener(e -> halt());
        edit.addActionListener(e -> editSelected());
        duplicate.addActionListener(e -> duplicateSelected());
        delete.addActionListener(e -> deleteSelected());
        up.addActionListener(e -> move(-1));
        down.addActionListener(e -> move(1));

        JButton click = button("Clique", Icons.click());
        JButton text = button("Texto", Icons.text());
        JButton keys = button("Atalho", Icons.keys());
        JButton wait = button("Espera", Icons.clock());
        JButton window = button("Janela", Icons.window());
        click.addActionListener(e -> insert(Editor.click(this, null)));
        text.addActionListener(e -> insert(Editor.text(this, null)));
        keys.addActionListener(e -> insert(Editor.keys(this, null)));
        wait.addActionListener(e -> insert(Editor.wait(this, null)));
        window.addActionListener(e -> insert(Editor.focus(this, null)));

        JButton open = button("Abrir", Icons.folder());
        JButton save = button("Salvar", Icons.save());
        open.addActionListener(e -> open());
        save.addActionListener(e -> save());

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.add(row(run, record, stop), BorderLayout.WEST);
        top.add(row(field("Vezes", repeats), field("Atraso", delay), field("Intervalo", interval)), BorderLayout.CENTER);
        top.add(row(open, save), BorderLayout.EAST);

        JPanel tools = new JPanel();
        tools.setLayout(new javax.swing.BoxLayout(tools, javax.swing.BoxLayout.Y_AXIS));
        JPanel createRow = row(click, text, keys, wait, window);
        JPanel changeRow = row(edit, duplicate, delete, up, down);
        createRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        changeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tools.add(createRow);
        tools.add(javax.swing.Box.createVerticalStrut(6));
        tools.add(changeRow);
        tools.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        Color background = UIManager.getColor("Panel.background");
        boolean dark = background != null && background.getRed() + background.getGreen() + background.getBlue() < 380;
        status.setFont(status.getFont().deriveFont(11f));
        status.setForeground(dark ? new Color(186, 186, 186) : new Color(70, 70, 70));
        status.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(tools, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(list);
        Color line = UIManager.getColor("Separator.foreground");
        scroll.setBorder(BorderFactory.createLineBorder(line != null ? line : new Color(180, 180, 180)));

        JPanel editor = new JPanel(new BorderLayout(0, 8));
        editor.add(top, BorderLayout.NORTH);
        editor.add(scroll, BorderLayout.CENTER);
        editor.add(bottom, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(12, 0));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
        root.add(sidebar(line), BorderLayout.WEST);
        root.add(editor, BorderLayout.CENTER);
        setContentPane(root);
        setJMenuBar(menu());
        updateEnabled();
    }

    private JComponent sidebar(Color line) {
        JLabel title = new JLabel("Macros");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 4));
        macroList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        macroList.setFixedCellHeight(30);
        macroList.setFont(macroList.getFont().deriveFont(12f));
        macroList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> source, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(source, value, index, selected, focus);
                String name = ((Path) value).getFileName().toString();
                if (name.endsWith(".macro")) name = name.substring(0, name.length() - 6);
                label.setIcon(Icons.macro());
                label.setIconTextGap(8);
                label.setText(name);
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
        JButton create = button("Nova", Icons.plus());
        removeMacro = button("Excluir", Icons.trash());
        create.addActionListener(e -> createMacro());
        removeMacro.addActionListener(e -> deleteMacro());
        JScrollPane saved = new JScrollPane(macroList);
        saved.setBorder(BorderFactory.createLineBorder(line != null ? line : new Color(180, 180, 180)));
        saved.setPreferredSize(new Dimension(200, 200));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        south.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        south.add(create);
        south.add(removeMacro);
        JPanel side = new JPanel(new BorderLayout());
        side.add(title, BorderLayout.NORTH);
        side.add(saved, BorderLayout.CENTER);
        side.add(south, BorderLayout.SOUTH);
        return side;
    }

    private JMenuBar menu() {
        JMenuBar bar = new JMenuBar();
        JMenu help = new JMenu("Ajuda");
        JMenuItem about = new JMenuItem("Sobre o MacroEasy");
        about.addActionListener(e -> About.show(this));
        help.add(about);
        bar.add(help);
        return bar;
    }

    private void bindKeys() {
        bind("run", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, shortcut), this::start);
        bind("stop", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), this::halt);
        bind("open", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, shortcut), this::open);
        bind("save", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, shortcut), this::save);
        bind("edit", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, shortcut), this::editSelected);
        bind("dup", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, shortcut), this::duplicateSelected);
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "delete");
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_SPACE, 0), "delete");
        list.getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                deleteSelected();
            }
        });
        bind("up", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, shortcut), () -> move(-1));
        bind("down", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, shortcut), () -> move(1));
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
        } catch (Exception ex) {
            end = "permission";
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
            JOptionPane.showMessageDialog(this,
                    "O sistema bloqueou o controle do mouse e do teclado.\n"
                            + "Abra Ajustes do Sistema → Privacidade e Segurança → Acessibilidade\n"
                            + "e permita o Java, o Terminal ou o MacroEasy.",
                    "MacroEasy", JOptionPane.WARNING_MESSAGE);
            setStatus("Sem permissão de acessibilidade.");
        } else {
            setStatus(message);
        }
        updateEnabled();
        toFront();
    }

    private void insert(Step step) {
        if (step == null || running) return;
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
        };
        if (next == null) return;
        model.set(index, next);
        list.setSelectedIndex(index);
        changed();
    }

    private void duplicateSelected() {
        int index = list.getSelectedIndex();
        if (index < 0 || running) return;
        Step copy = Step.parse(model.get(index).encode());
        if (copy == null) return;
        model.add(index + 1, copy);
        list.setSelectedIndex(index + 1);
        list.ensureIndexIsVisible(index + 1);
        changed();
    }

    private void deleteSelected() {
        int index = list.getSelectedIndex();
        if (index < 0 || running) return;
        model.remove(index);
        if (!model.isEmpty()) list.setSelectedIndex(Math.min(index, model.getSize() - 1));
        changed();
    }

    private void move(int delta) {
        int index = list.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= model.getSize() || running) return;
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
        return Path.of(System.getProperty("user.home"), "Documents", "MacroEasy");
    }

    private void refreshMacros() {
        loadingLibrary = true;
        try {
            macros.clear();
            Files.createDirectories(library());
            try (var files = Files.list(library())) {
                files.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".macro"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .forEach(macros::addElement);
            }
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
        Files.createDirectories(library());
        if (path.startsWith(library())) return path;
        String name = path.getFileName().toString();
        if (!name.toLowerCase().endsWith(".macro")) name = name + ".macro";
        Path dest = library().resolve(name);
        int copy = 2;
        while (Files.exists(dest)) {
            String base = name.toLowerCase().endsWith(".macro") ? name.substring(0, name.length() - 6) : name;
            dest = library().resolve(base + " " + copy + ".macro");
            copy++;
        }
        Files.copy(path, dest);
        return dest;
    }

    private String nextName() {
        int number = 1;
        while (Files.exists(library().resolve("Macro " + number + ".macro"))) number++;
        return "Macro " + number;
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
        int index = list.getSelectedIndex();
        run.setEnabled(idle && !model.isEmpty());
        record.setEnabled(idle);
        stop.setEnabled(running || recording);
        edit.setEnabled(idle && index >= 0);
        duplicate.setEnabled(idle && index >= 0);
        delete.setEnabled(idle && index >= 0);
        up.setEnabled(idle && index > 0);
        down.setEnabled(idle && index >= 0 && index < model.getSize() - 1);
        list.setEnabled(idle);
        macroList.setEnabled(idle);
        if (removeMacro != null) removeMacro.setEnabled(idle && file != null);
    }

    private void idleStatus() {
        int count = model.getSize();
        if (count == 0) {
            setStatus("Nenhum passo. Adicione um clique, texto, atalho, espera ou janela.");
        } else {
            setStatus(count + (count == 1 ? " passo. " : " passos. ")
                    + "Para interromper: Parar, ou o mouse no canto superior esquerdo.");
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
        recording = true;
        stopFlag.set(false);
        updateEnabled();
        setStatus("Gravando cliques e teclas. O intervalo entre elas fica no campo Intervalo.");
        recorder = new Recorder(new Recorder.Out() {
            @Override
            public void add(Step step) {
                SwingUtilities.invokeLater(() -> appendRecorded(step, false));
            }

            @Override
            public void replaceLast(Step step) {
                SwingUtilities.invokeLater(() -> appendRecorded(step, true));
            }
        }, () -> hudHit);
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

    private void appendRecorded(Step step, boolean replaceClick) {
        if (replaceClick) {
            for (int i = model.getSize() - 1; i >= 0 && i >= model.getSize() - 3; i--) {
                if (model.get(i).kind == Step.Kind.CLICK) {
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
        return Path.of(System.getProperty("user.home"), ".macroeasy", "session.macro");
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
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        for (Component part : parts) panel.add(part);
        return panel;
    }

    private static JPanel field(String caption, JComponent editor) {
        JLabel label = new JLabel(caption);
        label.setFont(label.getFont().deriveFont(11f));
        JPanel box = new JPanel(new BorderLayout(0, 2));
        box.setOpaque(false);
        box.add(label, BorderLayout.NORTH);
        box.add(editor, BorderLayout.CENTER);
        box.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        return box;
    }

    private static JButton button(String text, javax.swing.Icon icon) {
        JButton button = new JButton(text, icon);
        button.setMargin(new Insets(3, 8, 3, 10));
        button.setIconTextGap(6);
        button.setFont(button.getFont().deriveFont(12f));
        button.setFocusPainted(false);
        return button;
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
