package macroeasy;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Posts scroll events measured in screen points on macOS. Unlike wheel lines, points are not
 * accelerated or rounded by the system, so the same distance lands in the same place every run.
 */
final class MacScroll {
    /** kCGScrollEventUnitPixel. */
    private static final int UNIT_PIXEL = 0;
    /** kCGHIDEventTap. */
    private static final int HID_TAP = 0;

    private static MethodHandle create;
    private static MethodHandle post;
    private static MethodHandle release;
    private static boolean loaded;

    private MacScroll() {}

    /** Whether point scrolling can be posted here. */
    static synchronized boolean available() {
        if (!loaded) {
            loaded = true;
            if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return false;
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup cg = SymbolLookup.libraryLookup(
                        "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global());
                SymbolLookup cf = SymbolLookup.libraryLookup(
                        "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());
                create = linker.downcallHandle(cg.find("CGEventCreateScrollWheelEvent2").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                post = linker.downcallHandle(cg.find("CGEventPost").orElseThrow(),
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                release = linker.downcallHandle(cf.find("CFRelease").orElseThrow(),
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            } catch (Throwable ex) {
                create = null;
            }
        }
        return create != null;
    }

    /** Scrolls by {@code points}; positive moves the content up, as rolling the wheel down does. */
    static void post(int points) {
        if (points == 0 || !available()) return;
        try {
            MemorySegment event = (MemorySegment) create.invokeExact(MemorySegment.NULL, UNIT_PIXEL, 1, -points, 0, 0);
            if (event.address() == 0) return;
            try {
                post.invokeExact(HID_TAP, event);
            } finally {
                release.invokeExact(event);
            }
        } catch (Throwable ignored) {
            // Nothing posted; the step simply does not roll.
        }
    }

    /** Splits {@code total} points into even slices of at most {@code slice} points, keeping the exact sum. */
    static int[] slices(int total, int slice) {
        int count = Math.max(1, (Math.abs(total) + slice - 1) / slice);
        int[] parts = new int[count];
        int sent = 0;
        for (int i = 0; i < count; i++) {
            int target = (int) Math.round((double) total * (i + 1) / count);
            parts[i] = target - sent;
            sent = target;
        }
        return parts;
    }
}
