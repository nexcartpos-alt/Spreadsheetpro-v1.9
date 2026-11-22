import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Excel-style "point mode": while a formula is being typed, clicking or dragging cells writes the
 * reference (B2 or B2:B9) at the caret. Every reference in the formula gets its own colour, used both
 * for the text in the editor and for an outline around the cells on the grid.
 */
public final class FormulaPointer {
    public static final Color[] COLORS = {
            new Color(0x2E75B6), new Color(0xC00000), new Color(0x7030A0),
            new Color(0x00A04A), new Color(0xED7D31), new Color(0x00A0D0)
    };
    private static final Pattern REF = Pattern.compile(
            "(?<![A-Za-z0-9_!.$'\"])(\\$?[A-Za-z]{1,3}\\$?[0-9]{1,7})(?::(\\$?[A-Za-z]{1,3}\\$?[0-9]{1,7}))?(?![A-Za-z0-9_(!])");
    private static final Pattern CELL = Pattern.compile("\\$?([A-Za-z]{1,3})\\$?([0-9]{1,7})");
    private static final String OPERATORS = "=(,+-*/^&<>;%";

    /** A reference found in formula text. */
    public static final class Ref {
        public final int start, end, r1, c1, r2, c2, colorIndex;

        Ref(int start, int end, int r1, int c1, int r2, int c2, int colorIndex) {
            this.start = start; this.end = end;
            this.r1 = Math.min(r1, r2); this.c1 = Math.min(c1, c2);
            this.r2 = Math.max(r1, r2); this.c2 = Math.max(c1, c2);
            this.colorIndex = colorIndex;
        }
    }

    /** All plain (same-sheet) cell references in a formula, outside string literals. */
    public static List<Ref> parse(String text) {
        List<Ref> out = new ArrayList<>();
        if (text == null || !text.startsWith("=")) return out;
        List<String> seen = new ArrayList<>();
        boolean inQuote = false;
        int i = 0;
        // blank out string literals so refs inside them are ignored
        StringBuilder masked = new StringBuilder(text);
        for (int k = 0; k < text.length(); k++) {
            if (text.charAt(k) == '"') { inQuote = !inQuote; masked.setCharAt(k, ' '); }
            else if (inQuote) masked.setCharAt(k, ' ');
        }
        Matcher m = REF.matcher(masked);
        while (m.find()) {
            int[] a = cell(m.group(1));
            if (a == null) continue;
            int[] b = m.group(2) == null ? a : cell(m.group(2));
            if (b == null) continue;
            String key = m.group().toUpperCase().replace("$", "");
            int idx = seen.indexOf(key);
            if (idx < 0) { seen.add(key); idx = seen.size() - 1; }
            out.add(new Ref(m.start(), m.end(), a[0], a[1], b[0], b[1], idx % COLORS.length));
        }
        return out;
    }

    private static int[] cell(String s) {
        Matcher m = CELL.matcher(s);
        if (!m.matches()) return null;
        int row = Integer.parseInt(m.group(2)) - 1;
        if (row < 0) return null;
        return new int[]{row, FormulaEngine.parseColumn(m.group(1))};
    }

