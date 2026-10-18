package macroeasy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class StepTest {
    private static Step roundTrip(Step step) {
        Step back = Step.parse(step.encode());
        assertEquals(step.encode(), back.encode());
        return back;
    }

    @Test
    void everyKindSurvivesSaving() {
        roundTrip(Step.click(10, 20, 3, 2));
        roundTrip(Step.text("olá\nmundo \\ fim", false));
        roundTrip(Step.keys(new int[] {KeyEvent.VK_META, KeyEvent.VK_S}));
        roundTrip(Step.wait(750));
        roundTrip(Step.focus("Safari", "Início"));
        roundTrip(Step.scroll(5, 6, -12, true));
        Step drag = roundTrip(Step.drag(1, 2, 300, 400, 1).withGap(90));
        assertEquals(Step.Kind.DRAG, drag.kind);
        assertEquals(300, drag.x2);
        assertEquals(400, drag.y2);
        assertEquals(90, drag.gapMs);
    }

    @Test
    void textKeepsLineBreaksAndBackslashes() {
        Step back = roundTrip(Step.text("a\\nb\nc", true));
        assertEquals("a\\nb\nc", back.text);
        assertTrue(back.paste);
    }

    @Test
    void textThatLooksLikeAGapIsKept() {
        Step back = roundTrip(Step.text("preço|g10", true).withGap(5));
        assertEquals("preço|g10", back.text);
        assertEquals(5, back.gapMs);
    }

    @Test
    void filesFromVersionOneOneStillOpen() {
        assertEquals(Step.Kind.CLICK, Step.parse("CLICK 100 200 1 1|g-1").kind);
        Step scroll = Step.parse("SCROLL -3 10 20 1|g-1");
        assertEquals(-3, scroll.scroll);
        assertTrue(scroll.move);
        assertArrayEquals(new int[] {157, 83}, Step.parse("KEYS 157,83").keys);
        assertEquals(-1, Step.parse("WAIT 300").gapMs);
    }

    @Test
    void brokenLinesAreSkipped() {
        assertNull(Step.parse("CLICK a b"));
        assertNull(Step.parse("DRAG 1 2"));
        assertNull(Step.parse("NOPE 1"));
        assertNull(Step.parse("MACROEASY 1"));
        assertNull(Step.parse(""));
    }

    @Test
    void macroFileReadsWhatItWrites() {
        List<Step> steps = List.of(Step.click(1, 2, 1, 1), Step.drag(3, 4, 5, 6, 3), Step.wait(10));
        List<Step> back = MacroFile.read(MacroFile.write(steps));
        assertEquals(steps.size(), back.size());
        for (int i = 0; i < steps.size(); i++) assertEquals(steps.get(i).encode(), back.get(i).encode());
    }

    @Test
    void dragDescribesBothPoints() {
        assertTrue(Step.drag(1, 2, 3, 4, 1).describe().contains("1, 2  →  3, 4"));
    }
}
