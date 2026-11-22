import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/**
 * Undo / redo for one sheet. After every user action (any burst of model changes in one event) the sheet is
 * compared with the last remembered state; if it differs, the old state goes on the undo stack.
 */
public final class SheetUndo implements TableModelListener {
    private final SpreadsheetModel model;
    private final Runnable onRestore;
    private final Deque<SpreadsheetModel.Snapshot> undo = new ArrayDeque<>();
    private final Deque<SpreadsheetModel.Snapshot> redo = new ArrayDeque<>();
    private SpreadsheetModel.Snapshot base;
    private boolean pending;
    private boolean restoring;

    public SheetUndo(SpreadsheetModel model, Runnable onRestore) {
        this.model = model;
        this.onRestore = onRestore;
        this.base = model.snapshot();
        model.addTableModelListener(this);
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        schedule();
    }

    /** Call after changes that do not fire table events (notes, rules, pictures ...). */
    public void schedule() {
        if (restoring || pending) return;
        pending = true;
        SwingUtilities.invokeLater(this::commit);
    }

    /** Forget the history and treat the current state as the starting point (after loading a file). */
    public void reset() {
        undo.clear();
        redo.clear();
        base = model.snapshot();
        pending = false;
    }

    private int maxDepth() {
        return Math.max(5, Math.min(100, 3_000_000 / (base.weight() + 1)));
    }

    /** Records the sheet's current state as a new undo step if anything changed. */
    public void commit() {
        pending = false;
        if (restoring) return;
        SpreadsheetModel.Snapshot now = model.snapshot();
        if (now.same(base)) return;
        undo.push(base);
        while (undo.size() > maxDepth()) undo.removeLast();
        redo.clear();
        base = now;
    }

    public boolean canUndo() {
        commit();
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        commit();
        return !redo.isEmpty();
    }

    public boolean undo() {
        commit();
        if (undo.isEmpty()) return false;
        redo.push(base);
        apply(undo.pop());
        return true;
    }

    public boolean redo() {
        commit();
        if (redo.isEmpty()) return false;
        undo.push(base);
        apply(redo.pop());
        return true;
    }

    private void apply(SpreadsheetModel.Snapshot s) {
        restoring = true;
        try {
            model.restore(s);
            base = s;
        } finally {
            restoring = false;
        }
        if (onRestore != null) onRestore.run();
    }
}
