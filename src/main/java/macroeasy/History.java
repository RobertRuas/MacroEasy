package macroeasy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Undo and redo for the step list of the open macro. Each entry is a full copy of the list. */
final class History {
    private static final int LIMIT = 200;
    private final Deque<List<Step>> undo = new ArrayDeque<>();
    private final Deque<List<Step>> redo = new ArrayDeque<>();

    /** Call with the list as it was, right before a change. */
    void record(List<Step> before) {
        undo.push(List.copyOf(before));
        while (undo.size() > LIMIT) undo.removeLast();
        redo.clear();
    }

    /** Returns the list to show, or null when there is nothing to undo. */
    List<Step> undo(List<Step> current) {
        if (undo.isEmpty()) return null;
        redo.push(List.copyOf(current));
        return undo.pop();
    }

    /** Returns the list to show, or null when there is nothing to redo. */
    List<Step> redo(List<Step> current) {
        if (redo.isEmpty()) return null;
        undo.push(List.copyOf(current));
        return redo.pop();
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    void clear() {
        undo.clear();
        redo.clear();
    }
}
