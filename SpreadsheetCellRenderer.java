import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

/**
 * Rendering layer: paints each cell according to its {@link CellStyle}
 * (bold / italic / size, alignment, fill and text colour) with subtle grid
 * lines, a tinted selection and a green "active cell" frame like Excel.
 */
public class SpreadsheetCellRenderer extends DefaultTableCellRenderer {

    private static final Color ERROR_FG = new Color(0xC00000);

    private final SpreadsheetModel model;
    private double zoom = 1.0;
    private final Map<String, Font> familyCache = new HashMap<>();
    /** 0 = no filter arrow on the cell being painted, 1 = arrow, 2 = arrow of a filtered column. */
    private int arrowState;
    /** Sparkline of the cell being painted (null = none). */
    private Sparkline spark;
    /** True when the cell being painted has a note (red corner). */
    private boolean hasNote;
    /** Colour scale / icon / data bar of the cell being painted (null = none). */
    private CondRule.Visual visual;
    /** Cell under the mouse (-1 = none); gets a faint highlight. Set by the sheet view. */
    public int hoverRow = -1;
    public int hoverCol = -1;
    /** True while the selection frame is gliding to a new cell (the frame is painted by the sheet view instead). */
    public boolean suppressActive;

    public SpreadsheetCellRenderer(SpreadsheetModel model) {
        this.model = model;
    }

    /** Scale factor for explicitly sized fonts (the table's own font is already scaled). */
    public void setZoom(double zoom) {
        this.zoom = zoom;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, column);
        CellStyle st = model.getStyle(row, column);

        // Font
        Font base = table.getFont();
        int flags = Font.PLAIN;
        if (st != null && st.bold) flags |= Font.BOLD;
        if (st != null && st.italic) flags |= Font.ITALIC;
        float size = (st != null && st.fontSize > 0) ? (float) (st.fontSize * zoom) : base.getSize2D();
        if (st != null && st.fontFamily != null) {
            Font fam = familyCache.computeIfAbsent(st.fontFamily, n -> new Font(n, Font.PLAIN, 12));
            label.setFont(fam.deriveFont(flags, size));
        } else {
            label.setFont(base.deriveFont(flags, size));
        }

        // Alignment: explicit style wins; otherwise numbers right, errors centred, text left
        int align;
        if (st != null && st.alignment != CellStyle.ALIGN_AUTO) align = st.alignment;
        else if (model.isShowFormulas() && model.getInput(row, column).startsWith("=")) align = SwingConstants.LEFT;
        else if (model.isFormulaError(row, column) || model.getComputedValue(row, column) instanceof Boolean) {
            align = SwingConstants.CENTER; // errors and TRUE/FALSE are centred, like Excel
        }
        else if (model.isNumeric(row, column)) align = SwingConstants.RIGHT;
        else align = SwingConstants.LEFT;
        label.setHorizontalAlignment(align);

        // Colours
        Color bg = (st != null && st.background != null) ? st.background : Theme.cellBg;
        Color fg = (st != null && st.foreground != null) ? st.foreground : Theme.cellFg;
        if (model.isFormulaError(row, column) && (st == null || st.foreground == null)) fg = ERROR_FG;

        CondRule rule = model.matchingRule(row, column);
        if (rule != null) {
            if (rule.bg != null) bg = rule.bg;
            if (rule.fg != null) fg = rule.fg;
        }
        String link = model.linkAt(row, column);
        if (link != null) {
            if (st == null || st.foreground == null) fg = Theme.dark() ? new Color(0x6CB6FF) : new Color(0x0563C1);
            java.util.Map<java.awt.font.TextAttribute, Object> ua = new java.util.HashMap<>();
            ua.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
            label.setFont(label.getFont().deriveFont(ua));
        }
        visual = model.visualAt(row, column);
        if (visual != null && visual.bg != null && rule == null) {
            bg = visual.bg;
            if (st == null || st.foreground == null) fg = luminance(bg) < 0.45 ? Color.WHITE : new Color(0x202020);
        }
        int iconPad = visual != null && visual.icon >= 0 ? Math.max(12, (int) Math.round(15 * zoom)) : 0;
        spark = model.sparklineAt(row, column);
        hasNote = model.commentAt(row, column) != null;
        AutoFilter af = model.getFilter();
        arrowState = af != null && row == af.headerRow && af.coversColumn(column) ? (af.isFiltered(column) ? 2 : 1) : 0;

        boolean active = row == table.getSelectionModel().getLeadSelectionIndex()
                && column == table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        boolean frame = active && !suppressActive;
        if (isSelected && !active) bg = blend(bg, Theme.accent, Theme.current().dark ? 0.35f : 0.20f); // active cell stays "clean", like Excel
        else if (!isSelected && row == hoverRow && column == hoverCol) bg = blend(bg, Theme.accent, 0.10f);
        label.setBackground(bg);
        label.setForeground(fg);
        label.setOpaque(true);

