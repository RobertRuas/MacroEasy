package macroeasy;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Open application windows, and bringing one of them to the front. */
final class Windows {
    private Windows() {}

    static final class Item {
        final String app;
        final String title;

        Item(String app, String title) {
            this.app = app == null ? "" : app;
            this.title = title == null ? "" : title;
        }

        String label() {
            return title.isBlank() ? app : app + "  —  " + title;
        }

        @Override
        public String toString() {
            return label();
        }
    }

    static List<Item> list() {
        Native nativeApi = Native.load();
        if (nativeApi == null) return List.of();
        return nativeApi.list();
    }

    static void raise(String app, String title) {
        Native nativeApi = Native.load();
        if (nativeApi == null || app == null || app.isBlank()) return;
        nativeApi.raise(app, title == null ? "" : title);
    }

    private static final class Native {
        private static Native loaded;
        private static boolean failed;
        private final Arena arena = Arena.global();
        private final MethodHandle windowList;
        private final MethodHandle arrayCount;
        private final MethodHandle arrayAt;
        private final MethodHandle dictGet;
        private final MethodHandle cfString;
        private final MethodHandle cString;
        private final MethodHandle numberValue;
        private final MethodHandle release;
        private final MethodHandle createApp;
        private final MethodHandle copyAttr;
        private final MethodHandle setAttr;
        private final MethodHandle perform;
        private final MemorySegment ownerKey;
        private final MemorySegment nameKey;
        private final MemorySegment pidKey;
        private final MemorySegment layerKey;
        private final MemorySegment yes;
        private final MemorySegment no;
        private final MemorySegment axWindows;
        private final MemorySegment axTitle;
        private final MemorySegment axFrontmost;
        private final MemorySegment axMinimized;
        private final MemorySegment axMain;
        private final MemorySegment axRaise;

