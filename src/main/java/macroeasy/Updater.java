package macroeasy;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Installs a newer GitHub release. The progress modal stays up for at least ten seconds. */
final class Updater {
    static final String REPO = "RobertRuas/MacroEasy";
    private static final long MINIMUM_MS = 10_000;
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DOWNLOAD = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final AtomicBoolean checking = new AtomicBoolean();
    private static final AtomicBoolean showing = new AtomicBoolean();
    private static final Preferences prefs = Preferences.userNodeForPackage(MacroEasy.class);
    private static final String REQUIRED = "requiredVersion";
    private static final String CHECKED_VERSION = "checkedVersion";
    private static final String CHECKED_AT = "checkedAt";
    private static final long GRACE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Updater() {}

    static void start(MacroEasy app) {
        app.setEnabled(false);
        Thread worker = new Thread(() -> look(app), "macroeasy-update");
        worker.setDaemon(true);
        worker.start();
        Timer later = new Timer(30 * 60 * 1000, e -> {
            Thread again = new Thread(() -> look(app), "macroeasy-update");
            again.setDaemon(true);
            again.start();
        });
        later.setRepeats(true);
        later.start();
    }

    private static void look(MacroEasy app) {
        if (app.busy() || !checking.compareAndSet(false, true)) return;
        try {
            Release release = latest();
            if (release == null || !newer(release.version, About.VERSION)) {
                rememberCurrent();
                SwingUtilities.invokeLater(() -> app.setEnabled(true));
                return;
            }
            prefs.put(REQUIRED, strip(release.version));
            SwingUtilities.invokeLater(() -> open(app, release));
        } catch (Exception ignored) {
            if (offlineOk()) SwingUtilities.invokeLater(() -> app.setEnabled(true));
            else SwingUtilities.invokeLater(() -> open(app, null));
        } finally {
            checking.set(false);
        }
    }

