import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Copy / cut / paste of cell ranges. Inside the app a copy keeps formulas (shifted like Excel when pasted
 * elsewhere) and formatting; other programs receive plain tab-separated text, and tab-separated text from
 * Excel, Google Sheets or Notepad can be pasted in.
 */
public final class CellClipboard {
    /** What a copy remembers. */
    public static final class Data {
        final SpreadsheetModel source;
        final int r1, c1, rows, cols;
        final String[][] input, values;
        final CellStyle[][] style;
        final boolean cut;
        final String text;

        Data(SpreadsheetModel m, int r1, int c1, int r2, int c2, boolean cut) {
            this.source = m;
            this.r1 = r1;
            this.c1 = c1;
            this.rows = r2 - r1 + 1;
            this.cols = c2 - c1 + 1;
            this.cut = cut;
            input = new String[rows][cols];
            values = new String[rows][cols];
            style = new CellStyle[rows][cols];
            StringBuilder sb = new StringBuilder();
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    input[r][c] = m.getInput(r1 + r, c1 + c);
                    CellStyle st = m.getStyle(r1 + r, c1 + c);
                    style[r][c] = st == null ? null : st.copy();
                    Object v = m.getComputedValue(r1 + r, c1 + c);
                    values[r][c] = v == null ? "" : v instanceof Double ? plain((Double) v)
                            : v instanceof Boolean ? ((Boolean) v ? "TRUE" : "FALSE") : v.toString();
                    String shown = String.valueOf(m.getValueAt(r1 + r, c1 + c));
                    if (c > 0) sb.append('\t');
                    sb.append(quote(shown));
                }
                sb.append('\n');
            }
            text = sb.toString();
        }
    }

    public static final DataFlavor FLAVOR = makeFlavor();

    private static DataFlavor makeFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Data.class.getName(),
                    "Spreadsheet Pro cells", Data.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private CellClipboard() {
    }

    private static String plain(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        return new BigDecimal(d).round(new java.math.MathContext(15)).stripTrailingZeros().toPlainString();
    }

    private static String quote(String s) {
        if (s.indexOf('\t') < 0 && s.indexOf('\n') < 0 && s.indexOf('"') < 0) return s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    /** The transferable placed on the system clipboard. */
    public static Transferable copy(SpreadsheetModel m, int r1, int c1, int r2, int c2, boolean cut) {
        final Data d = new Data(m, r1, c1, r2, c2, cut);
        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{FLAVOR, DataFlavor.stringFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor f) {
                return FLAVOR.equals(f) || DataFlavor.stringFlavor.equals(f);
            }

            @Override
            public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                if (FLAVOR.equals(f)) return d;
                if (DataFlavor.stringFlavor.equals(f)) return d.text;
                throw new UnsupportedFlavorException(f);
            }
        };
    }

    /**
     * Pastes into the sheet with its top-left at (row, col). A single copied cell fills a larger selection
     * (selR1..selR2 / selC1..selC2). @return {r1, c1, r2, c2} of what was written, or null if nothing was.
     */
    public static int[] paste(SpreadsheetModel m, Transferable t, int row, int col,
                              int selR1, int selC1, int selR2, int selC2, boolean valuesOnly) {
        if (m.isProtected()) return null;
        try {
            if (t.isDataFlavorSupported(FLAVOR)) {
                return pasteInternal(m, (Data) t.getTransferData(FLAVOR), row, col, selR1, selC1, selR2, selC2, valuesOnly);
            }
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return pasteText(m, (String) t.getTransferData(DataFlavor.stringFlavor), row, col);
            }
        } catch (UnsupportedFlavorException | java.io.IOException | ClassCastException e) {
            return null;
        }
        return null;
    }

    private static int[] pasteInternal(SpreadsheetModel m, Data d, int row, int col,
                                       int selR1, int selC1, int selR2, int selC2, boolean valuesOnly) {
        int r1 = row;
        int c1 = col;
        int rows = d.rows;
        int cols = d.cols;
        boolean tile = d.rows == 1 && d.cols == 1 && (selR2 > selR1 || selC2 > selC1);
        if (tile) {
            r1 = selR1;
            c1 = selC1;
            rows = selR2 - selR1 + 1;
            cols = selC2 - selC1 + 1;
        }
        int r2 = Math.min(r1 + rows - 1, m.getRowCount() - 1);
        int c2 = Math.min(c1 + cols - 1, m.getColumnCount() - 1);
        if (r2 < r1 || c2 < c1) return null;
        m.beginBatch();
        try {
            for (int r = r1; r <= r2; r++) {
                for (int c = c1; c <= c2; c++) {
                    int sr = (r - r1) % d.rows;
                    int sc = (c - c1) % d.cols;
                    String in = d.input[sr][sc];
                    if (valuesOnly) {
                        CellStyle keep = m.getStyle(r, c);
                        m.setInputs(r, c, d.values[sr][sc], keep == null ? null : keep.copy());
                    } else {
                        if (!d.cut && in.startsWith("=")) in = FormulaRefs.translate(in, r - (d.r1 + sr), c - (d.c1 + sc));
                        CellStyle st = d.style[sr][sc];
                        m.setInputs(r, c, in, st == null ? null : st.copy());
                    }
                }
            }
        } finally {
            m.endBatch();
        }
        m.refreshAll();
        if (d.cut && !valuesOnly) {
            SpreadsheetModel src = d.source;
            boolean same = src == m;
            src.beginBatch();
            try {
                for (int r = 0; r < d.rows; r++) {
                    for (int c = 0; c < d.cols; c++) {
                        int rr = d.r1 + r;
                        int cc = d.c1 + c;
                        boolean inside = same && rr >= r1 && rr <= r2 && cc >= c1 && cc <= c2;
                        if (!inside) src.setInputs(rr, cc, "", null);
                    }
                }
            } finally {
                src.endBatch();
            }
            src.refreshAll();
        }
        return new int[]{r1, c1, r2, c2};
    }

    /** Tab-separated text (Excel quoting) into cells. */
    private static int[] pasteText(SpreadsheetModel m, String text, int row, int col) {
        List<List<String>> grid = parse(text);
        if (grid.isEmpty()) return null;
        int maxCols = 0;
        for (List<String> line : grid) maxCols = Math.max(maxCols, line.size());
        int r2 = Math.min(row + grid.size() - 1, m.getRowCount() - 1);
        int c2 = Math.min(col + maxCols - 1, m.getColumnCount() - 1);
        m.beginBatch();
        try {
            for (int r = 0; r < grid.size() && row + r <= r2; r++) {
                List<String> line = grid.get(r);
                for (int c = 0; c < line.size() && col + c <= c2; c++) m.setInput(row + r, col + c, line.get(c));
            }
        } finally {
            m.endBatch();
        }
        m.refreshAll();
        return new int[]{row, col, r2, c2};
    }

    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }
            if (ch == '"' && cell.length() == 0) {
                quoted = true;
                any = true;
            } else if (ch == '\t') {
                cur.add(cell.toString());
                cell.setLength(0);
                any = true;
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
                cur.add(cell.toString());
                cell.setLength(0);
                rows.add(cur);
                cur = new ArrayList<>();
                any = false;
            } else {
                cell.append(ch);
                any = true;
            }
        }
        if (any || cell.length() > 0 || !cur.isEmpty()) {
            cur.add(cell.toString());
            rows.add(cur);
        }
        return rows;
    }
}