        private Native() throws Throwable {
            Linker linker = Linker.nativeLinker();
            SymbolLookup cg = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", arena);
            SymbolLookup cf = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", arena);
            SymbolLookup ax = SymbolLookup.libraryLookup(
                    "/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices", arena);
            windowList = down(linker, cg, "CGWindowListCopyWindowInfo",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            arrayCount = down(linker, cf, "CFArrayGetCount",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            arrayAt = down(linker, cf, "CFArrayGetValueAtIndex",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            dictGet = down(linker, cf, "CFDictionaryGetValue",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            cfString = down(linker, cf, "CFStringCreateWithCString",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            cString = down(linker, cf, "CFStringGetCString",
                    FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
            numberValue = down(linker, cf, "CFNumberGetValue",
                    FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            release = down(linker, cf, "CFRelease", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            createApp = down(linker, ax, "AXUIElementCreateApplication",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            copyAttr = down(linker, ax, "AXUIElementCopyAttributeValue",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            setAttr = down(linker, ax, "AXUIElementSetAttributeValue",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            perform = down(linker, ax, "AXUIElementPerformAction",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            ownerKey = pointer(cg, "kCGWindowOwnerName");
            nameKey = pointer(cg, "kCGWindowName");
            pidKey = pointer(cg, "kCGWindowOwnerPID");
            layerKey = pointer(cg, "kCGWindowLayer");
            yes = pointer(cf, "kCFBooleanTrue");
            no = pointer(cf, "kCFBooleanFalse");
            axWindows = constant("AXWindows");
            axTitle = constant("AXTitle");
            axFrontmost = constant("AXFrontmost");
            axMinimized = constant("AXMinimized");
            axMain = constant("AXMain");
            axRaise = constant("AXRaise");
        }

        static Native load() {
            if (loaded != null || failed) return loaded;
            try {
                loaded = new Native();
            } catch (Throwable ex) {
                failed = true;
            }
            return loaded;
        }

        List<Item> list() {
            synchronized (this) {
                List<int[]> apps = new ArrayList<>();
                List<String> names = new ArrayList<>();
                MemorySegment array = MemorySegment.NULL;
                try {
                    array = (MemorySegment) windowList.invokeExact(1 | (1 << 4), 0);
                    if (array.address() == 0) return List.of();
                    long count = (long) arrayCount.invokeExact(array);
                    long self = ProcessHandle.current().pid();
                    for (long i = 0; i < count; i++) {
                        MemorySegment dict = (MemorySegment) arrayAt.invokeExact(array, i);
                        if (number(dict, layerKey) != 0) continue;
                        String owner = text((MemorySegment) dictGet.invokeExact(dict, ownerKey));
                        if (owner.isBlank() || "MacroEasy".equals(owner) || "Window Server".equals(owner)) continue;
                        int pid = number(dict, pidKey);
                        if (pid <= 0 || pid == self || containsPid(apps, pid)) continue;
                        apps.add(new int[] {pid});
                        names.add(owner);
                    }
                } catch (Throwable ex) {
                    return List.of();
                } finally {
                    release(array);
                }
                List<Item> items = new ArrayList<>();
                for (int i = 0; i < apps.size(); i++) {
                    String app = names.get(i);
                    List<String> titles = titles(apps.get(i)[0]);
                    if (titles.isEmpty()) items.add(new Item(app, ""));
                    else for (String title : titles) items.add(new Item(app, title));
                }
                items.sort(Comparator.comparing(Item::label, String.CASE_INSENSITIVE_ORDER));
                return items;
            }
        }

        void raise(String app, String title) {
            synchronized (this) {
                int pid = pidOf(app, title);
                if (pid <= 0) return;
                MemorySegment application = MemorySegment.NULL;
                MemorySegment windows = MemorySegment.NULL;
                try {
                    application = (MemorySegment) createApp.invokeExact(pid);
                    if (application.address() == 0) return;
                    set(application, axFrontmost, yes);
                    if (title.isBlank()) return;
                    windows = copy(application, axWindows);
                    if (windows.address() == 0) return;
                    long count = (long) arrayCount.invokeExact(windows);
                    for (long i = 0; i < count; i++) {
                        MemorySegment window = (MemorySegment) arrayAt.invokeExact(windows, i);
                        if (window.address() == 0 || !title.equals(titleOf(window))) continue;
                        set(window, axMinimized, no);
                        int raised = (int) perform.invokeExact(window, axRaise);
                        set(window, axMain, yes);
                        if (raised < -1) return;
                        return;
                    }
                } catch (Throwable ignored) {
                    // The rest of the macro still runs if this window is gone.
                } finally {
                    release(windows);
                    release(application);
                }
            }
        }

        private int pidOf(String app, String title) {
            MemorySegment array = MemorySegment.NULL;
            try {
                array = (MemorySegment) windowList.invokeExact(0, 0);
                if (array.address() == 0) return -1;
                long count = (long) arrayCount.invokeExact(array);
                int fallback = -1;
                for (long i = 0; i < count; i++) {
                    MemorySegment dict = (MemorySegment) arrayAt.invokeExact(array, i);
                    if (number(dict, layerKey) != 0) continue;
                    String owner = text((MemorySegment) dictGet.invokeExact(dict, ownerKey));
                    if (!owner.equalsIgnoreCase(app)) continue;
                    int pid = number(dict, pidKey);
                    if (fallback < 0) fallback = pid;
                    String name = text((MemorySegment) dictGet.invokeExact(dict, nameKey));
                    if (!title.isBlank() && title.equals(name)) return pid;
                }
                return fallback;
            } catch (Throwable ex) {
                return -1;
            } finally {
                release(array);
            }
        }

        private List<String> titles(int pid) {
            List<String> titles = new ArrayList<>();
            MemorySegment application = MemorySegment.NULL;
            MemorySegment windows = MemorySegment.NULL;
            try {
                application = (MemorySegment) createApp.invokeExact(pid);
                windows = copy(application, axWindows);
                if (windows.address() == 0) return titles;
                long count = (long) arrayCount.invokeExact(windows);
                for (long i = 0; i < count; i++) {
                    MemorySegment window = (MemorySegment) arrayAt.invokeExact(windows, i);
                    String title = titleOf(window);
                    if (!title.isBlank() && !titles.contains(title)) titles.add(title);
                }
            } catch (Throwable ignored) {
                return titles;
            } finally {
                release(windows);
                release(application);
            }
            return titles;
        }

        private String titleOf(MemorySegment window) throws Throwable {
            if (window.address() == 0) return "";
            MemorySegment value = copy(window, axTitle);
            try {
                return text(value);
            } finally {
                release(value);
            }
        }

        private MemorySegment copy(MemorySegment element, MemorySegment attribute) throws Throwable {
            if (element.address() == 0) return MemorySegment.NULL;
            MemorySegment slot = arena.allocate(ValueLayout.ADDRESS);
            int error = (int) copyAttr.invokeExact(element, attribute, slot);
            if (error != 0) return MemorySegment.NULL;
            return slot.get(ValueLayout.ADDRESS, 0);
        }

        private void set(MemorySegment element, MemorySegment attribute, MemorySegment value) throws Throwable {
            int ignored = (int) setAttr.invokeExact(element, attribute, value);
            if (ignored == 0) return;
        }

        private int number(MemorySegment dict, MemorySegment key) throws Throwable {
            MemorySegment value = (MemorySegment) dictGet.invokeExact(dict, key);
            if (value.address() == 0) return -1;
            MemorySegment out = arena.allocate(ValueLayout.JAVA_INT);
            boolean ok = (boolean) numberValue.invokeExact(value, 9, out);
            return ok ? out.get(ValueLayout.JAVA_INT, 0) : -1;
        }

        private String text(MemorySegment segment) throws Throwable {
            if (segment == null || segment.address() == 0) return "";
            MemorySegment buffer = arena.allocate(1024);
            boolean ok = (boolean) cString.invokeExact(segment, buffer, 1024L, 0x08000100);
            return ok ? buffer.getString(0) : "";
        }

        private MemorySegment constant(String value) throws Throwable {
            return (MemorySegment) cfString.invokeExact(MemorySegment.NULL, arena.allocateFrom(value), 0x08000100);
        }

        private void release(MemorySegment segment) {
            if (segment == null || segment.address() == 0) return;
            try {
                release.invokeExact(segment);
            } catch (Throwable ignored) {
                // A failed release must not hide the window list.
            }
        }

        private static boolean containsPid(List<int[]> apps, int pid) {
            for (int[] app : apps) if (app[0] == pid) return true;
            return false;
        }

        private static MethodHandle down(Linker linker, SymbolLookup library, String name, FunctionDescriptor type)
                throws Throwable {
            return linker.downcallHandle(library.find(name).orElseThrow(), type);
        }

        private static MemorySegment pointer(SymbolLookup library, String name) throws Throwable {
            return library.find(name).orElseThrow().reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
        }
    }
}