    static Release latest() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "MacroEasy")
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) throw new IOException("release " + response.statusCode());
        return parse(response.body());
    }

    static Release parse(String json) {
        if (json == null || json.isBlank()) return null;
        Matcher tag = TAG.matcher(json);
        if (!tag.find()) return null;
        int name = json.indexOf("\"name\": \"MacroEasy.zip\"");
        if (name < 0) name = json.indexOf("\"name\":\"MacroEasy.zip\"");
        if (name < 0) return null;
        Matcher download = DOWNLOAD.matcher(json);
        if (!download.find(name)) return null;
        String url = download.group(1).replace("\\u0026", "&");
        if (!trusted(url)) return null;
        return new Release(tag.group(1), url);
    }

    /** Offline use only after this exact version was confirmed, and only for seven days. */
    static boolean allowedOffline(String current, String checkedVersion, long checkedAt, long now, String required) {
        if (newer(required, current)) return false;
        if (current == null || current.isBlank() || !current.equals(strip(checkedVersion))) return false;
        if (checkedAt <= 0 || now < checkedAt) return false;
        return now - checkedAt <= GRACE_MS;
    }

    private static void rememberCurrent() {
        prefs.remove(REQUIRED);
        prefs.put(CHECKED_VERSION, About.VERSION);
        prefs.putLong(CHECKED_AT, System.currentTimeMillis());
    }

    private static boolean offlineOk() {
        return allowedOffline(About.VERSION, prefs.get(CHECKED_VERSION, ""),
                prefs.getLong(CHECKED_AT, 0), System.currentTimeMillis(), prefs.get(REQUIRED, ""));
    }

    static boolean newer(String latest, String current) {
        int[] next = parts(latest);
        int[] now = parts(current);
        if (next == null || now == null) return false;
        for (int i = 0; i < 3; i++) {
            if (next[i] != now[i]) return next[i] > now[i];
        }
        return false;
    }

    static int percent(long elapsedMs, boolean ready, double downloadRatio) {
        double time = Math.min(1, elapsedMs / (double) MINIMUM_MS);
        if (ready) return elapsedMs < MINIMUM_MS ? (int) Math.round(time * 100) : 100;
        if (elapsedMs < MINIMUM_MS) return (int) Math.round(time * 92);
        double downloaded = Math.max(0, Math.min(1, downloadRatio));
        return 92 + (int) Math.round(downloaded * 6);
    }

    static boolean finished(long elapsedMs, boolean ready) {
        return ready && elapsedMs >= MINIMUM_MS;
    }

    private static int[] parts(String version) {
        if (version == null) return null;
        String value = version.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        String[] raw = value.split("\\.");
        if (raw.length == 0 || raw[0].isBlank()) return null;
        int[] numbers = new int[3];
        for (int i = 0; i < numbers.length; i++) {
            if (i >= raw.length) continue;
            String digits = raw[i].replaceAll("^(\\d+).*$", "$1");
            if (!digits.chars().allMatch(Character::isDigit) || digits.isEmpty()) return null;
            numbers[i] = Integer.parseInt(digits);
        }
        return numbers;
    }

    private static boolean trusted(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && ("github.com".equalsIgnoreCase(host)
                    || host.toLowerCase().endsWith(".githubusercontent.com"));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String strip(String version) {
        if (version == null) return "";
        String value = version.trim();
        return value.startsWith("v") || value.startsWith("V") ? value.substring(1) : value;
    }

    private static void open(MacroEasy app, Release release) {
        if (!app.isDisplayable() || app.busy() || !showing.compareAndSet(false, true)) return;
        app.setEnabled(false);
        JDialog dialog = new JDialog(app, "MacroEasy", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setIconImage(MacroEasy.icon());
        JLabel title = new JLabel("Atualizando");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JLabel version = new JLabel(release == null ? "Sem conexão" : "Versão " + strip(release.version));
        version.setFont(version.getFont().deriveFont(12f));
        JLabel detail = new JLabel(release == null
                ? "Esta versão não abre sem confirmar a release mais recente."
                : "Esta versão não pode ser usada. O app abre de novo sozinho.");
        detail.setFont(detail.getFont().deriveFont(12f));
        Color muted = UIColor();
        version.setForeground(muted);
        detail.setForeground(muted);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(0);
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(360, 18));
        bar.setFont(bar.getFont().deriveFont(11f));
        JLabel status = new JLabel(release == null ? "Aguardando a rede" : "Baixando");
        JButton retry = new JButton("Tentar de novo");
        retry.setVisible(release == null);
        retry.setFont(retry.getFont().deriveFont(12f));
        status.setFont(status.getFont().deriveFont(11f));
        status.setForeground(muted);
        JPanel text = new JPanel(new BorderLayout(0, 4));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(version, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(status, BorderLayout.CENTER);
        actions.add(retry, BorderLayout.EAST);
        south.add(bar, BorderLayout.NORTH);
        south.add(actions, BorderLayout.SOUTH);
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(214, 214, 214)),
                BorderFactory.createEmptyBorder(22, 24, 20, 24)));
        root.add(text, BorderLayout.NORTH);
        root.add(detail, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(app);

        long[] started = {System.currentTimeMillis()};
        boolean[] ready = {false};
        boolean[] failed = {false};
        long[] bytes = {0};
        long[] total = {-1};
        Path[] payload = {null};
        boolean[] closing = {false};
        boolean[] attempt = {release != null};

        retry.addActionListener(e -> {
            if (release == null) {
                showing.set(false);
                dialog.dispose();
                Thread again = new Thread(() -> look(app), "macroeasy-update");
                again.setDaemon(true);
                again.start();
                return;
            }
            failed[0] = false;
            ready[0] = false;
            bytes[0] = 0;
            total[0] = -1;
            started[0] = System.currentTimeMillis();
            retry.setVisible(false);
            status.setText("Baixando");
            attempt[0] = true;
            Thread again = new Thread(() -> fetch(release, bytes, total, payload, ready, failed), "macroeasy-download");
            again.setDaemon(true);
            again.start();
        });

        Thread download = new Thread(() -> {
            if (attempt[0]) fetch(release, bytes, total, payload, ready, failed);
        }, "macroeasy-download");
        download.setDaemon(true);
        download.start();

        Timer[] clock = new Timer[1];
        clock[0] = new Timer(40, e -> {
            long elapsed = System.currentTimeMillis() - started[0];
            double ratio = total[0] > 0 ? bytes[0] / (double) total[0] : 0;
            if (!failed[0]) bar.setValue(percent(elapsed, ready[0], ratio));
            if (failed[0]) {
                status.setText("Não foi possível atualizar. Esta versão continua bloqueada.");
                retry.setVisible(true);
            } else if (!attempt[0]) status.setText("Aguardando a rede");
            else if (!ready[0]) status.setText("Baixando");
            else status.setText("Preparando");
            if (finished(elapsed, ready[0]) && !closing[0]) {
                closing[0] = true;
                clock[0].stop();
                bar.setValue(100);
                status.setText("Instalando");
                Thread install = new Thread(() -> install(payload[0], () -> {
                    failed[0] = true;
                    ready[0] = false;
                    closing[0] = false;
                    status.setText("Não foi possível instalar. Esta versão continua bloqueada.");
                    retry.setVisible(true);
                    clock[0].start();
                }), "macroeasy-install");
                install.setDaemon(true);
                install.start();
            }
        });
        clock[0].start();
        dialog.setVisible(true);
        clock[0].stop();
        showing.set(false);
    }

    private static void fetch(Release release, long[] bytes, long[] total, Path[] payload,
                              boolean[] ready, boolean[] failed) {
        try {
            Path zip = Files.createTempFile("macroeasy-", ".zip");
            download(release.url, zip, bytes, total);
            if (!zipListsApp(zip)) throw new IOException("zip sem MacroEasy.app");
            payload[0] = zip;
            ready[0] = true;
        } catch (Exception ex) {
            failed[0] = true;
        }
    }

    private static Color UIColor() {
        Color color = javax.swing.UIManager.getColor("Label.disabledForeground");
        return color == null ? new Color(110, 110, 110) : color;
    }

    private static void download(String url, Path zip, long[] bytes, long[] total) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "MacroEasy")
                .header("Accept", "application/octet-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException("download " + response.statusCode());
        response.headers().firstValueAsLong("Content-Length").ifPresent(length -> total[0] = length);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(zip)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                bytes[0] += read;
            }
        }
    }

    private static boolean zipListsApp(Path zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("MacroEasy.app/Contents/Info.plist")
                        || name.endsWith("/MacroEasy.app/Contents/Info.plist")) return true;
            }
        }
        return false;
    }

    static Path unzip(Path zip, Path work) throws IOException {
        long written = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path target = work.resolve(entry.getName()).normalize();
                if (!target.startsWith(work)) throw new IOException("caminho inválido");
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        written += read;
                        if (written > 800L * 1024 * 1024) throw new IOException("arquivo grande demais");
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
        try (var files = Files.walk(work)) {
            return files.filter(path -> path.getFileName() != null
                            && "MacroEasy.app".equals(path.getFileName().toString())
                            && Files.isDirectory(path)
                            && Files.isRegularFile(path.resolve("Contents/Info.plist")))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static void install(Path zip, Runnable failed) {
        try {
            Path destination = installedApp();
            Files.createDirectories(destination.getParent());
            Path staging = destination.resolveSibling(destination.getFileName().toString() + ".next");
            Path script = Files.createTempFile("macroeasy-update", ".sh");
            String body = """
                    #!/bin/sh
                    sleep 1.2
                    old=%s
                    archive=%s
                    staging=%s
                    work=$(mktemp -d)
                    fail() {
                      rm -rf "$work" "$staging"
                      open "$old"
                      exit 1
                    }
                    ditto -x -k "$archive" "$work" || fail
                    app=$(find "$work" -type d -name 'MacroEasy.app' -print -quit)
                    launcher="$app/Contents/MacOS/MacroEasy"
                    [ -f "$launcher" ] || fail
                    chmod +x "$launcher"
                    links=$(find "$app" -type l | wc -l | tr -d ' ')
                    [ "$links" -ge 1 ] || fail
                    rm -rf "$staging"
                    ditto "$app" "$staging" || fail
                    chmod +x "$staging/Contents/MacOS/MacroEasy"
                    codesign --force --deep --sign - "$staging" || fail
                    codesign --verify --deep --strict "$staging" || fail
                    rm -rf "$old" || fail
                    mv "$staging" "$old" || fail
                    xattr -dr com.apple.quarantine "$old" 2>/dev/null
                    codesign --force --deep --sign - "$old" || fail
                    codesign --verify --deep --strict "$old" || fail
                    open "$old"
                    rm -rf "$work" "$archive"
                    rm -f %s
                    """.formatted(q(destination), q(zip), q(staging), q(script));
            Files.writeString(script, body);
            Files.setPosixFilePermissions(script, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            new ProcessBuilder("sh", script.toString()).start();
            System.exit(0);
        } catch (IOException ex) {
            SwingUtilities.invokeLater(failed);
        }
    }

    private static Path installedApp() {
        String command = ProcessHandle.current().info().command().orElse("");
        Path macOs = command.isBlank() ? null : Path.of(command).getParent();
        if (macOs != null && "MacOS".equals(String.valueOf(macOs.getFileName()))) {
            Path contents = macOs.getParent();
            Path app = contents == null ? null : contents.getParent();
            if (app != null && String.valueOf(app.getFileName()).endsWith(".app")) return app;
        }
        return Path.of(System.getProperty("user.home"), "Applications", "MacroEasy.app");
    }

    private static String q(Path path) {
        return "'" + path.toAbsolutePath().toString().replace("'", "'\\''") + "'";
    }

    static final class Release {
        final String version;
        final String url;

        Release(String version, String url) {
            this.version = version;
            this.url = url;
        }
    }
}
