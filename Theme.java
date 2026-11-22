import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * Colours and fonts of the whole app. A {@link Palette} holds every colour the ribbon, the grid and the bars use;
 * {@link #set(Palette)} makes one current and {@link #install()} pushes it (and the interface font) into Swing so
 * menus, dialogs and scroll bars follow too. Code that paints itself reads the public fields below.
 */
public final class Theme {

    /** One complete look. */
    public static final class Palette {
        public final String id;
        public final String name;
        public final boolean dark;
        // brand / accent
        final Color accent, accentDark;
        // ribbon
        final Color ribbonBg, ribbonFg, ribbonIcon, ribbonDim, ribbonSep, ribbonHover, ribbonActive, ribbonActiveLine, ribbonPress;
        // ribbon tab strip
        final Color stripBg, tabFg, tabActiveBg, tabActiveFg, tabHover;
        // app chrome
        final Color appBg, barBg, fieldBg, fg, dimFg, border, statusBg;
        // grid
        final Color cellBg, cellFg, gridLine, headerBg, headerFg, headerSelBg, headerSelFg, headerHover, headerBorder;
        /** Swatch shown in the settings window. */
        final Color preview;

        Palette(String id, String name, boolean dark, Color[] c) {
            this.id = id;
            this.name = name;
            this.dark = dark;
            int i = 0;
            accent = c[i++]; accentDark = c[i++];
            ribbonBg = c[i++]; ribbonFg = c[i++]; ribbonIcon = c[i++]; ribbonDim = c[i++]; ribbonSep = c[i++];
            ribbonHover = c[i++]; ribbonActive = c[i++]; ribbonActiveLine = c[i++]; ribbonPress = c[i++];
            stripBg = c[i++]; tabFg = c[i++]; tabActiveBg = c[i++]; tabActiveFg = c[i++]; tabHover = c[i++];
            appBg = c[i++]; barBg = c[i++]; fieldBg = c[i++]; fg = c[i++]; dimFg = c[i++]; border = c[i++]; statusBg = c[i++];
            cellBg = c[i++]; cellFg = c[i++]; gridLine = c[i++]; headerBg = c[i++]; headerFg = c[i++];
            headerSelBg = c[i++]; headerSelFg = c[i++]; headerHover = c[i++]; headerBorder = c[i++];
            preview = c[i];
        }
    }

    private static Color h(int rgb) {
        return new Color(rgb);
    }

    private static Color a(int rgb, int alpha) {
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
    }

    /** Clean white ribbon with a green accent (the default). */
    public static final Palette LIGHT = new Palette("light", "Light", false, new Color[]{
            h(0x107C41), h(0x0B5D31),
            h(0xFFFFFF), h(0x2B3A32), h(0x107C41), h(0x7A8A82), h(0xE3E9E5),
            a(0x107C41, 26), a(0x107C41, 38), a(0x107C41, 110), a(0x107C41, 60),
            h(0xF3F6F4), h(0x55645C), h(0xFFFFFF), h(0x0B5D31), a(0x107C41, 20),
            h(0xF3F6F4), h(0xFFFFFF), h(0xFFFFFF), h(0x1B2B22), h(0x66756D), h(0xD5DCD8), h(0xF3F6F4),
            h(0xFFFFFF), h(0x111111), h(0xE2E6E4), h(0xF3F6F4), h(0x55645C),
            h(0xD6EDDF), h(0x0B5D31), h(0xE7F3EC), h(0xD0D7D3),
            h(0x107C41)});

    /** The original solid green ribbon. */
    public static final Palette CLASSIC = new Palette("classic", "Classic green", false, new Color[]{
            h(0x107C41), h(0x0B5D31),
            h(0x107C41), h(0xFFFFFF), h(0xFFFFFF), a(0xFFFFFF, 175), h(0x4FAE7B),
            a(0xFFFFFF, 45), a(0xFFFFFF, 70), a(0xFFFFFF, 150), a(0x000000, 60),
            h(0x0B5D31), h(0xFFFFFF), h(0x107C41), h(0xFFFFFF), a(0xFFFFFF, 40),
            h(0xF3F3F3), h(0xFFFFFF), h(0xFFFFFF), h(0x111111), h(0x666666), h(0xD0D0D0), h(0xF3F3F3),
            h(0xFFFFFF), h(0x000000), h(0xD9D9D9), h(0xF3F3F3), h(0x444444),
            h(0xC6E5D1), h(0x0B5D31), h(0xE3F1E8), h(0xBFBFBF),
            h(0x107C41)});

    public static final Palette DARK = new Palette("dark", "Dark", true, new Color[]{
            h(0x3FB97A), h(0x2E8B5A),
            h(0x25282B), h(0xE4E7E5), h(0x5FD39A), h(0x8A918D), h(0x3A3F43),
            a(0xFFFFFF, 18), a(0x5FD39A, 38), a(0x5FD39A, 130), a(0x000000, 70),
            h(0x1A1C1F), h(0xAAB1AD), h(0x25282B), h(0x5FD39A), a(0xFFFFFF, 16),
            h(0x1E2022), h(0x25282B), h(0x2E3236), h(0xE6E9E7), h(0x9AA19D), h(0x3C4146), h(0x1A1C1F),
            h(0x1F2124), h(0xE8EAE8), h(0x34383C), h(0x2A2D30), h(0xA7AEAA),
            h(0x2F5A45), h(0xCFF3E0), h(0x34393D), h(0x40454A),
            h(0x25282B)});

    public static final Palette OCEAN = new Palette("ocean", "Ocean blue", false, new Color[]{
            h(0x1565C0), h(0x0D47A1),
            h(0xEAF3FC), h(0x1E2F43), h(0x1565C0), h(0x6A7F96), h(0xCFDDEC),
            a(0x1565C0, 28), a(0x1565C0, 44), a(0x1565C0, 120), a(0x1565C0, 64),
            h(0x1565C0), h(0xFFFFFF), h(0xEAF3FC), h(0x0D47A1), a(0xFFFFFF, 40),
            h(0xF2F6FB), h(0xFFFFFF), h(0xFFFFFF), h(0x14263A), h(0x5F7389), h(0xC9D6E4), h(0xF2F6FB),
            h(0xFFFFFF), h(0x111111), h(0xDDE5EE), h(0xEEF3F9), h(0x4F6278),
            h(0xCFE2F7), h(0x0D47A1), h(0xE3EEFA), h(0xC4D2E2),
            h(0x1565C0)});

    public static final Palette PURPLE = new Palette("purple", "Purple", false, new Color[]{
            h(0x6A3FB5), h(0x4A2A86),
            h(0xF5F0FC), h(0x2C2140), h(0x6A3FB5), h(0x7F7494), h(0xDDD3EC),
            a(0x6A3FB5, 28), a(0x6A3FB5, 44), a(0x6A3FB5, 120), a(0x6A3FB5, 64),
            h(0x4A2A86), h(0xFFFFFF), h(0xF5F0FC), h(0x4A2A86), a(0xFFFFFF, 40),
            h(0xF6F3FA), h(0xFFFFFF), h(0xFFFFFF), h(0x261B38), h(0x6E6483), h(0xD6CDE4), h(0xF6F3FA),
            h(0xFFFFFF), h(0x111111), h(0xE3DDEC), h(0xF1ECF8), h(0x5E5373),
            h(0xE0D3F5), h(0x4A2A86), h(0xEBE3F7), h(0xD1C7E0),
            h(0x6A3FB5)});

    public static final Palette CONTRAST = new Palette("contrast", "High contrast", true, new Color[]{
            h(0xFFD400), h(0xB89B00),
            h(0x000000), h(0xFFFFFF), h(0xFFD400), h(0xCCCCCC), h(0xFFFFFF),
            a(0xFFD400, 50), a(0xFFD400, 80), h(0xFFD400), a(0xFFD400, 110),
            h(0x000000), h(0xFFFFFF), h(0x000000), h(0xFFD400), a(0xFFD400, 50),
            h(0x000000), h(0x000000), h(0x000000), h(0xFFFFFF), h(0xE0E0E0), h(0xFFFFFF), h(0x000000),
            h(0x000000), h(0xFFFFFF), h(0x808080), h(0x000000), h(0xFFD400),
            h(0xFFD400), h(0x000000), h(0x333300), h(0xFFFFFF),
            h(0x000000)});

    public static final List<Palette> ALL = Collections.unmodifiableList(
            java.util.Arrays.asList(LIGHT, CLASSIC, DARK, OCEAN, PURPLE, CONTRAST));

    // ---- current palette (read these when painting) ----
    private static Palette current = LIGHT;
    public static Color accent, accentDark;
    public static Color ribbonBg, ribbonFg, ribbonIcon, ribbonDim, ribbonSep, ribbonHover, ribbonActive, ribbonActiveLine, ribbonPress;
    public static Color stripBg, tabFg, tabActiveBg, tabActiveFg, tabHover;
    public static Color appBg, barBg, fieldBg, fg, dimFg, border, statusBg;
    public static Color cellBg, cellFg, gridLine, headerBg, headerFg, headerSelBg, headerSelFg, headerHover, headerBorder;

    // ---- fonts ----
    public static final String DEFAULT_UI_FAMILY = Font.SANS_SERIF;
    public static final int DEFAULT_UI_SIZE = 12;
    private static String uiFamily = DEFAULT_UI_FAMILY;
    private static int uiSize = DEFAULT_UI_SIZE;

    static {
        set(LIGHT);
    }

    // ---- cell font (the grid's default font) ----
    public static final String DEFAULT_CELL_FAMILY = Font.SANS_SERIF;
    public static final int DEFAULT_CELL_SIZE = 13;
    private static String cellFamily = DEFAULT_CELL_FAMILY;
    private static int cellSize = DEFAULT_CELL_SIZE;

    public static void setCellFont(String family, int size) {
        cellFamily = family == null || family.trim().isEmpty() ? DEFAULT_CELL_FAMILY : family;
        cellSize = Math.max(8, Math.min(40, size));
    }

    public static String cellFamily() { return cellFamily; }
    public static int cellSize() { return cellSize; }
    public static Font cellFont() { return new Font(cellFamily, Font.PLAIN, cellSize); }
    /** Row height / row-number width / column-header height that fit the chosen cell size (22 / 46 / 24 at size 13). */
    public static int cellRow() { return Math.max(20, Math.round(cellSize * 1.7f)); }
    public static int cellRowHeader() { return Math.max(46, 46 + (cellSize - 13) * 3); }
    public static int cellHeader() { return Math.max(24, cellSize + 11); }

    private Theme() {
    }

    private static boolean smooth = true;

    /** Smooth scrolling between cells and with the mouse wheel. */
    public static boolean smooth() {
        return smooth;
    }

    public static void setSmooth(boolean on) {
        smooth = on;
    }

    public static boolean dark() {
        return current.dark;
    }

    public static Palette current() {
        return current;
    }

    public static Palette byId(String id) {
        for (Palette p : ALL) if (p.id.equals(id)) return p;
        return LIGHT;
    }

    public static void set(Palette p) {
        current = p;
        accent = p.accent; accentDark = p.accentDark;
        ribbonBg = p.ribbonBg; ribbonFg = p.ribbonFg; ribbonIcon = p.ribbonIcon; ribbonDim = p.ribbonDim; ribbonSep = p.ribbonSep;
        ribbonHover = p.ribbonHover; ribbonActive = p.ribbonActive; ribbonActiveLine = p.ribbonActiveLine; ribbonPress = p.ribbonPress;
        stripBg = p.stripBg; tabFg = p.tabFg; tabActiveBg = p.tabActiveBg; tabActiveFg = p.tabActiveFg; tabHover = p.tabHover;
        appBg = p.appBg; barBg = p.barBg; fieldBg = p.fieldBg; fg = p.fg; dimFg = p.dimFg; border = p.border; statusBg = p.statusBg;
        cellBg = p.cellBg; cellFg = p.cellFg; gridLine = p.gridLine; headerBg = p.headerBg; headerFg = p.headerFg;
        headerSelBg = p.headerSelBg; headerSelFg = p.headerSelFg; headerHover = p.headerHover; headerBorder = p.headerBorder;
    }

    public static void setUiFont(String family, int size) {
        uiFamily = family == null || family.trim().isEmpty() ? DEFAULT_UI_FAMILY : family;
        uiSize = Math.max(9, Math.min(28, size));
    }

    public static String uiFamily() {
        return uiFamily;
    }

    public static int uiSize() {
        return uiSize;
    }

    /** The interface font (plain) at the chosen size plus {@code delta}. */
    public static Font ui(int style, int delta) {
        return new Font(uiFamily, style, Math.max(8, uiSize + delta));
    }

    /** Hover/pressed/selected overlay helper: the same colour with a different opacity. */
    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    // ---- push into Swing ----

    /** A Metal colour scheme built from the current palette and the chosen interface font. */
    private static final class MetalScheme extends DefaultMetalTheme {
        private final Palette p = current;
        private final FontUIResource font = new FontUIResource(ui(Font.PLAIN, 0));
        private final FontUIResource bold = new FontUIResource(ui(Font.BOLD, 0));

        @Override public String getName() { return "Spreadsheet Pro " + p.name; }
        @Override protected ColorUIResource getPrimary1() { return new ColorUIResource(p.accentDark); }
        @Override protected ColorUIResource getPrimary2() { return new ColorUIResource(p.headerSelBg); }
        @Override protected ColorUIResource getPrimary3() { return new ColorUIResource(p.headerSelBg); }
        @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(p.border); }
        @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(p.border); }
        @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(p.appBg); }
        @Override protected ColorUIResource getBlack() { return new ColorUIResource(p.fg); }
        @Override protected ColorUIResource getWhite() { return new ColorUIResource(p.fieldBg); }
        @Override public FontUIResource getControlTextFont() { return font; }
        @Override public FontUIResource getSystemTextFont() { return font; }
        @Override public FontUIResource getUserTextFont() { return font; }
        @Override public FontUIResource getMenuTextFont() { return font; }
        @Override public FontUIResource getWindowTitleFont() { return bold; }
        @Override public FontUIResource getSubTextFont() { return new FontUIResource(ui(Font.PLAIN, -1)); }
    }

    /** Installs the Metal look and feel with the current palette and font; call {@link #refresh} afterwards for open windows. */
    public static void install() {
        try {
            MetalLookAndFeel.setCurrentTheme(new MetalScheme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) {
            // keep whatever look and feel is active
        }
        Palette p = current;
        ColorUIResource sel = new ColorUIResource(p.headerSelBg);
        ColorUIResource selFg = new ColorUIResource(p.headerSelFg);
        String[] selectionBg = {"Menu.selectionBackground", "MenuItem.selectionBackground", "CheckBoxMenuItem.selectionBackground",
                "RadioButtonMenuItem.selectionBackground", "List.selectionBackground", "ComboBox.selectionBackground",
                "Table.selectionBackground", "TextField.selectionBackground", "TextArea.selectionBackground",
                "TextPane.selectionBackground", "FormattedTextField.selectionBackground", "PasswordField.selectionBackground",
                "Tree.selectionBackground", "Spinner.selectionBackground"};
        for (String k : selectionBg) UIManager.put(k, sel);
        String[] selectionFg = {"Menu.selectionForeground", "MenuItem.selectionForeground", "CheckBoxMenuItem.selectionForeground",
                "RadioButtonMenuItem.selectionForeground", "List.selectionForeground", "ComboBox.selectionForeground",
                "Table.selectionForeground", "TextField.selectionForeground", "TextArea.selectionForeground",
                "TextPane.selectionForeground", "FormattedTextField.selectionForeground", "PasswordField.selectionForeground",
                "Tree.selectionForeground"};
        for (String k : selectionFg) UIManager.put(k, selFg);
        UIManager.put("Menu.background", new ColorUIResource(p.ribbonBg));
        UIManager.put("MenuBar.background", new ColorUIResource(p.stripBg));
        UIManager.put("MenuBar.foreground", new ColorUIResource(p.tabFg));
        UIManager.put("Menu.foreground", new ColorUIResource(p.tabFg));
        UIManager.put("MenuBar.borderColor", new ColorUIResource(p.border));
        UIManager.put("PopupMenu.background", new ColorUIResource(p.barBg));
        UIManager.put("MenuItem.background", new ColorUIResource(p.barBg));
        UIManager.put("MenuItem.foreground", new ColorUIResource(p.fg));
        UIManager.put("CheckBoxMenuItem.background", new ColorUIResource(p.barBg));
        UIManager.put("CheckBoxMenuItem.foreground", new ColorUIResource(p.fg));
        UIManager.put("Separator.foreground", new ColorUIResource(p.border));
        UIManager.put("ToolTip.background", new ColorUIResource(p.dark ? p.stripBg : new Color(0xFFFBE6)));
        UIManager.put("ToolTip.foreground", new ColorUIResource(p.fg));
        UIManager.put("TabbedPane.selected", new ColorUIResource(p.barBg));
        UIManager.put("TabbedPane.background", new ColorUIResource(p.stripBg));
        UIManager.put("TabbedPane.foreground", new ColorUIResource(p.fg));
        UIManager.put("TabbedPane.contentAreaColor", new ColorUIResource(p.border));
        UIManager.put("TabbedPane.light", new ColorUIResource(p.border));
        UIManager.put("TabbedPane.highlight", new ColorUIResource(p.border));
        UIManager.put("TabbedPane.shadow", new ColorUIResource(p.border));
        UIManager.put("TabbedPane.darkShadow", new ColorUIResource(p.border));
        UIManager.put("TabbedPane.selectHighlight", new ColorUIResource(p.accent));
        UIManager.put("TabbedPane.unselectedBackground", new ColorUIResource(p.stripBg));
        UIManager.put("ScrollBar.thumb", new ColorUIResource(p.dark ? new Color(0x4A5056) : new Color(0xC9D1CD)));
        UIManager.put("ScrollBar.thumbShadow", new ColorUIResource(p.border));
        UIManager.put("ScrollBar.thumbHighlight", new ColorUIResource(p.border));
        UIManager.put("ScrollBar.track", new ColorUIResource(p.appBg));
        UIManager.put("ScrollBar.trackHighlight", new ColorUIResource(p.appBg));
        UIManager.put("Button.select", new ColorUIResource(p.headerSelBg));
        UIManager.put("Button.focus", new ColorUIResource(p.accent));
        UIManager.put("Slider.focus", new ColorUIResource(p.accent));
        UIManager.put("OptionPane.background", new ColorUIResource(p.appBg));
        UIManager.put("OptionPane.messageForeground", new ColorUIResource(p.fg));
        UIManager.put("Panel.background", new ColorUIResource(p.appBg));
        UIManager.put("Viewport.background", new ColorUIResource(p.appBg));
        UIManager.put("Table.gridColor", new ColorUIResource(p.gridLine));
        UIManager.put("TableHeader.background", new ColorUIResource(p.headerBg));
        UIManager.put("TableHeader.foreground", new ColorUIResource(p.headerFg));
        UIManager.put("TextField.inactiveForeground", new ColorUIResource(p.dimFg));
        UIManager.put("Label.disabledForeground", new ColorUIResource(p.dimFg));
        UIManager.put("Button.disabledText", new ColorUIResource(p.dimFg));
        UIManager.put("MenuItem.disabledForeground", new ColorUIResource(p.dimFg));
        UIManager.put("Menu.disabledForeground", new ColorUIResource(p.dimFg));
        UIManager.put("Button.background", new ColorUIResource(p.dark ? new Color(0x34393D) : p.barBg));
        UIManager.put("ComboBox.background", new ColorUIResource(p.fieldBg));
        UIManager.put("ComboBox.foreground", new ColorUIResource(p.fg));
        UIManager.put("ComboBox.disabledBackground", new ColorUIResource(p.appBg));
        UIManager.put("List.background", new ColorUIResource(p.fieldBg));
        UIManager.put("List.foreground", new ColorUIResource(p.fg));
        UIManager.put("Table.background", new ColorUIResource(p.cellBg));
        UIManager.put("Table.foreground", new ColorUIResource(p.cellFg));
        UIManager.put("TextField.background", new ColorUIResource(p.fieldBg));
        UIManager.put("TextField.foreground", new ColorUIResource(p.fg));
        UIManager.put("TextField.caretForeground", new ColorUIResource(p.fg));
        UIManager.put("TextArea.background", new ColorUIResource(p.fieldBg));
        UIManager.put("TextArea.foreground", new ColorUIResource(p.fg));
        UIManager.put("TextArea.caretForeground", new ColorUIResource(p.fg));
        UIManager.put("FormattedTextField.background", new ColorUIResource(p.fieldBg));
        UIManager.put("FormattedTextField.foreground", new ColorUIResource(p.fg));
        UIManager.put("PasswordField.background", new ColorUIResource(p.fieldBg));
        UIManager.put("PasswordField.foreground", new ColorUIResource(p.fg));
        UIManager.put("Spinner.background", new ColorUIResource(p.fieldBg));
        // every font in the table follows the interface font (family and size)
        FontUIResource f = new FontUIResource(ui(Font.PLAIN, 0));
        FontUIResource fb = new FontUIResource(ui(Font.BOLD, 0));
        List<Object> keys = new ArrayList<>();
        Enumeration<Object> en = UIManager.getDefaults().keys();
        while (en.hasMoreElements()) keys.add(en.nextElement());
        for (Object k : keys) {
            if (k instanceof String && ((String) k).endsWith(".font") && UIManager.get(k) instanceof Font) {
                boolean boldKey = ((String) k).startsWith("InternalFrame.title") || ((String) k).startsWith("TitledBorder");
                UIManager.put(k, boldKey ? fb : f);
            }
        }
    }

    /** Re-skins every open window after {@link #install()}. */
    public static void refresh() {
        for (Window w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
    }
}
