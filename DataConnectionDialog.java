import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;

/** Create or edit a data connection: pick the source, see a preview, choose where the data lands. */
public class DataConnectionDialog extends JDialog {
    private final DataConnection conn;
    private boolean accepted;
    private char[] password = new char[0];

    private final JTextField name = new JTextField(24);
    private final JTextField source = new JTextField(34);
    private final JButton browse = new JButton(I18n.t("Browse..."));
    private final JComboBox<String> delim = new JComboBox<>(new String[]{"Detect", "Comma ,", "Semicolon ;", "Tab", "Pipe |"});
    private final JCheckBox header = new JCheckBox(I18n.t("First row is a header (make it bold)"), true);
    private final JTextField path = new JTextField(20);
    private final JSpinner tableIdx = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    private final JTextArea query = new JTextArea(3, 34);
    private final JTextField jar = new JTextField(26);
    private final JTextField user = new JTextField(12);
    private final JPasswordField pass = new JPasswordField(12);
    private final JTextField target = new JTextField(6);
    private final JCheckBox keep = new JCheckBox(I18n.t("Keep this connection so I can refresh it later"), true);
    private final JTable preview = new JTable();
    private final JLabel previewNote = new JLabel(" ");

    public DataConnectionDialog(Frame owner, DataConnection c, String title) {
        super(owner, title, true);
        this.conn = c;
        build();
        load();
        pack();
        setMinimumSize(new Dimension(640, 560));
        setLocationRelativeTo(owner);
    }

    public boolean accepted() { return accepted; }
    public char[] password() { return password; }
    public boolean keep() { return keep.isSelected(); }

