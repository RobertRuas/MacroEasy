package macroeasy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** The folder where macros are kept, and the file names inside it. */
final class Library {
    private Library() {}

    static Path root() {
        return Path.of(System.getProperty("user.home"), "Documents", "MacroEasy");
    }

    static Path sessionFile() {
        return Path.of(System.getProperty("user.home"), ".macroeasy", "session.macro");
    }

    /** Every .macro file in the folder, sorted by name. */
    static List<Path> list(Path root) throws IOException {
        Files.createDirectories(root);
        try (var files = Files.list(root)) {
            return files.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".macro"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    /** The first free "Macro N" name. */
    static String nextName(Path root) {
        int number = 1;
        while (Files.exists(root.resolve("Macro " + number + ".macro"))) number++;
        return "Macro " + number;
    }

    /** Copies a macro from elsewhere into the folder, adding " 2", " 3"… when the name is taken. */
    static Path importInto(Path root, Path path) throws IOException {
        Files.createDirectories(root);
        if (path.startsWith(root)) return path;
        String name = path.getFileName().toString();
        if (!name.toLowerCase().endsWith(".macro")) name = name + ".macro";
        String base = name.substring(0, name.length() - 6);
        Path dest = root.resolve(name);
        int copy = 2;
        while (Files.exists(dest)) {
            dest = root.resolve(base + " " + copy + ".macro");
            copy++;
        }
        Files.copy(path, dest);
        return dest;
    }
}