    /** Colours the references inside a text field (call after every edit). */
    public static void highlight(JTextField field) {
        Highlighter h = field.getHighlighter();
        if (h == null) return;
        h.removeAllHighlights();
        for (Ref r : parse(field.getText())) {
            final Color col = COLORS[r.colorIndex];
            try {
                h.addHighlight(r.start, r.end, (g, p0, p1, bounds, c) -> {
                    try {
                        Rectangle a = c.modelToView(p0);
                        Rectangle b = c.modelToView(p1);
                        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 55));
                        g.fillRect(a.x, a.y, b.x - a.x, a.height);
                        g.setColor(col);
                        g.fillRect(a.x, a.y + a.height - 2, b.x - a.x, 2);
                    } catch (BadLocationException ignored) {
                    }
                });
            } catch (BadLocationException ignored) {
            }
        }
    }

    // ------------------------------------------------------------------

    private final JTable table;
    private final Supplier<JTextField> active;
    private boolean pointing;
    private boolean dragging;
    private int pStart, pEnd;
    private int anchorRow, anchorCol;
    private boolean updating;

    public FormulaPointer(JTable table, Supplier<JTextField> active) {
        this.table = table;
        this.active = active;
    }

    /** Keeps the pointing session valid: any change not made by us ends it. */
    public void watch(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (!updating) pointing = false;
                highlight(field);
                table.repaint();
            }
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { }
        });
        field.addCaretListener(e -> {
            if (!updating && pointing && field.getCaretPosition() != pEnd) pointing = false;
        });
    }

    private boolean canPoint(JTextField f) {
        String t = f.getText();
        if (!t.startsWith("=")) return false;
        if (pointing && f.getSelectionStart() == f.getSelectionEnd() && f.getCaretPosition() == pEnd) return true;
        int pos = Math.min(f.getSelectionStart(), f.getSelectionEnd());
        int i = pos - 1;
        while (i >= 0 && t.charAt(i) == ' ') i--;
        if (i < 0) return false;
        return OPERATORS.indexOf(t.charAt(i)) >= 0;
    }

    private void write(JTextField f, int from, int to, String ref) {
        updating = true;
        try {
            f.getDocument().remove(from, to - from);
            f.getDocument().insertString(from, ref, null);
            pStart = from;
            pEnd = from + ref.length();
            f.setCaretPosition(pEnd);
            pointing = true;
        } catch (BadLocationException ignored) {
        } finally {
            updating = false;
        }
        highlight(f);
        table.repaint();
    }

    private static String ref(int r1, int c1, int r2, int c2) {
        int ra = Math.min(r1, r2), rb = Math.max(r1, r2), ca = Math.min(c1, c2), cb = Math.max(c1, c2);
        if (ra == rb && ca == cb) return FormulaEngine.cellRef(ra, ca);
        return FormulaEngine.cellRef(ra, ca) + ":" + FormulaEngine.cellRef(rb, cb);
    }

    /** @return true when the event was used for pointing */
    public boolean handleMouse(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1 && e.getID() == MouseEvent.MOUSE_PRESSED) return false;
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            JTextField f = active.get();
            if (f == null || !canPoint(f)) return false;
            int r = table.rowAtPoint(e.getPoint()), c = table.columnAtPoint(e.getPoint());
            if (r < 0 || c < 0) return false;
            anchorRow = r; anchorCol = c;
            int from, to;
            if (pointing && f.getCaretPosition() == pEnd) { from = pStart; to = pEnd; }
            else { from = Math.min(f.getSelectionStart(), f.getSelectionEnd()); to = Math.max(f.getSelectionStart(), f.getSelectionEnd()); }
            write(f, from, to, ref(r, c, r, c));
            dragging = true;
            e.consume();
            return true;
        }
        if (dragging && (e.getID() == MouseEvent.MOUSE_RELEASED || e.getID() == MouseEvent.MOUSE_CLICKED)) {
            if (e.getID() == MouseEvent.MOUSE_RELEASED) dragging = false;
            e.consume();
            return true;
        }
        return false;
    }

    public boolean handleMotion(MouseEvent e) {
        if (!dragging || e.getID() != MouseEvent.MOUSE_DRAGGED) return false;
        JTextField f = active.get();
        if (f == null) return true;
        int r = table.rowAtPoint(e.getPoint()), c = table.columnAtPoint(e.getPoint());
        if (r < 0 || c < 0) return true;
        write(f, pStart, pEnd, ref(anchorRow, anchorCol, r, c));
        e.consume();
        return true;
    }

    /** Outlines every referenced range on the grid in its colour. */
    public void paint(Graphics g0) {
        JTextField f = active.get();
        if (f == null || !f.getText().startsWith("=")) return;
        List<Ref> refs = parse(f.getText());
        if (refs.isEmpty()) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Ref r : refs) {
            if (r.r1 >= table.getRowCount() || r.c1 >= table.getColumnCount()) continue;
            Rectangle a = table.getCellRect(r.r1, r.c1, true);
            Rectangle b = table.getCellRect(Math.min(r.r2, table.getRowCount() - 1), Math.min(r.c2, table.getColumnCount() - 1), true);
            Rectangle u = a.union(b);
            Color col = COLORS[r.colorIndex];
            g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 28));
            g.fillRect(u.x, u.y, u.width, u.height);
            g.setColor(col);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(u.x + 1, u.y + 1, u.width - 3, u.height - 3);
        }
        g.dispose();
    }
}
