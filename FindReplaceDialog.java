import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Find & Replace (Ctrl+F / Ctrl+H): this sheet or the whole workbook, with a clickable "Find All" list. */
public class FindReplaceDialog extends JDialog {

    /** What the dialog needs from the application. */
    public interface Host {
        int sheetCount();
        String sheetName(int i);
        SpreadsheetModel model(int i);
        int currentSheet();
        int[] activeCell();
        void go(int sheet, int row, int col);
        void changed();
        void status(String text);
    }

    private final Host host;
    private final JTextField findField = new JTextField(22);
    private final JTextField replField = new JTextField(22);
    private final JCheckBox matchCase = new JCheckBox(I18n.t("Match case"));
    private final JCheckBox wholeCell = new JCheckBox(I18n.t("Match entire cell contents"));
    private final JComboBox<String> within = new JComboBox<>(new String[]{I18n.t("Sheet"), I18n.t("Workbook")});
    private final JComboBox<String> lookIn = new JComboBox<>(new String[]{I18n.t("Values"), I18n.t("Formulas")});
    private final JLabel info = new JLabel(" ");
    private final DefaultTableModel results = new DefaultTableModel(
            new Object[]{I18n.t("Sheet"), I18n.t("Cell"), I18n.t("Value"), I18n.t("Formula")}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable resTable = new JTable(results);
    private final List<int[]> hits = new ArrayList<>(); // {sheet,row,col} for the result rows
    private final JPanel resultPanel = new JPanel(new BorderLayout());
    private boolean expanded;

    public FindReplaceDialog(Frame owner, Host host, boolean replaceFocus) {
        super(owner, I18n.t("Find and Replace"), false);
        this.host = host;
        build();
        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        (replaceFocus ? replField : findField).requestFocusInWindow();
    }

    /** Pre-fills the "find what" box (the selected cell's text). */
    public void setFindText(String t) {
        if (t != null && !t.isEmpty() && t.length() < 200 && !t.contains("\n")) {
            findField.setText(t);
            findField.selectAll();
        }
    }

    public void focusFind() {
        findField.requestFocusInWindow();
        findField.selectAll();
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0; form.add(new JLabel(I18n.t("Find what:")), g);
        g.gridx = 1; g.gridwidth = 3; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; form.add(findField, g);
        g.gridwidth = 1; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        g.gridx = 0; g.gridy = 1; form.add(new JLabel(I18n.t("Replace with:")), g);
        g.gridx = 1; g.gridwidth = 3; g.fill = GridBagConstraints.HORIZONTAL; form.add(replField, g);
        g.gridwidth = 1; g.fill = GridBagConstraints.NONE;
        g.gridx = 0; g.gridy = 2; form.add(new JLabel(I18n.t("Within:")), g);
        g.gridx = 1; form.add(within, g);
        g.gridx = 2; form.add(new JLabel(I18n.t("Look in:")), g);
        g.gridx = 3; form.add(lookIn, g);
        g.gridx = 0; g.gridy = 3; g.gridwidth = 2; form.add(matchCase, g);
        g.gridx = 2; g.gridwidth = 2; form.add(wholeCell, g);
        g.gridx = 0; g.gridy = 4; g.gridwidth = 4; form.add(info, g);
        root.add(form, BorderLayout.CENTER);

        JPanel btns = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        btns.add(btn("Find Next", this::findNext));
        btns.add(btn("Find All", this::findAll));
        btns.add(btn("Replace", this::replaceOne));
        btns.add(btn("Replace All", this::replaceAll));
        btns.add(btn("Close", this::dispose));
        JPanel east = new JPanel(new BorderLayout());
        east.add(btns, BorderLayout.NORTH);
        root.add(east, BorderLayout.EAST);

        resTable.setFillsViewportHeight(true);
        resTable.setAutoCreateRowSorter(false);
        resTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int r = resTable.rowAtPoint(e.getPoint());
                if (r >= 0 && r < hits.size() && e.getClickCount() >= 1) {
                    int[] h = hits.get(r);
                    host.go(h[0], h[1], h[2]);
                }
            }
        });
        JScrollPane sp = new JScrollPane(resTable);
        sp.setPreferredSize(new Dimension(520, 170));
        resultPanel.add(sp, BorderLayout.CENTER);
        resultPanel.setVisible(false);
        root.add(resultPanel, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(null);
        findField.addActionListener(e -> findNext());
    }

    private JButton btn(String text, Runnable r) {
        JButton b = new JButton(I18n.t(text));
        b.addActionListener(e -> r.run());
        return b;
    }

    private FindEngine engine() {
        if (findField.getText().isEmpty()) {
            info.setText(I18n.t("Type something to find."));
            return null;
        }
        return new FindEngine(findField.getText(), matchCase.isSelected(), wholeCell.isSelected(), lookIn.getSelectedIndex() == 1);
    }

    /** Every hit in the chosen scope, in sheet order then row-major, as {sheet,row,col}. */
    private List<int[]> collect(FindEngine fe) {
        List<int[]> out = new ArrayList<>();
        int from = within.getSelectedIndex() == 1 ? 0 : host.currentSheet();
        int to = within.getSelectedIndex() == 1 ? host.sheetCount() - 1 : host.currentSheet();
        for (int s = from; s <= to; s++) {
            for (int[] rc : fe.findAll(host.model(s))) out.add(new int[]{s, rc[0], rc[1]});
        }
        return out;
    }

    private static int compare(int[] a, int s, int r, int c) {
        if (a[0] != s) return Integer.compare(a[0], s);
        if (a[1] != r) return Integer.compare(a[1], r);
        return Integer.compare(a[2], c);
    }

    private int[] nextHit(List<int[]> all) {
        int[] cur = host.activeCell();
        int s = host.currentSheet();
        for (int[] h : all) if (compare(h, s, cur[0], cur[1]) > 0) return h;
        return all.isEmpty() ? null : all.get(0);
    }

    private void findNext() {
        FindEngine fe = engine();
        if (fe == null) return;
        List<int[]> all = collect(fe);
        int[] h = nextHit(all);
        if (h == null) {
            info.setText(I18n.t("Nothing found."));
            return;
        }
        host.go(h[0], h[1], h[2]);
        info.setText(I18n.t("Found in") + " " + host.sheetName(h[0]) + "!" + FormulaEngine.cellRef(h[1], h[2]));
    }

    private void findAll() {
        FindEngine fe = engine();
        if (fe == null) return;
        List<int[]> all = collect(fe);
        hits.clear();
        results.setRowCount(0);
        int shown = 0;
        for (int[] h : all) {
            if (shown++ >= 5000) break;
            SpreadsheetModel m = host.model(h[0]);
            hits.add(h);
            String in = m.getInput(h[1], h[2]);
            results.addRow(new Object[]{host.sheetName(h[0]), FormulaEngine.cellRef(h[1], h[2]),
                    String.valueOf(m.getValueAt(h[1], h[2])), in.startsWith("=") ? in : ""});
        }
        info.setText(all.size() + " " + I18n.t(all.size() == 1 ? "cell found" : "cells found")
                + (all.size() > 5000 ? " (" + I18n.t("showing the first 5000") + ")" : ""));
        if (!resultPanel.isVisible()) {
            resultPanel.setVisible(true);
            pack();
        }
    }

    private void replaceOne() {
        FindEngine fe = engine();
        if (fe == null) return;
        int[] cur = host.activeCell();
        SpreadsheetModel m = host.model(host.currentSheet());
        if (fe.cellMatches(m, cur[0], cur[1])) {
            if (!fe.replaceable(m, cur[0], cur[1])) {
                info.setText(I18n.t("This cell cannot be changed (protected, read-only or a formula)."));
            } else {
                String nt = fe.replaced(m.getInput(cur[0], cur[1]), replField.getText());
                if (nt != null) {
                    m.setInput(cur[0], cur[1], nt);
                    host.changed();
                }
            }
        }
        findNext();
    }

    private void replaceAll() {
        FindEngine fe = engine();
        if (fe == null) return;
        int changedCells = 0;
        int skipped = 0;
        int from = within.getSelectedIndex() == 1 ? 0 : host.currentSheet();
        int to = within.getSelectedIndex() == 1 ? host.sheetCount() - 1 : host.currentSheet();
        for (int s = from; s <= to; s++) {
            SpreadsheetModel m = host.model(s);
            for (int[] rc : fe.findAll(m)) {
                if (!fe.replaceable(m, rc[0], rc[1])) {
                    skipped++;
                    continue;
                }
                String nt = fe.replaced(m.getInput(rc[0], rc[1]), replField.getText());
                if (nt != null) {
                    m.setInput(rc[0], rc[1], nt);
                    changedCells++;
                }
            }
        }
        if (changedCells > 0) host.changed();
        String msg = changedCells + " " + I18n.t(changedCells == 1 ? "replacement made" : "replacements made");
        if (skipped > 0) msg += " - " + skipped + " " + I18n.t("skipped (protected or formula results)");
        info.setText(msg);
        host.status(msg);
    }
}
