package macroeasy;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Records clicks and keys into steps until {@link #stop()} is called. */
final class Recorder {
    private static final long MOUSE_LEFT = 1L << 1;
    private static final long MOUSE_RIGHT = 1L << 3;
    private static final long MOUSE_OTHER = 1L << 25;
    private static final long KEY_DOWN = 1L << 10;
    private static final long COMMAND = 0x100000;
    private static final long CONTROL = 0x40000;
    private static final long OPTION = 0x80000;
    private static final long SHIFT = 0x20000;

    interface Out {
        void add(Step step);
        void replaceLast(Step step);
    }

    private final Out out;
    private final Supplier<Rectangle> ignore;
    private final StringBuilder typed = new StringBuilder();
    private volatile boolean stopRequested;
    private long lastNs;
    private SegmentAllocator points;
    private MethodHandle location;
    private MethodHandle flags;
    private MethodHandle field;
    private MethodHandle unicode;
    private MemorySegment actual;
    private MemorySegment chars;

    Recorder(Out out, Supplier<Rectangle> ignore) {
        this.out = out;
        this.ignore = ignore;
    }

    void stop() {
        stopRequested = true;
    }

    /** Blocks until stopped. Returns an error message, or null when the recording was kept. */
    String run() {
        stopRequested = false;
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return "A gravação está disponível no macOS.";
        }
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup cg = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", arena);
            SymbolLookup cf = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", arena);
            MethodHandle request = linker.downcallHandle(cg.find("CGRequestListenEventAccess").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN));
            boolean allowed = (boolean) request.invokeExact();
            if (!allowed && stopRequested) return null;
            MethodHandle callback = MethodHandles.lookup().findVirtual(Recorder.class, "onEvent",
                    MethodType.methodType(MemorySegment.class, MemorySegment.class, int.class,
                            MemorySegment.class, MemorySegment.class)).bindTo(this);
            MemorySegment stub = linker.upcallStub(callback, FunctionDescriptor.of(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS), arena);
            MethodHandle create = linker.downcallHandle(cg.find("CGEventTapCreate").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            long mask = MOUSE_LEFT | MOUSE_RIGHT | MOUSE_OTHER | KEY_DOWN;
            MemorySegment tap = (MemorySegment) create.invokeExact(1, 0, 1, mask, stub, MemorySegment.NULL);
            if (tap.address() == 0) {
                return "Para gravar, permita o MacroEasy em Monitoramento de entrada, nos Ajustes do Sistema.";
            }
            MethodHandle source = linker.downcallHandle(cf.find("CFMachPortCreateRunLoopSource").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            MethodHandle current = linker.downcallHandle(cf.find("CFRunLoopGetCurrent").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS));
            MethodHandle add = linker.downcallHandle(cf.find("CFRunLoopAddSource").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle enable = linker.downcallHandle(cg.find("CGEventTapEnable").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN));
            MethodHandle runMode = linker.downcallHandle(cf.find("CFRunLoopRunInMode").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_BOOLEAN));
            MemorySegment mode = cf.find("kCFRunLoopDefaultMode").orElseThrow()
                    .reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
            MemorySegment portSource = (MemorySegment) source.invokeExact(MemorySegment.NULL, tap, 0L);
            MemorySegment loop = (MemorySegment) current.invokeExact();
            add.invokeExact(loop, portSource, mode);
            enable.invokeExact(tap, true);
            location = linker.downcallHandle(cg.find("CGEventGetLocation").orElseThrow(),
                    FunctionDescriptor.of(MemoryLayoutPoint(), ValueLayout.ADDRESS));
            flags = linker.downcallHandle(cg.find("CGEventGetFlags").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            field = linker.downcallHandle(cg.find("CGEventGetIntegerValueField").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            unicode = linker.downcallHandle(cg.find("CGEventKeyboardGetUnicodeString").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment pointBuf = arena.allocate(16);
            points = (size, align) -> pointBuf;
            actual = arena.allocate(ValueLayout.JAVA_LONG);
            chars = arena.allocate(32);
            lastNs = System.nanoTime();
            while (!stopRequested) {
                int ignored = (int) runMode.invokeExact(mode, 0.15d, false);
                if (ignored == Integer.MIN_VALUE) break;
            }
            flushText();
            enable.invokeExact(tap, false);
            return null;
        } catch (Throwable ex) {
            return "Não foi possível gravar. Permita o MacroEasy em Monitoramento de entrada.";
        }
    }

    private static java.lang.foreign.MemoryLayout MemoryLayoutPoint() {
        return java.lang.foreign.MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
    }

    @SuppressWarnings("unused")
    private MemorySegment onEvent(MemorySegment proxy, int type, MemorySegment event, MemorySegment user) {
        try {
            if (!stopRequested && event.address() != 0 && type != 0xFFFFFFFE) handle(type, event);
        } catch (Throwable ignored) {
            // One bad event should not end the recording.
        }
        return event;
    }

    private void handle(int type, MemorySegment event) throws Throwable {
        if (type == 10) {
            key(event, (long) field.invokeExact(event, 9), (long) field.invokeExact(event, 8), (long) flags.invokeExact(event));
            return;
        }
        MemorySegment point = (MemorySegment) location.invokeExact(points, event);
        int x = (int) Math.round(point.get(ValueLayout.JAVA_DOUBLE, 0));
        int y = (int) Math.round(point.get(ValueLayout.JAVA_DOUBLE, 8));
        Rectangle skip = ignore.get();
        if (skip != null && skip.contains(x, y)) return;
        int button = switch (type) {
            case 3 -> 3;
            case 25 -> 2;
            default -> 1;
        };
        int clicks = (int) (long) field.invokeExact(event, 1);
        flushText();
        if (clicks >= 2) {
            out.replaceLast(Step.click(x, y, button, 2));
            lastNs = System.nanoTime();
            return;
        }
        gap();
        out.add(Step.click(x, y, button, 1));
    }

    private void key(MemorySegment event, long code, long repeat, long flag) throws Throwable {
        if (repeat != 0 || isModifier((int) code)) return;
        boolean shortcut = (flag & (COMMAND | CONTROL | OPTION)) != 0;
        int vk = macToJava((int) code);
        if (shortcut || isSpecial((int) code)) {
            if (vk == KeyEvent.VK_UNDEFINED) return;
            flushText();
            gap();
            List<Integer> keys = new ArrayList<>(5);
            if ((flag & COMMAND) != 0) keys.add(KeyEvent.VK_META);
            if ((flag & CONTROL) != 0) keys.add(KeyEvent.VK_CONTROL);
            if ((flag & OPTION) != 0) keys.add(KeyEvent.VK_ALT);
            if ((flag & SHIFT) != 0) keys.add(KeyEvent.VK_SHIFT);
            keys.add(vk);
            out.add(Step.keys(keys.stream().mapToInt(Integer::intValue).toArray()));
            return;
        }
        if ((int) code == 0x33) {
            if (!typed.isEmpty()) {
                typed.deleteCharAt(typed.length() - 1);
                lastNs = System.nanoTime();
            } else {
                gap();
                out.add(Step.keys(new int[] {KeyEvent.VK_BACK_SPACE}));
            }
            return;
        }
        if ((int) code == 0x24 || (int) code == 0x4C) {
            appendText("\n");
            return;
        }
        if ((int) code == 0x30) {
            appendText("\t");
            return;
        }
        String text = characters(event);
        if (!text.isEmpty()) appendText(text.replace('\r', '\n'));
    }

    private void appendText(String text) {
        if (typed.isEmpty()) gap();
        else if (millisSinceLast() >= 500) {
            flushText();
            gap();
        }
        typed.append(text);
        lastNs = System.nanoTime();
    }

    private String characters(MemorySegment event) throws Throwable {
        actual.set(ValueLayout.JAVA_LONG, 0, 0);
        unicode.invokeExact(event, 8L, actual, chars);
        int count = (int) actual.get(ValueLayout.JAVA_LONG, 0);
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count && i < 8; i++) {
            char c = chars.get(ValueLayout.JAVA_CHAR, i * 2L);
            if (c != 0) sb.append(c);
        }
        return sb.toString();
    }

    private void flushText() {
        if (typed.isEmpty()) return;
        out.add(Step.text(typed.toString(), true));
        typed.setLength(0);
    }

    private void gap() {
        lastNs = System.nanoTime();
    }

    private long millisSinceLast() {
        return (System.nanoTime() - lastNs) / 1_000_000L;
    }

    private static boolean isModifier(int code) {
        return code == 0x37 || code == 0x38 || code == 0x3A || code == 0x3B
                || code == 0x3C || code == 0x3D || code == 0x3E;
    }

    private static boolean isSpecial(int code) {
        return code == 0x35 || (code >= 0x60 && code <= 0x7E);
    }

    private static int macToJava(int code) {
        return switch (code) {
            case 0x00 -> KeyEvent.VK_A;
            case 0x01 -> KeyEvent.VK_S;
            case 0x02 -> KeyEvent.VK_D;
            case 0x03 -> KeyEvent.VK_F;
            case 0x04 -> KeyEvent.VK_H;
            case 0x05 -> KeyEvent.VK_G;
            case 0x06 -> KeyEvent.VK_Z;
            case 0x07 -> KeyEvent.VK_X;
            case 0x08 -> KeyEvent.VK_C;
            case 0x09 -> KeyEvent.VK_V;
            case 0x0B -> KeyEvent.VK_B;
            case 0x0C -> KeyEvent.VK_Q;
            case 0x0D -> KeyEvent.VK_W;
            case 0x0E -> KeyEvent.VK_E;
            case 0x0F -> KeyEvent.VK_R;
            case 0x10 -> KeyEvent.VK_Y;
            case 0x11 -> KeyEvent.VK_T;
            case 0x12 -> KeyEvent.VK_1;
            case 0x13 -> KeyEvent.VK_2;
            case 0x14 -> KeyEvent.VK_3;
            case 0x15 -> KeyEvent.VK_4;
            case 0x16 -> KeyEvent.VK_6;
            case 0x17 -> KeyEvent.VK_5;
            case 0x18 -> KeyEvent.VK_EQUALS;
            case 0x19 -> KeyEvent.VK_9;
            case 0x1A -> KeyEvent.VK_7;
            case 0x1B -> KeyEvent.VK_MINUS;
            case 0x1C -> KeyEvent.VK_8;
            case 0x1D -> KeyEvent.VK_0;
            case 0x1E -> KeyEvent.VK_CLOSE_BRACKET;
            case 0x1F -> KeyEvent.VK_O;
            case 0x20 -> KeyEvent.VK_U;
            case 0x21 -> KeyEvent.VK_OPEN_BRACKET;
            case 0x22 -> KeyEvent.VK_I;
            case 0x23 -> KeyEvent.VK_P;
            case 0x24, 0x4C -> KeyEvent.VK_ENTER;
            case 0x25 -> KeyEvent.VK_L;
            case 0x26 -> KeyEvent.VK_J;
            case 0x27 -> KeyEvent.VK_QUOTE;
            case 0x28 -> KeyEvent.VK_K;
            case 0x29 -> KeyEvent.VK_SEMICOLON;
            case 0x2A -> KeyEvent.VK_BACK_SLASH;
            case 0x2B -> KeyEvent.VK_COMMA;
            case 0x2C -> KeyEvent.VK_SLASH;
            case 0x2D -> KeyEvent.VK_N;
            case 0x2E -> KeyEvent.VK_M;
            case 0x2F -> KeyEvent.VK_PERIOD;
            case 0x30 -> KeyEvent.VK_TAB;
            case 0x31 -> KeyEvent.VK_SPACE;
            case 0x32 -> KeyEvent.VK_BACK_QUOTE;
            case 0x33 -> KeyEvent.VK_BACK_SPACE;
            case 0x35 -> KeyEvent.VK_ESCAPE;
            case 0x7A -> KeyEvent.VK_F1;
            case 0x78 -> KeyEvent.VK_F2;
            case 0x63 -> KeyEvent.VK_F3;
            case 0x76 -> KeyEvent.VK_F4;
            case 0x60 -> KeyEvent.VK_F5;
            case 0x61 -> KeyEvent.VK_F6;
            case 0x62 -> KeyEvent.VK_F7;
            case 0x64 -> KeyEvent.VK_F8;
            case 0x65 -> KeyEvent.VK_F9;
            case 0x6D -> KeyEvent.VK_F10;
            case 0x67 -> KeyEvent.VK_F11;
            case 0x6F -> KeyEvent.VK_F12;
            case 0x73 -> KeyEvent.VK_HOME;
            case 0x77 -> KeyEvent.VK_END;
            case 0x74 -> KeyEvent.VK_PAGE_UP;
            case 0x79 -> KeyEvent.VK_PAGE_DOWN;
            case 0x7B -> KeyEvent.VK_LEFT;
            case 0x7C -> KeyEvent.VK_RIGHT;
            case 0x7D -> KeyEvent.VK_DOWN;
            case 0x7E -> KeyEvent.VK_UP;
            default -> KeyEvent.VK_UNDEFINED;
        };
    }
}
