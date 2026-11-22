import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/** "Appearance and language": language, theme, interface font/size and cell font/size. Changes apply live. */
public class SettingsDialog extends JDialog {
    private final Runnable onChange;
    private final List<ThemeCard> cards = new ArrayList<>();
    private final JComboBox<I18n.Lang> lang = new JComboBox<>(I18n.LANGS);
    private final JComboBox<String> uiFamily = new JComboBox<>();
    private final JSpinner uiSize = new JSpinner(new SpinnerNumberModel(Theme.uiSize(), 9, 28, 1));
    private final JComboBox<String> cellFamily = new JComboBox<>();
    private final JSpinner cellSize = new JSpinner(new SpinnerNumberModel(Theme.cellSize(), 8, 40, 1));
    private final javax.swing.JCheckBox smooth = new javax.swing.JCheckBox("Smooth scrolling between cells");
    private final JLabel preview = new JLabel();
    private final JPanel content = new JPanel(new GridBagLayout());
    private boolean quiet;

    public SettingsDialog(java.awt.Frame owner, Runnable onChange) {
        super(owner, "Appearance", false);
        this.onChange = onChange;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        List<String> fams = families();
        for (String f : fams) {
            uiFamily.addItem(f);
            cellFamily.addItem(f);
        }
        quiet = true;
        lang.setSelectedItem(I18n.current());
        uiFamily.setSelectedItem(match(fams, Theme.uiFamily()));
        cellFamily.setSelectedItem(match(fams, Theme.cellFamily()));
        quiet = false;

        content.setBorder(new EmptyBorder(14, 18, 10, 18));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 4, 5, 4);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        row = heading(g, row, "Language");
        addRow(g, row++, "Language", lang, null);
        row = heading(g, row, "Theme");
        JPanel themes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        themes.setOpaque(false);
        for (Theme.Palette p : Theme.ALL) {
            ThemeCard c = new ThemeCard(p);
            cards.add(c);
            themes.add(c);
        }
        g.gridx = 0;
        g.gridy = row++;
        g.gridwidth = 3;
        content.add(themes, g);
        g.gridwidth = 1;

        row = heading(g, row, "Whole app");
        addRow(g, row++, "Interface font", uiFamily, null);
        addRow(g, row++, "Interface text size", uiSize, null);
        row = heading(g, row, "Spreadsheet cells");
        smooth.setSelected(Theme.smooth());
        smooth.setOpaque(false);
        smooth.addActionListener(e -> {
            if (quiet) return;
            Theme.setSmooth(smooth.isSelected());
            changed();
        });
        g.gridx = 0;
        g.gridy = row++;
        g.gridwidth = 3;
        content.add(smooth, g);
        g.gridwidth = 1;
        addRow(g, row++, "Cell font", cellFamily, null);
        addRow(g, row++, "Cell text size", cellSize, null);

