import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.util.HashMap;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JTextField;

/**
 * Language support. English strings are the keys; {@link #t(String)} returns the translation for the chosen
 * language (or the English text when there is none). {@link #apply(Component)} translates a whole window in place
 * (button text, tool tips, menus, labels) and remembers the original English so switching languages is lossless.
 */
public final class I18n {
    /** Language code, native name, translations (null = English), right-to-left. */
    public static final class Lang {
        public final String code, name;
        final String[] table;
        public final boolean rtl;

        Lang(String code, String name, String[] table, boolean rtl) {
            this.code = code;
            this.name = name;
            this.table = table;
            this.rtl = rtl;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final Lang[] LANGS = {
            new Lang("en", "English", null, false),
            new Lang("ur", "اردو", LangUr.T, true),
            new Lang("ar", "العربية", LangAr.T, true),
            new Lang("es", "Español", LangEs.T, false),
            new Lang("fr", "Français", LangFr.T, false)};

    private static Lang current = LANGS[0];
    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();
    private static final String P_TEXT = "i18n.text", P_TEXT_OUT = "i18n.textOut", P_TIP = "i18n.tip", P_TIP_OUT = "i18n.tipOut";

    private I18n() {
    }

    public static Lang current() {
        return current;
    }

    public static String code() {
        return current.code;
    }

    public static boolean isRtl() {
        return current.rtl;
    }

    public static void setLanguage(String code) {
        for (Lang l : LANGS) if (l.code.equals(code)) current = l;
    }

    public static String t(String english) {
        if (english == null || current.table == null) return english;
        Map<String, String> m = CACHE.get(current.code);
        if (m == null) {
            m = new HashMap<>();
            for (int i = 0; i < LangEn.T.length && i < current.table.length; i++) m.put(LangEn.T[i], current.table[i]);
            CACHE.put(current.code, m);
        }
        String r = m.get(english);
        if (r == null && english.endsWith(" \u25BE")) { // drop-down buttons: "Insert \u25BE"
            String base = m.get(english.substring(0, english.length() - 2));
            if (base != null) return base + " \u25BE";
        }
        return r != null ? r : english;
    }

    /** Translates {@code root} and everything inside it (and the menus hanging off menu bars / menus). */
    public static void apply(Component root) {
        walk(root);
    }

    /** Flips layout direction for right-to-left languages (call on the chrome, not on the grid). */
    public static void orient(Component root, boolean rtl) {
        root.applyComponentOrientation(rtl ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
    }

    private static void walk(Component c) {
        if (c instanceof JTextField) return; // user text
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            if (c instanceof AbstractButton) {
                AbstractButton b = (AbstractButton) c;
                String cur = b.getText();
                if (cur != null && !cur.isEmpty()) {
                    b.setText(translate(jc, P_TEXT, P_TEXT_OUT, cur));
                }
            } else if (c instanceof JLabel) {
                JLabel l = (JLabel) c;
                String cur = l.getText();
                if (cur != null && !cur.isEmpty() && !cur.startsWith("<")) {
                    l.setText(translate(jc, P_TEXT, P_TEXT_OUT, cur));
                }
            }
            String tip = jc.getToolTipText();
            if (tip != null && !tip.isEmpty()) jc.setToolTipText(translate(jc, P_TIP, P_TIP_OUT, tip));
        }
        if (c instanceof JMenu) {
            for (Component m : ((JMenu) c).getMenuComponents()) walk(m);
        } else if (c instanceof JMenuBar) {
            for (Component m : ((JMenuBar) c).getComponents()) walk(m);
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) walk(k);
        }
    }

    /**
     * Keeps the English original in a client property. If the code changed the text since we last wrote it, the
     * new text is the new original.
     */
    private static String translate(JComponent jc, String origKey, String outKey, String cur) {
        Object orig = jc.getClientProperty(origKey);
        Object out = jc.getClientProperty(outKey);
        String english;
        if (orig == null || out == null || !cur.equals(out)) english = cur;
        else english = (String) orig;
        String shown = t(english);
        jc.putClientProperty(origKey, english);
        jc.putClientProperty(outKey, shown);
        return shown;
    }
}
