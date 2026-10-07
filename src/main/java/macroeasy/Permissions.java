package macroeasy;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.swing.BorderFactory;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Blocks the editor until macOS has granted every permission the macro needs. */
final class Permissions {
    private Permissions() {}

    private static Gate gate;

    static void ensure(Frame owner, Runnable ready) {
        if (!isMac() || Access.granted()) {
            ready.run();
            return;
        }
        if (gate != null && gate.isDisplayable()) return;
        gate = new Gate(owner, ready);
        gate.setVisible(true);
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static void openSettings(String pane) {
        try {
            Process quit = new ProcessBuilder("osascript", "-e",
                    "tell application \"System Settings\" to quit").start();
            quit.waitFor();
            Thread.sleep(400);
        } catch (Exception ignored) {
            // Opening the pane still works if Settings was already closed.
        }
        try {
            new ProcessBuilder("open",
                    "x-apple.systempreferences:com.apple.settings.PrivacySecurity.extension?" + pane)
                    .start();
        } catch (Exception ignored) {
            // The checklist still explains where to click.
        }
    }

    /** Native checks. Loaded once; null when the system libraries cannot be called. */
    private static final class Access {
        private static Access loaded;
        private static boolean failed;
        private final MethodHandle trusted;
        private final MethodHandle trustedWithOptions;
        private final MethodHandle dictionary;
        private final MethodHandle release;
        private final MethodHandle listenAllowed;
        private final MethodHandle requestListen;
        private final MethodHandle hidCheck;
        private final MethodHandle hidRequest;
        private final MemorySegment promptKey;
        private final MemorySegment yes;
        private final MemorySegment keyCallbacks;
        private final MemorySegment valueCallbacks;
        private final Arena arena = Arena.global();

        private Access() throws Throwable {
            Linker linker = Linker.nativeLinker();
            SymbolLookup ax = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices", arena);
            SymbolLookup cf = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", arena);
            SymbolLookup cg = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", arena);
            trusted = down(linker, ax, "AXIsProcessTrusted", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN));
            trustedWithOptions = down(linker, ax, "AXIsProcessTrustedWithOptions",
                    FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
            dictionary = down(linker, cf, "CFDictionaryCreate", FunctionDescriptor.of(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            release = down(linker, cf, "CFRelease", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            listenAllowed = down(linker, cg, "CGPreflightListenEventAccess",
                    FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN));
            requestListen = down(linker, cg, "CGRequestListenEventAccess",
                    FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN));
            MethodHandle check = null;
            MethodHandle request = null;
            try {
                SymbolLookup iokit = SymbolLookup.libraryLookup(
                        "/System/Library/Frameworks/IOKit.framework/IOKit", arena);
                check = down(linker, iokit, "IOHIDCheckAccess",
                        FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.JAVA_INT));
                request = down(linker, iokit, "IOHIDRequestAccess",
                        FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.JAVA_INT));
            } catch (Throwable ignored) {
                // Core Graphics still covers the same Monitoramento de entrada permission.
            }
            hidCheck = check;
            hidRequest = request;
            promptKey = pointer(ax, "kAXTrustedCheckOptionPrompt");
            yes = pointer(cf, "kCFBooleanTrue");
            keyCallbacks = cf.find("kCFTypeDictionaryKeyCallBacks").orElseThrow();
            valueCallbacks = cf.find("kCFTypeDictionaryValueCallBacks").orElseThrow();
        }

        static boolean granted() {
            Access access = load();
            return access != null && access.accessibility() && access.listening();
        }

        static Access load() {
            if (loaded != null || failed) return loaded;
            try {
                loaded = new Access();
            } catch (Throwable ex) {
                failed = true;
            }
            return loaded;
        }

        boolean accessibility() {
            try {
                return (boolean) trusted.invokeExact();
            } catch (Throwable ex) {
                return false;
            }
        }

        boolean listening() {
            try {
                if ((boolean) listenAllowed.invokeExact()) return true;
            } catch (Throwable ignored) {
                // Fall through to the IOKit check.
            }
            if (hidCheck == null) return false;
            try {
                return (boolean) hidCheck.invokeExact(1);
            } catch (Throwable ex) {
                return false;
            }
        }

        void request() {
            synchronized (this) {
                if (!accessibility()) promptAccessibility();
            }
        }

        void requestListening() {
            synchronized (this) {
                if (hidRequest != null) {
                    try {
                        boolean ignored = (boolean) hidRequest.invokeExact(1);
                    } catch (Throwable ignored) {
                        // CGRequestListenEventAccess below still registers the app.
                    }
                }
                try {
                    boolean ignored = (boolean) requestListen.invokeExact();
                } catch (Throwable ignored) {
                    // The event tap is what places MacroEasy in the system list.
                }
                registerListenTap();
            }
        }

        private void promptAccessibility() {
            try (Arena scratch = Arena.ofConfined()) {
                MemorySegment keys = scratch.allocate(ValueLayout.ADDRESS);
                MemorySegment values = scratch.allocate(ValueLayout.ADDRESS);
                keys.set(ValueLayout.ADDRESS, 0, promptKey);
                values.set(ValueLayout.ADDRESS, 0, yes);
                MemorySegment options = (MemorySegment) dictionary.invokeExact(
                        MemorySegment.NULL, keys, values, 1L, keyCallbacks, valueCallbacks);
                try {
                    boolean prompted = (boolean) trustedWithOptions.invokeExact(options);
                    if (!prompted) return;
                } finally {
                    if (options != null && options.address() != 0) release.invokeExact(options);
                }
            } catch (Throwable ignored) {
                // The gate keeps polling and can open System Settings directly.
            }
        }

        private static MethodHandle down(Linker linker, SymbolLookup library, String name, FunctionDescriptor type)
                throws Throwable {
            return linker.downcallHandle(library.find(name).orElseThrow(), type);
        }

        /** A listen-only tap is what makes macOS list the app under Monitoramento de entrada. */
        private void registerListenTap() {
            try (Arena scratch = Arena.ofConfined()) {
                Linker linker = Linker.nativeLinker();
                SymbolLookup cg = SymbolLookup.libraryLookup(
                        "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", scratch);
                SymbolLookup cf = SymbolLookup.libraryLookup(
                        "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", scratch);
                MethodHandle callback = MethodHandles.lookup().findVirtual(Access.class, "ignoreEvent",
                        MethodType.methodType(MemorySegment.class, MemorySegment.class, int.class,
                                MemorySegment.class, MemorySegment.class)).bindTo(this);
                MemorySegment stub = linker.upcallStub(callback, FunctionDescriptor.of(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS), scratch);
                MethodHandle create = down(linker, cg, "CGEventTapCreate", FunctionDescriptor.of(
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                MemorySegment tap = (MemorySegment) create.invokeExact(1, 0, 1, 1L << 10, stub, MemorySegment.NULL);
                if (tap.address() == 0) return;
                MethodHandle enable = down(linker, cg, "CGEventTapEnable",
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN));
                MethodHandle source = down(linker, cf, "CFMachPortCreateRunLoopSource", FunctionDescriptor.of(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
                MethodHandle current = down(linker, cf, "CFRunLoopGetCurrent", FunctionDescriptor.of(ValueLayout.ADDRESS));
                MethodHandle add = down(linker, cf, "CFRunLoopAddSource", FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                MethodHandle runMode = down(linker, cf, "CFRunLoopRunInMode", FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_BOOLEAN));
                MemorySegment mode = cf.find("kCFRunLoopDefaultMode").orElseThrow()
                        .reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
                MemorySegment portSource = (MemorySegment) source.invokeExact(MemorySegment.NULL, tap, 0L);
                MemorySegment loop = (MemorySegment) current.invokeExact();
                add.invokeExact(loop, portSource, mode);
                enable.invokeExact(tap, true);
                int ignored = (int) runMode.invokeExact(mode, 0.4d, false);
                enable.invokeExact(tap, false);
                if (ignored == Integer.MIN_VALUE) return;
            } catch (Throwable ignored) {
                // Settings still open so the user can enable the app if it is already listed.
            }
        }

        @SuppressWarnings("unused")
        private MemorySegment ignoreEvent(MemorySegment proxy, int type, MemorySegment event, MemorySegment user) {
            return event;
        }

        private static MemorySegment pointer(SymbolLookup library, String name) throws Throwable {
            MemorySegment symbol = library.find(name).orElseThrow();
            return symbol.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
        }
    }

    private static final class Gate extends JDialog {
        private final JLabel accessibilityMark = new JLabel();
        private final JLabel listenMark = new JLabel();
        private final JButton accessibilityAction = new JButton("Autorizar");
        private final JButton listenAction = new JButton("Autorizar");
        private final Runnable ready;
        private boolean handedOff;
        private final Timer timer;

        Gate(Frame owner, Runnable ready) {
            super(owner, "MacroEasy", false);
            this.ready = ready;
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            setIconImage(MacroEasy.icon());
            JLabel title = new JLabel("Antes de começar");
            title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 15f));
            JLabel body = new JLabel("<html><body style='width:420px'>O MacroEasy precisa destas autorizações. "
                    + "Autorizar coloca o app na lista do sistema. Ative o interruptor e clique em Reabrir: "
                    + "o macOS só grava a permissão na próxima abertura.</body></html>");
            body.setFont(body.getFont().deriveFont(12f));
            JButton reopen = new JButton("Reabrir");
            reopen.setFont(reopen.getFont().deriveFont(12f));
            accessibilityAction.setFont(accessibilityAction.getFont().deriveFont(12f));
            listenAction.setFont(listenAction.getFont().deriveFont(12f));
            accessibilityAction.addActionListener(e -> authorize(true));
            listenAction.addActionListener(e -> authorize(false));
            reopen.addActionListener(e -> relaunch());
            JPanel list = new JPanel(new GridLayout(2, 1, 0, 10));
            list.setOpaque(false);
            list.add(row("Acessibilidade", "Clique, teclado e janelas.", accessibilityMark, accessibilityAction));
            list.add(row("Monitoramento de entrada", "Gravação do que você faz.", listenMark, listenAction));
            JPanel text = new JPanel(new BorderLayout(0, 10));
            text.setOpaque(false);
            text.add(title, BorderLayout.NORTH);
            text.add(body, BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actions.setOpaque(false);
            actions.add(reopen);
            JPanel root = new JPanel(new BorderLayout(0, 16));
            root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
            root.add(text, BorderLayout.NORTH);
            root.add(list, BorderLayout.CENTER);
            root.add(actions, BorderLayout.SOUTH);
            setContentPane(root);
            pack();
            setResizable(false);
            setLocationRelativeTo(owner);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    if (!handedOff) System.exit(0);
                }
            });
            timer = new Timer(400, e -> refresh());
            timer.start();
            refresh();
        }

