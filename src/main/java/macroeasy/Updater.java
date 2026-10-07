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
    private static final AtomicBoolean installing = new AtomicBoolean();
    private static final Preferences prefs = Preferences.userNodeForPackage(MacroEasy.class);
    private static final String REQUIRED = "requiredVersion";
    private static final String CHECKED_VERSION = "checkedVersion";
    private static final String CHECKED_AT = "checkedAt";
    private static final long GRACE_MS = 5L * 60 * 1000;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Updater() {}

    static void start(MacroEasy app, Runnable ready) {
        app.setEnabled(false);
        Thread worker = new Thread(() -> look(app, ready), "macroeasy-update");
        worker.setDaemon(true);
        worker.start();
        Timer later = new Timer(30 * 60 * 1000, e -> {
            Thread again = new Thread(() -> look(app, ready), "macroeasy-update");
            again.setDaemon(true);
            again.start();
        });
        later.setRepeats(true);
        later.start();
    }

    private static void look(MacroEasy app, Runnable ready) {
        if (app.busy() || !checking.compareAndSet(false, true)) return;
        try {
            Release release = latest();
            if (release == null || !newer(release.version, About.VERSION)) {
                rememberCurrent();
                clearFailure();
                SwingUtilities.invokeLater(ready);
                return;
            }
            prefs.put(REQUIRED, strip(release.version));
            boolean automatic = autoInstall(readFailure(), release.version, About.VERSION);
            SwingUtilities.invokeLater(() -> open(app, release, automatic, ready));
        } catch (Exception ignored) {
            if (offlineOk()) SwingUtilities.invokeLater(ready);
            else SwingUtilities.invokeLater(() -> open(app, null, false, ready));
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

    /** A failed install of this same release must wait for the retry button. */
    static boolean autoInstall(String failedMarker, String latest, String current) {
        if (!newer(latest, current)) return false;
        if (failedMarker == null || failedMarker.isBlank()) return true;
        return !strip(failedMarker).equals(strip(latest));
    }

    static Path failureMarker() {
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "MacroEasy", "update-failed");
    }

    private static String readFailure() {
        try {
            Path marker = failureMarker();
            if (!Files.isRegularFile(marker)) return "";
            return Files.readString(marker).trim();
        } catch (IOException ex) {
            return "";
        }
    }

    private static void clearFailure() {
        try {
            Files.deleteIfExists(failureMarker());
        } catch (IOException ignored) {
            // The next launch still refuses to repeat a failed install while the file remains.
        }
    }

    /** Offline use only after this exact version was confirmed, and only for five minutes. */
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

    private static void open(MacroEasy app, Release release, boolean automatic, Runnable ready) {
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
        JLabel status = new JLabel(release == null ? "Aguardando a rede"
                : automatic ? "Baixando" : "A instalação anterior não substituiu o app.");
        JButton retry = new JButton("Tentar de novo");
        retry.setVisible(release == null || !automatic);
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
        dialog.toFront();

        long[] started = {System.currentTimeMillis()};
        boolean[] got = {false};
        boolean[] failed = {false};
        long[] bytes = {0};
        long[] total = {-1};
        Path[] payload = {null};
        boolean[] closing = {false};
        boolean[] attempt = {automatic && release != null};

        retry.addActionListener(e -> {
            if (release == null) {
                showing.set(false);
                dialog.dispose();
                Thread again = new Thread(() -> look(app, ready), "macroeasy-update");
                again.setDaemon(true);
                again.start();
                return;
            }
            clearFailure();
            failed[0] = false;
            got[0] = false;
            bytes[0] = 0;
            total[0] = -1;
            started[0] = System.currentTimeMillis();
            retry.setVisible(false);
            status.setText("Baixando");
            attempt[0] = true;
            Thread again = new Thread(() -> fetch(release, bytes, total, payload, got, failed), "macroeasy-download");
            again.setDaemon(true);
            again.start();
        });

        Thread download = new Thread(() -> {
            if (attempt[0]) fetch(release, bytes, total, payload, got, failed);
        }, "macroeasy-download");
        download.setDaemon(true);
        download.start();

        Timer[] clock = new Timer[1];
        clock[0] = new Timer(40, e -> {
            long elapsed = System.currentTimeMillis() - started[0];
            double ratio = total[0] > 0 ? bytes[0] / (double) total[0] : 0;
            if (!attempt[0]) bar.setValue(0);
            else if (!failed[0]) bar.setValue(percent(elapsed, got[0], ratio));
            if (failed[0]) {
                status.setText("Não foi possível atualizar. Esta versão continua bloqueada.");
                retry.setVisible(true);
            } else if (!attempt[0]) status.setText(release == null
                    ? "Aguardando a rede"
                    : "A instalação anterior não substituiu o app.");
            else if (!got[0]) status.setText("Baixando");
            else status.setText("Preparando");
            if (finished(elapsed, got[0]) && !closing[0]) {
                closing[0] = true;
                clock[0].stop();
                bar.setValue(100);
                status.setText("Instalando");
                Thread install = new Thread(() -> install(payload[0], release.version, () -> {
                    failed[0] = true;
                    got[0] = false;
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

    private static void install(Path zip, String version, Runnable failed) {
        if (!installing.compareAndSet(false, true)) return;
        try {
            Path destination = installedApp();
            Files.createDirectories(destination.getParent());
            Path script = Files.createTempFile("macroeasy-update", ".sh");
            String body = installScript(ProcessHandle.current().pid(), destination, zip, script,
                    failureMarker(), strip(version));
            Files.writeString(script, body);
            Files.setPosixFilePermissions(script, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            new ProcessBuilder("/bin/sh", script.toString()).start();
            System.exit(0);
        } catch (IOException ex) {
            installing.set(false);
            SwingUtilities.invokeLater(failed);
        }
    }

    /** Replaces the bundle only after this process is gone, and records a failure instead of looping. */
    static String installScript(long pid, Path destination, Path zip, Path script, Path marker, String version) {
        Path lock = marker.resolveSibling("update.lock");
        Path log = Path.of(System.getProperty("user.home"), "Library", "Logs", "MacroEasy-update.log");
        return """
                #!/bin/sh
                log=%s
                mkdir -p "$(dirname "$log")"
                exec >> "$log" 2>&1
                echo "----- $(date) pid=%d"
                old=%s
                archive=%s
                marker=%s
                lock=%s
                version=%s
                if [ -d "$lock" ]; then
                  stale=$(/usr/bin/find "$lock" -mmin +2 -print -quit)
                  if [ -z "$stale" ]; then
                    echo "instalação já em andamento"
                    exit 0
                  fi
                  /bin/rm -rf "$lock"
                fi
                mkdir -p "$(dirname "$lock")"
                mkdir "$lock" || exit 0
                i=0
                while /bin/kill -0 %d 2>/dev/null; do
                  i=$((i+1))
                  if [ "$i" -eq 24 ]; then
                    /bin/kill -TERM %d 2>/dev/null || true
                  fi
                  if [ "$i" -gt 40 ]; then
                    break
                  fi
                  /bin/sleep 0.25
                done
                /bin/sleep 0.4
                work=$(mktemp -d)
                stage=$(mktemp -d)
                staging="$stage/MacroEasy.app"
                backup="$old.previous"
                fail() {
                  echo "falhou: $1"
                  mkdir -p "$(dirname "$marker")"
                  printf '%%s\\n' "$version" > "$marker"
                  rm -rf "$work" "$stage" "$lock"
                  if [ -d "$backup" ]; then
                    rm -rf "$old"
                    mv "$backup" "$old"
                  fi
                  /usr/bin/open "$old"
                  exit 1
                }
                /usr/bin/ditto -x -k "$archive" "$work" || fail "extrair"
                app="$work/MacroEasy.app"
                [ -d "$app" ] || app=$(/usr/bin/find "$work" -type d -name 'MacroEasy.app' -print -quit)
                launcher="$app/Contents/MacOS/MacroEasy"
                [ -f "$launcher" ] || fail "sem lançador"
                /bin/chmod +x "$launcher"
                links=$(/usr/bin/find "$app" -type l | /usr/bin/wc -l | /usr/bin/tr -d ' ')
                [ "$links" -ge 1 ] || fail "sem atalhos"
                /usr/bin/ditto "$app" "$staging" || fail "copiar"
                /bin/chmod +x "$staging/Contents/MacOS/MacroEasy"
                /usr/bin/codesign --force --deep --sign - "$staging" || fail "assinar"
                /usr/bin/codesign --verify --deep --strict "$staging" || fail "verificar"
                /bin/rm -rf "$backup"
                moved=0
                i=0
                while [ "$i" -lt 20 ]; do
                  if /bin/mv "$old" "$backup"; then
                    moved=1
                    break
                  fi
                  i=$((i+1))
                  /bin/sleep 0.25
                done
                [ "$moved" -eq 1 ] || fail "mover o app antigo"
                /bin/mv "$staging" "$old" || fail "colocar o app novo"
                /usr/bin/xattr -dr com.apple.quarantine "$old" 2>/dev/null
                /bin/chmod +x "$old/Contents/MacOS/MacroEasy"
                /usr/bin/codesign --verify --deep --strict "$old" || /usr/bin/codesign --force --deep --sign - "$old" || fail "assinar instalado"
                /usr/bin/codesign --verify --deep --strict "$old" || fail "verificar instalado"
                /bin/rm -rf "$backup" "$work" "$stage" "$archive" "$lock"
                /bin/rm -f "$marker" %s
                echo "instalado"
                /usr/bin/open "$old"
                """.formatted(q(log), pid, q(destination), q(zip), q(marker), q(lock),
                q(version), pid, pid, q(script));
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
        return q(path.toAbsolutePath().toString());
    }

    private static String q(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
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
