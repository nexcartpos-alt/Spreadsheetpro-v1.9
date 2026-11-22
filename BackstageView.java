import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * The full-window "File" screen (like Excel's Backstage): a coloured sidebar with Info, New, Open, Save,
 * Save As, Print, Share, Export, Close, Account and Options, and a page for each.
 */
public class BackstageView extends JPanel {

    /** What the app provides to the screen. */
    public interface Host {
        String fileName();
        File file();
        int sheetCount();
        boolean encrypted();
        boolean readOnly();
        boolean dirty();
        void open(File f);
        /** Runs a command by id: new, open, openxlsx, save, saveas, print, preview, pagesetup, pdf, xlsx, csv, importcsv,
         * history, saveversion, recover, password, update, options, autosave, getdata, close, exit. */
        void act(String id);
        String version();
    }

    private static final String[] PAGES = {"Info", "New", "Open", "Save", "Save As", "Print", "Share", "Export", "Close", "-", "Account", "Options"};

    private final Host host;
    private final JPanel sidebar = new JPanel();
    private final JPanel content = new JPanel(new BorderLayout());
    private final java.util.Map<String, SideItem> items = new java.util.LinkedHashMap<>();
    private String selected = "Info";

    public BackstageView(Host host) {
        super(new BorderLayout());
        this.host = host;
        setFocusable(true);
        setOpaque(true);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(190, 10));
        sidebar.setOpaque(true);
        sidebar.setBackground(Theme.dark() ? Theme.accentDark : Theme.accent);
        sidebar.add(new BackButton());
        sidebar.add(Box.createVerticalStrut(14));
        for (String p : PAGES) {
            if (p.equals("-")) {
                sidebar.add(Box.createVerticalStrut(14));
                JSeparator sep = new JSeparator();
                sep.setMaximumSize(new Dimension(110, 2));
                sep.setForeground(Theme.alpha(Color.WHITE, 90));
                sep.setAlignmentX(0.18f);
                sidebar.add(sep);
                sidebar.add(Box.createVerticalStrut(14));
                continue;
            }
            SideItem it = new SideItem(p);
            items.put(p, it);
            sidebar.add(it);
        }
        sidebar.add(Box.createVerticalGlue());
        add(sidebar, BorderLayout.WEST);
        content.setOpaque(true);
        content.setBackground(Theme.cellBg);
        add(content, BorderLayout.CENTER);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                host.act("closeBackstage");
            }
        });
        // swallow clicks so nothing underneath reacts
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseMotionAdapter() { });
        show("Info");
    }

    /** Opens on a given page (Info, New, Open ...). */
    public void show(String page) {
        selected = page;
        for (SideItem i : items.values()) i.repaint();
        content.removeAll();
        content.setBackground(Theme.cellBg);
        sidebar.setBackground(Theme.dark() ? Theme.accentDark : Theme.accent);
        JComponent p;
        switch (page) {
            case "New": p = newPage(); break;
            case "Open": p = openPage(); break;
            case "Save As": p = saveAsPage(); break;
            case "Print": p = printPage(); break;
            case "Share": p = sharePage(); break;
            case "Export": p = exportPage(); break;
            case "Close": p = closePage(); break;
            case "Account": p = accountPage(); break;
            case "Options": p = optionsPage(); break;
            default: p = infoPage();
        }
        content.add(p, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    // ---- sidebar pieces ----

    private final class BackButton extends JComponent {
        BackButton() {
            setPreferredSize(new Dimension(190, 78));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Back (Esc)");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    host.act("closeBackstage");
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int d = 40, x = 22, y = 20;
            g.drawOval(x, y, d, d);
            g.drawLine(x + 11, y + d / 2, x + d - 11, y + d / 2);
            g.drawLine(x + 11, y + d / 2, x + 19, y + d / 2 - 8);
            g.drawLine(x + 11, y + d / 2, x + 19, y + d / 2 + 8);
            g.dispose();
        }
    }

    private final class SideItem extends JComponent {
        private final String page;
        private boolean hover;

        SideItem(String page) {
            this.page = page;
            setPreferredSize(new Dimension(190, 41));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 41));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }

                @Override
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }

                @Override
                public void mouseClicked(MouseEvent e) { choose(page); }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (page.equals(selected)) {
                g.setColor(Theme.alpha(Color.WHITE, 45));
                g.fillRect(0, 0, getWidth(), getHeight());
            } else if (hover) {
                g.setColor(Theme.alpha(Color.WHITE, 22));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.setColor(Color.WHITE);
            g.setFont(Theme.ui(Font.PLAIN, 1));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(I18n.t(page), 26, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    /** Save, Save As ... that do something at once show their page; Save just saves. */
    private void choose(String page) {
        if (page.equals("Save")) {
            host.act("save");
            return;
        }
        show(page);
    }

    // ---- building blocks ----

    private JPanel page(String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(26, 40, 20, 40));
        JLabel t = new JLabel(I18n.t(title));
        t.setFont(Theme.ui(Font.PLAIN, 22));
        t.setForeground(Theme.cellFg);
        t.setBorder(new EmptyBorder(0, 0, 18, 0));
        p.add(t, BorderLayout.NORTH);
        return p;
    }

    private JPanel column() {
        JPanel c = new JPanel();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setOpaque(false);
        return c;
    }

    /** A big clickable row: icon, title, description. */
    private JComponent tile(String icon, String title, String desc, Runnable action) {
        return tile(icon, title, desc, action, false);
    }

    private JComponent tile(String icon, String title, String desc, Runnable action, boolean on) {
        JPanel t = new JPanel(new BorderLayout(14, 0)) {
            private boolean hover;

            {
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) { hover = true; repaint(); }

                    @Override
                    public void mouseExited(MouseEvent e) { hover = false; repaint(); }

                    @Override
                    public void mouseClicked(MouseEvent e) { action.run(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (hover || on) {
                    g.setColor(Theme.alpha(Theme.accent, on ? 70 : 40));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                super.paintComponent(g);
            }
        };
        t.setBorder(new EmptyBorder(10, 12, 10, 12));
        t.setMaximumSize(new Dimension(520, 64));
        t.setAlignmentX(LEFT_ALIGNMENT);
        JLabel ic = new JLabel(Icons.get(icon, 30, Theme.accent));
        ic.setPreferredSize(new Dimension(36, 36));
        t.add(ic, BorderLayout.WEST);
        JPanel txt = new JPanel(new GridLayout(0, 1));
        txt.setOpaque(false);
        JLabel a = new JLabel(I18n.t(title));
        a.setFont(Theme.ui(Font.PLAIN, 2));
        a.setForeground(Theme.cellFg);
        txt.add(a);
        if (desc != null) {
            JLabel b = new JLabel(I18n.t(desc));
            b.setFont(Theme.ui(Font.PLAIN, -1));
            b.setForeground(Theme.alpha(Theme.cellFg, 150));
            txt.add(b);
        }
        t.add(txt, BorderLayout.CENTER);
        return t;
    }

    private JLabel heading(String s) {
        JLabel l = new JLabel(I18n.t(s));
        l.setFont(Theme.ui(Font.BOLD, 1));
        l.setForeground(Theme.accent);
        l.setBorder(new EmptyBorder(14, 12, 4, 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private Runnable go(String id) {
        return () -> host.act(id);
    }

    // ---- pages ----

    private JComponent infoPage() {
        JPanel p = page("Info");
        JPanel body = new JPanel(new GridLayout(1, 2, 40, 0));
        body.setOpaque(false);
        JPanel left = column();
        JLabel name = new JLabel(host.fileName());
        name.setFont(Theme.ui(Font.PLAIN, 12));
        name.setForeground(Theme.cellFg);
        name.setAlignmentX(LEFT_ALIGNMENT);
        name.setBorder(new EmptyBorder(0, 12, 2, 0));
        left.add(name);
        File f = host.file();
        JLabel where = new JLabel(f == null ? I18n.t("Not saved yet") : f.getParent());
        where.setForeground(Theme.alpha(Theme.cellFg, 150));
        where.setAlignmentX(LEFT_ALIGNMENT);
        where.setBorder(new EmptyBorder(0, 12, 10, 0));
        left.add(where);
        left.add(heading("Protect Workbook"));
        left.add(tile("lock", "Set Open Password...", host.encrypted() ? "This workbook is password protected" : "Encrypt this workbook so it asks for a password", go("password")));
        left.add(heading("Manage Workbook"));
        left.add(tile("history", "Version History...", "See and restore earlier versions", go("history")));
        left.add(tile("save", "Save Version...", "Keep a named copy of this moment", go("saveversion")));
        left.add(tile("recover", "Recover Unsaved Workbooks...", "Bring back work from a crash", go("recover")));
        JPanel right = column();
        right.add(heading("Properties"));
        right.add(prop("Name", host.fileName()));
        right.add(prop("Sheets", String.valueOf(host.sheetCount())));
        right.add(prop("Size", f == null ? "-" : humanSize(f.length())));
        right.add(prop("Last modified", f == null ? "-" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(f.lastModified()))));
        right.add(prop("Saved", host.dirty() ? "Has unsaved changes" : "All changes saved"));
        right.add(prop("Password", host.encrypted() ? "Yes" : "No"));
        right.add(prop("Read-only", host.readOnly() ? "Yes" : "No"));
        body.add(left);
        body.add(right);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JComponent prop(String k, String v) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setOpaque(false);
        r.setBorder(new EmptyBorder(3, 12, 3, 0));
        r.setMaximumSize(new Dimension(520, 28));
        r.setAlignmentX(LEFT_ALIGNMENT);
        JLabel a = new JLabel(I18n.t(k));
        a.setPreferredSize(new Dimension(130, 20));
        a.setForeground(Theme.alpha(Theme.cellFg, 150));
        JLabel b = new JLabel(I18n.t(v));
        b.setForeground(Theme.cellFg);
        r.add(a, BorderLayout.WEST);
        r.add(b, BorderLayout.CENTER);
        return r;
    }

    private static String humanSize(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format("%.1f KB", n / 1024.0);
        return String.format("%.1f MB", n / 1048576.0);
    }

    private JComponent newPage() {
        JPanel p = page("New");
        JPanel col = column();
        col.add(tile("newsheet", "Blank workbook", "Start with an empty sheet", go("new")));
        col.add(tile("csvin", "Import a CSV file...", "Open a .csv as a new sheet", go("importcsv")));
        col.add(tile("getdata", "Get Data...", "Bring in data from a file, JSON, a web page or a database", go("getdata")));
        p.add(col, BorderLayout.CENTER);
        return p;
    }

    private JComponent openPage() {
        JPanel p = page("Open");
        JPanel body = new JPanel(new GridLayout(1, 2, 30, 0));
        body.setOpaque(false);
        JPanel places = column();
        places.add(tile("history", "Recent Workbooks", null, () -> show("Open"), true));
        places.add(tile("csvin", "Computer", "Browse for a workbook...", go("open")));
        places.add(tile("table", "Excel file (.xlsx)", "Open a workbook made in Excel", go("openxlsx")));
        JPanel recent = column();
        recent.add(heading("Recent Workbooks"));
        List<File> files = RecentFiles.files();
        if (files.isEmpty()) {
            JLabel none = new JLabel(I18n.t("No recent workbooks yet."));
            none.setBorder(new EmptyBorder(8, 12, 0, 0));
            none.setForeground(Theme.alpha(Theme.cellFg, 150));
            none.setAlignmentX(LEFT_ALIGNMENT);
            recent.add(none);
        }
        for (File f : files) {
            String parent = f.getParent() == null ? "" : f.getParent();
            recent.add(tile(f.getName().toLowerCase().endsWith(".xlsx") ? "table" : "save", f.getName(), parent, () -> host.open(f)));
        }
        JScrollPane sp = new JScrollPane(recent);
        sp.setBorder(null);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        body.add(places);
        body.add(sp);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JComponent saveAsPage() {
        JPanel p = page("Save As");
        JPanel col = column();
        col.add(tile("save", "Spreadsheet Pro workbook (.spx)", "Keeps everything: charts, pictures, drawings, connections", go("saveas")));
        col.add(tile("csvout", "Excel workbook (.xlsx)", "A copy that opens in Excel", go("xlsx")));
        col.add(tile("pdf", "PDF", "A fixed copy of this sheet", go("pdf")));
        col.add(tile("csvout", "CSV (comma separated)", "Just the values of this sheet", go("csv")));
        p.add(col, BorderLayout.CENTER);
        return p;
    }

    private JComponent printPage() {
        JPanel p = page("Print");
        JPanel col = column();
        col.add(tile("print", "Print...", "Send this sheet to a printer", go("print")));
        col.add(tile("preview", "Print Preview...", "See the pages before printing", go("preview")));
        col.add(tile("sheetsize", "Page Setup...", "Paper, margins, headers and footers", go("pagesetup")));
        p.add(col, BorderLayout.CENTER);
        return p;
    }

    private JComponent sharePage() {
        JPanel p = page("Share");
        JPanel col = column();
        col.add(tile("pdf", "Send as PDF", "Make a PDF you can attach to a message", go("pdf")));
        col.add(tile("csvout", "Send as Excel file", "Make an .xlsx everyone can open", go("xlsx")));
        col.add(tile("save", "Save a copy", "Save under a new name to send or back up", go("saveas")));
        p.add(col, BorderLayout.CENTER);
        return p;
    }

    private JComponent exportPage() {
        JPanel p = page("Export");
        JPanel col = column();
        col.add(tile("pdf", "Export as PDF...", "Save this sheet as a PDF file", go("pdf")));
        col.add(tile("csvout", "Export to Excel (.xlsx)...", "Save the workbook for Excel", go("xlsx")));
        col.add(tile("csvout", "Export CSV...", "Save this sheet as a .csv file", go("csv")));
        p.add(col, BorderLayout.CENTER);
        return p;
    }

    private JComponent closePage() {
        JPanel p = page("Close");
        JPanel col = column();
        col.add(tile("trash", "Close this workbook", "Asks to save first, then shows a blank workbook", go("close")));
        col.add(tile("trash", "Exit Spreadsheet Pro", "Close the whole program", go("exit")));
        p.add(col, BorderLayout.CENTER);
        return p;
    }

    private JComponent accountPage() {
        JPanel p = page("Account");
        JPanel col = column();
        JLabel app = new JLabel("Spreadsheet Pro");
        app.setFont(Theme.ui(Font.PLAIN, 12));
        app.setForeground(Theme.cellFg);
        app.setBorder(new EmptyBorder(0, 12, 2, 0));
        app.setAlignmentX(LEFT_ALIGNMENT);
        col.add(app);
        JLabel ver = new JLabel(I18n.t("Version") + " " + host.version());
        ver.setForeground(Theme.alpha(Theme.cellFg, 150));
        ver.setBorder(new EmptyBorder(0, 12, 12, 0));
        ver.setAlignmentX(LEFT_ALIGNMENT);
        col.add(ver);
        col.add(tile("update", "Check for Updates...", "See if a newer version is available", go("update")));
        p.add(col, BorderLayout.CENTER);
        return p;
    }

    private JComponent optionsPage() {
        JPanel p = page("Options");
        JPanel col = column();
        col.add(tile("palette", "Appearance & Language...", "Theme, language, fonts and text size", go("options")));
        col.add(tile("save", "AutoSave Settings...", "How often your work is saved for you", go("autosave")));
        p.add(col, BorderLayout.CENTER);
        return p;
    }
}
