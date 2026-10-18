package macroeasy;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Reads whether Esc is held down right now, in any application. The playback
 * polls it so Esc stops a macro even while another app has the focus.
 * macOS uses Core Graphics, Windows uses user32, Linux uses X11. Where none of
 * them can be called (Wayland without X11, for example) it always answers false.
 */
final class EscapeKey {
    private static final int MAC_ESCAPE = 0x35;
    private static final int WINDOWS_ESCAPE = 0x1B;
    private static final long X11_ESCAPE = 0xFF1B;

    private static Probe probe;
    private static boolean failed;

    private EscapeKey() {}

    static synchronized boolean down() {
        if (probe == null && !failed) {
            try {
                probe = load();
            } catch (Throwable ex) {
                probe = null;
            }
            failed = probe == null;
        }
        if (probe == null) return false;
        try {
            return probe.down();
        } catch (Throwable ex) {
            return false;
        }
    }

    private interface Probe {
        boolean down() throws Throwable;
    }

    private static Probe load() throws Throwable {
        String os = System.getProperty("os.name", "").toLowerCase();
        Linker linker = Linker.nativeLinker();
        Arena arena = Arena.global();
        if (os.contains("mac")) {
            SymbolLookup cg = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", arena);
            MethodHandle state = linker.downcallHandle(cg.find("CGEventSourceKeyState").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.JAVA_INT, ValueLayout.JAVA_SHORT));
            // kCGEventSourceStateCombinedSessionState
            return () -> (boolean) state.invokeExact(0, (short) MAC_ESCAPE);
        }
        if (os.contains("win")) {
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", arena);
            MethodHandle async = linker.downcallHandle(user32.find("GetAsyncKeyState").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.JAVA_INT));
            return () -> ((short) async.invokeExact(WINDOWS_ESCAPE) & 0x8000) != 0;
        }
        if (System.getenv("DISPLAY") == null || System.getenv("DISPLAY").isBlank()) return null;
        SymbolLookup x11 = SymbolLookup.libraryLookup("libX11.so.6", arena);
        MethodHandle open = linker.downcallHandle(x11.find("XOpenDisplay").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MethodHandle keycode = linker.downcallHandle(x11.find("XKeysymToKeycode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        MethodHandle keymap = linker.downcallHandle(x11.find("XQueryKeymap").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MemorySegment display = (MemorySegment) open.invokeExact(MemorySegment.NULL);
        if (display.address() == 0) return null;
        int code = Byte.toUnsignedInt((byte) keycode.invokeExact(display, X11_ESCAPE));
        if (code == 0) return null;
        MemorySegment keys = arena.allocate(32);
        return () -> {
            int ignored = (int) keymap.invokeExact(display, keys);
            return (keys.get(ValueLayout.JAVA_BYTE, code / 8) & (1 << (code % 8))) != 0;
        };
    }
}
