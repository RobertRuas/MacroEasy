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
    void trackpadFractionsAddUpToWholeLines() {
        ScrollTally tally = new ScrollTally();
        tally.start(10, 20, -1);
        assertNull(tally.add(0.3));
        assertTrue(tally.continues(0.3, 50, 700));
        assertEquals(1, tally.add(0.3).scroll);
        tally.add(0.3);
        Step step = tally.add(0.3);
        assertEquals(1, step.scroll);
        assertEquals(10, step.x);
        assertEquals(2, tally.add(0.4).scroll);
        assertFalse(tally.continues(-1, 50, 700));
        assertFalse(tally.continues(1, 900, 700));
        tally.reset();
        assertFalse(tally.continues(1, 0, 700));
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
        assertEquals(new Dimension(960, 560), WindowSize.defaultSize(2560, 1440));
        Dimension laptop = WindowSize.defaultSize(1280, 800);
        assertTrue(laptop.width <= 1280 && laptop.height <= 800);
        assertTrue(laptop.width >= 600 && laptop.height >= 400);
    }
    @Test
    void dragPathStartsGentlyAndEndsOnTheTarget() {
        int[][] path = Player.dragPath(100, 100, 400, 100);
        assertEquals(102, path[0][0]);
        assertEquals(105, path[1][0]);
        assertTrue(path.length >= 30);
        assertEquals(399, path[path.length - 2][0]);
        assertEquals(400, path[path.length - 1][0]);
        assertEquals(100, path[path.length - 1][1]);
        for (int i = 1; i < path.length; i++) {
            assertTrue(path[i][0] >= path[i - 1][0]);
            assertTrue(path[i][0] - path[i - 1][0] <= 12);
        }
        int[][] tiny = Player.dragPath(10, 10, 13, 10);
        assertEquals(13, tiny[tiny.length - 1][0]);
    }

    @Test
    void pointScrollSlicesAddUpExactly() {
        int[] parts = MacScroll.slices(-347, 20);
        int sum = 0;
        for (int part : parts) {
            sum += part;
            assertTrue(Math.abs(part) <= 20 && part < 0);
        }
        assertEquals(-347, sum);
        assertEquals(1, MacScroll.slices(5, 20).length);
    }

    @Test
    void trackpadPointsAreKeptExactly() {
        ScrollTally tally = new ScrollTally();
        tally.start(10, 20, -1);
        assertEquals(37, tally.add(0.3, 37).scroll);
        Step step = tally.add(0.6, 120);
        assertTrue(step.pixels);
        assertEquals(157, step.scroll);
        assertEquals(4, step.scrollLines());
    }

    @Test
    void pointScrollSurvivesSaving() {
        Step step = Step.scrollPoints(5, 6, -240, true).withGap(90);
        Step back = Step.parse(step.encode());
        assertTrue(back.pixels);
        assertEquals(-240, back.scroll);
        assertEquals(90, back.gapMs);
        Step lines = Step.parse("SCROLL 3 5 6 1|g-1");
        assertFalse(lines.pixels);
        assertEquals(3, lines.scrollLines());
    }
    @Test
    void textCanHoldRandomNumbers() {
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 200; i++) {
            int n = Integer.parseInt(Step.expand("{9-15}", random));
            assertTrue(n >= 9 && n <= 15);
        }
        String line = Step.expand("Idade { 15 - 9 } anos, {3-3}", random);
        assertTrue(line.matches("Idade (9|1[0-5]) anos, 3"));
        assertEquals("sem {chave} e {a-b}", Step.expand("sem {chave} e {a-b}", random));
    }
}
