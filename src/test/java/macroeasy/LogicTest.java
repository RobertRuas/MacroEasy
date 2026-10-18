package macroeasy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogicTest {
    @Test
    void undoAndRedoWalkThroughChanges() {
        History history = new History();
        List<Step> empty = List.of();
        List<Step> one = List.of(Step.wait(1));
        List<Step> two = List.of(Step.wait(1), Step.wait(2));
        assertNull(history.undo(empty));
        history.record(empty);
        history.record(one);
        assertEquals(one, history.undo(two));
        assertEquals(empty, history.undo(one));
        assertFalse(history.canUndo());
        assertEquals(one, history.redo(empty));
        assertEquals(two, history.redo(one));
        assertFalse(history.canRedo());
        history.undo(two);
        history.record(one);
        assertFalse(history.canRedo());
    }

    @Test
    void cornerStopWorksOnEveryScreenButNotBesideIt() {
        Rectangle left = new Rectangle(-1920, 0, 1920, 1080);
        assertTrue(Player.inCorner(-1918, 3, left));
        assertFalse(Player.inCorner(-1918, 500, left));
        Rectangle main = new Rectangle(0, 0, 1440, 900);
        assertTrue(Player.inCorner(0, 0, main));
        assertFalse(Player.inCorner(-500, 0, main));
    }

    @Test
    void recordingTellsDragsFromClicks() {
        assertFalse(Recorder.isDrag(100, 100, 103, 102));
        assertTrue(Recorder.isDrag(100, 100, 100, 140));
    }

    @Test
    void recordingKeepsRealPauses() {
        assertEquals(-1, Recorder.realGap(0, 5_000_000_000L));
        assertEquals(1500, Recorder.realGap(1_000_000_000L, 2_500_000_000L));
        assertEquals(600_000, Recorder.realGap(1, 10_000_000_000_000L));
    }

    @Test
    void shortcutsFollowTheKeyboardLayout() {
        assertEquals(KeyEvent.VK_Z, Recorder.letterOrDigit('z', false));
        assertEquals(KeyEvent.VK_Q, Recorder.letterOrDigit('Q', false));
        assertEquals(KeyEvent.VK_A, Recorder.letterOrDigit((char) 1, true));
        assertEquals(KeyEvent.VK_7, Recorder.letterOrDigit('7', false));
        assertEquals(KeyEvent.VK_UNDEFINED, Recorder.letterOrDigit('ç', false));
    }

    @Test
    void windowFitsSmallAndLargeScreens() {
        assertEquals(new Dimension(980, 600), WindowSize.defaultSize(2560, 1440));
        Dimension laptop = WindowSize.defaultSize(1280, 800);
        assertTrue(laptop.width <= 1280 && laptop.height <= 800);
        assertTrue(laptop.width >= 820 && laptop.height >= 500);
    }
}