        private JPanel row(String name, String detail, JLabel mark, JButton action) {
            JLabel heading = new JLabel(name);
            heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD, 12f));
            JLabel sub = new JLabel(detail);
            sub.setFont(sub.getFont().deriveFont(11f));
            mark.setFont(mark.getFont().deriveFont(11f));
            JPanel copy = new JPanel(new BorderLayout(0, 2));
            copy.setOpaque(false);
            copy.add(heading, BorderLayout.NORTH);
            copy.add(sub, BorderLayout.CENTER);
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new java.awt.Color(190, 190, 190)),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            row.add(mark, BorderLayout.WEST);
            row.add(copy, BorderLayout.CENTER);
            row.add(action, BorderLayout.EAST);
            return row;
        }

        private void authorize(boolean accessibility) {
            toBack();
            new Thread(() -> {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                Access access = Access.load();
                if (access != null) {
                    if (accessibility) access.request();
                    else access.requestListening();
                }
                if (!handedOff) {
                    openSettings(accessibility ? "Privacy_Accessibility" : "Privacy_ListenEvent");
                }
                SwingUtilities.invokeLater(this::refresh);
            }, "permissions").start();
        }

        private void refresh() {
            if (handedOff) return;
            Access access = Access.load();
            boolean accessibility = access != null && access.accessibility();
            boolean listening = access != null && access.listening();
            mark(accessibilityMark, accessibilityAction, accessibility);
            mark(listenMark, listenAction, listening);
            if (accessibility && listening) {
                handedOff = true;
                timer.stop();
                setVisible(false);
                dispose();
                ready.run();
            }
        }

        private static void mark(JLabel label, JButton action, boolean ok) {
            label.setIcon(ok ? Icons.ok() : Icons.pending());
            label.setIconTextGap(6);
            label.setText(ok ? "Ok" : "Pendente");
            action.setEnabled(!ok);
            action.setText(ok ? "Autorizado" : "Autorizar");
        }
    }

    /** macOS keeps the old answer for this process, so a fresh launch has to read it. */
    private static void relaunch() {
        String command = ProcessHandle.current().info().command().orElse("");
        Path app = appBundle(command);
        try {
            if (app != null) {
                new ProcessBuilder("sh", "-c", "sleep 0.4; open '" + app.toAbsolutePath() + "'").start();
            }
        } catch (java.io.IOException ignored) {
            // Quitting still leaves the user able to open the app from the Finder.
        }
        System.exit(0);
    }

    private static Path appBundle(String command) {
        if (command.isBlank()) return null;
        Path macOs = Path.of(command).getParent();
        if (macOs == null || !"MacOS".equals(macOs.getFileName().toString())) return null;
        Path contents = macOs.getParent();
        return contents == null ? null : contents.getParent();
    }
}
