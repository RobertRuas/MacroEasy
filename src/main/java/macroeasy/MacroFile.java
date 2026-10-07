package macroeasy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MacroFile {
    private MacroFile() {}

    public static String write(List<Step> steps) {
        StringBuilder sb = new StringBuilder("MACROEASY 1\n");
        for (Step step : steps) sb.append(step.encode()).append('\n');
        return sb.toString();
    }

    public static List<Step> read(String data) {
        List<Step> steps = new ArrayList<>();
        for (String line : data.split("\n", -1)) {
            Step step = Step.parse(line.trim());
            if (step != null) steps.add(step);
        }
        return steps;
    }

    public static void save(Path path, List<Step> steps) throws IOException {
        Files.writeString(path, write(steps), StandardCharsets.UTF_8);
    }

    public static List<Step> load(Path path) throws IOException {
        return read(Files.readString(path, StandardCharsets.UTF_8));
    }
}
