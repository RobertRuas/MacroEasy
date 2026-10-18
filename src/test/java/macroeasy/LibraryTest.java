package macroeasy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryTest {
    @TempDir
    Path dir;

    @Test
    void importsWithoutOverwriting() throws Exception {
        Path root = dir.resolve("lib");
        Path outside = dir.resolve("Login.macro");
        MacroFile.save(outside, List.of(Step.wait(5)));
        assertEquals(root.resolve("Login.macro"), Library.importInto(root, outside));
        assertEquals(root.resolve("Login 2.macro"), Library.importInto(root, outside));
        assertEquals(root.resolve("Login 3.macro"), Library.importInto(root, outside));
        assertEquals(1, MacroFile.load(root.resolve("Login 3.macro")).size());
    }

    @Test
    void listsMacrosByNameAndSkipsOtherFiles() throws Exception {
        Files.writeString(dir.resolve("b.macro"), "");
        Files.writeString(dir.resolve("A.macro"), "");
        Files.writeString(dir.resolve("notes.txt"), "");
        assertEquals(List.of(dir.resolve("A.macro"), dir.resolve("b.macro")), Library.list(dir));
    }

    @Test
    void picksTheNextFreeName() throws Exception {
        assertEquals("Macro 1", Library.nextName(dir));
        Files.writeString(dir.resolve("Macro 1.macro"), "");
        assertEquals("Macro 2", Library.nextName(dir));
    }

    @Test
    void savingLeavesNoTemporaryFile() throws Exception {
        Path file = dir.resolve("x.macro");
        MacroFile.save(file, List.of(Step.wait(1)));
        MacroFile.save(file, List.of(Step.wait(2)));
        assertEquals(List.of(file), Library.list(dir));
        assertEquals(2, MacroFile.load(file).get(0).waitMs);
    }
}
