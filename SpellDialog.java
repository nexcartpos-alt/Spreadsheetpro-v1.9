import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Walks through the misspelt words of a sheet, like Excel's Spelling dialog. */
public class SpellDialog extends JDialog {
    private final SpreadsheetModel model;
    private final Consumer<int[]> goTo;      // select a cell
    private final Runnable changed;          // tell the app something was edited
    private final List<int[]> cells = new ArrayList<>();
    private int ci = -1, pos;
    private int[] cur;                       // current cell {row,col}
    private SpellChecker.Token tok;

    private final JLabel where = new JLabel(" ");
    private final JTextArea context = new JTextArea(3, 30);
    private final JTextField replacement = new JTextField();
    private final DefaultListModel<String> sugModel = new DefaultListModel<>();
    private final JList<String> sugList = new JList<>(sugModel);
    private int fixed;

    public SpellDialog(Frame owner, SpreadsheetModel model, Consumer<int[]> goTo, Runnable changed) {
        super(owner, I18n.t("Spelling"), false);
        this.model = model;
        this.goTo = goTo;
        this.changed = changed;
        SpellChecker.resetIgnored();
        for (int[] rc : model.storedCells()) {
            String in = model.getInput(rc[0], rc[1]);
            if (in != null && !in.startsWith("=") && !model.isNumeric(rc[0], rc[1]) && !SpellChecker.misspelled(in).isEmpty()) cells.add(rc);
        }
        build();
        pack();
        setMinimumSize(new Dimension(480, 360));
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(this::next);
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(where, BorderLayout.NORTH);
        context.setEditable(false);
        context.setLineWrap(true);
        context.setWrapStyleWord(true);
        top.add(new JScrollPane(context), BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        JPanel mid = new JPanel(new BorderLayout(4, 4));
        JPanel rep = new JPanel(new BorderLayout(6, 0));
        rep.add(new JLabel(I18n.t("Change to:")), BorderLayout.WEST);
        rep.add(replacement, BorderLayout.CENTER);
        mid.add(rep, BorderLayout.NORTH);
        sugList.setVisibleRowCount(6);
        sugList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && sugList.getSelectedValue() != null) replacement.setText(sugList.getSelectedValue());
        });
        mid.add(new JScrollPane(sugList), BorderLayout.CENTER);
        root.add(mid, BorderLayout.CENTER);

        JPanel btns = new JPanel(new GridLayout(0, 1, 4, 4));
        btns.add(btn("Ignore", () -> { pos = tok.end; next(); }));
        btns.add(btn("Ignore All", () -> { SpellChecker.ignoreAll(tok.word); pos = tok.end; next(); }));
        btns.add(btn("Add to Dictionary", () -> { SpellChecker.addToDictionary(tok.word); pos = tok.end; next(); }));
        btns.add(btn("Change", () -> change(false)));
        btns.add(btn("Change All", () -> change(true)));
        btns.add(btn("Close", this::dispose));
        JPanel east = new JPanel(new BorderLayout());
        east.add(btns, BorderLayout.NORTH);
        root.add(east, BorderLayout.EAST);
        setContentPane(root);
    }

    private JButton btn(String text, Runnable r) {
        JButton b = new JButton(I18n.t(text));
        b.addActionListener(e -> r.run());
        return b;
    }

    private void change(boolean all) {
        String rep = replacement.getText();
        if (tok == null || rep.isEmpty()) return;
        String bad = tok.word;
        replaceInCurrent(rep);
        if (all) {
            for (int k = ci; k < cells.size(); k++) {
                int[] rc = cells.get(k);
                String text = model.getInput(rc[0], rc[1]);
                if (text == null) continue;
                String nt = text;
                List<SpellChecker.Token> bads = SpellChecker.misspelled(text);
                for (int j = bads.size() - 1; j >= 0; j--) {
                    SpellChecker.Token t = bads.get(j);
                    if (t.word.equals(bad) && !(rc[0] == cur[0] && rc[1] == cur[1] && t.start < pos)) {
                        nt = nt.substring(0, t.start) + rep + nt.substring(t.end);
                    }
                }
                if (!nt.equals(text)) {
                    model.setInput(rc[0], rc[1], nt);
                    fixed++;
                    if (rc[0] == cur[0] && rc[1] == cur[1]) {
                        // later occurrences in the current cell were replaced: scan again from the cursor
                    }
                }
            }
            changed.run();
        }
        next();
    }

    private void replaceInCurrent(String rep) {
        String text = model.getInput(cur[0], cur[1]);
        String nt = text.substring(0, tok.start) + rep + text.substring(tok.end);
        model.setInput(cur[0], cur[1], nt);
        pos = tok.start + rep.length();
        fixed++;
        changed.run();
    }

    private void next() {
        while (true) {
            if (cur != null) {
                String text = model.getInput(cur[0], cur[1]);
                for (SpellChecker.Token t : SpellChecker.misspelled(text)) {
                    if (t.start >= pos) { show(text, t); return; }
                }
            }
            ci++;
            pos = 0;
            if (ci >= cells.size()) { finish(); return; }
            cur = cells.get(ci);
        }
    }

    private void show(String text, SpellChecker.Token t) {
        tok = t;
        goTo.accept(cur);
        where.setText(I18n.t("Not in dictionary") + " - " + FormulaEngine.cellRef(cur[0], cur[1]));
        context.setText(text);
        try {
            context.getHighlighter().removeAllHighlights();
            context.getHighlighter().addHighlight(t.start, t.end,
                    new DefaultHighlighter.DefaultHighlightPainter(new Color(0xFF6B6B)));
        } catch (Exception ignored) {
        }
        sugModel.clear();
        for (String s : SpellChecker.suggest(t.word, 8)) sugModel.addElement(s);
        if (!sugModel.isEmpty()) sugList.setSelectedIndex(0);
        else replacement.setText(t.word);
    }

    private void finish() {
        tok = null;
        dispose();
        JOptionPane.showMessageDialog(getOwner(), fixed == 0 && cells.isEmpty()
                        ? I18n.t("No spelling mistakes found.")
                        : I18n.t("Spell check complete.") + " (" + fixed + ")",
                I18n.t("Spelling"), JOptionPane.INFORMATION_MESSAGE);
    }
}