    private void build() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(10, 10, 4, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;
        int y = 0;
        row(form, g, y++, "Name:", name);
        JPanel src = new JPanel(new BorderLayout(4, 0));
        src.add(source, BorderLayout.CENTER);
        src.add(browse, BorderLayout.EAST);
        row(form, g, y++, conn.kind == DataConnection.Kind.WEB ? "Web address:" : conn.kind == DataConnection.Kind.DATABASE ? "Connection string:" : "File:", src);
        switch (conn.kind) {
            case TEXT:
                row(form, g, y++, "Separator:", delim);
                row(form, g, y++, "", header);
                break;
            case JSON:
                row(form, g, y++, "Path to the list (optional):", path);
                row(form, g, y++, "", new JLabel("Example: data.items - leave empty to detect"));
                break;
            case WEB:
                row(form, g, y++, "Path (JSON, optional):", path);
                row(form, g, y++, "Table number (web pages):", tableIdx);
                row(form, g, y++, "", header);
                break;
            default:
                JPanel jr = new JPanel(new BorderLayout(4, 0));
                jr.add(jar, BorderLayout.CENTER);
                JButton jb = new JButton(I18n.t("Browse..."));
                jb.addActionListener(e -> pick(jar, "jar"));
                jr.add(jb, BorderLayout.EAST);
                row(form, g, y++, "Driver (.jar):", jr);
                row(form, g, y++, "SELECT query:", new JScrollPane(query));
                JPanel up = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                up.add(user);
                up.add(new JLabel(I18n.t("Password:")));
                up.add(pass);
                row(form, g, y++, "User:", up);
                row(form, g, y++, "", new JLabel("The password is never saved in the workbook."));
        }
        row(form, g, y++, "Put the data at cell:", target);
        row(form, g, y++, "", keep);

        browse.setVisible(conn.kind == DataConnection.Kind.TEXT || conn.kind == DataConnection.Kind.JSON);
        browse.addActionListener(e -> pick(source, conn.kind == DataConnection.Kind.JSON ? "json" : "csv"));

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.setBorder(new EmptyBorder(0, 10, 0, 10));
        JButton pv = new JButton(I18n.t("Preview"));
        pv.addActionListener(e -> runPreview());
        JPanel pvTop = new JPanel(new BorderLayout());
        pvTop.add(pv, BorderLayout.WEST);
        pvTop.add(previewNote, BorderLayout.CENTER);
        center.add(pvTop, BorderLayout.NORTH);
        center.add(new JScrollPane(preview), BorderLayout.CENTER);
        center.setPreferredSize(new Dimension(600, 170));

        JButton ok = new JButton(I18n.t("Import"));
        JButton cancel = new JButton(I18n.t("Cancel"));
        ok.addActionListener(e -> accept());
        cancel.addActionListener(e -> dispose());
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btn.add(ok);
        btn.add(cancel);
        getRootPane().setDefaultButton(ok);

        JPanel root = new JPanel(new BorderLayout());
        root.add(form, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(btn, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void row(JPanel p, GridBagConstraints g, int y, String label, JComponent c) {
        g.gridy = y;
        g.gridx = 0;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        p.add(new JLabel(label.isEmpty() ? "" : I18n.t(label)), g);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = c instanceof JCheckBox || c instanceof JLabel ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
        p.add(c, g);
    }

    private void pick(JTextField f, String ext) {
        JFileChooser fc = new JFileChooser();
        if (f.getText().trim().length() > 0) fc.setSelectedFile(new File(f.getText().trim()));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) f.setText(fc.getSelectedFile().getAbsolutePath());
    }

    private void load() {
        name.setText(conn.name);
        source.setText(conn.source);
        header.setSelected(conn.header);
        path.setText(conn.path);
        tableIdx.setValue(Math.max(1, conn.tableIndex));
        query.setText(conn.query);
        jar.setText(conn.driverJar);
        user.setText(conn.user);
        target.setText(FormulaEngine.cellRef(conn.row, conn.col));
        String d = conn.delimiter == null ? "" : conn.delimiter;
        delim.setSelectedIndex(d.isEmpty() ? 0 : d.equals(",") ? 1 : d.equals(";") ? 2 : d.equals("\t") || d.equals("tab") ? 3 : 4);
    }

    private void store() {
        conn.name = name.getText().trim();
        conn.source = source.getText().trim();
        conn.header = header.isSelected();
        conn.path = path.getText().trim();
        conn.tableIndex = (Integer) tableIdx.getValue();
        conn.query = query.getText().trim();
        conn.driverJar = jar.getText().trim();
        conn.user = user.getText().trim();
        password = pass.getPassword();
        String[] d = {"", ",", ";", "\t", "|"};
        conn.delimiter = d[Math.max(0, delim.getSelectedIndex())];
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*([A-Za-z]{1,3})(\\d{1,7})\\s*$").matcher(target.getText());
        if (m.matches()) {
            conn.row = Integer.parseInt(m.group(2)) - 1;
            conn.col = FormulaEngine.parseColumn(m.group(1));
        }
    }

    private void runPreview() {
        store();
        previewNote.setText(" " + I18n.t("Loading..."));
        DataConnection c = conn.copy();
        char[] pw = password;
        new SwingWorker<DataImporter.Table, Void>() {
            @Override
            protected DataImporter.Table doInBackground() throws Exception {
                return DataImporter.fetch(c, pw);
            }

            @Override
            protected void done() {
                try {
                    DataImporter.Table t = get();
                    int cols = 0;
                    for (String[] r : t.rows) cols = Math.max(cols, r.length);
                    DefaultTableModel dm = new DefaultTableModel();
                    for (int i = 0; i < cols; i++) dm.addColumn(FormulaEngine.columnLabel(i));
                    for (int i = 0; i < Math.min(8, t.rows.size()); i++) dm.addRow(t.rows.get(i));
                    preview.setModel(dm);
                    previewNote.setText(" " + t.rows.size() + " " + I18n.t("rows") + ", " + cols + " " + I18n.t("columns")
                            + (t.note == null ? "" : " - " + t.note));
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    previewNote.setText(" " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void accept() {
        store();
        if (conn.source.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter where the data comes from first.", getTitle(), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (conn.kind == DataConnection.Kind.DATABASE && conn.query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a SELECT query first.", getTitle(), JOptionPane.WARNING_MESSAGE);
            return;
        }
        accepted = true;
        dispose();
    }
}
