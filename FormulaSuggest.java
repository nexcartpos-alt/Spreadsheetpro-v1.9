import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

/**
 * Formula suggestions: while a formula is typed ("=su") a list of matching functions pops up under the field.
 * Up/Down choose, Tab or Enter insert the name and an opening bracket, Esc closes. Inside a function call
 * a one-line hint shows its arguments, e.g. VLOOKUP(lookup_value, table, column, [exact_match]).
 */
public final class FormulaSuggest {
    private static final int MAX = 8;

    private final JTextComponent field;
    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<FunctionCatalog.Entry> model = new DefaultListModel<>();
    private final JList<FunctionCatalog.Entry> list = new JList<>(model);
    private final JLabel hint = new JLabel();
    private final JPanel box = new JPanel(new BorderLayout());
    private int tokenStart = -1;
    private boolean listMode;

    private FormulaSuggest(JTextComponent field) {
        this.field = field;
        popup.setFocusable(false);
        popup.setBorder(BorderFactory.createLineBorder(Theme.border));
        list.setFocusable(false);
        list.setVisibleRowCount(MAX);
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0) {
                    list.setSelectedIndex(i);
                    accept();
                }
            }
        });
        hint.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        hint.setOpaque(true);
        box.setFocusable(false);

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { later(); }
            @Override public void removeUpdate(DocumentEvent e) { later(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        field.addCaretListener(e -> later());
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                hide();
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!popup.isVisible() || !listMode) return;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN:
                        move(1);
                        e.consume();
                        break;
                    case KeyEvent.VK_UP:
                        move(-1);
                        e.consume();
                        break;
                    case KeyEvent.VK_TAB:
                    case KeyEvent.VK_ENTER:
                        accept();
                        e.consume();
                        break;
                    case KeyEvent.VK_ESCAPE:
                        hide();
                        e.consume();
                        break;
                    default:
                }
            }
        });
    }

    /** Adds suggestions to a text field or editor. */
    public static void attach(JTextComponent field) {
        new FormulaSuggest(field);
    }

    private void later() {
        SwingUtilities.invokeLater(this::update);
    }

    private void move(int d) {
        int n = model.getSize();
        if (n == 0) return;
        list.setSelectedIndex((list.getSelectedIndex() + d + n) % n);
        list.ensureIndexIsVisible(list.getSelectedIndex());
    }

    private void hide() {
        popup.setVisible(false);
        field.setFocusTraversalKeysEnabled(true);
        tokenStart = -1;
    }

    private static boolean nameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    private void update() {
        if (!field.isShowing() || !field.hasFocus()) {
            hide();
            return;
        }
        String text = field.getText();
        int caret = Math.min(field.getCaretPosition(), text.length());
        if (!text.startsWith("=")) {
            hide();
            return;
        }
        // token being typed
        int s = caret;
        while (s > 0 && nameChar(text.charAt(s - 1))) s--;
        String token = text.substring(s, caret);
        boolean afterOperator = s > 0 && "=(,+-*/^&<>; :".indexOf(text.charAt(s - 1)) >= 0;
        if (!token.isEmpty() && Character.isLetter(token.charAt(0)) && afterOperator && !inQuotes(text, s)) {
            List<FunctionCatalog.Entry> found = matches(token);
            boolean exactOnly = found.size() == 1 && found.get(0).name.equalsIgnoreCase(token) && caret < text.length()
                    && text.charAt(caret) == '(';
            if (!found.isEmpty() && !exactOnly) {
                showList(found, s);
                return;
            }
        }
        // inside a call: show the arguments
        FunctionCatalog.Entry e = enclosingCall(text, caret);
        if (e != null) {
            showHint(e);
            return;
        }
        hide();
    }

    private static boolean inQuotes(String text, int pos) {
        boolean in = false;
        for (int i = 0; i < pos; i++) if (text.charAt(i) == '"') in = !in;
        return in;
    }

    private static List<FunctionCatalog.Entry> matches(String token) {
        String up = token.toUpperCase(Locale.ROOT);
        List<FunctionCatalog.Entry> starts = new ArrayList<>();
        List<FunctionCatalog.Entry> contains = new ArrayList<>();
        for (FunctionCatalog.Entry e : FunctionCatalog.all()) {
            if (e.name.startsWith(up)) starts.add(e);
            else if (up.length() >= 2 && e.name.contains(up)) contains.add(e);
        }
        starts.sort((a, b) -> a.name.length() != b.name.length() ? a.name.length() - b.name.length() : a.name.compareTo(b.name));
        contains.sort((a, b) -> a.name.compareTo(b.name));
        starts.addAll(contains);
        return starts.size() > MAX ? new ArrayList<>(starts.subList(0, MAX)) : starts;
    }

    private static FunctionCatalog.Entry enclosingCall(String text, int caret) {
        int depth = 0;
        boolean in = false;
        for (int i = caret - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '"') in = !in;
            if (in) continue;
            if (c == ')') depth++;
            else if (c == '(') {
                if (depth == 0) {
                    int e = i;
                    int s = e;
                    while (s > 0 && nameChar(text.charAt(s - 1))) s--;
                    if (s == e) return null;
                    return FunctionCatalog.find(text.substring(s, e));
                }
                depth--;
            }
        }
        return null;
    }

    private void showList(List<FunctionCatalog.Entry> found, int start) {
        listMode = true;
        tokenStart = start;
        Object keep = list.getSelectedValue();
        model.clear();
        for (FunctionCatalog.Entry e : found) model.addElement(e);
        int idx = keep == null ? 0 : Math.max(0, found.indexOf(keep));
        list.setSelectedIndex(idx);
        list.setBackground(Theme.fieldBg);
        list.setForeground(Theme.fg);
        list.setSelectionBackground(Theme.headerSelBg);
        list.setSelectionForeground(Theme.headerSelFg);
        list.setFont(Theme.ui(Font.PLAIN, 0));
        list.setPrototypeCellValue(null);
        box.removeAll();
        box.setBackground(Theme.fieldBg);
        box.add(list, BorderLayout.CENTER);
        JLabel foot = new JLabel(I18n.t("Tab") + " / Enter");
        foot.setFont(Theme.ui(Font.PLAIN, -2));
        foot.setForeground(Theme.dimFg);
        foot.setBorder(BorderFactory.createEmptyBorder(2, 8, 3, 8));
        box.add(foot, BorderLayout.SOUTH);
        popup.removeAll();
        popup.setBackground(Theme.fieldBg);
        popup.setBorder(BorderFactory.createLineBorder(Theme.border));
        popup.add(box);
        field.setFocusTraversalKeysEnabled(false); // Tab accepts the suggestion
        place(start);
    }

    private void showHint(FunctionCatalog.Entry e) {
        listMode = false;
        field.setFocusTraversalKeysEnabled(true);
        hint.setText(e.syntax + "  —  " + e.description);
        hint.setBackground(Theme.dark() ? Theme.stripBg : new Color(0xFFFBE6));
        hint.setForeground(Theme.fg);
        hint.setFont(Theme.ui(Font.PLAIN, -1));
        popup.removeAll();
        popup.setBorder(BorderFactory.createLineBorder(Theme.border));
        popup.add(hint);
        place(field.getCaretPosition());
    }

    private void place(int pos) {
        try {
            Rectangle2D r = field.modelToView2D(Math.max(0, pos));
            int x = r == null ? 0 : (int) r.getX();
            popup.pack();
            if (popup.isVisible()) {
                popup.setVisible(false); // re-show at the new place and size
            }
            popup.show(field, x, field.getHeight() + 2);
        } catch (BadLocationException ex) {
            hide();
        }
    }

    private void accept() {
        FunctionCatalog.Entry e = list.getSelectedValue();
        if (e == null || tokenStart < 0) {
            hide();
            return;
        }
        try {
            int caret = field.getCaretPosition();
            String after = field.getText().substring(caret);
            String insert = e.name + (after.startsWith("(") ? "" : "(");
            field.getDocument().remove(tokenStart, caret - tokenStart);
            field.getDocument().insertString(tokenStart, insert, null);
            field.setCaretPosition(tokenStart + e.name.length() + 1);
        } catch (BadLocationException ex) {
            // leave the text as it is
        }
        hide();
    }

    private static final class Renderer extends JPanel implements ListCellRenderer<FunctionCatalog.Entry> {
        private final JLabel name = new JLabel();
        private final JLabel desc = new JLabel();

        Renderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            add(name, BorderLayout.WEST);
            add(desc, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends FunctionCatalog.Entry> l, FunctionCatalog.Entry e,
                                                      int i, boolean sel, boolean focus) {
            name.setText(e.name);
            name.setFont(Theme.ui(Font.BOLD, 0));
            String d = e.description;
            if (d.length() > 64) d = d.substring(0, 61) + "...";
            desc.setText(d);
            desc.setFont(Theme.ui(Font.PLAIN, -2));
            setBackground(sel ? Theme.headerSelBg : Theme.fieldBg);
            name.setForeground(sel ? Theme.headerSelFg : Theme.accent);
            desc.setForeground(sel ? Theme.headerSelFg : Theme.dimFg);
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(380, d.width), d.height);
        }
    }
}
