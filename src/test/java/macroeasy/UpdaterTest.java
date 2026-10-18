package macroeasy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UpdaterTest {
    @Test
    void comparesVersions() {
        assertTrue(Updater.newer("v1.2", "1.1"));
        assertTrue(Updater.newer("1.1.1", "1.1"));
        assertFalse(Updater.newer("1.1", "1.1"));
        assertFalse(Updater.newer("v1.0", "1.1"));
        assertFalse(Updater.newer("", "1.1"));
        assertFalse(Updater.newer("abc", "1.1"));
    }

    @Test
    void opensOfflineUnlessANewerReleaseWasSeen() {
        assertTrue(Updater.allowedOffline("1.1", ""));
        assertTrue(Updater.allowedOffline("1.1", "1.1"));
        assertFalse(Updater.allowedOffline("1.1", "1.2"));
    }

    @Test
    void retriesAFailedInstallOnlyByHand() {
        assertTrue(Updater.autoInstall("", "1.2", "1.1"));
        assertFalse(Updater.autoInstall("1.2", "v1.2", "1.1"));
        assertTrue(Updater.autoInstall("1.2", "1.3", "1.1"));
        assertFalse(Updater.autoInstall("", "1.1", "1.1"));
    }

    @Test
    void readsTheReleaseZip() {
        String json = """
                {"tag_name": "v1.2", "assets": [
                  {"name": "MacroEasy.jar", "browser_download_url": "https://github.com/x/y/MacroEasy.jar"},
                  {"name": "MacroEasy.zip", "browser_download_url": "https://github.com/x/y/MacroEasy.zip"}]}
                """;
        Updater.Release release = Updater.parse(json);
        assertEquals("v1.2", release.version);
        assertEquals("https://github.com/x/y/MacroEasy.zip", release.url);
    }

    @Test
    void refusesDownloadsOutsideGitHub() {
        String json = """
                {"tag_name": "v1.2", "assets": [
                  {"name": "MacroEasy.zip", "browser_download_url": "https://example.com/MacroEasy.zip"}]}
                """;
        assertNull(Updater.parse(json));
    }

    @Test
    void findsTheAppBundleOnlyInsideOne() {
        assertEquals(Path.of("/Applications/MacroEasy.app"),
                Updater.bundleOf("/Applications/MacroEasy.app/Contents/MacOS/MacroEasy"));
        assertNull(Updater.bundleOf("/usr/bin/java"));
        assertNull(Updater.bundleOf(""));
    }
}
