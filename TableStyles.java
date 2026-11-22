import java.awt.Color;

/** Ready-made "Format as Table" looks: a coloured header row, optional banded rows and thin borders. */
public final class TableStyles {

    public static final class Style {
        public final String name;
        public final Color header, headerText, band1, band2;

        Style(String name, int header, int headerText, int band1, int band2) {
            this.name = name;
            this.header = new Color(header);
            this.headerText = new Color(headerText);
            this.band1 = new Color(band1);
            this.band2 = new Color(band2);
        }
    }

    public static final Style[] ALL = {
            new Style("Green", 0x107C41, 0xFFFFFF, 0xE2F1E8, 0xFFFFFF),
            new Style("Blue", 0x2E75B6, 0xFFFFFF, 0xDEEAF6, 0xFFFFFF),
            new Style("Orange", 0xED7D31, 0xFFFFFF, 0xFBE5D6, 0xFFFFFF),
            new Style("Purple", 0x7030A0, 0xFFFFFF, 0xE9DDF3, 0xFFFFFF),
            new Style("Red", 0xC0392B, 0xFFFFFF, 0xF8DAD6, 0xFFFFFF),
            new Style("Teal", 0x138D90, 0xFFFFFF, 0xD6F0F0, 0xFFFFFF),
            new Style("Grey", 0x595959, 0xFFFFFF, 0xE7E6E6, 0xFFFFFF),
            new Style("Gold", 0xBF9000, 0xFFFFFF, 0xFFF2CC, 0xFFFFFF),
            new Style("Dark", 0x1F2933, 0xFFFFFF, 0xD9DEE3, 0xF3F5F7),
    };

    private TableStyles() {
    }

    /**
     * Colours the block r1..r2 x c1..c2.
     * @param header  first row is a header row (coloured, bold)
     * @param banded  alternate the fill of the body rows
     */
    public static void apply(SpreadsheetModel m, int r1, int c1, int r2, int c2, Style s, boolean header, boolean banded) {
        m.beginBatch();
        try {
            for (int r = r1; r <= r2; r++) {
                final boolean isHead = header && r == r1;
                final int body = r - r1 - (header ? 1 : 0);
                final Color fill = isHead ? s.header : (banded ? (body % 2 == 0 ? s.band1 : s.band2) : s.band2);
                for (int c = c1; c <= c2; c++) {
                    m.updateStyle(r, c, st -> {
                        st.background = fill;
                        if (isHead) {
                            st.foreground = s.headerText;
                            st.bold = true;
                        } else if (st.foreground != null && st.foreground.equals(s.headerText)) {
                            st.foreground = null;
                            st.bold = false;
                        }
                    });
                }
            }
        } finally {
            m.endBatch();
        }
    }

    /** Removes fills, bold and text colour from the block (the "None" style). */
    public static void clear(SpreadsheetModel m, int r1, int c1, int r2, int c2) {
        m.beginBatch();
        try {
            for (int r = r1; r <= r2; r++) {
                for (int c = c1; c <= c2; c++) {
                    m.updateStyle(r, c, st -> {
                        st.background = null;
                        st.foreground = null;
                        st.bold = false;
                    });
                }
            }
        } finally {
            m.endBatch();
        }
    }
}