        // Borders
        if (frame) {
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.accent, 2),
                    BorderFactory.createEmptyBorder(0, 2 + iconPad, 0, 2)));
        } else {
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 1, Theme.gridLine),
                    BorderFactory.createEmptyBorder(0, 4 + iconPad, 0, 4)));
        }
        return label;
    }

    /** Where the filter arrow sits inside a header cell (used for painting and for clicks). */
    public static Rectangle arrowRect(Rectangle cell, double zoom) {
        int size = Math.max(10, (int) Math.round(16 * zoom));
        size = Math.min(size, Math.max(10, cell.height - 2));
        return new Rectangle(cell.x + cell.width - size - 2, cell.y + (cell.height - size) / 2, size, size);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (visual != null) paintVisual((Graphics2D) g);
        if (spark != null) {
            SparklinePainter.paint((Graphics2D) g, spark, spark.values(model), new Rectangle(0, 0, getWidth(), getHeight()));
        }
        if (hasNote) {
            int t = Math.max(6, (int) Math.round(8 * zoom));
            g.setColor(new Color(0xD32F2F));
            g.fillPolygon(new int[]{getWidth() - t, getWidth(), getWidth()}, new int[]{0, 0, t}, 3);
        }
        if (arrowState == 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Rectangle r = arrowRect(new Rectangle(0, 0, getWidth(), getHeight()), zoom);
        g2.setColor(arrowState == 2 ? Theme.accent : Theme.fieldBg);
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 4, 4);
        g2.setColor(arrowState == 2 ? Theme.accent : Theme.dimFg);
        g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 4, 4);
        g2.setColor(arrowState == 2 ? Theme.fieldBg : Theme.fg);
        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        int a = Math.max(3, r.width / 5);
        g2.fillPolygon(new int[]{cx - a, cx + a, cx}, new int[]{cy - a / 2, cy - a / 2, cy + a / 2 + 1}, 3);
        g2.dispose();
    }

    private static double luminance(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
    }

    private void paintVisual(Graphics2D g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        if (visual.bar >= 0 && visual.barColor != null) {
            int bw = (int) Math.round((w - 4) * visual.bar);
            Color bc = visual.barColor;
            g.setColor(new Color(bc.getRed(), bc.getGreen(), bc.getBlue(), 120));
            g.fillRect(2, 3, Math.max(1, bw), Math.max(2, h - 7));
            g.setColor(new Color(bc.getRed(), bc.getGreen(), bc.getBlue(), 200));
            g.drawRect(2, 3, Math.max(1, bw), Math.max(2, h - 7));
        }
        if (visual.icon >= 0 && visual.iconSet != null) {
            int n = CondRule.iconCount(visual.iconSet);
            int sz = Math.max(9, Math.min(h - 6, (int) Math.round(12 * zoom)));
            int x = 3;
            int y = (h - sz) / 2;
            paintIcon(g, visual.iconSet, visual.icon, n, x, y, sz);
        }
        g.dispose();
    }

    private static final Color GOOD = new Color(0x2E9E4F);
    private static final Color MID = new Color(0xE8B000);
    private static final Color BAD = new Color(0xD64545);

    /** Colour for position i of n (0 = worst, n-1 = best). */
    private static Color tone(int i, int n) {
        if (n <= 1) return GOOD;
        double f = i / (double) (n - 1);
        return f < 0.34 ? BAD : f < 0.67 ? MID : GOOD;
    }

    static void paintIcon(Graphics2D g, String set, int i, int n, int x, int y, int sz) {
        Color c = tone(i, n);
        g.setColor(c);
        int cx = x + sz / 2;
        int cy = y + sz / 2;
        switch (set) {
            case "TRAFFIC3":
                g.fillOval(x, y, sz, sz);
                g.setColor(new Color(0, 0, 0, 60));
                g.drawOval(x, y, sz - 1, sz - 1);
                break;
            case "SYMBOLS3": {
                g.fillOval(x, y, sz, sz);
                g.setColor(Color.WHITE);
                g.setStroke(new java.awt.BasicStroke(Math.max(1.5f, sz / 6f), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                int d = sz / 4;
                if (i == n - 1) { // check
                    g.drawLine(cx - d, cy, cx - d / 3, cy + d - 1);
                    g.drawLine(cx - d / 3, cy + d - 1, cx + d, cy - d + 1);
                } else if (i == 0) { // cross
                    g.drawLine(cx - d + 1, cy - d + 1, cx + d - 1, cy + d - 1);
                    g.drawLine(cx - d + 1, cy + d - 1, cx + d - 1, cy - d + 1);
                } else { // exclamation
                    g.drawLine(cx, cy - d, cx, cy + 1);
                    g.fillOval(cx - 1, cy + d - 2, 2, 2);
                }
                break;
            }
            case "FLAGS3": {
                g.setStroke(new java.awt.BasicStroke(1.5f));
                g.drawLine(x + 2, y, x + 2, y + sz);
                g.fillPolygon(new int[]{x + 2, x + sz, x + 2}, new int[]{y, y + sz / 4 + 1, y + sz / 2}, 3);
                break;
            }
            case "ARROWS3": case "ARROWS4": case "ARROWS5": {
                // positions: worst..best -> down, down-right, right, up-right, up (spread across n)
                double f = n <= 1 ? 1 : i / (double) (n - 1);
                // angle measured from "pointing right": best = up (-90deg screen), worst = down (+90deg)
                double a = (0.5 - f) * Math.PI; // f=1 -> -90deg (up), f=0 -> +90deg (down)
                double len = sz * 0.5;
                double hx = Math.cos(a) * len;
                double hy = Math.sin(a) * len;
                java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
                double ux = Math.cos(a);
                double uy = Math.sin(a);
                double px = -uy;
                double py = ux;
                double hw = sz * 0.30;
                double hl = sz * 0.34;
                p.moveTo(cx + hx, cy + hy);
                p.lineTo(cx + hx - ux * hl + px * hw, cy + hy - uy * hl + py * hw);
                p.lineTo(cx + hx - ux * hl * 0.55 + px * hw * 0.28, cy + hy - uy * hl * 0.55 + py * hw * 0.28);
                p.lineTo(cx - hx + px * hw * 0.28, cy - hy + py * hw * 0.28);
                p.lineTo(cx - hx - px * hw * 0.28, cy - hy - py * hw * 0.28);
                p.lineTo(cx + hx - ux * hl * 0.55 - px * hw * 0.28, cy + hy - uy * hl * 0.55 - py * hw * 0.28);
                p.lineTo(cx + hx - ux * hl - px * hw, cy + hy - uy * hl - py * hw);
                p.closePath();
                g.fill(p);
                break;
            }
            default: { // RATING4 / RATING5: signal bars, i+1 of n filled
                int bw = Math.max(2, sz / (n + 1));
                int gap = Math.max(1, (sz - bw * n) / Math.max(1, n - 1));
                for (int k = 0; k < n; k++) {
                    int bh = (int) Math.round(sz * (0.35 + 0.65 * (k + 1) / n));
                    g.setColor(k <= i ? new Color(0x3B6FB6) : new Color(0xB0B8C4));
                    g.fillRect(x + k * (bw + gap), y + sz - bh, bw, bh);
                }
            }
        }
    }

    private static Color blend(Color a, Color b, float amount) {
        float inv = 1f - amount;
        return new Color(
                Math.round(a.getRed() * inv + b.getRed() * amount),
                Math.round(a.getGreen() * inv + b.getGreen() * amount),
                Math.round(a.getBlue() * inv + b.getBlue() * amount));
    }

    // ------------------------------------------------------------------

    /** Column header (A, B, C ...) - highlights columns that contain the selection. */
    public static class ColumnHeaderRenderer extends DefaultTableCellRenderer {
        public int hover = -1; // header under the mouse

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, column);
            boolean sel = table.isColumnSelected(column);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.setFont(table.getFont().deriveFont(Font.BOLD, Math.max(7f, table.getFont().getSize2D() - 1f)));
            l.setOpaque(true);
            l.setBackground(sel ? Theme.headerSelBg : column == hover ? Theme.headerHover : Theme.headerBg);
            l.setForeground(sel || column == hover ? Theme.headerSelFg : Theme.headerFg);
            l.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Theme.headerBorder));
            return l;
        }
    }

    /** Row header (1, 2, 3 ...) - used as the JList in the scroll pane's row header. */
    public static class RowHeaderRenderer extends JLabel implements ListCellRenderer<String> {
        private final JTable table;
        public int hover = -1; // row header under the mouse
        private int index;

        public RowHeaderRenderer(JTable table) {
            this.table = table;
            setOpaque(true);
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        /** Same height as the table row, so rows hidden by a filter disappear from the numbers too. */
        @Override
        public java.awt.Dimension getPreferredSize() {
            java.awt.Dimension d = super.getPreferredSize();
            return new java.awt.Dimension(d.width, table.getRowHeight(Math.max(0, Math.min(index, table.getRowCount() - 1))));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            this.index = index;
            boolean sel = table.isRowSelected(index);
            setText(value);
            setFont(table.getFont().deriveFont(Font.BOLD, Math.max(7f, table.getFont().getSize2D() - 1f)));
            setBackground(sel ? Theme.headerSelBg : index == hover ? Theme.headerHover : Theme.headerBg);
            setForeground(sel || index == hover ? Theme.headerSelFg : Theme.headerFg);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Theme.headerBorder));
            return this;
        }
    }
}