        preview.setText("Spreadsheet text looks like this: 1,234.50");
        preview.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border),
                new EmptyBorder(8, 10, 8, 10)));
        preview.setOpaque(true);
        g.gridx = 0;
        g.gridy = row++;
        g.gridwidth = 3;
        g.insets = new Insets(10, 4, 4, 4);
        content.add(preview, g);
        JLabel note = new JLabel("Menus, ribbon and tooltips are translated; some messages may stay in English.");
        note.setForeground(Theme.dimFg);
        note.setName("note");
        g.gridy = row++;
        g.insets = new Insets(2, 4, 2, 4);
        content.add(note, g);
        JLabel live = new JLabel("Changes apply instantly and are remembered.");
        live.setForeground(Theme.dimFg);
        g.gridy = row++;
        content.add(live, g);

        JButton reset = new JButton("Reset to defaults");
        JButton close = new JButton("Close");
        reset.addActionListener(e -> resetDefaults());
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(reset);
        buttons.add(close);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        lang.addActionListener(e -> {
            if (quiet) return;
            I18n.setLanguage(((I18n.Lang) lang.getSelectedItem()).code);
            changed();
        });
        uiFamily.addActionListener(e -> {
            if (!quiet) applyFonts();
        });
        uiSize.addChangeListener(e -> {
            if (!quiet) applyFonts();
        });
        cellFamily.addActionListener(e -> {
            if (!quiet) applyFonts();
        });
        cellSize.addChangeListener(e -> {
            if (!quiet) applyFonts();
        });
        refreshLook();
        pack();
        setMinimumSize(new Dimension(520, getHeight()));
        setLocationRelativeTo(owner);
    }

    private static List<String> families() {
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (String f : new String[]{Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED}) {
            out.add(f);
            seen.add(f);
        }
        String[] all = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        java.util.Arrays.sort(all);
        for (String f : all) if (seen.add(f)) out.add(f);
        return out;
    }

    private static String match(List<String> fams, String f) {
        for (String x : fams) if (x.equalsIgnoreCase(f)) return x;
        return Font.SANS_SERIF;
    }

    private int heading(GridBagConstraints g, int row, String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.ui(Font.BOLD, 1));
        l.setForeground(Theme.accent);
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 3;
        g.insets = new Insets(10, 4, 2, 4);
        content.add(l, g);
        g.gridwidth = 1;
        g.insets = new Insets(5, 4, 5, 4);
        return row + 1;
    }

    private void addRow(GridBagConstraints g, int row, String label, JComponent field, JComponent extra) {
        g.gridy = row;
        g.gridx = 0;
        g.weightx = 0;
        content.add(new JLabel(label), g);
        g.gridx = 1;
        g.weightx = 1;
        g.gridwidth = 2;
        content.add(field, g);
        g.gridwidth = 1;
        g.weightx = 0;
    }

    private void applyFonts() {
        Theme.setUiFont((String) uiFamily.getSelectedItem(), ((Number) uiSize.getValue()).intValue());
        Theme.setCellFont((String) cellFamily.getSelectedItem(), ((Number) cellSize.getValue()).intValue());
        changed();
    }

    private void resetDefaults() {
        quiet = true;
        Theme.set(Theme.LIGHT);
        Theme.setUiFont(Theme.DEFAULT_UI_FAMILY, Theme.DEFAULT_UI_SIZE);
        Theme.setCellFont(Theme.DEFAULT_CELL_FAMILY, Theme.DEFAULT_CELL_SIZE);
        I18n.setLanguage("en");
        Theme.setSmooth(true);
        smooth.setSelected(true);
        lang.setSelectedItem(I18n.current());
        uiFamily.setSelectedItem(Theme.DEFAULT_UI_FAMILY);
        uiSize.setValue(Theme.DEFAULT_UI_SIZE);
        cellFamily.setSelectedItem(Theme.DEFAULT_CELL_FAMILY);
        cellSize.setValue(Theme.DEFAULT_CELL_SIZE);
        quiet = false;
        changed();
    }

    private void changed() {
        onChange.run();
        refreshLook();
    }

    /** Re-reads colours, fonts, language and direction for this window. */
    private void refreshLook() {
        setTitle(I18n.t("Appearance"));
        I18n.apply(getContentPane());
        I18n.orient(getContentPane(), I18n.isRtl());
        preview.setFont(Theme.cellFont());
        preview.setBackground(Theme.cellBg);
        preview.setForeground(Theme.cellFg);
        preview.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border),
                new EmptyBorder(8, 10, 8, 10)));
        getContentPane().setBackground(Theme.appBg);
        for (ThemeCard c : cards) c.repaint();
        for (Component c : getContentPane().getComponents()) if (c instanceof JComponent) ((JComponent) c).revalidate();
        for (Component c : getRootPane().getComponents()) c.repaint();
        revalidate();
        pack();
    }

    /** A small clickable preview of one theme. */
    private final class ThemeCard extends JComponent {
        private final Theme.Palette p;

        ThemeCard(Theme.Palette p) {
            this.p = p;
            setPreferredSize(new Dimension(92, 66));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(p.name);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Theme.set(p);
                    changed();
                }
            });
        }

        @Override
        public String getToolTipText() {
            return I18n.t(p.name);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean sel = Theme.current() == p;
            g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h - 18, 10, 10));
            g.setColor(p.stripBg);
            g.fillRect(0, 0, w, 10);
            g.setColor(p.ribbonBg);
            g.fillRect(0, 10, w, 16);
            g.setColor(p.cellBg);
            g.fillRect(0, 26, w, h);
            g.setColor(p.gridLine);
            for (int y = 34; y < h - 18; y += 8) g.drawLine(0, y, w, y);
            g.drawLine(w / 2, 26, w / 2, h);
            g.setColor(p.accent);
            g.fillRoundRect(8, 13, 12, 8, 4, 4);
            g.fillRect(w / 2 + 4, 36, 20, 6);
            g.setClip(null);
            g.setColor(sel ? Theme.accent : Theme.border);
            g.setStroke(new BasicStroke(sel ? 2.5f : 1f));
            g.drawRoundRect(1, 1, w - 3, h - 20, 10, 10);
            g.setFont(Theme.ui(sel ? Font.BOLD : Font.PLAIN, -2));
            g.setColor(Theme.fg);
            String n = I18n.t(p.name);
            int tw = g.getFontMetrics().stringWidth(n);
            g.drawString(n, Math.max(0, (w - tw) / 2), h - 4);
            g.dispose();
        }
    }
}
