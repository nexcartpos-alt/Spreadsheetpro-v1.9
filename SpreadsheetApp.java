import java.awt.Container;
import javax.swing.DefaultListModel;
import java.awt.BorderLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Supplier;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;
import javax.swing.DefaultListCellRenderer;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.BasicStroke;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JPasswordField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * UI &amp; layout layer: the main window.
 *
 * <ul>
 *   <li>Excel-style green ribbon (#107C41) with all tools</li>
 *   <li>Formula bar: cell badge, "fx" label and a formula text field synced with the selection</li>
 *   <li>Multi-sheet workbook in a JTabbedPane (add / rename / delete tabs)</li>
 *   <li>Status bar with Count / Sum / Average of the selected cells</li>
 * </ul>
 * Each worksheet is a {@link SpreadsheetModel} shown in a JTable that uses
 * {@link SpreadsheetCellRenderer}; formulas are evaluated by {@link FormulaEngine}.
 */
public class SpreadsheetApp extends JFrame {


    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel cellBadge = new JLabel("A1", SwingConstants.CENTER);
    private final JTextField formulaField = new JTextField();
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel statsLabel = new JLabel("");
    private final JComboBox<Integer> sizeCombo = new JComboBox<>(new Integer[]{9, 10, 11, 12, 13, 14, 16, 18, 20});
    private final JPanel fillChip = new JPanel();
    private static final String DEFAULT_FONT = "Default";
    private final JComboBox<String> fontCombo = new JComboBox<>(fontNames());
    private final JComboBox<String> numFmtCombo = new JComboBox<>(CellFormats.NAMES);
    private final JPanel textChip = new JPanel();
    private Color currentTextColor = new Color(0xC00000);
    private RibbonButton filterBtn, showFormulasBtn, circleBtn;
    private final JLabel autoSaveLabel = new JLabel("");
    private JCheckBoxMenuItem autoSaveItem;
    private JCheckBoxMenuItem readOnlyItem;
    private RibbonButton readOnlyBtn;
    private boolean readOnlyMode = false;
    private final VersionStore versions = new VersionStore(VersionStore.defaultRoot());
    private AutoSaveSwitch autoSwitch;
    private final AutoSaveManager autoSave = new AutoSaveManager(new AutoSaveManager.Host() {
        @Override
        public boolean isDirty() {
            return dirty && !readOnlyMode;
        }

        @Override
        public boolean hasFile() {
            return currentFile != null;
        }

        @Override
        public String displayName() {
            return currentFile == null ? "Untitled" : currentFile.getName();
        }

        @Override
        public void writeRecovery(File file) throws IOException {
            WorkbookIO.save(file, collectSheets(), tabs.getSelectedIndex(), workbookPassword);
        }

        @Override
        public boolean saveToFile() {
            try {
                WorkbookIO.save(currentFile, collectSheets(), tabs.getSelectedIndex(), workbookPassword);
                dirty = false;
                updateTitle();
                return true;
            } catch (IOException ex) {
                return false;
            }
        }

        @Override
        public void autoSaved(String message) {
            autoSaveLabel.setText(message.startsWith("AutoSave failed") ? "AutoSave failed" : "\u2713 " + message.replace("AutoSaved ", "AutoSaved "));
            autoSaveLabel.setToolTipText(message);
        }
    });

    private Color currentBackground = new Color(0xFFF2CC);
    private int sheetCounter = 0;
    private boolean updatingUi = false;
    private final JLabel zoomLabel = new JLabel("100%");
    private final JSlider zoomSlider = new JSlider(SpreadsheetModel.MIN_ZOOM, SpreadsheetModel.MAX_ZOOM, 100);
    private MacroIDEDialog macroDialog;

    private final JLabel protLabel = new JLabel("", SwingConstants.CENTER);
    private RibbonButton protectButton;
    private final CardLayout ribbonCards = new CardLayout();
    private JPanel ribbonBody;
    private String ribbonSelected = "Home";
    private final List<RibbonTab> ribbonTabs = new ArrayList<>();
    private RibbonButton boldBtn, italicBtn, alignLeftBtn, alignCenterBtn, alignRightBtn;
    private final DrawingLayer.Settings draw = new DrawingLayer.Settings();
    private final Map<DrawingLayer.Tool, RibbonButton> toolButtons = new EnumMap<>(DrawingLayer.Tool.class);
    private final List<SwatchButton> swatches = new ArrayList<>();
    private final JPanel drawChip = new JPanel();
    private final JLabel widthLabel = new JLabel("3 px");
    private File currentFile;
    private JComponent northPanel, formulaBarPanel, statusBarPanel;
    private char[] workbookPassword;
    private boolean dirty = false;
    private boolean loading = false;

    public static void main(String[] args) {
        boolean noSplash = java.awt.GraphicsEnvironment.isHeadless() || Boolean.getBoolean("spx.nosplash")
                || Arrays.asList(args).contains("--no-splash");
        SwingUtilities.invokeLater(() -> {
            AppSettings.load();
            Theme.install();
            if (noSplash) {
                SpreadsheetApp app = new SpreadsheetApp();
                app.setVisible(true);
                SwingUtilities.invokeLater(app::offerRecovery);
                app.checkForUpdatesInBackground();
                return;
            }
            // the splash animation plays while the real window is built behind it
            SpreadsheetApp[] holder = new SpreadsheetApp[1];
            SplashWindow.play(() -> holder[0] = new SpreadsheetApp(), () -> {
                SpreadsheetApp app = holder[0] != null ? holder[0] : new SpreadsheetApp();
                app.setVisible(true);
                app.toFront();
                SwingUtilities.invokeLater(app::offerRecovery);
                app.checkForUpdatesInBackground();
            });
        });
    }

    public SpreadsheetApp() {
        super("Spreadsheet Pro");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApp();
            }
        });
        setIconImage(Icons.logo(64));
        setSize(1440, 780);
        setLocationRelativeTo(null);
        setJMenuBar(buildMenuBar());

        JPanel north = new JPanel(new BorderLayout());
        north.add(buildRibbon(), BorderLayout.NORTH);
        formulaBarPanel = buildFormulaBar();
        north.add(formulaBarPanel, BorderLayout.SOUTH);
        northPanel = north;
        add(north, BorderLayout.NORTH);

        tabs.setTabPlacement(JTabbedPane.BOTTOM);
        tabs.addChangeListener(e -> syncFormulaBar());
        add(tabs, BorderLayout.CENTER);
        statusBarPanel = buildStatusBar();
        add(statusBarPanel, BorderLayout.SOUTH);

        addSheet(nextSheetName());
        applyLanguage();
        dirty = false;
        updateTitle();
        autoSave.start();
    }

    // ==================================================================
    // Ribbon
    // ==================================================================

    private static final String[] RIBBON_TAB_NAMES = {"Home", "Insert", "Draw", "Page Layout", "Data", "Power Pivot",
            "Developer", "Review", "View", "Help"};

    private JComponent buildRibbon() {
        JPanel root = new TP(new BorderLayout(), () -> Theme.ribbonBg);

        JPanel strip = new TP(new FlowLayout(FlowLayout.LEFT, 2, 0), () -> Theme.stripBg);
        strip.setBorder(new EmptyBorder(4, 8, 0, 8));

        ribbonBody = new TP(ribbonCards, () -> Theme.ribbonBg);
        ribbonBody.add(homeTab(), "Home");
        ribbonBody.add(insertTab(), "Insert");
        ribbonBody.add(drawTab(), "Draw");
        ribbonBody.add(pageLayoutTab(), "Page Layout");
        ribbonBody.add(dataTab(), "Data");
        ribbonBody.add(pivotTab(), "Power Pivot");
        ribbonBody.add(developerTab(), "Developer");
        ribbonBody.add(reviewTab(), "Review");
        ribbonBody.add(viewTab(), "View");
        ribbonBody.add(helpTab(), "Help");
        JButton fileTab = new JButton("File") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.accentDark : Theme.accent);
                g2.fillRoundRect(0, 2, getWidth(), getHeight() - 2, 8, 8);
                g2.dispose();
                setForeground(Color.WHITE);
                super.paintComponent(g);
            }
        };
        fileTab.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        fileTab.setRolloverEnabled(true);
        fileTab.setFocusable(false);
        fileTab.setBorderPainted(false);
        fileTab.setContentAreaFilled(false);
        fileTab.setOpaque(false);
        fileTab.setFont(Theme.ui(Font.BOLD, 0));
        fileTab.setBorder(new EmptyBorder(6, 18, 6, 18));
        fileTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fileTab.setToolTipText("File: save, open, print, export and more");
        fileTab.addActionListener(e -> showBackstage("Info"));
        strip.add(fileTab);
        for (String name : RIBBON_TAB_NAMES) {
            RibbonTab t = new RibbonTab(name);
            ribbonTabs.add(t);
            strip.add(t);
        }
        autoSwitch = new AutoSaveSwitch();
        JPanel top = new TP(new BorderLayout(), () -> Theme.stripBg);
        top.add(strip, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.setOpaque(false);
        right.add(autoSwitch);
        top.add(right, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);
        root.add(ribbonBody, BorderLayout.CENTER);
        selectRibbonTab("Home");
        return root;
    }

    private void selectRibbonTab(String name) {
        ribbonSelected = name;
        ribbonCards.show(ribbonBody, name);
        for (RibbonTab t : ribbonTabs) t.repaint();
        if (!"Draw".equals(name)) setDrawTool(DrawingLayer.Tool.NONE); // leaving Draw switches drawing off
    }

    private JPanel tabPanel() {
        return new TP(new FlowLayout(FlowLayout.LEFT, 0, 4), () -> Theme.ribbonBg);
    }

    /** A titled cluster of ribbon controls with a thin divider on its right. */
    private JComponent group(String title, JComponent... items) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        row.setOpaque(false);
        for (JComponent c : items) row.add(c);
        JLabel label = new TL(title, SwingConstants.CENTER, () -> Theme.ribbonDim, -2, Font.PLAIN);
        JPanel middle = new JPanel(new GridBagLayout()); // centres the buttons vertically
        middle.setOpaque(false);
        middle.add(row);
        JPanel g = new JPanel(new BorderLayout(0, 2)) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(new CompoundBorder(new MatteBorder(0, 0, 0, 1, Theme.ribbonSep), new EmptyBorder(0, 6, 0, 6)));
            }

            @Override
            public Dimension getPreferredSize() { // same height so labels line up
                int h = 80 + Math.round((Theme.uiSize() - 12) * 3.5f);
                return new Dimension(super.getPreferredSize().width, Math.max(72, h));
            }
        };
        g.setOpaque(false);
        g.add(middle, BorderLayout.CENTER);
        g.add(label, BorderLayout.SOUTH);
        return g;
    }

    /** "Default", the three built-in families, then every font installed on this computer. */
    private static String[] fontNames() {
        String[] installed = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        java.util.Arrays.sort(installed, String.CASE_INSENSITIVE_ORDER);
        List<String> list = new ArrayList<>();
        list.add(DEFAULT_FONT);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String f : new String[]{"SansSerif", "Serif", "Monospaced"}) {
            list.add(f);
            seen.add(f);
        }
        for (String f : installed) if (seen.add(f) && !f.startsWith(".")) list.add(f);
        return list.toArray(new String[0]);
    }

    private JPanel flowRow(JComponent... items) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.setOpaque(false);
        for (JComponent c : items) row.add(c);
        return row;
    }

    private JPanel chipBox(JPanel chip) {
        JPanel box = new JPanel(new GridBagLayout());
        box.setOpaque(false);
        box.add(chip);
        return box;
    }

    private RibbonButton menuButton(String text, String icon, String tip, Supplier<JPopupMenu> popup) {
        RibbonButton[] self = new RibbonButton[1];
        self[0] = button(text + " \u25BE", icon, tip, e -> popup.get().show(self[0], 0, self[0].getHeight()));
        return self[0];
    }

    private JComponent homeTab() {
        JPanel p = tabPanel();
        p.add(group("Clipboard",
                button("Paste", "paste", "Paste copied cells (Ctrl+V). Ctrl+Shift+V pastes values only", e -> pasteFromClipboard(false)),
                button("Cut", "cut", "Cut the selected cells (Ctrl+X)", e -> copyFromMenu(true)),
                button("Copy", "copy", "Copy the selected cells with formulas and formatting (Ctrl+C)", e -> copyFromMenu(false)),
                button("Undo", "undo", "Undo the last change (Ctrl+Z)", e -> undoAction()),
                button("Redo", "redo", "Redo (Ctrl+Y)", e -> redoAction())));
        p.add(group("File",
                button("Save", "save", "Save the workbook (Ctrl+S)", e -> saveWorkbook()),
                button("New Sheet", "newsheet", "Add a new worksheet (Shift+F11)", e -> addSheet(nextSheetName())),
                button("Delete Sheet", "deletesheet", "Delete the current worksheet",
                        e -> deleteSheet(tabs.getSelectedIndex()))));

        // Font: family + size on the first row, style and colours on the second
        fontCombo.setFocusable(false);
        fontCombo.setPreferredSize(new Dimension(132, 24));
        fontCombo.setToolTipText("Font of the selected cells");
        fontCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null && !DEFAULT_FONT.equals(value)) {
                    Font preview = new Font(value.toString(), Font.PLAIN, Theme.uiSize() + 2);
                    // symbol fonts cannot draw their own name: keep the normal font for those
                    setFont(preview.canDisplayUpTo(value.toString()) < 0 ? preview : list.getFont());
                } else {
                    setFont(list.getFont());
                }
                return this;
            }
        });
        fontCombo.setMaximumRowCount(16);
        fontCombo.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                SwingUtilities.invokeLater(() -> {
                    Object child = fontCombo.getAccessibleContext().getAccessibleChild(0);
                    if (child instanceof javax.swing.JComponent) {
                        javax.swing.JComponent pop = (javax.swing.JComponent) child;
                        pop.setPreferredSize(new Dimension(Math.max(280, fontCombo.getWidth()), pop.getPreferredSize().height));
                        pop.revalidate();
                    }
                });
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
            }
        });
        fontCombo.addActionListener(e -> {
            if (updatingUi || fontCombo.getSelectedItem() == null) return;
            final String family = fontCombo.getSelectedItem().toString();
            applyToSelection(st -> st.fontFamily = DEFAULT_FONT.equals(family) ? null : family);
        });
        sizeCombo.setSelectedItem(13);
        sizeCombo.setFocusable(false);
        sizeCombo.setToolTipText("Font size of the selected cells");
        sizeCombo.addActionListener(e -> {
            if (updatingUi || sizeCombo.getSelectedItem() == null) return;
            final int size = (Integer) sizeCombo.getSelectedItem();
            applyToSelection(st -> st.fontSize = size);
        });
        boldBtn = iconButton("bold", "Bold (Ctrl+B)", e -> toggleStyle(true));
        italicBtn = iconButton("italic", "Italic (Ctrl+I)", e -> toggleStyle(false));
        fillChip.setPreferredSize(new Dimension(16, 16));
        fillChip.setBackground(currentBackground);
        fillChip.setBorder(BorderFactory.createLineBorder(Theme.ribbonFg, 1));
        fillChip.setToolTipText("Current fill colour");
        textChip.setPreferredSize(new Dimension(16, 16));
        textChip.setBackground(currentTextColor);
        textChip.setBorder(BorderFactory.createLineBorder(Theme.ribbonFg, 1));
        textChip.setToolTipText("Current text colour");
        JPanel fontBox = new JPanel();
        fontBox.setOpaque(false);
        fontBox.setLayout(new BoxLayout(fontBox, BoxLayout.Y_AXIS));
        fontBox.add(flowRow(fontCombo, sizeCombo));
        fontBox.add(flowRow(boldBtn, italicBtn,
                iconButton("fill", "Fill colour of the selected cells", e -> pickFillColor()), chipBox(fillChip),
                iconButton("nofill", "Remove the fill colour", e -> applyToSelection(st -> st.background = null)),
                iconButton("textcolor", "Text colour of the selected cells", e -> pickTextColor()), chipBox(textChip)));
        p.add(group("Font", fontBox));

        alignLeftBtn = iconButton("alignleft", "Align left (Ctrl+L)", e -> align(SwingConstants.LEFT));
        alignCenterBtn = iconButton("aligncenter", "Align center (Ctrl+E)", e -> align(SwingConstants.CENTER));
        alignRightBtn = iconButton("alignright", "Align right (Ctrl+R)", e -> align(SwingConstants.RIGHT));
        p.add(group("Alignment", alignLeftBtn, alignCenterBtn, alignRightBtn));

        // Number: format list plus percent / comma / decimals
        numFmtCombo.setFocusable(false);
        numFmtCombo.setPreferredSize(new Dimension(130, 24));
        numFmtCombo.setToolTipText("Number format of the selected cells");
        numFmtCombo.addActionListener(e -> {
            if (!updatingUi && numFmtCombo.getSelectedIndex() >= 0) setNumberFormat(numFmtCombo.getSelectedIndex());
        });
        JPanel numBox = new JPanel();
        numBox.setOpaque(false);
        numBox.setLayout(new BoxLayout(numBox, BoxLayout.Y_AXIS));
        numBox.add(flowRow(numFmtCombo));
        numBox.add(flowRow(
                iconButton("percent", "Percent style (25% = 0.25)", e -> setNumberFormat(CellFormats.PERCENT)),
                iconButton("comma", "Comma style (1,234.00)", e -> setNumberFormat(CellFormats.COMMA)),
                iconButton("decup", "Increase decimals", e -> adjustDecimals(1)),
                iconButton("decdown", "Decrease decimals", e -> adjustDecimals(-1))));
        p.add(group("Number", numBox));

        p.add(group("Styles",
                menuButton("Cell Styles", "styles", "Ready-made looks: Good, Bad, Title, Total ...", this::cellStylesMenu),
                menuButton("Format as Table", "table", "Colour a range as a table with banded rows and a filter", this::tableStylesMenu),
                menuButton("Conditional", "condfmt", "Highlight cells automatically by their value", this::conditionalMenu)));
        p.add(group("Cells",
                menuButton("Insert", "insertrow", "Insert rows or columns", this::insertMenu),
                menuButton("Delete", "deleterow", "Delete rows or columns", this::deleteMenu)));
        p.add(group("Editing",
                button("AutoSum", "sum", "Sum the numbers above or to the left", e -> autoSum()),
                button("Function", "function", "Browse and insert a function (Shift+F3)", e -> openInsertFunction()),
                menuButton("Fill", "fillseries", "Fill cells down, right, up or left, or make a number series", this::fillMenu),
                button("Find", "find", "Find and replace text in this sheet or the whole workbook (Ctrl+F / Ctrl+H)", e -> showFind(false)),
                menuButton("Sort & Filter", "filter", "Sort the table or filter its rows", this::sortFilterMenu)));
        return p;
    }

    private JComponent dataTab() {
        JPanel p = tabPanel();
        p.add(group("Get & Refresh",
                menuButton("Get Data", "getdata", "Bring in data from a file, JSON, a web page or a database", this::getDataMenu),
                button("Refresh All", "refresh", "Reload every data connection in the workbook", e -> refreshAllConnections()),
                button("Connections", "link", "See, edit, refresh or remove data connections", e -> connectionsDialog())));
        p.add(group("Sort & Filter",
                button("Sort A-Z", "sortaz", "Sort the table by the selected column, smallest first", e -> sortSelection(true)),
                button("Sort Z-A", "sortza", "Sort the table by the selected column, largest first", e -> sortSelection(false)),
                button("Custom Sort", "customsort", "Sort by up to three columns", e -> customSort()),
                filterBtn = button("Filter", "filter", "Show a drop-down arrow on every header to filter rows", e -> toggleFilter()),
                button("Clear", "clearfilter", "Show all rows again (keeps the arrows)", e -> clearFilterAction())));
        p.add(group("Data Tools",
                button("Validation", "validation", "Limit what can be typed: a list, whole numbers, decimals or text length", e -> validationDialog()),
                circleBtn = button("Circle Invalid", "circle", "Circle cells that break their validation rule", e -> toggleCircleInvalid()),
                button("Clear Rules", "trash", "Remove validation rules from the selected cells", e -> clearValidationAction()),
                button("Remove Duplicates", "dedupe", "Delete repeated rows from the table", e -> removeDuplicatesAction()),
                button("Text to Columns", "split", "Split text in a column into several columns", e -> textToColumnsAction())));
        p.add(group("What-If",
                button("Goal Seek", "goalseek", "Find the input that makes a formula give a wanted result", e -> goalSeekDialog())));
        p.add(group("Sheet",
                button("Sheet Size", "sheetsize", "Give this sheet more rows (up to 1,048,576)", e -> sheetSizeDialog())));
        p.add(group("Excel",
                button("Open Excel", "csvin", "Open an .xlsx workbook made in Excel", e -> openExcelDialog()),
                button("Export Excel", "csvout", "Save the workbook as an .xlsx file for Excel", e -> exportExcel())));
        p.add(group("Get & Export",
                button("Import CSV", "csvin", "Load a .csv file into the sheet", e -> importCsv()),
                button("Export CSV", "csvout", "Save the sheet as a .csv file", e -> exportCsv())));
        return p;
    }

    private JComponent pivotTab() {
        JPanel p = tabPanel();
        p.add(group("Analyze",
                button("PivotTable", "pivot", "Summarize a table by one or two fields on a new sheet", e -> openPivot()),
                button("Summary", "summary", "Statistics for every column of the table on a new sheet", e -> quickSummary())));
        p.add(group("Calculations",
                button("Calculated Column", "calc", "Add a column from a formula such as [Qty]*[Price]", e -> addCalculatedColumn())));
        return p;
    }

    private JComponent developerTab() {
        JPanel p = tabPanel();
        p.add(group("Code",
                button("VBA Macro", "macro", "Open the macro editor and runner", e -> openMacroIde())));
        p.add(group("Inspect",
                showFormulasBtn = button("Show Formulas", "showformula", "Show formulas instead of their results on this sheet",
                        e -> toggleShowFormulas()),
                button("Inspect Cell", "inspect", "Details of the active cell", e -> inspectCell()),
                button("Workbook Info", "info", "Sheets, formulas, pictures and drawings in this workbook", e -> workbookInfo())));
        return p;
    }

    private JComponent helpTab() {
        JPanel p = tabPanel();
        p.add(group("Help",
                button("Formulas", "help", "Formula reference", e -> showFormulaHelp()),
                button("Shortcuts", "keyboard", "Keyboard shortcuts", e -> showShortcuts()),
                button("Updates", "update", "Check whether a newer version is available", e -> checkForUpdates(true)),
                button("About", "about", "About Spreadsheet Pro", e -> showAbout())));
        p.add(group("AutoSave",
                button("Settings", "autosave", "AutoSave on/off, interval and where it saves", e -> showAutoSaveSettings())));
        return p;
    }

    private JComponent insertTab() {
        JPanel p = tabPanel();
        p.add(group("Illustrations",
                button("Picture", "picture", "Insert a picture from a file", e -> insertPictureFromFile()),
                button("Clipboard", "clipboard", "Paste a picture from the clipboard", e -> insertPictureFromClipboard())));
        p.add(group("Links",
                button("Link", "link", "Link the active cell to a web page, e-mail address or place in this workbook (Ctrl+K)", e -> insertLink()),
                button("Remove", "trash", "Remove the links from the selected cells", e -> removeLinks())));
        p.add(group("Charts",
                menuButton("Chart", "chart", "Insert a chart from the selected cells", this::chartMenuTypes),
                button("Edit Chart", "inspect", "Change the type, title or data of the selected chart", e -> editSelectedChart())));
        p.add(group("Sparklines",
                menuButton("Sparkline", "sparkline", "A tiny chart inside a cell", this::sparklineMenu),
                button("Clear", "trash", "Remove sparklines from the selected cells", e -> clearSparklinesAction())));
        p.add(group("Data",
                button("Sample Data", "chart", "Insert date, sample data or a chart placeholder", e -> openInsertDialog()),
                button("Function", "function", "Browse and insert a function (Shift+F3)", e -> openInsertFunction())));
        p.add(group("Code",
                button("VBA Macro", "macro", "Open the macro editor and runner", e -> openMacroIde())));
        protectButton = button("Protect Sheet", "lock", "Protect or unprotect this sheet, optionally with a password",
                e -> toggleProtection());
        p.add(group("Security", protectButton));
        return p;
    }

    private static final Color[] SWATCH_COLORS = {
            new Color(0xE53935), new Color(0xFB8C00), new Color(0xFDD835), new Color(0x43A047),
            new Color(0x1E88E5), new Color(0x8E24AA), new Color(0x212121), new Color(0xFFFFFF)};

    private JComponent drawTab() {
        JPanel p = tabPanel();
        Object[][] defs = {
                {DrawingLayer.Tool.NONE, "cursor", "Select", "Select cells (turns drawing off)"},
                {DrawingLayer.Tool.PEN, "pen", "Pen", "Draw freehand lines"},
                {DrawingLayer.Tool.HIGHLIGHTER, "highlighter", "Highlighter", "Paint translucent highlights over cells"},
                {DrawingLayer.Tool.LINE, "line", "Line", "Straight line - hold Shift for 45° angles"},
                {DrawingLayer.Tool.RECT, "rect", "Rectangle", "Rectangle - hold Shift for a square"},
                {DrawingLayer.Tool.ELLIPSE, "ellipse", "Ellipse", "Ellipse - hold Shift for a circle"},
                {DrawingLayer.Tool.ERASER, "eraser", "Eraser", "Click or drag over a stroke or shape to erase it"}};
        List<JComponent> tools = new ArrayList<>();
        for (Object[] d : defs) {
            DrawingLayer.Tool tool = (DrawingLayer.Tool) d[0];
            RibbonButton b = button((String) d[2], (String) d[1], (String) d[3], e -> setDrawTool(tool));
            toolButtons.put(tool, b);
            tools.add(b);
        }
        toolButtons.get(DrawingLayer.Tool.NONE).setActive(true);
        p.add(group("Tools", tools.toArray(new JComponent[0])));
        RibbonButton brushesBtn = button("Brushes", "pen", "Pen, pencil, marker, calligraphy, crayon, spray, watercolor, dashed and neon", e -> openBrushGallery(null));
        brushesBtn.addMouseListener(new MouseAdapter() { // hovering for a moment opens the gallery too
            private javax.swing.Timer t;

            @Override
            public void mouseEntered(MouseEvent e) {
                t = new javax.swing.Timer(450, x -> openBrushGallery(brushesBtn));
                t.setRepeats(false);
                t.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (t != null) t.stop();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (t != null) t.stop();
            }
        });
        p.add(group("Brushes", brushesBtn));

        JPanel swatchGrid = new JPanel(new java.awt.GridLayout(2, 4, 4, 4));
        swatchGrid.setOpaque(false);
        for (Color c : SWATCH_COLORS) {
            SwatchButton sb = new SwatchButton(c);
            swatches.add(sb);
            swatchGrid.add(sb);
        }
        drawChip.setPreferredSize(new Dimension(22, 22));
        drawChip.setBackground(draw.color);
        drawChip.setBorder(BorderFactory.createLineBorder(Theme.ribbonFg, 2));
        drawChip.setToolTipText("Current drawing colour");
        JPanel chipBox = new JPanel(new GridBagLayout());
        chipBox.setOpaque(false);
        chipBox.add(drawChip);
        p.add(group("Colour", swatchGrid, chipBox,
                button("More", "palette", "Pick any drawing colour", e -> pickDrawColor())));

        JSlider widthSlider = new JSlider(1, 24, 3);
        widthSlider.setOpaque(false);
        widthSlider.setFocusable(false);
        widthSlider.setPreferredSize(new Dimension(120, 22));
        widthSlider.setToolTipText("Brush / line thickness");
        widthLabel.setForeground(Theme.ribbonFg);
        widthSlider.addChangeListener(e -> {
            draw.width = widthSlider.getValue();
            widthLabel.setText(widthSlider.getValue() + " px");
            refreshBrushPreview();
        });
        JCheckBox fillShapes = new JCheckBox("Fill shapes");
        fillShapes.setOpaque(false);
        fillShapes.setForeground(Theme.ribbonFg);
        fillShapes.setFocusable(false);
        fillShapes.addActionListener(e -> draw.filled = fillShapes.isSelected());
        JPanel brush = new JPanel();
        brush.setOpaque(false);
        brush.setLayout(new BoxLayout(brush, BoxLayout.Y_AXIS));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        top.setOpaque(false);
        brushPreview.setIcon(BrushGallery.thumbnail(draw, 96, 24));
        brushPreview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        brushPreview.setToolTipText("Choose a brush style");
        brushPreview.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openBrushGallery(brushPreview);
            }
        });
        top.add(brushPreview);
        top.add(widthLabel);
        brush.add(top);
        brush.add(widthSlider);
        brush.add(fillShapes);
        p.add(group("Brush", brush));

        p.add(group("Actions",
                button("Undo", "undo", "Undo the last drawing change (Ctrl+Z)", e -> drawUndo()),
                button("Redo", "redo", "Redo (Ctrl+Y)", e -> drawRedo()),
                button("Clear All", "trash", "Remove every drawing on this sheet (can be undone)", e -> drawClear())));
        return p;
    }

    private JComponent viewTab() {
        JPanel p = tabPanel();
        p.add(group("Zoom",
                button("Zoom In", "zoomin", "Zoom in (Ctrl +, Ctrl+wheel)", e -> zoomBy(10)),
                button("Zoom Out", "zoomout", "Zoom out (Ctrl -, Ctrl+wheel)", e -> zoomBy(-10)),
                button("100%", "zoomreset", "Back to 100% (Ctrl+0)", e -> setZoom(100))));
        p.add(group("Layout",
                button("Reset Columns", "columns", "Restore the default column widths", e -> resetColumnWidths())));
        p.add(group("Help",
                button("Formulas", "help", "Formula reference", e -> showFormulaHelp())));
        p.add(group("Appearance",
                button("Appearance", "palette", "Language, theme, font and text size", e -> openAppearance())));
        return p;
    }

    // ------------------------------------------------------------------
    // Drawing tools
    // ------------------------------------------------------------------

    private final JLabel brushPreview = new JLabel();
    private JPopupMenu brushPopup;

    private void openBrushGallery(Component anchor) {
        if (brushPopup != null && brushPopup.isVisible()) return;
        Component a = anchor != null ? anchor : brushPreview;
        brushPopup = BrushGallery.create(draw, b -> {
            draw.brush = b;
            setDrawTool(DrawingLayer.Tool.PEN);
            refreshBrushPreview();
            status("Brush: " + I18n.t(b.label));
        });
        brushPopup.show(a, 0, a.getHeight() + 2);
    }

    private void refreshBrushPreview() {
        brushPreview.setIcon(BrushGallery.thumbnail(draw, 96, 24));
        brushPreview.repaint();
    }

    private void setDrawTool(DrawingLayer.Tool tool) {
        SheetView sv = currentSheet();
        if (tool != DrawingLayer.Tool.NONE && sv != null && sv.model.isProtected()) {
            status("This sheet is protected - unprotect it to draw.");
            Toolkit.getDefaultToolkit().beep();
            tool = DrawingLayer.Tool.NONE;
        }
        draw.tool = tool;
        for (Map.Entry<DrawingLayer.Tool, RibbonButton> en : toolButtons.entrySet()) {
            en.getValue().setActive(en.getKey() == tool);
        }
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component c = tabs.getComponentAt(i);
            if (c instanceof SheetView) ((SheetView) c).drawing.updateCursor();
        }
        switch (tool) {
            case PEN:
                status("Pen: drag on the sheet to draw. Ctrl+Z undoes, Select (arrow) goes back to cells.");
                break;
            case HIGHLIGHTER:
                status("Highlighter: drag over cells to highlight them.");
                break;
            case LINE:
                status("Line: drag from start to end (hold Shift for 45° angles).");
                break;
            case RECT:
                status("Rectangle: drag a corner to corner (Shift = square).");
                break;
            case ELLIPSE:
                status("Ellipse: drag across its bounds (Shift = circle).");
                break;
            case ERASER:
                status("Eraser: click or drag across a stroke or shape to remove it.");
                break;
            default:
                break;
        }
    }

    private void selectDrawColor(Color c) {
        draw.color = c;
        refreshBrushPreview();
        drawChip.setBackground(c);
        for (SwatchButton sb : swatches) sb.repaint();
    }

    private void pickDrawColor() {
        Color c = javax.swing.JColorChooser.showDialog(this, "Drawing colour", draw.color);
        if (c != null) selectDrawColor(c);
    }

    private void drawUndo() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        status(sv.drawing.undo() ? "Undid the last drawing change" : "Nothing to undo");
    }

    private void drawRedo() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        status(sv.drawing.redo() ? "Redid the drawing change" : "Nothing to redo");
    }

    private void drawClear() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        status(sv.drawing.clearAll() ? "All drawings removed (Undo brings them back)" : "There are no drawings on this sheet");
    }

    /** Round colour button for the quick palette. */
    private final class SwatchButton extends JComponent {
        private final Color color;
        private boolean hover;

        SwatchButton(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(20, 20));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Drawing colour");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectDrawColor(SwatchButton.this.color);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int inset = hover ? 1 : 3;
            g2.setColor(color);
            g2.fillOval(inset, inset, getWidth() - 2 * inset, getHeight() - 2 * inset);
            g2.setColor(color.equals(draw.color) ? Theme.ribbonFg : Theme.alpha(Theme.ribbonFg, 140));
            g2.setStroke(new BasicStroke(color.equals(draw.color) ? 2.4f : 1f));
            g2.drawOval(inset, inset, getWidth() - 2 * inset - 1, getHeight() - 2 * inset - 1);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------
    // Ribbon widgets
    // ------------------------------------------------------------------

    private RibbonButton button(String text, String icon, String tip, java.awt.event.ActionListener action) {
        RibbonButton b = new RibbonButton(text, icon, tip, 24);
        b.addActionListener(action);
        return b;
    }

    private RibbonButton iconButton(String icon, String tip, java.awt.event.ActionListener action) {
        RibbonButton b = new RibbonButton("", icon, tip, 20);
        b.addActionListener(action);
        return b;
    }

    /** Panel whose background follows the current theme (re-read whenever the look is refreshed). */
    static class TP extends JPanel {
        private final java.util.function.Supplier<Color> bg;

        TP(java.awt.LayoutManager lm, java.util.function.Supplier<Color> bg) {
            super(lm);
            this.bg = bg;
            setBackground(bg.get());
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (bg != null) setBackground(bg.get());
        }
    }

    /** Label whose colour and font follow the current theme (sizeDelta from the interface font size). */
    static class TL extends JLabel {
        private final java.util.function.Supplier<Color> fg;
        private final int delta;
        private final int style;

        TL(String text, int align, java.util.function.Supplier<Color> fg, int delta, int style) {
            super(text, align);
            this.fg = fg;
            this.delta = delta;
            this.style = style;
            applyLook();
        }

        private void applyLook() {
            setForeground(fg.get());
            if (style >= 0 && !(delta == 0 && style == 0)) setFont(Theme.ui(style, delta));
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (fg != null) applyLook();
        }
    }

    /** Ribbon tab (Home / Insert / Draw / View). The selected one merges with the ribbon body. */
    private final class RibbonTab extends JButton {
        private final String name;

        RibbonTab(String name) {
            super(name);
            this.name = name;
            setUI(new BasicButtonUI());
            setRolloverEnabled(true);
            setFocusable(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            applyLook();
            setBorder(new EmptyBorder(6, 16, 6, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addActionListener(e -> selectRibbonTab(name));
        }

        private void applyLook() {
            setUI(new BasicButtonUI());
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setFont(Theme.ui(Font.BOLD, 0));
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (name != null) applyLook();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean sel = name.equals(ribbonSelected);
            if (sel) {
                g2.setColor(Theme.tabActiveBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 10, 10, 10);
            } else if (getModel().isRollover()) {
                g2.setColor(Theme.tabHover);
                g2.fillRoundRect(0, 2, getWidth(), getHeight(), 8, 8);
            }
            g2.dispose();
            setForeground(sel ? Theme.tabActiveFg : Theme.tabFg);
            super.paintComponent(g);
        }
    }

    /** Flat ribbon button: icon above the label, hover / pressed highlight and an "on" state. */
    private static final class RibbonButton extends JButton {
        private final int iconSize;
        private boolean active;
        private String iconName;

        RibbonButton(String text, String icon, String tip, int iconSize) {
            super(text);
            this.iconSize = iconSize;
            setUI(new BasicButtonUI());
            setRolloverEnabled(true);
            setToolTipText(tip);
            setFocusPainted(false);
            setFocusable(false); // keeps keyboard focus in the grid
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            boolean iconOnly = text == null || text.isEmpty();
            setBorder(iconOnly ? new EmptyBorder(7, 7, 7, 7) : new EmptyBorder(5, 8, 3, 8));
            iconName = icon;
            applyLook();
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setIconTextGap(3);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setActive(boolean on) {
            if (active != on) {
                active = on;
                repaint();
            }
        }

        private void applyLook() {
            setUI(new BasicButtonUI());
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(Theme.ribbonFg);
            setFont(Theme.ui(Font.PLAIN, -1));
            if (iconName != null) setIcon(Icons.get(iconName, iconSize, Theme.ribbonIcon));
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (iconSize != 0) applyLook();
        }

        void setIconName(String icon) {
            iconName = icon;
            setIcon(Icons.get(icon, iconSize, Theme.ribbonIcon));
        }

        @Override
        protected void paintComponent(Graphics g) {
            ButtonModel m = getModel();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active) {
                g2.setColor(Theme.ribbonActive);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Theme.ribbonActiveLine);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            if (m.isPressed()) {
                g2.setColor(Theme.ribbonPress);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            } else if (m.isRollover()) {
                g2.setColor(Theme.ribbonHover);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ==================================================================
    // Formula bar and status bar
    // ==================================================================

    private JComponent buildFormulaBar() {
        JPanel bar = new TP(new BorderLayout(6, 0), () -> Theme.barBg) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Theme.border), new EmptyBorder(4, 6, 4, 6)));
                if (cellBadge != null) {
                    cellBadge.setBackground(Theme.statusBg);
                    cellBadge.setForeground(Theme.fg);
                    cellBadge.setFont(Theme.ui(Font.BOLD, 1));
                    cellBadge.setBorder(BorderFactory.createLineBorder(Theme.border));
                }
                if (formulaField != null) {
                    formulaField.setFont(Theme.ui(Font.PLAIN, 1));
                    formulaField.setBorder(new CompoundBorder(BorderFactory.createLineBorder(Theme.border), new EmptyBorder(2, 6, 2, 6)));
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, Math.max(36, 26 + 2 * Theme.uiSize() - 12));
            }
        };

        cellBadge.setPreferredSize(new Dimension(90, 26));
        cellBadge.setOpaque(true);
        cellBadge.setBackground(Theme.statusBg);
        cellBadge.setForeground(Theme.fg);
        cellBadge.setFont(Theme.ui(Font.BOLD, 1));
        cellBadge.setBorder(BorderFactory.createLineBorder(Theme.border));

        JLabel fx = new TL("fx", SwingConstants.LEADING, () -> Theme.accent, 0, 0);
        fx.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 16));
        fx.setBorder(new EmptyBorder(0, 6, 0, 6));
        fx.setToolTipText("Insert Function...");
        fx.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fx.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openInsertFunction();
            }
        });

        JPanel left = new JPanel(new BorderLayout(6, 0));
        left.setOpaque(false);
        left.add(cellBadge, BorderLayout.WEST);
        left.add(fx, BorderLayout.CENTER);

        formulaField.setFont(Theme.ui(Font.PLAIN, 1));
        formulaField.setBorder(new CompoundBorder(BorderFactory.createLineBorder(Theme.border), new EmptyBorder(2, 6, 2, 6)));
        FormulaSuggest.attach(formulaField);
        formulaField.addActionListener(e -> commitFormulaBar());
        formulaField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "revert");
        formulaField.getActionMap().put("revert", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                syncFormulaBar();
                SheetView sv = currentSheet();
                if (sv != null) sv.table.requestFocusInWindow();
            }
        });

        bar.add(left, BorderLayout.WEST);
        bar.add(formulaField, BorderLayout.CENTER);
        return bar;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new TP(new BorderLayout(), () -> Theme.statusBg) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, Theme.border), new EmptyBorder(3, 10, 3, 10)));
                if (autoSaveLabel != null) autoSaveLabel.setForeground(Theme.accent);
                if (statusLabel != null) statusLabel.setForeground(Theme.fg);
                if (statsLabel != null) statsLabel.setForeground(Theme.dimFg);
                if (zoomLabel != null) zoomLabel.setForeground(Theme.fg);
            }
        };
        bar.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, Theme.border), new EmptyBorder(3, 10, 3, 10)));
        bar.add(statusLabel, BorderLayout.WEST);
        protLabel.setForeground(new Color(0xC00000));
        protLabel.setFont(protLabel.getFont().deriveFont(Font.BOLD));
        bar.add(protLabel, BorderLayout.CENTER);
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        east.setOpaque(false);
        autoSaveLabel.setForeground(Theme.accent);
        east.add(autoSaveLabel);
        east.add(statsLabel);
        JButton minus = zoomButton("-", "Zoom out");
        minus.addActionListener(e -> zoomBy(-10));
        JButton plus = zoomButton("+", "Zoom in");
        plus.addActionListener(e -> zoomBy(10));
        zoomSlider.setOpaque(false);
        zoomSlider.setFocusable(false);
        zoomSlider.setPreferredSize(new Dimension(120, 20));
        zoomSlider.addChangeListener(e -> {
            if (!updatingUi) setZoom(zoomSlider.getValue());
        });
        zoomLabel.setPreferredSize(new Dimension(56, 18));
        zoomLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        zoomLabel.setToolTipText("Click to reset to 100%");
        zoomLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setZoom(100);
            }
        });
        east.add(minus);
        east.add(zoomSlider);
        east.add(plus);
        east.add(zoomLabel);
        bar.add(east, BorderLayout.EAST);
        return bar;
    }

    private JButton zoomButton(String text, String tip) {
        JButton b = new JButton(text);
        b.setMargin(new java.awt.Insets(0, 4, 0, 4));
        b.setPreferredSize(new Dimension(22, 20));
        b.setFocusable(false);
        b.setToolTipText(tip);
        return b;
    }

    // ------------------------------------------------------------------
    // Zoom and Insert Function
    // ------------------------------------------------------------------

    private void setZoom(int percent) {
        SheetView sv = currentSheet();
        if (sv == null) return;
        sv.setZoom(percent);
        updateZoomUi(sv);
        markDirty();
        status("Zoom " + sv.model.getZoomPercent() + "%");
    }

    private void zoomBy(int delta) {
        SheetView sv = currentSheet();
        if (sv != null) setZoom(sv.model.getZoomPercent() + delta);
    }

    private void updateZoomUi(SheetView sv) {
        int z = sv.model.getZoomPercent();
        updatingUi = true;
        zoomSlider.setValue(z);
        updatingUi = false;
        zoomLabel.setText(z + "%");
    }

    private void openInsertFunction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        new InsertFunctionDialog(this, this::insertFunction).setVisible(true);
    }

    /** Puts NAME() into the formula bar (after an existing '=' formula, at the caret) with the caret inside. */
    private void insertFunction(String name) {
        String text = formulaField.getText();
        int caret = formulaField.getCaretPosition();
        String call = name + "()";
        int newCaret;
        if (!text.startsWith("=")) {
            text = "=" + call;
            newCaret = name.length() + 2;
        } else {
            caret = Math.max(1, Math.min(caret, text.length()));
            text = text.substring(0, caret) + call + text.substring(caret);
            newCaret = caret + name.length() + 1;
        }
        formulaField.setText(text);
        formulaField.requestFocusInWindow();
        formulaField.setCaretPosition(newCaret);
        status("Inserted " + name + " - fill in the arguments and press Enter");
    }

    private void status(String message) {
        statusLabel.setText(message);
    }

    /** Pushes the current selection into the badge, formula field, size box and stats label. */
    private void syncFormulaBar() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        updateProtectUi(sv);
        updateZoomUi(sv);
        JTable t = sv.table;
        int r = t.getSelectionModel().getLeadSelectionIndex();
        int c = t.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (!sv.model.inBounds(r, c)) {
            cellBadge.setText("");
            formulaField.setText("");
            statsLabel.setText("");
            return;
        }
        int[] rows = t.getSelectedRows();
        int[] cols = t.getSelectedColumns();
        if (rows.length * cols.length > 1) {
            cellBadge.setText(FormulaEngine.cellRef(rows[0], cols[0]) + ":"
                    + FormulaEngine.cellRef(rows[rows.length - 1], cols[cols.length - 1]));
        } else {
            cellBadge.setText(FormulaEngine.cellRef(r, c));
        }
        formulaField.setText(sv.model.getInput(r, c));
        formulaField.setCaretPosition(0);

        CellStyle st = sv.model.getStyle(r, c);
        updatingUi = true;
        sizeCombo.setSelectedItem(st != null && st.fontSize > 0 ? st.fontSize : 13);
        updatingUi = false;
        if (boldBtn != null) {
            boldBtn.setActive(st != null && st.bold);
            italicBtn.setActive(st != null && st.italic);
            alignLeftBtn.setActive(st != null && st.alignment == SwingConstants.LEFT);
            alignCenterBtn.setActive(st != null && st.alignment == SwingConstants.CENTER);
            alignRightBtn.setActive(st != null && st.alignment == SwingConstants.RIGHT);
        }
        updatingUi = true;
        fontCombo.setSelectedItem(st != null && st.fontFamily != null ? st.fontFamily : DEFAULT_FONT);
        numFmtCombo.setSelectedIndex(st != null ? st.numFmt : 0);
        updatingUi = false;
        if (showFormulasBtn != null) showFormulasBtn.setActive(sv.model.isShowFormulas());
        if (filterBtn != null) filterBtn.setActive(sv.model.getFilter() != null);
        if (circleBtn != null) circleBtn.setActive(sv.circleInvalid);

        // Excel-like statistics for multi-cell selections
        int count = 0;
        double sum = 0;
        if (rows.length * cols.length > 1) {
            for (int rr : rows) {
                for (int cc : cols) {
                    Object v = sv.model.getComputedValue(rr, cc);
                    if (v instanceof Double) {
                        count++;
                        sum += (Double) v;
                    }
                }
            }
        }
        statsLabel.setText(count > 0
                ? "Count: " + count + "     Sum: " + FormulaEngine.formatNumber(sum)
                + "     Average: " + FormulaEngine.formatNumber(sum / count)
                : "");
    }

    /** Enter in the formula bar: write the text into the active cell and move down. */
    private void commitFormulaBar() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        int r = sv.table.getSelectionModel().getLeadSelectionIndex();
        int c = sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (!sv.model.inBounds(r, c)) return;
        if (!guardEditable(sv)) return;
        if (sv.table.isEditing()) sv.table.getCellEditor().cancelCellEditing();
        sv.model.setInput(r, c, formulaField.getText());
        if (r + 1 < sv.model.getRowCount()) sv.table.changeSelection(r + 1, c, false, false);
        sv.table.requestFocusInWindow();
    }

    // ==================================================================
    // Menu bar
    // ==================================================================

    private JMenuItem menuItem(String text, int key, int modifiers, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        String ic = menuIconFor(text);
        if (ic != null) {
            item.setIcon(Icons.get(ic, 16, Theme.fg));
            item.putClientProperty("icon", ic);
        }
        if (key != 0) item.setAccelerator(KeyStroke.getKeyStroke(key, modifiers));
        item.addActionListener(e -> action.run());
        return item;
    }

    private static String menuIconFor(String text) {
        switch (text) {
            case "New Workbook":
            case "New Sheet":
                return "newsheet";
            case "Open...":
                return "open";
            case "Undo":
                return "undo";
            case "Redo":
                return "redo";
            case "Cut":
                return "cut";
            case "Copy":
                return "copy";
            case "Paste":
            case "Paste Values":
                return "paste";
            case "Print...":
                return "print";
            case "Print Preview...":
                return "preview";
            case "Export as PDF...":
                return "pdf";
            case "Page Setup...":
                return "pagesetup";
            case "Version History...":
                return "history";
            case "Save Version...":
                return "snapshot";
            case "Recover Unsaved Workbooks...":
                return "recover";
            case "Command Palette...":
                return "cmdpalette";
            case "Save":
            case "Save As...":
                return "save";
            case "Set Open Password...":
            case "Protect Sheet...":
                return "lock";
            case "Unprotect Sheet...":
                return "unlock";
            case "Clear Contents":
            case "Delete Selected Picture":
                return "trash";
            case "Zoom In":
                return "zoomin";
            case "Zoom Out":
                return "zoomout";
            case "Zoom 100%":
                return "zoomreset";
            case "Reset Column Widths":
                return "columns";
            case "Function...":
                return "function";
            case "Picture from File...":
                return "picture";
            case "Picture from Clipboard":
                return "clipboard";
            case "Date / Sample Data / Chart...":
                return "chart";
            case "Formula Reference":
                return "help";
            case "AutoSave Settings...":
                return "autosave";
            case "Keyboard Shortcuts":
                return "keyboard";
            case "About Spreadsheet Pro":
                return "about";
            case "Check for Updates...":
                return "update";
            default:
                return null;
        }
    }

    private JMenuBar buildMenuBar() {
        int ctrl = InputEvent.CTRL_DOWN_MASK;
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(menuItem("File Screen", KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK, () -> showBackstage("Info")));
        file.addSeparator();
        file.add(menuItem("New Workbook", KeyEvent.VK_N, ctrl, this::newWorkbook));
        file.add(menuItem("Open...", KeyEvent.VK_O, ctrl, this::openWorkbook));
        file.add(menuItem("Save", KeyEvent.VK_S, ctrl, () -> saveWorkbook()));
        file.add(menuItem("Open Excel File (.xlsx)...", 0, 0, this::openExcelDialog));
        file.add(menuItem("Export to Excel (.xlsx)...", 0, 0, this::exportExcel));
        file.add(menuItem("Save As...", KeyEvent.VK_S, ctrl | InputEvent.SHIFT_DOWN_MASK, () -> saveWorkbookAs()));
        file.addSeparator();
        file.add(menuItem("Print...", KeyEvent.VK_P, ctrl, this::printSheet));
        file.add(menuItem("Print Preview...", 0, 0, this::printPreview));
        file.add(menuItem("Page Setup...", 0, 0, this::pageSetupDialog));
        file.add(menuItem("Export as PDF...", 0, 0, this::exportPdf));
        file.addSeparator();
        file.add(menuItem("Version History...", 0, 0, this::versionHistory));
        file.add(menuItem("Save Version...", 0, 0, this::saveNamedVersion));
        file.add(menuItem("Recover Unsaved Workbooks...", 0, 0, this::recoverUnsaved));
        file.addSeparator();
        file.add(menuItem("Set Open Password...", 0, 0, this::setOpenPassword));
        autoSaveItem = new JCheckBoxMenuItem("AutoSave", autoSave.isEnabled());
        autoSaveItem.addActionListener(e -> setAutoSave(autoSaveItem.isSelected()));
        file.add(autoSaveItem);
        file.add(menuItem("AutoSave Settings...", 0, 0, this::showAutoSaveSettings));
        file.addSeparator();
        file.add(menuItem("Import CSV...", 0, 0, this::importCsv));
        file.add(menuItem("Export CSV...", 0, 0, this::exportCsv));
        file.addSeparator();
        file.add(menuItem("Exit", 0, 0, this::exitApp));

        JMenu edit = new JMenu("Edit");
        edit.add(menuItem("Undo", 0, 0, this::undoAction));
        edit.add(menuItem("Redo", 0, 0, this::redoAction));
        edit.addSeparator();
        edit.add(menuItem("Cut", 0, 0, () -> copyFromMenu(true)));
        edit.add(menuItem("Copy", 0, 0, () -> copyFromMenu(false)));
        edit.add(menuItem("Paste", 0, 0, () -> pasteFromClipboard(false)));
        edit.add(menuItem("Paste Values", 0, 0, () -> pasteFromClipboard(true)));
        edit.add(menuItem("Find...", KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK, () -> showFind(false)));
        edit.add(menuItem("Replace...", KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK, () -> showFind(true)));
        edit.add(menuItem("Insert Link...", KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK, this::insertLink));
        edit.add(menuItem("Quick Analysis", KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK, () -> {
            SheetView q = currentSheet();
            if (q == null) return;
            java.awt.Rectangle r = q.quickRectPublic();
            if (r != null) showQuickAnalysis(q.table, r.x, r.y + r.height + 2);
            else showQuickAnalysis(q.table, 20, 20);
        }));
        edit.addSeparator();
        edit.addSeparator();
        edit.add(menuItem("Fill Down", KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK, () -> fillDirection(FillSeries.Dir.DOWN)));
        edit.add(menuItem("Fill Right", KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK, () -> fillDirection(FillSeries.Dir.RIGHT)));
        edit.add(menuItem("Fill Series...", 0, 0, this::seriesDialog));
        edit.addSeparator();
        edit.add(menuItem("Clear Contents", 0, 0, this::clearSelection));
        edit.add(menuItem("Clear Formatting", 0, 0, () -> applyToSelection(CellStyle::reset)));
        edit.addSeparator();
        edit.add(menuItem("Delete Selected Picture", 0, 0, this::deleteSelectedPicture));

        JMenu view = new JMenu("View");
        view.add(menuItem("Zoom In", KeyEvent.VK_EQUALS, ctrl, () -> zoomBy(10)));
        view.add(menuItem("Zoom Out", KeyEvent.VK_MINUS, ctrl, () -> zoomBy(-10)));
        view.add(menuItem("Zoom 100%", KeyEvent.VK_0, ctrl, () -> setZoom(100)));
        view.addSeparator();
        for (int z : new int[]{50, 75, 100, 125, 150, 200}) {
            view.add(menuItem(z + "%", 0, 0, () -> setZoom(z)));
        }
        view.addSeparator();
        view.add(menuItem("Reset Column Widths", 0, 0, this::resetColumnWidths));
        view.addSeparator();
        view.add(menuItem("Appearance & Language...", 0, 0, this::openAppearance));

        JMenu insert = new JMenu("Insert");
        insert.add(menuItem("New Sheet", KeyEvent.VK_F11, InputEvent.SHIFT_DOWN_MASK, () -> addSheet(nextSheetName())));
        insert.add(menuItem("Function...", KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK, this::openInsertFunction));
        insert.addSeparator();
        insert.add(menuItem("Picture from File...", 0, 0, this::insertPictureFromFile));
        insert.add(menuItem("Picture from Clipboard", 0, 0, this::insertPictureFromClipboard));
        insert.addSeparator();
        insert.add(menuItem("Date / Sample Data / Chart...", 0, 0, this::openInsertDialog));

        JMenu review = new JMenu("Review");
        review.add(menuItem("Protect Sheet...", 0, 0, this::protectSheetDialog));
        review.add(menuItem("Unprotect Sheet...", 0, 0, this::unprotectSheetDialog));
        review.addSeparator();
        readOnlyItem = new JCheckBoxMenuItem("Read-Only Mode", readOnlyMode);
        readOnlyItem.addActionListener(e -> setReadOnly(readOnlyItem.isSelected()));
        review.add(readOnlyItem);
        review.add(menuItem("Spelling...", KeyEvent.VK_F7, 0, this::spellCheck));
        review.add(menuItem("Version History...", 0, 0, this::versionHistory));

        JMenu help = new JMenu("Help");
        help.add(menuItem("Formula Reference", 0, 0, this::showFormulaHelp));
        help.add(menuItem("Keyboard Shortcuts", KeyEvent.VK_SLASH, ctrl, this::showShortcuts));
        help.add(menuItem("Command Palette...", KeyEvent.VK_P, ctrl | InputEvent.SHIFT_DOWN_MASK, this::openCommandPalette));
        help.add(menuItem("Check for Updates...", 0, 0, () -> checkForUpdates(true)));
        JCheckBoxMenuItem autoUpdate = new JCheckBoxMenuItem("Check for updates when starting", UpdateChecker.autoCheckEnabled());
        autoUpdate.addActionListener(e -> UpdateChecker.setAutoCheck(autoUpdate.isSelected()));
        help.add(autoUpdate);
        help.add(menuItem("About Spreadsheet Pro", 0, 0, this::showAbout));

        bar.add(file);
        bar.add(edit);
        bar.add(view);
        bar.add(insert);
        bar.add(review);
        bar.add(help);
        return bar;
    }

    private JMenuItem ctxItem(String text, String icon, Runnable action) {
        JMenuItem item = new JMenuItem(I18n.t(text), Icons.get(icon, 16, Theme.fg));
        item.addActionListener(e -> action.run());
        return item;
    }

    /** Right-click menu for the grid. */
    private void showCellMenu(SheetView sv, MouseEvent e) {
        JTable t = sv.table;
        int r = t.rowAtPoint(e.getPoint());
        int c = t.columnAtPoint(e.getPoint());
        if (r < 0 || c < 0) return;
        if (!t.isCellSelected(r, c)) t.changeSelection(r, c, false, false);
        t.requestFocusInWindow();
        JPopupMenu m = new JPopupMenu();
        m.add(ctxItem("Insert Function...", "function", this::openInsertFunction));
        m.add(ctxItem("Clear Contents", "trash", this::clearSelection));
        m.addSeparator();
        m.add(ctxItem("Bold", "bold", () -> toggleStyle(true)));
        m.add(ctxItem("Italic", "italic", () -> toggleStyle(false)));
        m.add(ctxItem("Align Left", "alignleft", () -> align(SwingConstants.LEFT)));
        m.add(ctxItem("Align Center", "aligncenter", () -> align(SwingConstants.CENTER)));
        m.add(ctxItem("Align Right", "alignright", () -> align(SwingConstants.RIGHT)));
        m.addSeparator();
        m.add(ctxItem("Fill Color...", "fill", this::pickFillColor));
        m.add(ctxItem("No Fill", "nofill", () -> applyToSelection(st -> st.background = null)));
        m.addSeparator();
        final int col = c;
        m.add(ctxItem(I18n.code().equals("en") ? "Fit Column " + FormulaEngine.columnLabel(c) + " to Content" : I18n.t("Fit Column ") + FormulaEngine.columnLabel(c), "columns", () -> sv.autoFitColumn(col)));
        m.add(ctxItem("Insert Picture...", "picture", this::insertPictureFromFile));
        m.addSeparator();
        m.add(ctxItem(sv.model.linkAt(r, c) == null ? "Link..." : "Edit Link...", "link", this::insertLink));
        if (sv.model.linkAt(r, c) != null) {
            m.add(ctxItem("Open Link", "link", () -> openLinkAt(sv, r, c)));
            if (sv.model.explicitLinkAt(r, c) != null) m.add(ctxItem("Remove Link", "trash", this::removeLinks));
        }
        m.add(ctxItem(sv.model.commentAt(r, c) == null ? "Add Note..." : "Edit Note...", "comment", this::editNote));
        if (sv.model.commentAt(r, c) != null) m.add(ctxItem("Delete Note", "trash", this::deleteNotes));
        m.show(t, e.getX(), e.getY());
    }

    // ------------------------------------------------------------------
    // Undo / redo and clipboard
    // ------------------------------------------------------------------

    private void resetAllUndo() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component c = tabs.getComponentAt(i);
            if (c instanceof SheetView && ((SheetView) c).undo != null) ((SheetView) c).undo.reset();
        }
    }

    private void undoAction() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        if (sv.table.isEditing()) sv.table.getCellEditor().cancelCellEditing();
        if (sv.model.isReadOnly()) {
            status("This workbook is read-only.");
            return;
        }
        status(sv.undo.undo() ? "Undone" : "Nothing to undo");
    }

    private void redoAction() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        if (sv.table.isEditing()) sv.table.getCellEditor().cancelCellEditing();
        if (sv.model.isReadOnly()) {
            status("This workbook is read-only.");
            return;
        }
        status(sv.undo.redo() ? "Redone" : "Nothing to redo");
    }

    private int[] clipRect(SheetView sv) {
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) {
            int r = Math.max(0, sv.table.getSelectionModel().getLeadSelectionIndex());
            int c = Math.max(0, sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
            return new int[]{r, c, r, c};
        }
        return new int[]{rows[0], cols[0], rows[rows.length - 1], cols[cols.length - 1]};
    }

    private void setClipboard(java.awt.datatransfer.Transferable t) {
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                cb.setContents(t, null);
                return;
            } catch (IllegalStateException busy) {
                try {
                    Thread.sleep(40);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        status("The clipboard is busy - try again");
    }

    private void copyCells(SheetView sv, boolean cut) {
        if (sv == null) return;
        if (cut && !guardEditable(sv)) return;
        int[] r = clipRect(sv);
        setClipboard(CellClipboard.copy(sv.model, r[0], r[1], r[2], r[3], cut));
        sv.showAnts(r);
        int n = (r[2] - r[0] + 1) * (r[3] - r[1] + 1);
        status((cut ? "Cut " : "Copied ") + n + " cell(s) - select where to paste and press Ctrl+V");
    }

    private void copyFromMenu(boolean cut) {
        copyCells(currentSheet(), cut);
    }

    private void pasteFromClipboard(boolean valuesOnly) {
        SheetView sv = currentSheet();
        if (sv == null) return;
        try {
            pasteCells(sv, Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null), valuesOnly);
        } catch (IllegalStateException busy) {
            status("The clipboard is busy - try again");
        }
    }

    private boolean pasteCells(SheetView sv, java.awt.datatransfer.Transferable t, boolean valuesOnly) {
        if (sv == null || t == null) return false;
        boolean hasCells = t.isDataFlavorSupported(CellClipboard.FLAVOR);
        boolean hasText = t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
        if (!hasCells && !hasText && t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
            insertPictureFromClipboard();
            return true;
        }
        if (!guardEditable(sv)) return false;
        if (sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        int[] sel = clipRect(sv);
        int[] done = CellClipboard.paste(sv.model, t, sel[0], sel[1], sel[0], sel[1], sel[2], sel[3], valuesOnly);
        if (done == null) {
            status("Nothing to paste");
            return false;
        }
        sv.table.setRowSelectionInterval(done[0], done[2]);
        sv.table.setColumnSelectionInterval(done[1], done[3]);
        sv.table.changeSelection(done[0], done[1], false, false);
        sv.table.changeSelection(done[2], done[3], false, true);
        if (hasCells) {
            // a cut can only be pasted once
            try {
                CellClipboard.Data d = (CellClipboard.Data) t.getTransferData(CellClipboard.FLAVOR);
                if (d.cut) {
                    sv.clearAnts();
                    setClipboard(new java.awt.datatransfer.StringSelection(d.text));
                }
            } catch (java.awt.datatransfer.UnsupportedFlavorException | java.io.IOException ignored) {
                // leave the clipboard as it is
            }
        }
        markDirty();
        status("Pasted " + ((done[2] - done[0] + 1) * (done[3] - done[1] + 1)) + " cell(s)");
        return true;
    }

    // ------------------------------------------------------------------
    // Appearance: theme, fonts, language
    // ------------------------------------------------------------------

    private SettingsDialog settingsDialog;

    private void openAppearance() {
        if (settingsDialog != null && settingsDialog.isDisplayable()) {
            settingsDialog.toFront();
            return;
        }
        settingsDialog = new SettingsDialog(this, this::applyAppearance);
        settingsDialog.setVisible(true);
    }

    /** Pushes the current theme, fonts and language into every open window. Called after any setting changes. */
    void applyAppearance() {
        Theme.install();
        Theme.refresh();
        AppSettings.save();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component c = tabs.getComponentAt(i);
            if (c instanceof SheetView) ((SheetView) c).restyle();
        }
        fillChip.setBorder(BorderFactory.createLineBorder(Theme.ribbonFg, 1));
        textChip.setBorder(BorderFactory.createLineBorder(Theme.ribbonFg, 1));
        drawChip.setBorder(BorderFactory.createLineBorder(Theme.ribbonFg, 2));
        widthLabel.setForeground(Theme.ribbonFg);
        restyleRibbonLeaves(northPanel);
        recolorMenuIcons(getJMenuBar());
        JMenuBar mb = getJMenuBar();
        mb.setOpaque(true);
        mb.setBackground(Theme.stripBg);
        for (int i = 0; i < mb.getMenuCount(); i++) {
            mb.getMenu(i).setForeground(Theme.tabFg);
            mb.getMenu(i).setOpaque(false);
        }
        tabs.setBackground(Theme.stripBg);
        tabs.setForeground(Theme.fg);
        applyLanguage();
        revalidate();
        repaint();
    }

    private void restyleRibbonLeaves(Component c) {
        if (c instanceof JCheckBox) {
            c.setForeground(Theme.ribbonFg);
            c.setFont(Theme.ui(Font.PLAIN, -1));
        }
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) restyleRibbonLeaves(k);
    }

    private void recolorMenuIcons(Component c) {
        if (c instanceof JMenuItem) {
            Object ic = ((JMenuItem) c).getClientProperty("icon");
            if (ic != null) ((JMenuItem) c).setIcon(Icons.get(ic.toString(), 16, Theme.fg));
        }
        if (c instanceof JMenu) for (Component k : ((JMenu) c).getMenuComponents()) recolorMenuIcons(k);
        else if (c instanceof Container) for (Component k : ((Container) c).getComponents()) recolorMenuIcons(k);
    }

    /** Translates the ribbon, menus and status bar and sets the reading direction of the chrome (not the grid). */
    private void applyLanguage() {
        boolean rtl = I18n.isRtl();
        I18n.apply(getJMenuBar());
        I18n.apply(northPanel);
        I18n.apply(statusBarPanel);
        I18n.orient(getJMenuBar(), rtl);
        I18n.orient(northPanel, rtl);
        I18n.orient(statusBarPanel, rtl);
        I18n.orient(formulaBarPanel, false); // cell addresses and formulas always read left to right
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component c = tabs.getComponentAt(i);
            if (c instanceof SheetView) I18n.orient(c, false);
        }
        for (RibbonTab t : ribbonTabs) t.revalidate();
        if (autoSaveItem != null) autoSaveItem.setText(I18n.t("AutoSave"));
    }

    private void resetColumnWidths() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        sv.model.resetColumnWidths();
        sv.applyZoom();
        markDirty();
        status("Column widths reset");
    }

    private void showFormulaHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("Formulas start with '='.\n\n")
          .append("Operators:   +  -  *  /  ^  %  &  = <> < > <= >=  and parentheses\n")
          .append("References:  =A1+B2*5      Ranges: =SUM(A1:A10)\n")
          .append("Text goes in quotes: =\"Hello \"&A1      TRUE and FALSE are values\n\n")
          .append("Click the fx button (or Insert > Function) to browse functions.\n\n");
        String cat = "";
        for (FunctionCatalog.Entry e : FunctionCatalog.all()) {
            if (!e.category.equals(cat)) {
                cat = e.category;
                sb.append("\n== ").append(cat).append(" ==\n");
            }
            sb.append("  ").append(e.syntax).append("\n");
        }
        sb.append("\nErrors:\n")
          .append("  #DIV/0!   division by zero\n")
          .append("  #SYNTAX!  malformed formula or wrong number of arguments\n")
          .append("  #NAME?    unknown function or name\n")
          .append("  #N/A      lookup value not found\n")
          .append("  #ERROR!   bad reference, text used as a number, circular reference\n\n")
          .append("View: Ctrl+mouse wheel or Ctrl +/- / 0 zoom; drag a column header edge to resize,\n")
          .append("double-click the edge to fit the content.\n")
          .append("Shortcuts: Ctrl+B bold, Ctrl+I italic, Ctrl+L/E/R align, Delete clear, F2 edit cell");
        JTextArea text = new JTextArea(sb.toString(), 24, 62);
        text.setEditable(false);
        text.setCaretPosition(0);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(text), "Formula Reference", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================================================================
    // Sheets (tabs)
    // ==================================================================

    private String nextSheetName() {
        return "Sheet" + (++sheetCounter);
    }

    private SheetView currentSheet() {
        Component c = tabs.getSelectedComponent();
        return c instanceof SheetView ? (SheetView) c : null;
    }

    private SheetView addSheet(String name) {
        if (readOnlyMode && tabs.getTabCount() > 0) {
            status("This workbook is read-only. Turn off Review > Read-Only Mode to add sheets.");
            Toolkit.getDefaultToolkit().beep();
            return currentSheet();
        }
        return addSheet(name, new SpreadsheetModel());
    }

    private SheetView addSheet(String name, SpreadsheetModel model) {
        model.setReadOnly(readOnlyMode);
        SheetView sv = new SheetView(model);
        tabs.addTab(name, sv);
        int idx = tabs.getTabCount() - 1;
        TabHeader header = new TabHeader(name);
        header.setLocked(model.isSheetProtected());
        tabs.setTabComponentAt(idx, header);
        tabs.setSelectedIndex(idx);
        SwingUtilities.invokeLater(() -> sv.table.requestFocusInWindow());
        status("Added " + name);
        markDirty();
        return sv;
    }

    private void deleteSheet(int index) {
        if (index < 0) return;
        if (readOnlyMode) {
            status("This workbook is read-only. Turn off Review > Read-Only Mode to delete sheets.");
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        if (tabs.getTabCount() <= 1) {
            JOptionPane.showMessageDialog(this, "A workbook must contain at least one sheet.",
                    "Delete Sheet", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Component comp = tabs.getComponentAt(index);
        if (comp instanceof SheetView && ((SheetView) comp).model.isSheetProtected()) {
            JOptionPane.showMessageDialog(this, "This sheet is protected. Unprotect it before deleting it.",
                    "Delete Sheet", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String title = tabs.getTitleAt(index);
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete \"" + title + "\"? This cannot be undone.", "Delete Sheet",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            tabs.removeTabAt(index);
            status("Deleted " + title);
            markDirty();
        }
    }

    private void renameSheet(int index) {
        if (readOnlyMode) {
            status("This workbook is read-only. Turn off Review > Read-Only Mode to rename sheets.");
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        if (index < 0) return;
        Component header = tabs.getTabComponentAt(index);
        if (header instanceof TabHeader) {
            ((TabHeader) header).startRename();
            return;
        }
        Object input = JOptionPane.showInputDialog(this, "Sheet name:", "Rename Sheet",
                JOptionPane.PLAIN_MESSAGE, null, null, tabs.getTitleAt(index));
        if (input != null) applyRename(index, input.toString());
    }

    /** @return false when the name was refused (empty or already used) */
    private boolean applyRename(int index, String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > 60) return false;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (i != index && tabs.getTitleAt(i).equalsIgnoreCase(name)) {
                status("There is already a sheet called \"" + name + "\" - pick another name.");
                return false;
            }
        }
        if (name.equals(tabs.getTitleAt(index))) return true;
        tabs.setTitleAt(index, name);
        Component header = tabs.getTabComponentAt(index);
        if (header instanceof TabHeader) ((TabHeader) header).setTitle(name);
        markDirty();
        return true;
    }

    /** Moves a sheet tab (and its sheet) so it ends up at position {@code to}. */
    private void moveSheetTab(int from, int to) {
        if (from < 0 || to < 0 || from == to || from >= tabs.getTabCount() || to >= tabs.getTabCount()) return;
        Component comp = tabs.getComponentAt(from);
        String title = tabs.getTitleAt(from);
        Component hdr = tabs.getTabComponentAt(from);
        tabs.removeTabAt(from);
        tabs.insertTab(title, null, comp, null, to);
        tabs.setTabComponentAt(to, hdr);
        tabs.setSelectedIndex(to);
        markDirty();
    }

    /** Copies a sheet (cells, formats, charts, pictures ...) and puts the copy at {@code insertAt} (or the end if negative). */
    private void duplicateSheet(int index, int insertAt) {
        if (readOnlyMode) {
            status("This workbook is read-only. Turn off Review > Read-Only Mode to copy sheets.");
            return;
        }
        if (index < 0 || !(tabs.getComponentAt(index) instanceof SheetView)) return;
        SheetView src = (SheetView) tabs.getComponentAt(index);
        String base = tabs.getTitleAt(index);
        String name = null;
        for (int n = 2; name == null; n++) {
            String cand = base + " (" + n + ")";
            boolean used = false;
            for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getTitleAt(i).equalsIgnoreCase(cand)) used = true;
            if (!used) name = cand;
        }
        File tmp = null;
        try {
            tmp = File.createTempFile("spx-copy", ".spx");
            List<WorkbookIO.SheetData> one = new ArrayList<>();
            one.add(new WorkbookIO.SheetData(base, src.model));
            WorkbookIO.save(tmp, one, 0, null);
            WorkbookIO.WorkbookData data = WorkbookIO.load(tmp, null);
            addSheet(name, data.sheets.get(0).model);
            int last = tabs.getTabCount() - 1;
            int at = insertAt < 0 ? Math.min(index + 1, last) : Math.min(insertAt, last);
            moveSheetTab(last, at);
            status("Copied \"" + base + "\" to \"" + name + "\"");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not copy the sheet: " + ex.getMessage(), "Copy Sheet", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static final int[] TAB_COLORS = {0xC00000, 0xED7D31, 0xFFC000, 0x00B050, 0x00B0F0, 0x2E75B6, 0x7030A0, 0xFF66CC, 0x7F7F7F, 0x1F2933};

    private void setTabColor(int index, Color c) {
        if (index < 0 || !(tabs.getComponentAt(index) instanceof SheetView)) return;
        ((SheetView) tabs.getComponentAt(index)).model.setTabColor(c);
        Component h = tabs.getTabComponentAt(index);
        if (h != null) h.repaint();
        tabs.repaint();
        markDirty();
    }

    /** Custom tab title with a close button, a rename-on-double-click and a context menu. */
    private final class TabHeader extends JPanel {
        private final JLabel title;
        private final JLabel lockLabel = new JLabel(new LockIcon());

        private boolean dragging;
        private int dragFrom = -1;

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int i = tabs.indexOfTabComponent(this);
            if (i >= 0 && tabs.getComponentAt(i) instanceof SheetView) {
                Color c = ((SheetView) tabs.getComponentAt(i)).model.getTabColor();
                if (c != null) {
                    g.setColor(c);
                    g.fillRect(1, getHeight() - 4, getWidth() - 2, 4);
                }
            }
        }

        void startRename() {
            int k = getComponentZOrder(title);
            if (k < 0) return;
            JTextField f = new JTextField(title.getText(), Math.max(6, title.getText().length() + 2));
            f.setBorder(BorderFactory.createLineBorder(Theme.accent));
            f.setFont(title.getFont());
            remove(title);
            add(f, k);
            revalidate();
            repaint();
            final boolean[] done = {false};
            Runnable finish = () -> {
                if (done[0]) return;
                done[0] = true;
                int idx = tabs.indexOfTabComponent(TabHeader.this);
                if (idx >= 0) applyRename(idx, f.getText());
                remove(f);
                add(title, k);
                revalidate();
                repaint();
                SheetView sv = currentSheet();
                if (sv != null) sv.table.requestFocusInWindow();
            };
            f.addActionListener(e -> finish.run());
            f.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    finish.run();
                }
            });
            f.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelRename");
            f.getActionMap().put("cancelRename", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    f.setText(title.getText());
                    finish.run();
                }
            });
            SwingUtilities.invokeLater(() -> {
                f.requestFocusInWindow();
                f.selectAll();
            });
        }

        TabHeader(String name) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            lockLabel.setVisible(false);
            lockLabel.setToolTipText("This sheet is protected");
            add(lockLabel);
            title = new JLabel(name);
            add(title);

            JButton close = new JButton("\u00D7");
            close.setFocusable(false);
            close.setBorder(new EmptyBorder(0, 3, 0, 3));
            close.setContentAreaFilled(false);
            close.setFont(close.getFont().deriveFont(Font.BOLD, 14f));
            close.setForeground(Color.GRAY);
            close.setToolTipText("Close sheet");
            close.addActionListener(e -> deleteSheet(tabs.indexOfTabComponent(TabHeader.this)));
            close.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    close.setForeground(new Color(0xC00000));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    close.setForeground(Color.GRAY);
                }
            });
            add(close);

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = tabs.indexOfTabComponent(TabHeader.this);
                    if (i >= 0) tabs.setSelectedIndex(i);
                    if (e.isPopupTrigger()) showMenu(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) showMenu(e);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        renameSheet(tabs.indexOfTabComponent(TabHeader.this));
                    }
                }
            };
            addMouseListener(ma);
            title.addMouseListener(ma);
            MouseAdapter drag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragFrom = tabs.indexOfTabComponent(TabHeader.this);
                    dragging = false;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || dragFrom < 0) return;
                    dragging = true;
                    setCursor(Cursor.getPredefinedCursor(e.isControlDown() ? Cursor.CROSSHAIR_CURSOR : Cursor.MOVE_CURSOR));
                    if (e.isControlDown()) return; // Ctrl + drag copies on release
                    int from = tabs.indexOfTabComponent(TabHeader.this);
                    int to = dropIndex(e);
                    if (to >= 0 && to != from) moveSheetTab(from, to);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    setCursor(Cursor.getDefaultCursor());
                    if (dragging && e.isControlDown() && SwingUtilities.isLeftMouseButton(e)) {
                        int to = dropIndex(e);
                        duplicateSheet(tabs.indexOfTabComponent(TabHeader.this), to < 0 ? -1 : to);
                    }
                    dragging = false;
                    dragFrom = -1;
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
            title.addMouseListener(drag);
            title.addMouseMotionListener(drag);
        }

        private int dropIndex(MouseEvent e) {
            java.awt.Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), tabs);
            return tabs.indexAtLocation(p.x, p.y);
        }

        void setTitle(String name) {
            title.setText(name);
        }

        void setLocked(boolean locked) {
            lockLabel.setVisible(locked);
            revalidate();
            repaint();
        }

        private void showMenu(MouseEvent e) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem rename = new JMenuItem("Rename...");
            rename.addActionListener(a -> renameSheet(tabs.indexOfTabComponent(TabHeader.this)));
            JMenuItem delete = new JMenuItem("Delete");
            delete.addActionListener(a -> deleteSheet(tabs.indexOfTabComponent(TabHeader.this)));
            JMenu color = new JMenu("Tab Color");
            for (int rgb : TAB_COLORS) {
                JMenuItem it = new JMenuItem("      ");
                it.setOpaque(true);
                it.setBackground(new Color(rgb));
                it.addActionListener(a -> setTabColor(tabs.indexOfTabComponent(TabHeader.this), new Color(rgb)));
                color.add(it);
            }
            JMenuItem none = new JMenuItem("No Color");
            none.addActionListener(a -> setTabColor(tabs.indexOfTabComponent(TabHeader.this), null));
            color.add(none);
            JMenuItem copy = new JMenuItem("Duplicate");
            copy.addActionListener(a -> duplicateSheet(tabs.indexOfTabComponent(TabHeader.this), -1));
            JMenuItem left = new JMenuItem("Move Left");
            left.addActionListener(a -> { int i = tabs.indexOfTabComponent(TabHeader.this); moveSheetTab(i, i - 1); });
            JMenuItem right = new JMenuItem("Move Right");
            right.addActionListener(a -> { int i = tabs.indexOfTabComponent(TabHeader.this); moveSheetTab(i, i + 1); });
            menu.add(rename);
            menu.add(color);
            menu.add(copy);
            menu.addSeparator();
            menu.add(left);
            menu.add(right);
            menu.addSeparator();
            menu.add(delete);
            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    // ==================================================================
    // One worksheet view (table + headers)
    // ==================================================================

    private final class SheetView extends JPanel {
        final SpreadsheetModel model;
        final JTable table;
        final JList<String> rowHeader;
        ImageLayer layer; // pictures floating above the grid
        ChartLayer charts; // interactive charts above the pictures
        FillHandle fill; // drag the selection corner to fill cells
        Runnable fillUndo; // puts back the cells overwritten by the last fill
        SheetUndo undo; // undo / redo history of this sheet
        int[] copyRect; // {r1, c1, r2, c2} of the range with the "marching ants" frame, or null
        private float antsPhase;
        private javax.swing.Timer antsTimer;
        boolean circleInvalid; // "Circle Invalid Data" is switched on
        List<int[]> invalidCache = new ArrayList<>();
        boolean invalidDirty = true;
        DrawingLayer drawing; // pen / shapes painted above the charts
        private final SpreadsheetCellRenderer.ColumnHeaderRenderer colHeaderRenderer =
                new SpreadsheetCellRenderer.ColumnHeaderRenderer();
        private SpreadsheetCellRenderer.RowHeaderRenderer rowHeaderRenderer;
        final SpreadsheetCellRenderer cellRenderer;
        final FormulaCellEditor cellEditor;
        FormulaPointer pointer;
        private final JScrollPane scroll;
        private boolean applying = false;
        private boolean hiddenApplied = false;

        /** The small Quick Analysis button that appears under a multi-cell selection. */
        private java.awt.Rectangle quickBtn;

        private java.awt.Rectangle quickRect() {
            if (table.isEditing() || table.getSelectionModel().getValueIsAdjusting()
                    || table.getColumnModel().getSelectionModel().getValueIsAdjusting()) return null;
            int[] rows = table.getSelectedRows();
            int[] cols = table.getSelectedColumns();
            if (rows.length == 0 || cols.length == 0 || rows.length * cols.length < 2) return null;
            if (!model.hasDataIn(rows[0], cols[0], rows[rows.length - 1], cols[cols.length - 1])) return null;
            java.awt.Rectangle box = table.getCellRect(rows[0], cols[0], true)
                    .union(table.getCellRect(rows[rows.length - 1], cols[cols.length - 1], true));
            return new java.awt.Rectangle(box.x + box.width - 24, box.y + box.height + 3, 22, 22);
        }

        java.awt.Rectangle quickRectPublic() { return quickRect(); }

        void paintQuickButton(Graphics g0) {
            quickBtn = quickRect();
            if (quickBtn == null) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.fieldBg);
            g.fillRoundRect(quickBtn.x, quickBtn.y, quickBtn.width, quickBtn.height, 6, 6);
            g.setColor(Theme.accent);
            g.drawRoundRect(quickBtn.x, quickBtn.y, quickBtn.width - 1, quickBtn.height - 1, 6, 6);
            Icons.get("quick", 16, Theme.accent).paintIcon(table, g, quickBtn.x + 3, quickBtn.y + 3);
            g.dispose();
        }

        boolean handleQuickClick(MouseEvent e) {
            if (quickBtn == null || e.getButton() != MouseEvent.BUTTON1 || !quickBtn.contains(e.getPoint())) return false;
            if (e.getID() == MouseEvent.MOUSE_PRESSED) showQuickAnalysis(table, quickBtn.x, quickBtn.y + quickBtn.height + 2);
            e.consume();
            return true;
        }

        SheetView(SpreadsheetModel sheetModel) {
            super(new BorderLayout());
            this.model = sheetModel;

            table = new JTable(model) {
                @Override
                public void scrollRectToVisible(java.awt.Rectangle r) {
                    if (!SheetView.this.glideScroll(r)) super.scrollRectToVisible(r);
                }

                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (SheetView.this.fill != null) SheetView.this.fill.paint(g);
                    SheetView.this.paintValidationOverlay(g);
                    if (SheetView.this.layer != null) SheetView.this.layer.paint(g);
                    if (SheetView.this.charts != null) SheetView.this.charts.paint(g);
                    if (SheetView.this.drawing != null) SheetView.this.drawing.paint(g);
                    if (SheetView.this.pointer != null) SheetView.this.pointer.paint(g);
                    SheetView.this.paintQuickButton(g);
                    SheetView.this.paintAnts(g);
                    SheetView.this.paintFrame(g);
                }

                @Override
                public String getToolTipText(MouseEvent e) {
                    int r = rowAtPoint(e.getPoint());
                    int c = columnAtPoint(e.getPoint());
                    if (r < 0 || c < 0) return null;
                    String note = SheetView.this.model.commentAt(r, c);
                    if (note == null) {
                        String lk = SheetView.this.model.linkAt(r, c);
                        if (lk == null) return null;
                        String el = lk.startsWith("#") ? "Place in this workbook" : lk;
                        return "<html><b>" + el.replace("&", "&amp;").replace("<", "&lt;") + "</b><br>Ctrl+click to follow link</html>";
                    }
                    String esc = note.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
                    return "<html><div style='width:220px'>" + esc + "</div></html>";
                }

                @Override
                protected void processMouseEvent(MouseEvent e) {
                    if (SheetView.this.handleQuickClick(e)) return;
                    if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getButton() == MouseEvent.BUTTON1 && e.isControlDown()
                            && !e.isShiftDown() && !isEditing()) {
                        int lr = rowAtPoint(e.getPoint());
                        int lc = columnAtPoint(e.getPoint());
                        if (lr >= 0 && lc >= 0 && SheetView.this.model.linkAt(lr, lc) != null) {
                            changeSelection(lr, lc, false, false);
                            openLinkAt(SheetView.this, lr, lc);
                            return;
                        }
                    }
                    if (SheetView.this.pointer != null && SheetView.this.pointer.handleMouse(e)) return;
                    if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getButton() == MouseEvent.BUTTON1
                            && SheetView.this.drawing != null && (SheetView.this.handleFilterClick(e) || SheetView.this.handleDropdownClick(e))) return;
                    if (SheetView.this.drawing != null && SheetView.this.drawing.handleMouse(e)) return;
                    if (SheetView.this.charts != null && SheetView.this.charts.handleMouse(e)) return;
                    if (SheetView.this.layer != null && SheetView.this.layer.handleMouse(e)) return;
                    if (SheetView.this.fill != null && SheetView.this.fill.handleMouse(e)) return;
                    super.processMouseEvent(e);
                }

                @Override
                protected void processMouseMotionEvent(MouseEvent e) {
                    if (SheetView.this.pointer != null && SheetView.this.pointer.handleMotion(e)) return;
                    if (SheetView.this.drawing != null && SheetView.this.drawing.handleMotion(e)) return;
                    if (SheetView.this.charts != null && SheetView.this.charts.handleMotion(e)) return;
                    if (SheetView.this.layer != null && SheetView.this.layer.handleMotion(e)) return;
                    if (SheetView.this.fill != null && SheetView.this.fill.handleMotion(e)) return;
                    super.processMouseMotionEvent(e);
                }

                @Override
                public boolean editCellAt(int row, int column, java.util.EventObject e) {
                    if (SheetView.this.model.isProtected()) {
                        guardEditable(SheetView.this);
                        return false;
                    }
                    return super.editCellAt(row, column, e);
                }

                /** Typing over a selected cell replaces its content, like Excel. */
                @Override
                protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
                    boolean wasEditing = isEditing();
                    boolean handled = super.processKeyBinding(ks, e, condition, pressed);
                    if (!wasEditing && isEditing() && e.getID() == KeyEvent.KEY_PRESSED
                            && e.getKeyChar() != KeyEvent.CHAR_UNDEFINED
                            && getEditorComponent() instanceof JTextField) {
                        ((JTextField) getEditorComponent()).selectAll();
                    }
                    return handled;
                }
            };
            table.setFont(Theme.cellFont());
            table.setRowHeight(Theme.cellRow());
            table.setBackground(Theme.cellBg);
            table.setForeground(Theme.cellFg);
            table.setGridColor(Theme.gridLine);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            table.setCellSelectionEnabled(true);
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.getTableHeader().setReorderingAllowed(false);
            table.getTableHeader().setDefaultRenderer(colHeaderRenderer);
            table.getTableHeader().setPreferredSize(new Dimension(100, 24));
            cellRenderer = new SpreadsheetCellRenderer(model);
            cellEditor = new FormulaCellEditor(model, table.getFont());
            cellEditor.validator = this::validateTyped;
            pointer = new FormulaPointer(table, () -> {
                if (table.isEditing() && table.getEditorComponent() instanceof JTextField) return (JTextField) table.getEditorComponent();
                if (formulaField.isFocusOwner()) return formulaField;
                return null;
            });
            pointer.watch((JTextField) cellEditor.getComponent());
            pointer.watch(formulaField);
            table.setDefaultRenderer(Object.class, cellRenderer);
            table.setDefaultEditor(Object.class, cellEditor);
            layer = new ImageLayer(table, model, SpreadsheetApp.this::markDirty);
            charts = new ChartLayer(table, model, new ChartLayer.Host() {
                @Override
                public void changed() {
                    markDirty();
                }

                @Override
                public void edit(ChartSpec chart) {
                    editChart(chart);
                }

                @Override
                public void menu(ChartSpec chart, MouseEvent e) {
                    showChartMenu(SheetView.this, chart, e);
                }
            });
            fill = new FillHandle(table, model, new FillHandle.Host() {
                @Override
                public void filled(int cells, Runnable undo) {
                    fillUndo = undo;
                    markDirty();
                    status("Filled " + cells + " cell(s) - press Ctrl+Z to undo");
                }

                @Override
                public void blocked() {
                    status("This sheet is protected - unprotect it to fill cells.");
                }
            });
            drawing = new DrawingLayer(table, model, draw, SpreadsheetApp.this::markDirty,
                    () -> status("This sheet is protected - unprotect it to draw."));

            rowHeader = new JList<>(new RowLabelModel(model)); // virtual: works for a million rows
            rowHeader.setFixedCellWidth(46); // row heights vary when a filter hides rows
            rowHeaderRenderer = new SpreadsheetCellRenderer.RowHeaderRenderer(table);
            rowHeader.setCellRenderer(rowHeaderRenderer);
            rowHeader.setBackground(Theme.headerBg);
            rowHeader.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = rowHeader.locationToIndex(e.getPoint());
                    if (i < 0) return;
                    table.setRowSelectionInterval(i, i);
                    table.setColumnSelectionInterval(model.getColumnCount() - 1, 0); // active cell = column A
                    table.requestFocusInWindow();
                }
            });

            scroll = new JScrollPane(table);
            scroll.setRowHeaderView(rowHeader);
            JPanel corner = new TP(null, () -> Theme.headerBg);
            scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, corner);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getVerticalScrollBar().setUnitIncrement(22);
            add(scroll, BorderLayout.CENTER);

            installKeyBindings();
            installZoomAndResize();
            installGlide();
            installFrameGlide();
            installHover();
            applyZoom();
            drawing.updateCursor();
            undo = new SheetUndo(model, this::afterUndoRestore);
            installClipboard();

            ListSelectionListener l = e -> {
                if (e.getValueIsAdjusting()) return;
                if (SheetView.this == currentSheet()) syncFormulaBar();
                rowHeader.repaint();
                table.getTableHeader().repaint();
                glideFrameTo(table.getSelectionModel().getLeadSelectionIndex(),
                        table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
            };
            table.getSelectionModel().addListSelectionListener(l);
            table.getColumnModel().getSelectionModel().addListSelectionListener(l);

            // Reactive formula bar: whenever the model changes (typing, macros, insert) refresh it
            model.addTableModelListener(e -> {
                markDirty();
                if (SheetView.this == currentSheet()) syncFormulaBar();
            });

            table.changeSelection(0, 0, false, false);
        }

        /** Hides the rows the filter excludes (shrunk to 1 pixel) and keeps the row numbers in step. */
        void applyRowVisibility() {
            boolean[] hidden = model.computeHiddenRows();
            table.setRowHeight(scaled(Theme.cellRow()));
            boolean any = false;
            for (int r = 0; r < hidden.length; r++) {
                if (hidden[r]) {
                    table.setRowHeight(r, 1);
                    any = true;
                }
            }
            hiddenApplied = any;
            if (any) {
                rowHeader.setFixedCellHeight(1); // force the list to re-measure its rows
                rowHeader.setFixedCellHeight(-1);
            } else {
                rowHeader.setFixedCellHeight(table.getRowHeight()); // fast path for huge sheets
            }
            rowHeader.revalidate();
            rowHeader.repaint();
            table.revalidate();
            table.repaint();
        }

        /** Clicking a filter arrow opens the value list. @return true if the click was on an arrow. */
        private boolean handleFilterClick(MouseEvent e) {
            AutoFilter af = model.getFilter();
            if (af == null || drawing.isActive()) return false;
            int row = table.rowAtPoint(e.getPoint());
            int col = table.columnAtPoint(e.getPoint());
            if (row != af.headerRow || !af.coversColumn(col)) return false;
            java.awt.Rectangle arrow = SpreadsheetCellRenderer.arrowRect(table.getCellRect(row, col, false), factor());
            if (!arrow.contains(e.getPoint())) return false;
            if (model.isProtected()) {
                status("This sheet is protected - unprotect it to change the filter.");
                return true;
            }
            final int column = col;
            int last = model.filterLastRow();
            Point where = new Point(arrow.x, arrow.y + arrow.height);
            SwingUtilities.convertPointToScreen(where, table);
            new FilterDialog(SpreadsheetApp.this, where, String.valueOf(model.getValueAt(af.headerRow, col)),
                    model.distinctFilterKeys(col, af.headerRow + 1, last), af.copyAllowed(col),
                    new FilterDialog.Callback() {
                        @Override
                        public void apply(java.util.Set<String> allowed) {
                            model.setFilterValues(column, allowed);
                            applyRowVisibility();
                            markDirty();
                            status(allowed == null ? "Showing all rows" : "Filtered column "
                                    + FormulaEngine.columnLabel(column) + " (" + allowed.size() + " value(s) shown)");
                        }

                        @Override
                        public void sort(boolean ascending) {
                            if (model.sortRows(af.headerRow + 1, model.filterLastRow(), af.firstCol, af.lastCol,
                                    column, ascending)) {
                                applyRowVisibility();
                                markDirty();
                                status("Sorted by column " + FormulaEngine.columnLabel(column));
                            }
                        }
                    }).setVisible(true);
            return true;
        }

        /** Called after the sheet got more (or fewer) rows. */
        void refreshRows() {
            rowHeader.setModel(new RowLabelModel(model));
            applyRowVisibility();
            invalidDirty = true;
        }

        private java.awt.Rectangle dropdownArrow() {
            if (table.isEditing() || model.isProtected()) return null;
            int r = table.getSelectionModel().getLeadSelectionIndex();
            int c = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
            if (r < 0 || c < 0) return null;
            ValidationRule v = model.validationAt(r, c);
            if (v == null || v.type != ValidationRule.Type.LIST || !v.dropdown) return null;
            return SpreadsheetCellRenderer.arrowRect(table.getCellRect(r, c, false), factor());
        }

        private void paintValidationOverlay(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                java.awt.Rectangle a = dropdownArrow();
                if (a != null) {
                    g.setColor(Theme.fieldBg);
                    g.fillRoundRect(a.x, a.y, a.width, a.height, 4, 4);
                    g.setColor(Theme.dimFg);
                    g.drawRoundRect(a.x, a.y, a.width - 1, a.height - 1, 4, 4);
                    g.setColor(Theme.fg);
                    int cx = a.x + a.width / 2;
                    int cy = a.y + a.height / 2;
                    int d = Math.max(3, a.width / 5);
                    g.fillPolygon(new int[]{cx - d, cx + d, cx}, new int[]{cy - d / 2, cy - d / 2, cy + d / 2 + 1}, 3);
                }
                if (circleInvalid) {
                    if (invalidDirty) {
                        invalidCache = model.findInvalid();
                        invalidDirty = false;
                    }
                    g.setColor(new Color(0xE53935));
                    g.setStroke(new BasicStroke(2f));
                    java.awt.Rectangle clip = g.getClipBounds() == null ? table.getVisibleRect() : g.getClipBounds();
                    for (int[] cell : invalidCache) {
                        java.awt.Rectangle rc = table.getCellRect(cell[0], cell[1], false);
                        if (!rc.intersects(clip)) continue;
                        g.drawOval(rc.x - 1, rc.y - 2, rc.width + 1, rc.height + 3);
                    }
                }
            } finally {
                g.dispose();
            }
        }

        private boolean handleDropdownClick(MouseEvent e) {
            if (drawing != null && drawing.isActive()) return false;
            java.awt.Rectangle a = dropdownArrow();
            if (a == null || !a.contains(e.getPoint())) return false;
            int r = table.getSelectionModel().getLeadSelectionIndex();
            int c = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
            showDropdown(r, c);
            return true;
        }

        private void showDropdown(int row, int col) {
            ValidationRule v = model.validationAt(row, col);
            if (v == null) return;
            List<String> items = v.listItems(model);
            if (items.isEmpty()) {
                status("The list for this cell is empty.");
                return;
            }
            final JPopupMenu pm = new JPopupMenu();
            final JList<String> list = new JList<>(items.toArray(new String[0]));
            list.setVisibleRowCount(Math.min(10, items.size()));
            list.setSelectedValue(model.getInput(row, col), true);
            JScrollPane sp = new JScrollPane(list);
            java.awt.Rectangle cell = table.getCellRect(row, col, false);
            Dimension pref = sp.getPreferredSize();
            sp.setPreferredSize(new Dimension(Math.max(cell.width, Math.min(400, pref.width + 20)), pref.height));
            pm.add(sp);
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent ev) {
                    String pick = list.getSelectedValue();
                    pm.setVisible(false);
                    if (pick != null) {
                        model.setInput(row, col, pick);
                        markDirty();
                    }
                    table.requestFocusInWindow();
                }
            });
            pm.show(table, cell.x, cell.y + cell.height);
        }

        /** Called when a typed entry is about to be stored. @return 0 accept, 1 keep editing, 2 cancel the edit */
        int validateTyped(int row, int col, String text) {
            ValidationRule rule = model.violatedRule(row, col, text);
            if (rule == null) return 0;
            String msg = rule.check(model, text);
            String title = rule.title.isEmpty() ? "Invalid value" : rule.title;
            switch (rule.alert) {
                case STOP: {
                    int o = JOptionPane.showOptionDialog(SpreadsheetApp.this, msg, title, JOptionPane.DEFAULT_OPTION,
                            JOptionPane.ERROR_MESSAGE, null, new Object[]{"Retry", "Cancel"}, "Retry");
                    return o == 1 ? 2 : 1;
                }
                case WARNING: {
                    int o = JOptionPane.showConfirmDialog(SpreadsheetApp.this, msg + "\n\nDo you want to continue?", title,
                            JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                    return o == JOptionPane.YES_OPTION ? 0 : o == JOptionPane.NO_OPTION ? 1 : 2;
                }
                default: {
                    int o = JOptionPane.showConfirmDialog(SpreadsheetApp.this, msg, title, JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);
                    return o == JOptionPane.OK_OPTION ? 0 : 2;
                }
            }
        }

        double factor() {
            return model.getZoomPercent() / 100.0;
        }

        private int scaled(int base) {
            return Math.max(1, (int) Math.round(base * factor()));
        }

        void setZoom(int percent) {
            Rectangle vis = scroll.getViewport().getViewRect();
            double old = factor();
            model.setZoomPercent(percent);
            double ratio = factor() / old;
            applyZoom();
            if (ratio != 1.0) {
                scroll.getViewport().setViewPosition(new Point(
                        (int) Math.round(vis.x * ratio), (int) Math.round(vis.y * ratio)));
            }
        }

        // ---- undo / redo ----
        private void afterUndoRestore() {
            applyZoom();
            table.repaint();
            rowHeader.repaint();
            if (SheetView.this == currentSheet()) syncFormulaBar();
            markDirty();
        }

        // ---- copy / cut / paste ----
        private void installClipboard() {
            table.setTransferHandler(new javax.swing.TransferHandler() {
                @Override
                public int getSourceActions(JComponent c) {
                    return COPY_OR_MOVE;
                }

                @Override
                public void exportToClipboard(JComponent c, java.awt.datatransfer.Clipboard clip, int action) {
                    copyCells(SheetView.this, action == MOVE);
                }

                @Override
                public boolean canImport(JComponent c, java.awt.datatransfer.DataFlavor[] flavors) {
                    for (java.awt.datatransfer.DataFlavor f : flavors) {
                        if (f.equals(CellClipboard.FLAVOR) || f.equals(java.awt.datatransfer.DataFlavor.stringFlavor)
                                || f.equals(java.awt.datatransfer.DataFlavor.imageFlavor)) return true;
                    }
                    return false;
                }

                @Override
                public boolean importData(JComponent c, java.awt.datatransfer.Transferable t) {
                    return pasteCells(SheetView.this, t, false);
                }
            });
            table.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE && copyRect != null) clearAnts();
                }
            });
        }

        void showAnts(int[] rect) {
            copyRect = rect;
            if (antsTimer == null) {
                antsTimer = new javax.swing.Timer(120, e -> {
                    antsPhase = (antsPhase + 1.5f) % 12f;
                    table.repaint();
                });
            }
            if (rect == null) antsTimer.stop();
            else antsTimer.start();
            table.repaint();
        }

        void clearAnts() {
            showAnts(null);
        }

        private void paintAnts(Graphics g) {
            if (copyRect == null) return;
            java.awt.Rectangle a = table.getCellRect(copyRect[0], copyRect[1], true);
            java.awt.Rectangle b = table.getCellRect(copyRect[2], copyRect[3], true);
            java.awt.Rectangle r = a.union(b);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, -antsPhase));
            g2.setColor(Theme.accent);
            g2.drawRect(r.x + 1, r.y + 1, r.width - 3, r.height - 3);
            g2.dispose();
        }

        // ---- the selection frame glides from cell to cell ----
        private java.awt.Rectangle frameShown; // where the frame is drawn while it glides (null = not gliding)
        private java.awt.Rectangle frameLast; // the active cell before the last move
        private javax.swing.Timer frameTimer;
        private boolean mouseHeld;

        private void installFrameGlide() {
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    mouseHeld = true;
                    stopFrameGlide();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    mouseHeld = false;
                }
            });
        }

        private void stopFrameGlide() {
            if (frameTimer != null) frameTimer.stop();
            frameShown = null;
            cellRenderer.suppressActive = false;
            table.repaint();
        }

        /** Called when the active cell changed: slide the frame from the old cell to the new one. */
        private void glideFrameTo(int row, int col) {
            if (!model.inBounds(row, col)) return;
            java.awt.Rectangle target = table.getCellRect(row, col, false);
            java.awt.Rectangle from = frameShown != null ? new java.awt.Rectangle(frameShown) : frameLast;
            frameLast = target;
            if (!Theme.smooth() || mouseHeld || table.isEditing() || from == null || from.equals(target)
                    || Math.abs(from.x - target.x) + Math.abs(from.y - target.y) > 1800) {
                stopFrameGlide();
                return;
            }
            final java.awt.Rectangle start = from;
            final long t0 = System.nanoTime();
            final double ms = 120;
            cellRenderer.suppressActive = true;
            if (frameTimer != null) frameTimer.stop();
            frameTimer = new javax.swing.Timer(8, null);
            frameTimer.addActionListener(e -> {
                double t = Math.min(1.0, (System.nanoTime() - t0) / 1e6 / ms);
                double k = 1 - Math.pow(1 - t, 3); // ease-out
                java.awt.Rectangle now = frameShown == null ? new java.awt.Rectangle() : frameShown;
                now.x = (int) Math.round(start.x + (target.x - start.x) * k);
                now.y = (int) Math.round(start.y + (target.y - start.y) * k);
                now.width = (int) Math.round(start.width + (target.width - start.width) * k);
                now.height = (int) Math.round(start.height + (target.height - start.height) * k);
                frameShown = now;
                table.repaint();
                if (t >= 1.0) stopFrameGlide();
            });
            frameShown = new java.awt.Rectangle(start);
            frameTimer.start();
        }

        private void paintFrame(Graphics g) {
            if (frameShown == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Theme.accent);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(frameShown.x + 1, frameShown.y + 1, frameShown.width - 3, frameShown.height - 3);
            g2.dispose();
        }

        // ---- smooth movement between cells ----
        private long lastNavKey;
        private javax.swing.Timer glideTimer;
        private Point glideTarget;

        private void installGlide() {
            table.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    lastNavKey = System.currentTimeMillis();
                }
            });
        }

        /** Keyboard moves slide the view to the new cell instead of jumping. Returns false to use the normal jump. */
        private boolean glideScroll(java.awt.Rectangle r) {
            if (!Theme.smooth() || System.currentTimeMillis() - lastNavKey > 250) return false;
            if (!(table.getParent() instanceof javax.swing.JViewport)) return false;
            javax.swing.JViewport vp = (javax.swing.JViewport) table.getParent();
            java.awt.Rectangle vis = vp.getViewRect();
            Point from = glideTarget != null && glideTimer != null && glideTimer.isRunning() ? glideTarget : vp.getViewPosition();
            int nx = from.x;
            int ny = from.y;
            if (r.y < vis.y) ny = r.y;
            else if (r.y + r.height > vis.y + vis.height) ny = r.y + r.height - vis.height;
            if (r.x < vis.x) nx = r.x;
            else if (r.x + r.width > vis.x + vis.width) nx = r.x + r.width - vis.width;
            glideTo(vp, nx, ny);
            return true;
        }

        private void glideTo(javax.swing.JViewport vp, int x, int y) {
            Dimension size = table.getSize();
            Dimension ext = vp.getExtentSize();
            glideTarget = new Point(Math.max(0, Math.min(x, size.width - ext.width)), Math.max(0, Math.min(y, size.height - ext.height)));
            if (glideTimer == null) {
                glideTimer = new javax.swing.Timer(12, null);
                glideTimer.addActionListener(e -> {
                    Point cur = vp.getViewPosition();
                    int dx = glideTarget.x - cur.x;
                    int dy = glideTarget.y - cur.y;
                    if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
                        vp.setViewPosition(glideTarget);
                        glideTimer.stop();
                        return;
                    }
                    int sx = dx == 0 ? 0 : (int) Math.signum(dx) * Math.max(1, (int) Math.round(Math.abs(dx) * 0.34));
                    int sy = dy == 0 ? 0 : (int) Math.signum(dy) * Math.max(1, (int) Math.round(Math.abs(dy) * 0.34));
                    vp.setViewPosition(new Point(cur.x + sx, cur.y + sy));
                });
            }
            if (!glideTimer.isRunning()) glideTimer.start();
        }

        /** Mouse-wheel scrolling that eases to a stop. */
        private void wheelGlide(java.awt.event.MouseWheelEvent e) {
            javax.swing.JViewport vp = scroll.getViewport();
            Point from = glideTarget != null && glideTimer != null && glideTimer.isRunning() ? glideTarget : vp.getViewPosition();
            int step = Math.max(table.getRowHeight(), 16) * 3;
            double amount = e.getPreciseWheelRotation() * step;
            if (e.isShiftDown()) glideTo(vp, from.x + (int) Math.round(amount), from.y);
            else glideTo(vp, from.x, from.y + (int) Math.round(amount));
        }

        /** Re-reads colours and the cell font after a theme or font change. */
        void restyle() {
            table.setBackground(Theme.cellBg);
            table.setForeground(Theme.cellFg);
            table.setGridColor(Theme.gridLine);
            rowHeader.setBackground(Theme.headerBg);
            scroll.getViewport().setBackground(Theme.cellBg);
            scroll.setBackground(Theme.appBg);
            applyZoom();
            table.getTableHeader().repaint();
            rowHeader.repaint();
            table.repaint();
        }

        /** Re-lays out fonts, row height and column widths for the model's zoom level and stored widths. */
        void applyZoom() {
            applying = true;
            try {
                double f = factor();
                Font font = Theme.cellFont().deriveFont((float) (Theme.cellSize() * f));
                table.setFont(font);
                cellRenderer.setZoom(f);
                cellEditor.setFont(font);
                table.setRowHeight(scaled(Theme.cellRow()));
                for (int c = 0; c < model.getColumnCount(); c++) {
                    TableColumn col = table.getColumnModel().getColumn(c);
                    int w = scaled(model.getColumnWidth(c));
                    col.setPreferredWidth(w);
                    col.setWidth(w);
                }
                rowHeader.setFixedCellWidth(scaled(Theme.cellRowHeader()));
                applyRowVisibility();
                table.getTableHeader().setPreferredSize(new Dimension(100, scaled(Theme.cellHeader())));
                table.getTableHeader().resizeAndRepaint();
                table.revalidate();
                scroll.getVerticalScrollBar().setUnitIncrement(table.getRowHeight());
                scroll.revalidate();
                scroll.repaint();
            } finally {
                applying = false;
            }
        }

        /** Faint highlight for the cell / header under the mouse, plus the right-click menu. */
        private void installHover() {
            // rows hidden by the filter follow the data (edits can change which rows match)
            model.addTableModelListener(e -> {
                if (model.getFilter() != null || hiddenApplied) applyRowVisibility();
                invalidDirty = true;
                if (!model.getCharts().isEmpty() || !model.getSparklines().isEmpty() || circleInvalid) {
                    table.repaint(); // charts and sparklines read the cells live
                }
            });
            table.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (drawing.isActive()) setHoverCell(-1, -1);
                    else setHoverCell(table.rowAtPoint(e.getPoint()), table.columnAtPoint(e.getPoint()));
                }
            });
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    setHoverCell(-1, -1);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) showCellMenu(SheetView.this, e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) showCellMenu(SheetView.this, e);
                }
            });
            JTableHeader header = table.getTableHeader();
            header.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int c = header.columnAtPoint(e.getPoint());
                    if (c != colHeaderRenderer.hover) {
                        colHeaderRenderer.hover = c;
                        header.repaint();
                    }
                }
            });
            header.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    colHeaderRenderer.hover = -1;
                    header.repaint();
                }
            });
            rowHeader.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = rowHeader.locationToIndex(e.getPoint());
                    Rectangle rb = i < 0 ? null : rowHeader.getCellBounds(i, i);
                    if (rb == null || !rb.contains(e.getPoint())) i = -1;
                    if (i != rowHeaderRenderer.hover) {
                        rowHeaderRenderer.hover = i;
                        rowHeader.repaint();
                    }
                }
            });
            rowHeader.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    rowHeaderRenderer.hover = -1;
                    rowHeader.repaint();
                }
            });
        }

        private void setHoverCell(int r, int c) {
            if (r == cellRenderer.hoverRow && c == cellRenderer.hoverCol) return;
            repaintCell(cellRenderer.hoverRow, cellRenderer.hoverCol);
            cellRenderer.hoverRow = r;
            cellRenderer.hoverCol = c;
            repaintCell(r, c);
        }

        private void repaintCell(int r, int c) {
            if (r >= 0 && c >= 0) table.repaint(table.getCellRect(r, c, false));
        }

        private void installZoomAndResize() {
            // Remember widths the user drags (stored at 100% zoom so they survive zoom changes)
            table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
                @Override
                public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                    if (applying) return;
                    boolean changed = false;
                    for (int c = 0; c < model.getColumnCount(); c++) {
                        int w = table.getColumnModel().getColumn(c).getWidth();
                        if (w != scaled(model.getColumnWidth(c))) {
                            model.setColumnWidth(c, (int) Math.round(w / factor()));
                            changed = true;
                        }
                    }
                    if (changed) {
                        markDirty();
                        table.repaint();
                    }
                }

                @Override
                public void columnAdded(TableColumnModelEvent e) {
                }

                @Override
                public void columnRemoved(TableColumnModelEvent e) {
                }

                @Override
                public void columnMoved(TableColumnModelEvent e) {
                }

                @Override
                public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {
                }
            });

            // Double-click a column border to fit the column to its content
            JTableHeader header = table.getTableHeader();
            header.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() != 2) return;
                    int col = header.columnAtPoint(e.getPoint());
                    if (col < 0) return;
                    Rectangle r = header.getHeaderRect(col);
                    int target;
                    if (e.getX() - r.x <= 4) target = col - 1;
                    else if (r.x + r.width - e.getX() <= 4) target = col;
                    else return;
                    if (target >= 0) autoFitColumn(target);
                }
            });

            // Ctrl + mouse wheel zooms; the plain wheel keeps scrolling
            MouseWheelListener[] old = scroll.getMouseWheelListeners();
            for (MouseWheelListener l : old) scroll.removeMouseWheelListener(l);
            scroll.addMouseWheelListener(e -> {
                if (e.isControlDown()) {
                    zoomBy(e.getWheelRotation() < 0 ? 10 : -10);
                    e.consume();
                } else if (Theme.smooth()) {
                    wheelGlide(e);
                    e.consume();
                } else {
                    for (MouseWheelListener l : old) l.mouseWheelMoved(e);
                }
            });
        }

        void autoFitColumn(int c) {
            Font base = Theme.cellFont();
            int max = table.getFontMetrics(base.deriveFont(Font.BOLD)).stringWidth(FormulaEngine.columnLabel(c));
            for (int r = 0; r < model.getRowCount(); r++) {
                Object v = model.getValueAt(r, c);
                if (v == null || v.toString().isEmpty()) continue;
                CellStyle st = model.getStyle(r, c);
                int flags = (st != null && st.bold ? Font.BOLD : 0) | (st != null && st.italic ? Font.ITALIC : 0);
                float size = st != null && st.fontSize > 0 ? st.fontSize : Theme.cellSize();
                FontMetrics fm = table.getFontMetrics(base.deriveFont(flags, size));
                max = Math.max(max, fm.stringWidth(v.toString()));
            }
            model.setColumnWidth(c, max + 16);
            applyZoom();
            markDirty();
            status("Column " + FormulaEngine.columnLabel(c) + " fitted to content");
        }

        private void installKeyBindings() {
            int ctrl = InputEvent.CTRL_DOWN_MASK;
            bind(KeyEvent.VK_Z, ctrl, "undoDrawing", () -> {
                if (draw.tool != DrawingLayer.Tool.NONE) drawUndo();
                else undoAction();
            });
            bind(KeyEvent.VK_Y, ctrl, "redoDrawing", () -> {
                if (draw.tool != DrawingLayer.Tool.NONE) drawRedo();
                else redoAction();
            });
            bind(KeyEvent.VK_Z, ctrl | InputEvent.SHIFT_DOWN_MASK, "redoShift", () -> {
                if (draw.tool != DrawingLayer.Tool.NONE) drawRedo();
                else redoAction();
            });
            bind(KeyEvent.VK_V, ctrl | InputEvent.SHIFT_DOWN_MASK, "pasteValues", () -> pasteFromClipboard(true));
            bind(KeyEvent.VK_ADD, ctrl, "zoomInPad", () -> zoomBy(10));
            bind(KeyEvent.VK_PLUS, ctrl, "zoomInPlus", () -> zoomBy(10));
            bind(KeyEvent.VK_SUBTRACT, ctrl, "zoomOutPad", () -> zoomBy(-10));
            bind(KeyEvent.VK_NUMPAD0, ctrl, "zoomResetPad", () -> setZoom(100));
            bind(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK, "bold", () -> toggleStyle(true));
            bind(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK, "italic", () -> toggleStyle(false));
            bind(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK, "alignLeft", () -> align(SwingConstants.LEFT));
            bind(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK, "alignCenter", () -> align(SwingConstants.CENTER));
            bind(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK, "alignRight", () -> align(SwingConstants.RIGHT));
            bind(KeyEvent.VK_DELETE, 0, "clearContents", () -> {
                if (charts.hasSelection()) charts.deleteSelected();
                else if (layer.hasSelection()) layer.deleteSelected();
                else clearSelection();
            });
        }

        private void bind(int key, int modifiers, String name, Runnable action) {
            table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, modifiers), name);
            table.getActionMap().put(name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    action.run();
                }
            });
        }
    }

    /** In-cell editor that shows the raw input (formula text) instead of the computed value. */
    private static final class FormulaCellEditor extends DefaultCellEditor {
        interface Validator {
            /** 0 accept, 1 keep editing, 2 cancel the edit */
            int check(int row, int col, String text);
        }

        private final SpreadsheetModel model;
        Validator validator;
        private int editRow = -1;
        private int editCol = -1;

        FormulaCellEditor(SpreadsheetModel model, Font font) {
            super(new JTextField());
            this.model = model;
            setClickCountToStart(2);
            JTextField field = (JTextField) getComponent();
            field.setFont(font);
            field.setBorder(BorderFactory.createLineBorder(Theme.accent, 2));
            FormulaSuggest.attach(field);
        }

        void setFont(Font font) {
            getComponent().setFont(font);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                     int row, int column) {
            editRow = row;
            editCol = column;
            return super.getTableCellEditorComponent(table, model.getInput(row, column), isSelected, row, column);
        }

        @Override
        public boolean stopCellEditing() {
            if (validator != null && editRow >= 0) {
                int answer = validator.check(editRow, editCol, ((JTextField) getComponent()).getText());
                if (answer == 1) return false;
                if (answer == 2) {
                    cancelCellEditing();
                    return false;
                }
            }
            return super.stopCellEditing();
        }
    }

    // ==================================================================
    // Actions on the selection
    // ==================================================================

    private void applyToSelection(Consumer<CellStyle> change) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        sv.model.beginBatch();
        try {
            for (int r : rows) for (int c : cols) sv.model.updateStyle(r, c, change);
        } finally {
            sv.model.endBatch();
        }
        sv.table.requestFocusInWindow();
    }

    /** Excel behaviour: if every selected cell already has the style, remove it; otherwise apply it. */
    private void toggleStyle(boolean bold) {
        SheetView sv = currentSheet();
        if (sv == null) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;
        boolean all = true;
        outer:
        for (int r : rows) {
            for (int c : cols) {
                CellStyle st = sv.model.getStyle(r, c);
                boolean on = st != null && (bold ? st.bold : st.italic);
                if (!on) {
                    all = false;
                    break outer;
                }
            }
        }
        final boolean value = !all;
        applyToSelection(st -> {
            if (bold) st.bold = value;
            else st.italic = value;
        });
    }

    private void align(int alignment) {
        applyToSelection(st -> st.alignment = alignment);
    }

    private void pickFillColor() {
        if (!guardEditable(currentSheet())) return;
        Color c = javax.swing.JColorChooser.showDialog(this, "Choose Background Color", currentBackground);
        if (c == null) return;
        currentBackground = c;
        fillChip.setBackground(c);
        applyToSelection(st -> st.background = c);
    }

    // ------------------------------------------------------------------
    // Number formats, colours, styles, conditional formatting
    // ------------------------------------------------------------------

    private void setNumberFormat(int fmt) {
        applyToSelection(st -> {
            st.numFmt = fmt;
            st.decimals = -1;
        });
    }

    private void adjustDecimals(int delta) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        sv.model.beginBatch();
        try {
            for (int r : rows) {
                for (int c : cols) {
                    final Object v = sv.model.getComputedValue(r, c);
                    if (!(v instanceof Double)) continue;
                    sv.model.updateStyle(r, c, st -> {
                        int dec;
                        if (st.numFmt == CellFormats.GENERAL) {
                            dec = CellFormats.visibleDecimals((Double) v);
                            st.numFmt = CellFormats.NUMBER;
                        } else {
                            dec = st.decimals < 0 ? CellFormats.defaultDecimals(st.numFmt) : st.decimals;
                        }
                        st.decimals = Math.max(0, Math.min(15, dec + delta));
                    });
                }
            }
        } finally {
            sv.model.endBatch();
        }
        sv.table.requestFocusInWindow();
    }

    private void pickTextColor() {
        if (!guardEditable(currentSheet())) return;
        Color c = javax.swing.JColorChooser.showDialog(this, "Choose Text Color", currentTextColor);
        if (c == null) return;
        currentTextColor = c;
        textChip.setBackground(c);
        applyToSelection(st -> st.foreground = c);
    }

    // name, fill, text, bold, size, number format (-1 = unchanged)
    private static final Object[][] CELL_STYLES = {
            {"Normal", null, null, false, 0, -1},
            {"Good", 0xC6EFCE, 0x006100, false, 0, -1},
            {"Bad", 0xFFC7CE, 0x9C0006, false, 0, -1},
            {"Neutral", 0xFFEB9C, 0x9C5700, false, 0, -1},
            {"Note", 0xFFFFCC, 0x404040, false, 0, -1},
            {"Input", 0xFFF2CC, 0x3F3F76, false, 0, -1},
            {"Calculation", 0xF2F2F2, 0xFA7D00, true, 0, -1},
            {"Title", null, 0x107C41, true, 18, -1},
            {"Heading", 0xDDEBF7, 0x1F4E78, true, 14, -1},
            {"Total", 0xE2F0D9, null, true, 0, -1},
            {"Percent", null, null, false, 0, CellFormats.PERCENT},
            {"Currency", null, null, false, 0, CellFormats.CURRENCY},
            {"Comma", null, null, false, 0, CellFormats.COMMA}};

    // ---- Format as Table / Quick Analysis ----
    private boolean tblHeader = true, tblBanded = true, tblFilter = true;

    private JPopupMenu tableStylesMenu() {
        JPopupMenu m = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 3, 6, 6));
        grid.setBorder(new EmptyBorder(8, 8, 4, 8));
        grid.setOpaque(false);
        for (TableStyles.Style st : TableStyles.ALL) {
            JButton b = new JButton(new Icon() {
                public int getIconWidth() { return 64; }
                public int getIconHeight() { return 44; }
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    g.setColor(st.header); g.fillRect(x, y, 64, 11);
                    for (int i = 0; i < 3; i++) {
                        g.setColor(i % 2 == 0 ? st.band1 : st.band2); g.fillRect(x, y + 11 + i * 11, 64, 11);
                    }
                    g.setColor(new Color(0xB0B0B0)); g.drawRect(x, y, 63, 43);
                }
            });
            b.setToolTipText(st.name + " table style");
            b.setMargin(new java.awt.Insets(2, 2, 2, 2));
            b.setFocusable(false);
            b.addActionListener(e -> { m.setVisible(false); formatAsTable(st); });
            grid.add(b);
        }
        m.add(grid);
        m.addSeparator();
        JCheckBoxMenuItem h = new JCheckBoxMenuItem("My table has a header row", tblHeader);
        h.addActionListener(e -> tblHeader = h.isSelected());
        JCheckBoxMenuItem bd = new JCheckBoxMenuItem("Banded rows", tblBanded);
        bd.addActionListener(e -> tblBanded = bd.isSelected());
        JCheckBoxMenuItem fl = new JCheckBoxMenuItem("Filter buttons", tblFilter);
        fl.addActionListener(e -> tblFilter = fl.isSelected());
        m.add(h); m.add(bd); m.add(fl);
        m.addSeparator();
        JMenuItem clear = new JMenuItem("Clear table style");
        clear.addActionListener(e -> {
            SheetView sv = currentSheet();
            if (sv == null || !guardEditable(sv)) return;
            int[] r = tableRegion(sv);
            TableStyles.clear(sv.model, r[0], r[1], r[2], r[3]);
            sv.table.repaint();
            markDirty();
        });
        m.add(clear);
        return m;
    }

    private void formatAsTable(TableStyles.Style st) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] r = tableRegion(sv);
        if (!sv.model.hasDataIn(r[0], r[1], r[2], r[3])) {
            status("Click inside your data (or select it) first, then pick a table style.");
            return;
        }
        TableStyles.apply(sv.model, r[0], r[1], r[2], r[3], st, tblHeader, tblBanded);
        if (tblFilter && tblHeader && sv.model.getFilter() == null) {
            sv.model.enableFilter(r[0], r[1], r[3]);
            if (filterBtn != null) filterBtn.setActive(true);
        }
        sv.table.repaint();
        markDirty();
        status(st.name + " table style applied" + (sv.model.getFilter() != null && tblFilter ? " with filter buttons" : ""));
    }

    /** Numeric sub-block of a region: skips a text header row and text label columns. */
    private int[] numericBlock(SpreadsheetModel m, int[] reg) {
        int r1 = reg[0], c1 = reg[1], r2 = reg[2], c2 = reg[3];
        boolean headNum = false;
        for (int c = c1; c <= c2; c++) if (m.isNumeric(r1, c)) headNum = true;
        if (!headNum && r2 > r1) r1++;
        while (c1 < c2) {
            boolean any = false;
            for (int r = r1; r <= r2; r++) if (m.isNumeric(r, c1)) { any = true; break; }
            if (any) break;
            c1++;
        }
        return new int[]{r1, c1, r2, c2};
    }

    private void quickTotals(String fn, boolean down) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] n = numericBlock(sv.model, tableRegion(sv));
        sv.model.beginBatch();
        try {
            if (down) {
                int row = n[2] + 1;
                if (row >= sv.model.getRowCount()) return;
                for (int c = n[1]; c <= n[3]; c++) {
                    sv.model.setInput(row, c, "=" + fn + "(" + FormulaEngine.cellRef(n[0], c) + ":" + FormulaEngine.cellRef(n[2], c) + ")");
                    sv.model.updateStyle(row, c, st -> st.bold = true);
                }
            } else {
                int col = n[3] + 1;
                if (col >= sv.model.getColumnCount()) return;
                for (int r = n[0]; r <= n[2]; r++) {
                    sv.model.setInput(r, col, "=" + fn + "(" + FormulaEngine.cellRef(r, n[1]) + ":" + FormulaEngine.cellRef(r, n[3]) + ")");
                    sv.model.updateStyle(r, col, st -> st.bold = true);
                }
            }
        } finally {
            sv.model.endBatch();
        }
        sv.table.repaint();
        markDirty();
        status(fn + " added " + (down ? "below" : "to the right of") + " the selection");
    }

    private void quickSparklines(Sparkline.Type type) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] n = numericBlock(sv.model, tableRegion(sv));
        int col = n[3] + 1;
        if (col >= sv.model.getColumnCount()) return;
        for (int r = n[0]; r <= n[2]; r++) {
            sv.model.setSparkline(new Sparkline(r, col, r, n[1], r, n[3], type, SPARK_COLORS[0]));
        }
        sv.table.repaint();
        markDirty();
        status(type.label.toLowerCase() + " sparklines added next to the data");
    }

    /** The Quick Analysis popup (Ctrl+Q, or the small button at the corner of a selection). */
    void showQuickAnalysis(Component invoker, int x, int y) {
        SheetView sv = currentSheet();
        if (sv == null) return;
        int[] reg = tableRegion(sv);
        if (!sv.model.hasDataIn(reg[0], reg[1], reg[2], reg[3])) {
            status("Select some data first, then use Quick Analysis (Ctrl+Q).");
            return;
        }
        JPopupMenu m = new JPopupMenu();
        JTabbedPane tp = new JTabbedPane();
        tp.setFocusable(false);
        tp.addTab(I18n.t("Charts"), quickRow(
                quickItem("Column", () -> insertChart(ChartSpec.Type.COLUMN)),
                quickItem("Line", () -> insertChart(ChartSpec.Type.LINE)),
                quickItem("Pie", () -> insertChart(ChartSpec.Type.PIE)),
                quickItem("Radar", () -> insertChart(ChartSpec.Type.RADAR)),
                quickItem("Combo", () -> insertChart(ChartSpec.Type.COMBO)),
                quickItem("Scatter", () -> insertChart(ChartSpec.Type.SCATTER))));
        tp.addTab(I18n.t("Totals"), quickRow(
                quickItem("Sum", () -> quickTotals("SUM", true)),
                quickItem("Average", () -> quickTotals("AVERAGE", true)),
                quickItem("Count", () -> quickTotals("COUNT", true)),
                quickItem("Max", () -> quickTotals("MAX", true)),
                quickItem("Min", () -> quickTotals("MIN", true)),
                quickItem("Sum \u2192", () -> quickTotals("SUM", false))));
        JComponent[] styles = new JComponent[Math.min(6, TableStyles.ALL.length)];
        for (int i = 0; i < styles.length; i++) {
            TableStyles.Style st = TableStyles.ALL[i];
            styles[i] = quickItem(st.name, () -> formatAsTable(st));
        }
        tp.addTab(I18n.t("Tables"), quickRow(styles));
        tp.addTab(I18n.t("Sparklines"), quickRow(
                quickItem("Line", () -> quickSparklines(Sparkline.Type.LINE)),
                quickItem("Column", () -> quickSparklines(Sparkline.Type.COLUMN)),
                quickItem("Win/Loss", () -> quickSparklines(Sparkline.Type.WIN_LOSS))));
        m.add(tp);
        quickPopup = m;
        m.show(invoker, x, y);
    }

    private JPopupMenu quickPopup;

    private JPanel quickRow(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        for (JComponent c : items) p.add(c);
        return p;
    }

    private JComponent quickItem(String label, Runnable action) {
        JButton b = new JButton(I18n.t(label));
        b.setFocusable(false);
        b.addActionListener(e -> {
            if (quickPopup != null) quickPopup.setVisible(false);
            action.run();
        });
        return b;
    }

    // ---- Backstage ("File" screen) ----

    private BackstageView backstage;
    private JPanel backstageGlass;

    void showBackstage(String page) {
        SheetView sv = currentSheet();
        if (sv != null && sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        backstage = new BackstageView(new BackstageView.Host() {
            public String fileName() { return currentFile == null ? "Untitled" : currentFile.getName(); }
            public File file() { return currentFile; }
            public int sheetCount() { return tabs.getTabCount(); }
            public boolean encrypted() { return workbookPassword != null; }
            public boolean readOnly() { return readOnlyMode; }
            public boolean dirty() { return dirty; }
            public String version() { return AppInfo.version(); }

            public void open(File f) {
                hideBackstage();
                if (confirmDiscard()) openWorkbookFile(f);
            }

            public void act(String id) {
                hideBackstage();
                backstageAction(id);
            }
        });
        backstage.show(page);
        if (backstageGlass == null) {
            backstageGlass = new JPanel(new BorderLayout());
            backstageGlass.setOpaque(false);
            setGlassPane(backstageGlass);
        }
        backstageGlass.removeAll();
        backstageGlass.add(backstage, BorderLayout.CENTER);
        backstageGlass.setVisible(true);
        backstageGlass.revalidate();
        backstageGlass.repaint();
        backstage.requestFocusInWindow();
    }

    void hideBackstage() {
        if (backstageGlass != null) {
            backstageGlass.setVisible(false);
            backstageGlass.removeAll();
        }
    }

    boolean backstageVisible() {
        return backstageGlass != null && backstageGlass.isVisible();
    }

    void backstageAction(String id) {
        switch (id) {
            case "closeBackstage": break;
            case "new": newWorkbook(); break;
            case "open": openWorkbook(); break;
            case "openxlsx": openExcelDialog(); break;
            case "save": saveWorkbook(); break;
            case "saveas": saveWorkbookAs(); break;
            case "print": printSheet(); break;
            case "preview": printPreview(); break;
            case "pagesetup": pageSetupDialog(); break;
            case "pdf": exportPdf(); break;
            case "xlsx": exportExcel(); break;
            case "csv": exportCsv(); break;
            case "importcsv": importCsv(); break;
            case "history": versionHistory(); break;
            case "saveversion": saveNamedVersion(); break;
            case "recover": recoverUnsaved(); break;
            case "password": setOpenPassword(); break;
            case "update": checkForUpdates(true); break;
            case "options": openAppearance(); break;
            case "autosave": showAutoSaveSettings(); break;
            case "getdata": newConnection(DataConnection.Kind.TEXT); break;
            case "close": newWorkbook(); break;
            case "exit": exitApp(); break;
            default: break;
        }
    }

    // ---- Data connections ----

    private JPopupMenu getDataMenu() {
        JPopupMenu m = new JPopupMenu();
        for (DataConnection.Kind k : DataConnection.Kind.values()) {
            JMenuItem it = new JMenuItem(I18n.t(k.label) + "...");
            it.addActionListener(e -> newConnection(k));
            m.add(it);
        }
        return m;
    }

    private void newConnection(DataConnection.Kind kind) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        DataConnection c = new DataConnection();
        c.kind = kind;
        c.row = Math.max(0, sv.table.getSelectionModel().getLeadSelectionIndex());
        c.col = Math.max(0, sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
        if (kind == DataConnection.Kind.WEB) c.source = "https://";
        DataConnectionDialog d = new DataConnectionDialog(this, c, I18n.t("Get Data") + " - " + kind.label);
        d.setVisible(true);
        if (!d.accepted()) return;
        runConnection(sv, c, d.password(), d.keep(), true);
    }

    /** Fetches in the background, then writes the cells. */
    private void runConnection(SheetView sv, DataConnection c, char[] password, boolean keep, boolean isNew) {
        status("Loading data...");
        new javax.swing.SwingWorker<DataImporter.Table, Void>() {
            @Override
            protected DataImporter.Table doInBackground() throws Exception {
                return DataImporter.fetch(c, password);
            }

            @Override
            protected void done() {
                try {
                    DataImporter.Table t = get();
                    applyTable(sv, c, t);
                    if (keep && isNew && !sv.model.getConnections().contains(c)) sv.model.getConnections().add(c);
                    markDirty();
                    status("Loaded " + t.rows.size() + " row(s) from " + c.title() + (t.note == null ? "" : " - " + t.note));
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(SpreadsheetApp.this, "Could not load the data:\n" + cause.getMessage(),
                            "Data connection", JOptionPane.ERROR_MESSAGE);
                    status("Data connection failed");
                }
            }
        }.execute();
    }

    /** Writes a fetched table into the sheet at the connection's cell, clearing what the last import put there. */
    void applyTable(SheetView sv, DataConnection c, DataImporter.Table t) {
        SpreadsheetModel m = sv.model;
        int need = c.row + t.rows.size();
        if (need > m.getRowCount()) m.setRowCount(Math.min(SpreadsheetModel.MAX_ROWS, need + 20));
        m.beginBatch();
        try {
            for (int r = 0; r < c.lastRows; r++) {
                for (int cc = 0; cc < c.lastCols; cc++) if (m.inBounds(c.row + r, c.col + cc)) m.setInput(c.row + r, c.col + cc, "");
            }
            int usedRows = 0, usedCols = 0;
            for (int r = 0; r < t.rows.size() && c.row + r < m.getRowCount(); r++) {
                String[] row = t.rows.get(r);
                usedRows = r + 1;
                for (int k = 0; k < row.length && c.col + k < m.getColumnCount(); k++) {
                    usedCols = Math.max(usedCols, k + 1);
                    String v = row[k] == null ? "" : row[k];
                    if (v.startsWith("=")) v = "'" + v; // data from outside must never run as a formula
                    m.setInput(c.row + r, c.col + k, v);
                    if (r == 0 && c.header) final_bold(m, c.row, c.col + k);
                }
            }
            c.lastRows = usedRows;
            c.lastCols = usedCols;
            c.refreshed = System.currentTimeMillis();
        } finally {
            m.endBatch();
        }
        sv.table.repaint();
    }

    private static void final_bold(SpreadsheetModel m, int r, int col) {
        m.updateStyle(r, col, st -> st.bold = true);
    }

    private char[] askPassword(DataConnection c) {
        if (c.kind != DataConnection.Kind.DATABASE || c.user.isEmpty()) return new char[0];
        JPasswordField pf = new JPasswordField(16);
        int a = JOptionPane.showConfirmDialog(this, new Object[]{"Password for " + c.user + " on " + c.source, pf},
                "Database password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return a == JOptionPane.OK_OPTION ? pf.getPassword() : null;
    }

    private void refreshConnection(SheetView sv, DataConnection c) {
        if (!guardEditable(sv)) return;
        char[] pw = askPassword(c);
        if (pw == null) return;
        runConnection(sv, c, pw, true, false);
    }

    private void refreshAllConnections() {
        List<Object[]> all = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (!(tabs.getComponentAt(i) instanceof SheetView)) continue;
            SheetView sv = (SheetView) tabs.getComponentAt(i);
            for (DataConnection c : sv.model.getConnections()) all.add(new Object[]{sv, c});
        }
        if (all.isEmpty()) {
            status("This workbook has no data connections yet - use Get Data first.");
            return;
        }
        StringBuilder sb = new StringBuilder("Refresh these connections? They read from:\n\n");
        for (Object[] o : all) sb.append(" \u2022 ").append(((DataConnection) o[1]).source).append('\n');
        if (JOptionPane.showConfirmDialog(this, sb.toString(), "Refresh All", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
        for (Object[] o : all) refreshConnection((SheetView) o[0], (DataConnection) o[1]);
    }

    private void connectionsDialog() {
        javax.swing.JDialog d = new javax.swing.JDialog(this, I18n.t("Connections"), true);
        DefaultListModel<Object[]> lm = new DefaultListModel<>();
        Runnable fill = () -> {
            lm.clear();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (!(tabs.getComponentAt(i) instanceof SheetView)) continue;
                SheetView sv = (SheetView) tabs.getComponentAt(i);
                for (DataConnection c : sv.model.getConnections()) lm.addElement(new Object[]{sv, c, tabs.getTitleAt(i)});
            }
        };
        fill.run();
        JList<Object[]> list = new JList<>(lm);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                Object[] o = (Object[]) v;
                DataConnection c = (DataConnection) o[1];
                String when = c.refreshed == 0 ? "never" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(c.refreshed));
                return super.getListCellRendererComponent(l, c.title() + "   [" + o[2] + "!" + FormulaEngine.cellRef(c.row, c.col)
                        + ", refreshed " + when + "]", i, sel, foc);
            }
        });
        JButton refresh = new JButton(I18n.t("Refresh"));
        JButton edit = new JButton(I18n.t("Edit"));
        JButton remove = new JButton(I18n.t("Remove"));
        JButton close = new JButton(I18n.t("Close"));
        refresh.addActionListener(e -> {
            Object[] o = list.getSelectedValue();
            if (o != null) { d.dispose(); refreshConnection((SheetView) o[0], (DataConnection) o[1]); }
        });
        edit.addActionListener(e -> {
            Object[] o = list.getSelectedValue();
            if (o == null) return;
            SheetView sv = (SheetView) o[0];
            DataConnection c = (DataConnection) o[1];
            DataConnection work = c.copy();
            DataConnectionDialog dlg = new DataConnectionDialog(this, work, I18n.t("Edit connection"));
            dlg.setVisible(true);
            if (!dlg.accepted()) return;
            d.dispose();
            int idx = sv.model.getConnections().indexOf(c);
            if (idx >= 0) sv.model.getConnections().set(idx, work);
            runConnection(sv, work, dlg.password(), true, false);
        });
        remove.addActionListener(e -> {
            Object[] o = list.getSelectedValue();
            if (o == null) return;
            ((SheetView) o[0]).model.getConnections().remove(o[1]);
            markDirty();
            fill.run();
        });
        close.addActionListener(e -> d.dispose());
        JPanel btn = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        for (JButton b : new JButton[]{refresh, edit, remove, close}) btn.add(b);
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(new JLabel(I18n.t("Removing a connection keeps the data in the cells.")), BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        root.add(btn, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.setSize(640, 320);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // ---- Fill (down / right / series) ----

    private JPopupMenu fillMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem d = new JMenuItem(I18n.t("Down") + "   Ctrl+D");
        d.addActionListener(e -> fillDirection(FillSeries.Dir.DOWN));
        JMenuItem r = new JMenuItem(I18n.t("Right") + "   Ctrl+R");
        r.addActionListener(e -> fillDirection(FillSeries.Dir.RIGHT));
        JMenuItem u = new JMenuItem(I18n.t("Up"));
        u.addActionListener(e -> fillDirection(FillSeries.Dir.UP));
        JMenuItem l = new JMenuItem(I18n.t("Left"));
        l.addActionListener(e -> fillDirection(FillSeries.Dir.LEFT));
        JMenuItem s = new JMenuItem(I18n.t("Series..."));
        s.addActionListener(e -> seriesDialog());
        m.add(d); m.add(r); m.add(u); m.add(l);
        m.addSeparator();
        m.add(s);
        return m;
    }

    /** Ctrl+D / Ctrl+R: copy the first (or last) row or column of the selection over the rest. */
    private void fillDirection(FillSeries.Dir dir) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;
        int r1 = rows[0], r2 = rows[rows.length - 1], c1 = cols[0], c2 = cols[cols.length - 1];
        int sr1, sc1, sr2, sc2, n;
        switch (dir) {
            case DOWN:
                if (r1 == r2) { if (r1 == 0) return; sr1 = sr2 = r1 - 1; n = 1; } else { sr1 = sr2 = r1; n = r2 - r1; }
                sc1 = c1; sc2 = c2;
                break;
            case UP:
                if (r1 == r2) { if (r1 + 1 >= sv.model.getRowCount()) return; sr1 = sr2 = r1 + 1; n = 1; } else { sr1 = sr2 = r2; n = r2 - r1; }
                sc1 = c1; sc2 = c2;
                break;
            case RIGHT:
                if (c1 == c2) { if (c1 == 0) return; sc1 = sc2 = c1 - 1; n = 1; } else { sc1 = sc2 = c1; n = c2 - c1; }
                sr1 = r1; sr2 = r2;
                break;
            default:
                if (c1 == c2) { if (c1 + 1 >= sv.model.getColumnCount()) return; sc1 = sc2 = c1 + 1; n = 1; } else { sc1 = sc2 = c2; n = c2 - c1; }
                sr1 = r1; sr2 = r2;
        }
        int cells = FillSeries.fill(sv.model, sr1, sc1, sr2, sc2, dir, n, FillSeries.Mode.COPY);
        sv.table.repaint();
        markDirty();
        status("Filled " + cells + " cell(s)");
    }

    /** Fill > Series: 1, 2, 3 ... up to a stop value, in rows or columns, linear or growth. */
    private void seriesDialog() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;
        int r1 = rows[0], r2 = rows[rows.length - 1], c1 = cols[0], c2 = cols[cols.length - 1];
        JRadioButton inCols = new JRadioButton(I18n.t("Columns (down)"), (c2 - c1) <= (r2 - r1));
        JRadioButton inRows = new JRadioButton(I18n.t("Rows (across)"), !inCols.isSelected());
        ButtonGroup g1 = new ButtonGroup();
        g1.add(inCols); g1.add(inRows);
        JRadioButton lin = new JRadioButton(I18n.t("Linear (add)"), true);
        JRadioButton gro = new JRadioButton(I18n.t("Growth (multiply)"));
        ButtonGroup g2 = new ButtonGroup();
        g2.add(lin); g2.add(gro);
        JTextField step = new JTextField("1", 8);
        JTextField stop = new JTextField(r1 == r2 && c1 == c2 ? "6" : "", 8);
        JPanel p = new JPanel(new GridLayout(0, 2, 8, 6));
        p.add(new JLabel(I18n.t("Series in:"))); p.add(new JLabel(""));
        p.add(inCols); p.add(inRows);
        p.add(new JLabel(I18n.t("Type:"))); p.add(new JLabel(""));
        p.add(lin); p.add(gro);
        p.add(new JLabel(I18n.t("Step value:"))); p.add(step);
        p.add(new JLabel(I18n.t("Stop value:"))); p.add(stop);
        if (JOptionPane.showConfirmDialog(this, p, I18n.t("Series"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        java.math.BigDecimal st, sp = null;
        try {
            st = new java.math.BigDecimal(step.getText().trim());
            if (!stop.getText().trim().isEmpty()) sp = new java.math.BigDecimal(stop.getText().trim());
        } catch (NumberFormatException ex) {
            status("Step and stop must be numbers.");
            return;
        }
        FillSeries.SeriesResult res = FillSeries.series(sv.model, r1, c1, r2, c2, inRows.isSelected(), gro.isSelected(), st, sp);
        if (res == null) {
            status("Nothing to fill: start with a number in the first cell and use a step that is not 0 (growth: not 1).");
            return;
        }
        sv.table.setRowSelectionInterval(res.r1, res.r2);
        sv.table.setColumnSelectionInterval(res.c1, res.c2);
        sv.table.repaint();
        markDirty();
        status("Series filled (" + res.written + " cells)");
    }

    private JPopupMenu cellStylesMenu() {
        JPopupMenu m = new JPopupMenu();
        for (Object[] def : CELL_STYLES) {
            JMenuItem item = new JMenuItem((String) def[0]);
            item.setOpaque(true);
            if (def[1] != null) item.setBackground(new Color((Integer) def[1]));
            if (def[2] != null) item.setForeground(new Color((Integer) def[2]));
            if ((Boolean) def[3]) item.setFont(item.getFont().deriveFont(Font.BOLD));
            item.addActionListener(e -> applyCellStyle(def));
            m.add(item);
        }
        return m;
    }

    private void applyCellStyle(Object[] def) {
        final boolean normal = "Normal".equals(def[0]);
        applyToSelection(st -> {
            if (normal) {
                st.reset();
                return;
            }
            if (def[1] != null) st.background = new Color((Integer) def[1]);
            if (def[2] != null) st.foreground = new Color((Integer) def[2]);
            if ((Boolean) def[3]) st.bold = true;
            if ((Integer) def[4] > 0) st.fontSize = (Integer) def[4];
            if ((Integer) def[5] >= 0) {
                st.numFmt = (Integer) def[5];
                st.decimals = -1;
            }
        });
        status("Applied the \"" + def[0] + "\" style");
    }

    private JPopupMenu conditionalMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenu hl = new JMenu("Highlight Cells Rules");
        hl.add(ctxItem("Greater Than...", "condfmt", () -> addCondRule(CondRule.Type.GREATER)));
        hl.add(ctxItem("Less Than...", "condfmt", () -> addCondRule(CondRule.Type.LESS)));
        hl.add(ctxItem("Equal To...", "condfmt", () -> addCondRule(CondRule.Type.EQUAL)));
        hl.add(ctxItem("Between...", "condfmt", () -> addCondRule(CondRule.Type.BETWEEN)));
        hl.add(ctxItem("Text that Contains...", "condfmt", () -> addCondRule(CondRule.Type.CONTAINS)));
        hl.add(ctxItem("Duplicate Values...", "condfmt", () -> addCondRule(CondRule.Type.DUPLICATE)));
        m.add(hl);
        JMenu bars = new JMenu("Data Bars");
        String[][] barSet = {{"Blue Data Bar", "4F81BD"}, {"Green Data Bar", "63BE7B"}, {"Red Data Bar", "F8696B"},
                {"Orange Data Bar", "FFB628"}, {"Purple Data Bar", "8064A2"}};
        for (String[] b : barSet) bars.add(ctxItem(b[0], "condfmt", () -> addVisualRule(1, b[1], null)));
        m.add(bars);
        JMenu scales = new JMenu("Color Scales");
        String[][] scaleSet = {
                {"Green - Yellow - Red", "63BE7B,FFEB84,F8696B"}, {"Red - Yellow - Green", "F8696B,FFEB84,63BE7B"},
                {"Blue - White - Red", "5A8AC6,FCFCFF,F8696B"}, {"Red - White - Blue", "F8696B,FCFCFF,5A8AC6"},
                {"White - Green", "FCFCFF,63BE7B"}, {"White - Red", "FCFCFF,F8696B"},
                {"White - Blue", "FCFCFF,5A8AC6"}, {"Yellow - Green", "FFEB84,63BE7B"}};
        for (String[] sc : scaleSet) scales.add(ctxItem(sc[0], "condfmt", () -> addVisualRule(0, sc[1], null)));
        m.add(scales);
        JMenu icons = new JMenu("Icon Sets");
        String[][] iconSetList = {
                {"3 Arrows", "ARROWS3"}, {"4 Arrows", "ARROWS4"}, {"5 Arrows", "ARROWS5"},
                {"3 Traffic Lights", "TRAFFIC3"}, {"3 Symbols", "SYMBOLS3"}, {"3 Flags", "FLAGS3"},
                {"4 Ratings", "RATING4"}, {"5 Ratings", "RATING5"}};
        for (String[] is : iconSetList) icons.add(ctxItem(is[0], "condfmt", () -> addVisualRule(2, null, is[1])));
        m.add(icons);
        m.addSeparator();
        m.add(ctxItem("Clear Rules from Selected Cells", "trash", this::clearRulesSelected));
        m.add(ctxItem("Clear Rules from This Sheet", "trash", this::clearRulesSheet));
        return m;
    }

    /** kind: 0 = colour scale, 1 = data bar, 2 = icon set. */
    private void addVisualRule(int kind, String hex, String set) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;
        int r1 = rows[0], r2 = rows[rows.length - 1], c1 = cols[0], c2 = cols[cols.length - 1];
        // replace an existing visual rule of the same kind on exactly this range
        CondRule.Type t = kind == 0 ? CondRule.Type.COLOR_SCALE : kind == 1 ? CondRule.Type.DATA_BAR : CondRule.Type.ICON_SET;
        for (CondRule old : new java.util.ArrayList<>(sv.model.getCondRules())) {
            if (old.type == t && old.r1 == r1 && old.r2 == r2 && old.c1 == c1 && old.c2 == c2) sv.model.removeCondRule(old);
        }
        sv.model.addCondRule(kind == 0 ? CondRule.colorScale(r1, c1, r2, c2, hex.split(","))
                : kind == 1 ? CondRule.dataBar(r1, c1, r2, c2, hex) : CondRule.iconSet(r1, c1, r2, c2, set));
        sv.table.repaint();
        markDirty();
        status(kind == 0 ? "Color scale applied" : kind == 1 ? "Data bars applied" : "Icon set applied");
    }

    private void addCondRule(CondRule.Type type) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;
        JTextField v1 = new JTextField(12);
        JTextField v2 = new JTextField(12);
        JComboBox<String> preset = new JComboBox<>(CondRule.PRESET_NAMES);
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        String title;
        switch (type) {
            case GREATER:
                title = "Greater Than";
                panel.add(new JLabel("Format cells that are greater than:"));
                panel.add(v1);
                break;
            case LESS:
                title = "Less Than";
                panel.add(new JLabel("Format cells that are less than:"));
                panel.add(v1);
                break;
            case EQUAL:
                title = "Equal To";
                panel.add(new JLabel("Format cells that are equal to:"));
                panel.add(v1);
                break;
            case BETWEEN:
                title = "Between";
                panel.add(new JLabel("Format cells that are between:"));
                panel.add(v1);
                panel.add(new JLabel("and"));
                panel.add(v2);
                break;
            case CONTAINS:
                title = "Text that Contains";
                panel.add(new JLabel("Format cells that contain the text:"));
                panel.add(v1);
                break;
            default:
                title = "Duplicate Values";
                panel.add(new JLabel("Format cells with duplicate values"));
        }
        panel.add(new JLabel("with:"));
        panel.add(preset);
        if (JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            if (type == CondRule.Type.GREATER || type == CondRule.Type.LESS || type == CondRule.Type.BETWEEN) {
                Double.parseDouble(v1.getText().trim());
                if (type == CondRule.Type.BETWEEN) Double.parseDouble(v2.getText().trim());
            } else if (type == CondRule.Type.CONTAINS && v1.getText().trim().isEmpty()) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid " + (type == CondRule.Type.CONTAINS ? "text." : "number."),
                    title, JOptionPane.WARNING_MESSAGE);
            return;
        }
        int pi = preset.getSelectedIndex();
        sv.model.addCondRule(new CondRule(type, rows[0], cols[0], rows[rows.length - 1], cols[cols.length - 1],
                v1.getText().trim(), v2.getText().trim(), CondRule.PRESET_BG[pi], CondRule.PRESET_FG[pi]));
        sv.table.repaint();
        markDirty();
        status("Rule added: " + title.toLowerCase() + " - cells are highlighted automatically");
    }

    private void clearRulesSelected() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;
        int n = sv.model.clearCondRules(rows[0], cols[0], rows[rows.length - 1], cols[cols.length - 1]);
        sv.table.repaint();
        if (n > 0) markDirty();
        status(n + " rule(s) cleared from the selection");
    }

    private void clearRulesSheet() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int n = sv.model.clearAllCondRules();
        sv.table.repaint();
        if (n > 0) markDirty();
        status(n + " rule(s) cleared from this sheet");
    }

    // ------------------------------------------------------------------
    // Insert / delete cells, sort, filter, data tools
    // ------------------------------------------------------------------

    private JPopupMenu insertMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(ctxItem("Insert Rows Above", "insertrow", this::insertRowsAction));
        m.add(ctxItem("Insert Columns Left", "insertcol", this::insertColumnsAction));
        return m;
    }

    private JPopupMenu deleteMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(ctxItem("Delete Rows", "deleterow", this::deleteRowsAction));
        m.add(ctxItem("Delete Columns", "deletecol", this::deleteColumnsAction));
        return m;
    }

    private JPopupMenu sortFilterMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(ctxItem("Sort A to Z", "sortaz", () -> sortSelection(true)));
        m.add(ctxItem("Sort Z to A", "sortza", () -> sortSelection(false)));
        m.addSeparator();
        m.add(ctxItem("Filter", "filter", this::toggleFilter));
        m.add(ctxItem("Clear Filter", "clearfilter", this::clearFilterAction));
        return m;
    }

    private void afterStructureChange(SheetView sv, String message) {
        sv.applyZoom(); // re-applies column widths and hidden rows
        sv.table.repaint();
        markDirty();
        syncFormulaBar();
        status(message);
    }

    private void insertRowsAction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        if (rows.length == 0) return;
        int n = rows[rows.length - 1] - rows[0] + 1;
        if (sv.model.insertRows(rows[0], n)) afterStructureChange(sv, "Inserted " + n + " row(s) - formulas were adjusted");
    }

    private void insertColumnsAction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] cols = sv.table.getSelectedColumns();
        if (cols.length == 0) return;
        int n = cols[cols.length - 1] - cols[0] + 1;
        if (sv.model.insertColumns(cols[0], n)) afterStructureChange(sv, "Inserted " + n + " column(s) - formulas were adjusted");
    }

    private void deleteRowsAction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        if (rows.length == 0) return;
        int n = rows[rows.length - 1] - rows[0] + 1;
        if (sv.model.hasDataIn(rows[0], 0, rows[0] + n - 1, sv.model.getColumnCount() - 1)
                && JOptionPane.showConfirmDialog(this, "Delete " + n + " row(s) that contain data? This cannot be undone.",
                "Delete Rows", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (sv.model.deleteRows(rows[0], n)) afterStructureChange(sv, "Deleted " + n + " row(s) - formulas were adjusted");
    }

    private void deleteColumnsAction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] cols = sv.table.getSelectedColumns();
        if (cols.length == 0) return;
        int n = cols[cols.length - 1] - cols[0] + 1;
        if (sv.model.hasDataIn(0, cols[0], sv.model.getRowCount() - 1, cols[0] + n - 1)
                && JOptionPane.showConfirmDialog(this, "Delete " + n + " column(s) that contain data? This cannot be undone.",
                "Delete Columns", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (sv.model.deleteColumns(cols[0], n)) afterStructureChange(sv, "Deleted " + n + " column(s) - formulas were adjusted");
    }

    /** The selected block, or the table around the active cell when only one cell is selected. */
    private int[] tableRegion(SheetView sv) {
        JTable t = sv.table;
        int[] rows = t.getSelectedRows();
        int[] cols = t.getSelectedColumns();
        if (rows.length > 0 && cols.length > 0 && rows.length * cols.length > 1) {
            return new int[]{rows[0], cols[0], rows[rows.length - 1], cols[cols.length - 1]};
        }
        int r = t.getSelectionModel().getLeadSelectionIndex();
        int c = t.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        return sv.model.currentRegion(Math.max(0, r), Math.max(0, c));
    }

    /** Guess: a first row of text above numbers (or in bold) is a header row. */
    private static boolean looksLikeHeader(SpreadsheetModel m, int r1, int r2, int c1, int c2) {
        if (r2 <= r1) return false;
        boolean anyText = false;
        for (int c = c1; c <= c2; c++) {
            Object v = m.getComputedValue(r1, c);
            if (v instanceof Double) return false;
            if (v instanceof String) anyText = true;
        }
        if (!anyText) return false;
        for (int c = c1; c <= c2; c++) {
            for (int r = r1 + 1; r <= r2; r++) if (m.getComputedValue(r, c) instanceof Double) return true;
        }
        CellStyle st = m.getStyle(r1, c1);
        return st != null && st.bold;
    }

    private void sortSelection(boolean asc) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int col = sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        AutoFilter af = sv.model.getFilter();
        int r1;
        int r2;
        int c1;
        int c2;
        if (af != null && af.coversColumn(col)) {
            r1 = af.headerRow + 1;
            r2 = sv.model.filterLastRow();
            c1 = af.firstCol;
            c2 = af.lastCol;
        } else {
            int[] reg = tableRegion(sv);
            r1 = reg[0];
            c1 = reg[1];
            r2 = reg[2];
            c2 = reg[3];
            if (col < c1 || col > c2) {
                status("Click a cell in the column you want to sort by.");
                return;
            }
            if (looksLikeHeader(sv.model, r1, r2, c1, c2)) r1++;
        }
        if (r2 <= r1) {
            status("Nothing to sort here - click inside a table with at least two rows.");
            return;
        }
        if (sv.model.sortRows(r1, r2, c1, c2, col, asc)) {
            sv.applyRowVisibility();
            sv.table.repaint();
            markDirty();
            status("Sorted " + (r2 - r1 + 1) + " rows by column " + FormulaEngine.columnLabel(col)
                    + (asc ? " (A to Z)" : " (Z to A)") + " - the header row stays on top");
        }
    }

    private void toggleFilter() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        if (sv.model.getFilter() != null) {
            sv.model.disableFilter();
            sv.applyRowVisibility();
            if (filterBtn != null) filterBtn.setActive(false);
            markDirty();
            status("Filter removed - all rows are shown");
            return;
        }
        int[] reg = tableRegion(sv);
        if (!sv.model.hasDataIn(reg[0], reg[1], reg[2], reg[3])) {
            status("Click inside your table first, then turn on Filter.");
            return;
        }
        sv.model.enableFilter(reg[0], reg[1], reg[3]);
        if (filterBtn != null) filterBtn.setActive(true);
        sv.table.repaint();
        markDirty();
        status("Filter on - click an arrow in row " + (reg[0] + 1) + " to pick the values to show");
    }

    private void clearFilterAction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        if (sv.model.getFilter() == null) {
            status("There is no filter on this sheet.");
            return;
        }
        sv.model.clearFilterCriteria();
        sv.applyRowVisibility();
        markDirty();
        status("Filter cleared - all rows are shown");
    }

    private void removeDuplicatesAction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] reg = tableRegion(sv);
        int r1 = reg[0];
        if (looksLikeHeader(sv.model, reg[0], reg[2], reg[1], reg[3])) r1++;
        if (reg[2] <= r1) {
            status("Click inside a table with at least two rows first.");
            return;
        }
        int removed = sv.model.removeDuplicateRows(r1, reg[2], reg[1], reg[3]);
        if (removed < 0) return;
        sv.table.repaint();
        if (removed > 0) markDirty();
        status(removed == 0 ? "No duplicate rows found" : "Removed " + removed + " duplicate row(s)");
    }

    private void textToColumnsAction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        JTable t = sv.table;
        int col = t.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        int[] rows = t.getSelectedRows();
        int r1;
        int r2;
        if (rows.length > 1) {
            r1 = rows[0];
            r2 = rows[rows.length - 1];
        } else {
            r1 = r2 = Math.max(0, t.getSelectionModel().getLeadSelectionIndex());
            while (r1 > 0 && !sv.model.getInput(r1 - 1, col).isEmpty()) r1--;
            while (r2 < sv.model.getRowCount() - 1 && !sv.model.getInput(r2 + 1, col).isEmpty()) r2++;
        }
        JComboBox<String> kind = new JComboBox<>(new String[]{"Comma ( , )", "Semicolon ( ; )", "Tab", "Space", "Pipe ( | )"});
        JTextField custom = new JTextField(4);
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Split the text in column " + FormulaEngine.columnLabel(col) + " (rows " + (r1 + 1) + "-" + (r2 + 1) + ") at:"));
        panel.add(kind);
        panel.add(new JLabel("or a custom separator (overrides the list):"));
        panel.add(custom);
        if (JOptionPane.showConfirmDialog(this, panel, "Text to Columns", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String[] seps = {",", ";", "\t", " ", "|"};
        String delim = custom.getText().isEmpty() ? seps[kind.getSelectedIndex()] : custom.getText();
        if (col + 1 < sv.model.getColumnCount() && sv.model.hasDataIn(r1, col + 1, r2, col + 1)
                && JOptionPane.showConfirmDialog(this, "The columns to the right already contain data that may be overwritten. Continue?",
                "Text to Columns", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        int parts = sv.model.textToColumns(r1, r2, col, delim);
        sv.table.repaint();
        if (parts > 0) markDirty();
        status(parts > 1 ? "Split into " + parts + " columns" : "Nothing to split - the separator was not found");
    }

    // ------------------------------------------------------------------
    // Charts
    // ------------------------------------------------------------------

    private JPopupMenu chartMenuTypes() {
        JPopupMenu m = new JPopupMenu();
        for (ChartSpec.Type t : ChartSpec.Type.values()) {
            JMenuItem item = new JMenuItem(t.label + " Chart");
            item.addActionListener(e -> insertChart(t));
            m.add(item);
        }
        return m;
    }

    private void insertChart(ChartSpec.Type type) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] reg = tableRegion(sv);
        if (!sv.model.hasDataIn(reg[0], reg[1], reg[2], reg[3])) {
            JOptionPane.showMessageDialog(this, "Select the cells you want to chart first (or click inside your table).",
                    "Insert Chart", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        double z = sv.factor();
        java.awt.Rectangle box = sv.table.getCellRect(reg[0], reg[1], false).union(sv.table.getCellRect(reg[2], reg[3], false));
        int w = 460;
        int h = 290;
        int x = (int) Math.round((box.x + box.width) / z) + 30;
        int y = (int) Math.round(box.y / z);
        if (x + w > sv.table.getWidth() / z) { // no room on the right: put it below the data
            x = Math.max(10, (int) Math.round(box.x / z));
            y = (int) Math.round((box.y + box.height) / z) + 24;
        }
        ChartSpec spec = new ChartSpec(type, "", reg[0], reg[1], reg[2], reg[3], x, y, w, h);
        ChartData d = ChartData.read(sv.model, spec);
        if (d.series() == 1 && !d.names.get(0).startsWith("Series ")) spec.title = d.names.get(0);
        sv.model.addChart(spec);
        sv.charts.select(spec);
        sv.charts.animate(spec);
        sv.table.scrollRectToVisible(sv.table.getCellRect(reg[0], reg[1], true).union(new java.awt.Rectangle(
                (int) Math.round(spec.x * z), (int) Math.round(spec.y * z), (int) Math.round(spec.width * z),
                (int) Math.round(spec.height * z))));
        markDirty();
        status(type.label + " chart added - hover for values, click the legend to hide a series, drag to move, double-click to edit");
    }

    private void editSelectedChart() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        ChartSpec c = sv.charts.selected();
        if (c == null) {
            status("Click a chart first, then choose Edit Chart.");
            return;
        }
        editChart(c);
    }

    private static final java.util.regex.Pattern CHART_RANGE =
            java.util.regex.Pattern.compile("^\\s*([A-Za-z]{1,3})(\\d+)\\s*:\\s*([A-Za-z]{1,3})(\\d+)\\s*$");

    private void editChart(ChartSpec c) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        JComboBox<ChartSpec.Type> type = new JComboBox<>(ChartSpec.Type.values());
        type.setSelectedItem(c.type);
        type.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, value == null ? "" : ((ChartSpec.Type) value).label,
                        index, isSelected, cellHasFocus);
            }
        });
        JTextField title = new JTextField(c.title, 20);
        JTextField range = new JTextField(c.rangeText(), 12);
        JCheckBox legend = new JCheckBox("Show legend", c.legend);
        JCheckBox labels = new JCheckBox("Show values on the chart", c.dataLabels);
        JComboBox<ChartSpec.Trend> trend = new JComboBox<>(ChartSpec.Trend.values());
        trend.setSelectedItem(c.trend);
        trend.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list, value == null ? "" : ((ChartSpec.Trend) value).label,
                        index, isSelected, cellHasFocus);
            }
        });
        JCheckBox equation = new JCheckBox("Show the equation and R\u00B2", c.showEquation);
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Chart type:"));
        panel.add(type);
        panel.add(new JLabel("Title:"));
        panel.add(title);
        panel.add(new JLabel("Data range (first row = series names, first column = labels):"));
        panel.add(range);
        panel.add(legend);
        panel.add(labels);
        panel.add(new JLabel("Trendline (line, area, scatter and column charts):"));
        panel.add(trend);
        panel.add(equation);
        while (true) {
            if (JOptionPane.showConfirmDialog(this, panel, "Edit Chart", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            java.util.regex.Matcher m = CHART_RANGE.matcher(range.getText());
            if (m.matches()) {
                int c1 = FormulaEngine.parseColumn(m.group(1));
                int c2 = FormulaEngine.parseColumn(m.group(3));
                int r1 = Integer.parseInt(m.group(2)) - 1;
                int r2 = Integer.parseInt(m.group(4)) - 1;
                if (sv.model.inBounds(r1, c1) && sv.model.inBounds(r2, c2)) {
                    c.r1 = Math.min(r1, r2);
                    c.r2 = Math.max(r1, r2);
                    c.c1 = Math.min(c1, c2);
                    c.c2 = Math.max(c1, c2);
                    break;
                }
            }
            JOptionPane.showMessageDialog(this, "Enter the data range like A1:C6.", "Edit Chart", JOptionPane.WARNING_MESSAGE);
        }
        c.type = (ChartSpec.Type) type.getSelectedItem();
        c.title = title.getText().trim();
        c.legend = legend.isSelected();
        c.dataLabels = labels.isSelected();
        c.trend = (ChartSpec.Trend) trend.getSelectedItem();
        c.showEquation = equation.isSelected();
        c.hidden.clear();
        sv.table.repaint();
        markDirty();
        status("Chart updated");
    }

    private void saveChartImage(SheetView sv, ChartSpec chart) {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setSelectedFile(new File((chart.title.isEmpty() ? "chart" : chart.title.replaceAll("[^A-Za-z0-9 _-]", "_")) + ".png"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG picture (*.png)", "png"));
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".png")) f = new File(f.getParentFile(), f.getName() + ".png");
        try {
            java.awt.image.BufferedImage img = ChartPainter.toImage(chart, ChartData.read(sv.model, chart), 2.0);
            javax.imageio.ImageIO.write(img, "png", f);
            status("Chart saved as " + f.getName());
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the picture:\n" + ex.getMessage(), "Save Chart", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showChartMenu(SheetView sv, ChartSpec chart, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem("Edit Chart...");
        edit.addActionListener(ev -> editChart(chart));
        menu.add(edit);
        JMenu change = new JMenu("Change Chart Type");
        for (ChartSpec.Type t : ChartSpec.Type.values()) {
            JMenuItem item = new JMenuItem(t.label);
            item.setEnabled(t != chart.type);
            item.addActionListener(ev -> {
                chart.type = t;
                chart.hidden.clear();
                sv.charts.animate(chart);
                markDirty();
            });
            change.add(item);
        }
        menu.add(change);
        JMenu trendMenu = new JMenu("Trendline");
        for (ChartSpec.Trend t : ChartSpec.Trend.values()) {
            JMenuItem item = new JMenuItem(t.label);
            item.setEnabled(t != chart.trend);
            item.addActionListener(ev -> {
                chart.trend = t;
                sv.table.repaint();
                markDirty();
            });
            trendMenu.add(item);
        }
        menu.add(trendMenu);
        JMenuItem labelsItem = new JCheckBoxMenuItem("Data Labels", chart.dataLabels);
        labelsItem.addActionListener(ev -> {
            chart.dataLabels = !chart.dataLabels;
            sv.table.repaint();
            markDirty();
        });
        menu.add(labelsItem);
        JMenuItem saveImg = new JMenuItem("Save as Picture...");
        saveImg.addActionListener(ev -> saveChartImage(sv, chart));
        menu.add(saveImg);
        JMenuItem showAll = new JMenuItem("Show All Series");
        showAll.setEnabled(!chart.hidden.isEmpty());
        showAll.addActionListener(ev -> {
            chart.hidden.clear();
            sv.table.repaint();
        });
        menu.add(showAll);
        menu.addSeparator();
        JMenuItem del = new JMenuItem("Delete Chart");
        del.addActionListener(ev -> {
            sv.charts.select(chart);
            sv.charts.deleteSelected();
            status("Chart deleted");
        });
        menu.add(del);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // ------------------------------------------------------------------
    // Data tools: validation, goal seek, custom sort, sparklines, sheet size
    // ------------------------------------------------------------------

    private static final java.util.regex.Pattern BLOCK =
            java.util.regex.Pattern.compile("^\\s*\\$?([A-Za-z]{1,3})\\$?(\\d+)\\s*(?::\\s*\\$?([A-Za-z]{1,3})\\$?(\\d+))?\\s*$");

    /** "B2" or "B2:D9" -> {r1, c1, r2, c2}, or null when it is not inside the sheet. */
    private static int[] parseBlock(String text, SpreadsheetModel m) {
        java.util.regex.Matcher mt = BLOCK.matcher(text == null ? "" : text);
        if (!mt.matches()) return null;
        int c1 = FormulaEngine.parseColumn(mt.group(1));
        int r1 = Integer.parseInt(mt.group(2)) - 1;
        int c2 = mt.group(3) == null ? c1 : FormulaEngine.parseColumn(mt.group(3));
        int r2 = mt.group(4) == null ? r1 : Integer.parseInt(mt.group(4)) - 1;
        int[] a = {Math.min(r1, r2), Math.min(c1, c2), Math.max(r1, r2), Math.max(c1, c2)};
        return m.inBounds(a[0], a[1]) && m.inBounds(a[2], a[3]) ? a : null;
    }

    /** Exactly what is selected (not expanded to the table). */
    private static int[] selectionBlock(SheetView sv) {
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) {
            int r = Math.max(0, sv.table.getSelectionModel().getLeadSelectionIndex());
            int c = Math.max(0, sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
            return new int[]{r, c, r, c};
        }
        return new int[]{rows[0], cols[0], rows[rows.length - 1], cols[cols.length - 1]};
    }

    private static String blockText(int[] b) {
        String a = FormulaEngine.cellRef(b[0], b[1]);
        return b[0] == b[2] && b[1] == b[3] ? a : a + ":" + FormulaEngine.cellRef(b[2], b[3]);
    }

    private static JPanel labeled(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(130, 22));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    // ---- data validation ----

    private void validationDialog() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] sel = selectionBlock(sv);
        ValidationRule old = sv.model.validationAt(sel[0], sel[1]);
        JTextField range = new JTextField(blockText(sel), 14);
        JComboBox<ValidationRule.Type> type = new JComboBox<>(ValidationRule.Type.values());
        JComboBox<ValidationRule.Op> op = new JComboBox<>(ValidationRule.Op.values());
        JTextField v1 = new JTextField(14);
        JTextField v2 = new JTextField(14);
        JTextField source = new JTextField(22);
        JCheckBox blank = new JCheckBox("Ignore blank", true);
        JCheckBox drop = new JCheckBox("In-cell dropdown", true);
        JComboBox<ValidationRule.Alert> alert = new JComboBox<>(ValidationRule.Alert.values());
        JTextField title = new JTextField(22);
        JTextField message = new JTextField(22);
        javax.swing.ListCellRenderer<Object> labels = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                String t = value instanceof ValidationRule.Type ? ((ValidationRule.Type) value).label
                        : value instanceof ValidationRule.Op ? ((ValidationRule.Op) value).label
                        : value instanceof ValidationRule.Alert ? ((ValidationRule.Alert) value).label : String.valueOf(value);
                return super.getListCellRendererComponent(list, t, index, isSelected, cellHasFocus);
            }
        };
        type.setRenderer(labels);
        op.setRenderer(labels);
        alert.setRenderer(labels);
        if (old != null) {
            type.setSelectedItem(old.type);
            op.setSelectedItem(old.op);
            if (old.type == ValidationRule.Type.LIST) source.setText(old.v1);
            else v1.setText(old.v1);
            v2.setText(old.v2);
            blank.setSelected(old.allowBlank);
            drop.setSelected(old.dropdown);
            alert.setSelectedItem(old.alert);
            title.setText(old.title);
            message.setText(old.message);
        }
        Runnable refresh = () -> {
            boolean list = type.getSelectedItem() == ValidationRule.Type.LIST;
            source.setEnabled(list);
            drop.setEnabled(list);
            op.setEnabled(!list);
            v1.setEnabled(!list);
            v2.setEnabled(!list && ((ValidationRule.Op) op.getSelectedItem()).needsTwo());
        };
        type.addActionListener(e -> refresh.run());
        op.addActionListener(e -> refresh.run());
        refresh.run();

        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 5));
        panel.add(labeled("Apply to:", range));
        panel.add(labeled("Allow:", type));
        panel.add(labeled("List source:", source));
        panel.add(new JLabel("<html><small>Items separated by commas (Yes,No,Maybe) or a range like =$A$1:$A$5</small></html>"));
        panel.add(labeled("Data:", op));
        panel.add(labeled("Minimum / value:", v1));
        panel.add(labeled("Maximum:", v2));
        JPanel checks = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        checks.add(blank);
        checks.add(drop);
        panel.add(checks);
        panel.add(labeled("When invalid:", alert));
        panel.add(labeled("Alert title:", title));
        panel.add(labeled("Alert message:", message));
        while (true) {
            if (JOptionPane.showConfirmDialog(this, panel, "Data Validation", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            int[] blk = parseBlock(range.getText(), sv.model);
            ValidationRule.Type t = (ValidationRule.Type) type.getSelectedItem();
            ValidationRule.Op o = (ValidationRule.Op) op.getSelectedItem();
            if (blk == null) {
                JOptionPane.showMessageDialog(this, "Enter the cells like B2:B20.", "Data Validation", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            ValidationRule rule = new ValidationRule(t, o, blk[0], blk[1], blk[2], blk[3],
                    t == ValidationRule.Type.LIST ? source.getText().trim() : v1.getText().trim(), v2.getText().trim(),
                    blank.isSelected(), drop.isSelected(), (ValidationRule.Alert) alert.getSelectedItem(),
                    title.getText().trim(), message.getText().trim());
            if (!rule.isComplete()) {
                JOptionPane.showMessageDialog(this, t == ValidationRule.Type.LIST
                                ? "Enter the list items or a source range."
                                : "Enter a number for the limit" + (o.needsTwo() ? "s." : "."),
                        "Data Validation", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            sv.model.addValidation(rule);
            sv.invalidDirty = true;
            sv.table.repaint();
            markDirty();
            int bad = 0;
            for (int[] cell : sv.model.findInvalid()) if (rule.covers(cell[0], cell[1])) bad++;
            status("Validation added to " + rule.rangeText() + (bad > 0 ? " - " + bad
                    + " existing cell(s) break the rule (use Circle Invalid)" : ""));
            return;
        }
    }

    private void clearValidationAction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] b = selectionBlock(sv);
        int n = sv.model.clearValidation(b[0], b[1], b[2], b[3]);
        sv.invalidDirty = true;
        sv.table.repaint();
        if (n > 0) markDirty();
        status(n == 0 ? "No validation rules in the selection" : "Validation removed from " + n + " cell(s)");
    }

    private void toggleCircleInvalid() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        sv.circleInvalid = !sv.circleInvalid;
        sv.invalidDirty = true;
        if (circleBtn != null) circleBtn.setActive(sv.circleInvalid);
        sv.table.repaint();
        if (sv.circleInvalid) {
            int n = sv.model.findInvalid().size();
            status(n == 0 ? "No invalid data found" : n + " cell(s) break their validation rule - circled in red");
        } else {
            status("Circles hidden");
        }
    }

    // ---- goal seek ----

    private void goalSeekDialog() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] sel = selectionBlock(sv);
        JTextField setCell = new JTextField(sv.model.getInput(sel[0], sel[1]).startsWith("=") ? FormulaEngine.cellRef(sel[0], sel[1]) : "", 10);
        JTextField toValue = new JTextField(10);
        JTextField changing = new JTextField(10);
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 5));
        panel.add(labeled("Set cell:", setCell));
        panel.add(labeled("To value:", toValue));
        panel.add(labeled("By changing cell:", changing));
        panel.add(new JLabel("<html><small>The \"set cell\" must hold a formula that depends on the changing cell.</small></html>"));
        if (JOptionPane.showConfirmDialog(this, panel, "Goal Seek", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        int[] t = parseBlock(setCell.getText(), sv.model);
        int[] c = parseBlock(changing.getText(), sv.model);
        double goal;
        try {
            goal = Double.parseDouble(toValue.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "\"To value\" must be a number.", "Goal Seek", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (t == null || c == null || t[0] != t[2] || t[1] != t[3] || c[0] != c[2] || c[1] != c[3]) {
            JOptionPane.showMessageDialog(this, "Enter single cells like B5 for the two cell boxes.", "Goal Seek",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        GoalSeek.Result res = GoalSeek.solve(sv.model, t[0], t[1], goal, c[0], c[1]);
        if (!res.found) {
            GoalSeek.restore(sv.model, c[0], c[1], res);
            JOptionPane.showMessageDialog(this, res.message, "Goal Seek", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int keep = JOptionPane.showConfirmDialog(this,
                "Goal Seek found a solution.\n\n" + FormulaEngine.cellRef(t[0], t[1]) + " = "
                        + FormulaEngine.formatNumber(res.value) + "  (target " + FormulaEngine.formatNumber(goal) + ")\n"
                        + FormulaEngine.cellRef(c[0], c[1]) + " = " + FormulaEngine.formatNumber(res.x)
                        + "\n\nKeep this solution?", "Goal Seek", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (keep == JOptionPane.YES_OPTION) {
            markDirty();
            status("Goal Seek: " + FormulaEngine.cellRef(c[0], c[1]) + " set to " + FormulaEngine.formatNumber(res.x));
        } else {
            GoalSeek.restore(sv.model, c[0], c[1], res);
            status("Goal Seek cancelled - the original value is back");
        }
        sv.table.repaint();
    }

    // ---- custom sort ----

    private void customSort() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] reg;
        AutoFilter af = sv.model.getFilter();
        if (af != null) reg = new int[]{af.headerRow, af.firstCol, sv.model.filterLastRow(), af.lastCol};
        else reg = tableRegion(sv);
        if (reg[2] <= reg[0]) {
            status("Click inside a table with at least two rows first.");
            return;
        }
        JCheckBox header = new JCheckBox("My data has headers", af != null || looksLikeHeader(sv.model, reg[0], reg[2], reg[1], reg[3]));
        JComboBox<String>[] col = new JComboBox[3];
        JComboBox<String>[] order = new JComboBox[3];
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 5));
        panel.add(new JLabel("Sorting " + blockText(reg)));
        panel.add(header);
        Runnable fill = () -> {
            for (int i = 0; i < 3; i++) {
                int keep = col[i].getSelectedIndex();
                col[i].removeAllItems();
                if (i > 0) col[i].addItem("(none)");
                for (int c = reg[1]; c <= reg[3]; c++) {
                    Object h = sv.model.getComputedValue(reg[0], c);
                    col[i].addItem(header.isSelected() && h != null && !String.valueOf(h).isEmpty()
                            ? String.valueOf(h) : "Column " + FormulaEngine.columnLabel(c));
                }
                col[i].setSelectedIndex(Math.max(0, Math.min(keep, col[i].getItemCount() - 1)));
            }
        };
        for (int i = 0; i < 3; i++) {
            col[i] = new JComboBox<>();
            order[i] = new JComboBox<>(new String[]{"Ascending (A-Z, small to large)", "Descending (Z-A, large to small)"});
        }
        fill.run();
        header.addActionListener(e -> fill.run());
        String[] names = {"Sort by", "Then by", "Then by"};
        for (int i = 0; i < 3; i++) {
            panel.add(labeled(names[i] + ":", col[i]));
            panel.add(labeled("Order:", order[i]));
        }
        int selCol = sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
        if (selCol >= reg[1] && selCol <= reg[3]) col[0].setSelectedIndex(selCol - reg[1]);
        if (JOptionPane.showConfirmDialog(this, panel, "Custom Sort", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        List<Integer> keys = new ArrayList<>();
        List<Boolean> asc = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int idx = col[i].getSelectedIndex() - (i > 0 ? 1 : 0);
            if (idx < 0) continue;
            int column = reg[1] + idx;
            if (keys.contains(column)) continue;
            keys.add(column);
            asc.add(order[i].getSelectedIndex() == 0);
        }
        int r1 = reg[0] + (header.isSelected() ? 1 : 0);
        int[] k = new int[keys.size()];
        boolean[] a = new boolean[keys.size()];
        for (int i = 0; i < k.length; i++) {
            k[i] = keys.get(i);
            a[i] = asc.get(i);
        }
        if (sv.model.sortRowsMulti(r1, reg[2], reg[1], reg[3], k, a)) {
            sv.applyRowVisibility();
            sv.table.repaint();
            markDirty();
            status("Sorted " + (reg[2] - r1 + 1) + " rows by " + k.length + " column(s)");
        } else {
            status("Nothing to sort.");
        }
    }

    // ---- sparklines ----

    private JPopupMenu sparklineMenu() {
        JPopupMenu m = new JPopupMenu();
        for (Sparkline.Type t : Sparkline.Type.values()) {
            JMenuItem item = new JMenuItem(t.label);
            item.addActionListener(e -> sparklineDialog(t));
            m.add(item);
        }
        return m;
    }

    private static final String[] SPARK_COLOR_NAMES = {"Green", "Blue", "Orange", "Purple", "Red", "Teal"};
    private static final Color[] SPARK_COLORS = {new Color(0x107C41), new Color(0x1E88E5), new Color(0xFB8C00),
            new Color(0x8E24AA), new Color(0xE53935), new Color(0x00ACC1)};

    private void sparklineDialog(Sparkline.Type type) {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] sel = selectionBlock(sv);
        boolean multi = sel[0] != sel[2] || sel[1] != sel[3];
        String dataText = multi ? blockText(sel) : "";
        String locText = multi && sel[3] + 1 < sv.model.getColumnCount()
                ? blockText(new int[]{sel[0], sel[3] + 1, sel[2], sel[3] + 1}) : "";
        JTextField data = new JTextField(dataText, 14);
        JTextField where = new JTextField(locText, 14);
        JComboBox<String> color = new JComboBox<>(SPARK_COLOR_NAMES);
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 5));
        panel.add(labeled("Data range:", data));
        panel.add(labeled("Location range:", where));
        panel.add(labeled("Colour:", color));
        panel.add(new JLabel("<html><small>One sparkline per row (or per column) of the data range,<br>"
                + "drawn in the location cells, e.g. data B2:G9 and location H2:H9.</small></html>"));
        while (true) {
            if (JOptionPane.showConfirmDialog(this, panel, type.label + " Sparklines", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            int[] d = parseBlock(data.getText(), sv.model);
            int[] w = parseBlock(where.getText(), sv.model);
            String problem = null;
            List<Sparkline> made = new ArrayList<>();
            if (d == null || w == null) {
                problem = "Enter the data range and the location range, like B2:G9 and H2:H9.";
            } else {
                int dr = d[2] - d[0] + 1;
                int dc = d[3] - d[1] + 1;
                int wr = w[2] - w[0] + 1;
                int wc = w[3] - w[1] + 1;
                Color col = SPARK_COLORS[color.getSelectedIndex()];
                if (wc == 1 && wr == dr) { // one sparkline per data row
                    for (int i = 0; i < dr; i++) made.add(new Sparkline(w[0] + i, w[1], d[0] + i, d[1], d[0] + i, d[3], type, col));
                } else if (wr == 1 && wc == dc) { // one sparkline per data column
                    for (int i = 0; i < dc; i++) made.add(new Sparkline(w[0], w[1] + i, d[0], d[1] + i, d[2], d[1] + i, type, col));
                } else if (wr == 1 && wc == 1 && (dr == 1 || dc == 1)) {
                    made.add(new Sparkline(w[0], w[1], d[0], d[1], d[2], d[3], type, col));
                } else {
                    problem = "The location range must have one cell for each row (or column) of the data range.";
                }
            }
            if (problem != null) {
                JOptionPane.showMessageDialog(this, problem, "Sparklines", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            for (Sparkline sp : made) sv.model.setSparkline(sp);
            sv.table.repaint();
            markDirty();
            status(made.size() + " " + type.label.toLowerCase() + " sparkline(s) added");
            return;
        }
    }

    private void clearSparklinesAction() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] b = selectionBlock(sv);
        int n = sv.model.clearSparklines(b[0], b[1], b[2], b[3]);
        sv.table.repaint();
        if (n > 0) markDirty();
        status(n == 0 ? "No sparklines in the selection" : n + " sparkline(s) removed");
    }

    // ---- sheet size ----

    private void sheetSizeDialog() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        JComboBox<String> rows = new JComboBox<>(new String[]{"1,000", "10,000", "100,000", "500,000", "1,048,576"});
        rows.setEditable(true);
        rows.setSelectedItem(String.format("%,d", sv.model.getRowCount()));
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 5));
        panel.add(new JLabel("This sheet has " + String.format("%,d", sv.model.getRowCount()) + " rows. Make it:"));
        panel.add(rows);
        panel.add(new JLabel("<html><small>Up to 1,048,576 rows. Very large sheets use more memory and make<br>"
                + "sorting, filtering and saving slower. You cannot go below the last used row.</small></html>"));
        if (JOptionPane.showConfirmDialog(this, panel, "Sheet Size", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        int n;
        try {
            n = Integer.parseInt(String.valueOf(rows.getSelectedItem()).replace(",", "").replace(" ", "").trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a whole number of rows.", "Sheet Size", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (n < SpreadsheetModel.DEFAULT_ROWS || n > SpreadsheetModel.MAX_ROWS) {
            JOptionPane.showMessageDialog(this, "Rows must be between " + SpreadsheetModel.DEFAULT_ROWS + " and "
                    + String.format("%,d", SpreadsheetModel.MAX_ROWS) + ".", "Sheet Size", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!sv.model.setRowCount(n)) {
            JOptionPane.showMessageDialog(this, "There is data below row " + n + ". Delete it first or choose more rows.",
                    "Sheet Size", JOptionPane.WARNING_MESSAGE);
            return;
        }
        sv.refreshRows();
        markDirty();
        status("This sheet now has " + String.format("%,d", n) + " rows");
    }

    /** Row numbers 1, 2, 3 ... made up on demand, so a million rows cost no memory. */
    private static final class RowLabelModel extends javax.swing.AbstractListModel<String> {
        private final SpreadsheetModel model;

        RowLabelModel(SpreadsheetModel model) {
            this.model = model;
        }

        @Override
        public int getSize() {
            return model.getRowCount();
        }

        @Override
        public String getElementAt(int index) {
            return String.valueOf(index + 1);
        }
    }

    // ------------------------------------------------------------------
    // Power Pivot tools
    // ------------------------------------------------------------------

    private String uniqueSheetName(String base) {
        for (int n = 1; ; n++) {
            String name = base + n;
            boolean used = false;
            for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getTitleAt(i).equalsIgnoreCase(name)) used = true;
            if (!used) return name;
        }
    }

    private void openPivot() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        int[] reg = tableRegion(sv);
        if (reg[2] - reg[0] < 1) {
            JOptionPane.showMessageDialog(this, "Click inside a table first: a header row followed by data rows.",
                    "PivotTable", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new PivotDialog(this, sv.model, reg, cfg -> {
            SpreadsheetModel out = PivotBuilder.pivot(sv.model, cfg.r1, cfg.c1, cfg.r2, cfg.c2,
                    cfg.rowField, cfg.colField, cfg.valField, cfg.func, cfg.totals);
            addSheet(uniqueSheetName("Pivot"), out);
            status("PivotTable created on a new sheet");
        }).setVisible(true);
    }

    private void quickSummary() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        int[] reg = tableRegion(sv);
        if (reg[2] - reg[0] < 1) {
            JOptionPane.showMessageDialog(this, "Click inside a table first: a header row followed by data rows.",
                    "Summary", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        addSheet(uniqueSheetName("Summary"), PivotBuilder.summary(sv.model, reg[0], reg[1], reg[2], reg[3]));
        status("Column summary created on a new sheet");
    }

    private void addCalculatedColumn() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] reg = tableRegion(sv);
        if (reg[2] - reg[0] < 1) {
            JOptionPane.showMessageDialog(this, "Click inside a table first: a header row followed by data rows.",
                    "Calculated Column", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextField name = new JTextField("Total", 16);
        JTextField expr = new JTextField(20);
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Columns: " + String.join(", ", PivotBuilder.headers(sv.model, reg[0], reg[1], reg[3]))));
        panel.add(new JLabel("New column name:"));
        panel.add(name);
        panel.add(new JLabel("Formula using [Column] names, e.g. [Qty]*[Price]:"));
        panel.add(expr);
        if (JOptionPane.showConfirmDialog(this, panel, "Calculated Column", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (reg[3] + 1 < sv.model.getColumnCount() && sv.model.hasDataIn(reg[0], reg[3] + 1, reg[2], reg[3] + 1)
                && JOptionPane.showConfirmDialog(this, "The column to the right already contains data that will be overwritten. Continue?",
                "Calculated Column", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            int n = PivotBuilder.addCalculatedColumn(sv.model, reg[0], reg[1], reg[2], reg[3], name.getText(), expr.getText());
            sv.table.repaint();
            markDirty();
            status("Added column \"" + name.getText().trim() + "\" with " + n + " formulas");
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Calculated Column", JOptionPane.WARNING_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    // Developer, Help
    // ------------------------------------------------------------------

    private void toggleShowFormulas() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        sv.model.setShowFormulas(!sv.model.isShowFormulas());
        if (showFormulasBtn != null) showFormulasBtn.setActive(sv.model.isShowFormulas());
        sv.table.repaint();
        status(sv.model.isShowFormulas() ? "Showing formulas on this sheet" : "Showing results");
    }

    private void inspectCell() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        int r = Math.max(0, sv.table.getSelectionModel().getLeadSelectionIndex());
        int c = Math.max(0, sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
        Object v = sv.model.getComputedValue(r, c);
        CellStyle st = sv.model.getStyle(r, c);
        String type = v == null ? "Empty" : v instanceof Double ? "Number" : v instanceof Boolean ? "Logical"
                : FormulaEngine.isErrorCode(String.valueOf(v)) ? "Error" : "Text";
        StringBuilder sb = new StringBuilder();
        sb.append("Cell:         ").append(FormulaEngine.cellRef(r, c)).append('\n');
        sb.append("Input:        ").append(sv.model.getInput(r, c)).append('\n');
        sb.append("Value:        ").append(v == null ? "" : v).append('\n');
        sb.append("Type:         ").append(type).append('\n');
        sb.append("Displayed as: ").append(sv.model.getValueAt(r, c)).append('\n');
        sb.append("Number format: ").append(st == null ? "General" : CellFormats.NAMES[st.numFmt]).append('\n');
        sb.append("Font:         ").append(st == null || st.fontFamily == null ? "Default" : st.fontFamily)
                .append(st != null && st.fontSize > 0 ? ", " + st.fontSize + " pt" : "")
                .append(st != null && st.bold ? ", bold" : "").append(st != null && st.italic ? ", italic" : "").append('\n');
        CondRule rule = sv.model.matchingRule(r, c);
        sb.append("Rule applied: ").append(rule == null ? "none" : rule.type.name().toLowerCase()).append('\n');
        JTextArea area = new JTextArea(sb.toString(), 10, 46);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Inspect Cell", JOptionPane.PLAIN_MESSAGE);
    }

    private void workbookInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(currentFile == null ? "(not saved yet)" : currentFile.getAbsolutePath()).append('\n');
        sb.append("Password: ").append(workbookPassword != null ? "yes (encrypted)" : "no").append('\n');
        sb.append("AutoSave: ").append(autoSave.isEnabled() ? "on, every " + autoSave.getIntervalSeconds() + " s ("
                + (autoSave.getMode() == AutoSaveManager.Mode.FILE ? "into the file" : "recovery copy") + ")" : "off").append("\n\n");
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component comp = tabs.getComponentAt(i);
            if (!(comp instanceof SheetView)) continue;
            SpreadsheetModel m = ((SheetView) comp).model;
            int cells = 0;
            int formulas = 0;
            for (int r = 0; r < m.getRowCount(); r++) {
                for (int c = 0; c < m.getColumnCount(); c++) {
                    String in = m.getInput(r, c);
                    if (in.isEmpty()) continue;
                    cells++;
                    if (in.startsWith("=")) formulas++;
                }
            }
            sb.append(tabs.getTitleAt(i)).append(":  ").append(cells).append(" cells, ").append(formulas)
                    .append(" formulas, ").append(m.getImages().size()).append(" picture(s), ")
                    .append(m.getShapes().size()).append(" drawing(s), ").append(m.getCharts().size()).append(" chart(s), ").append(m.getValidations().size())
                    .append(" validation rule(s), ").append(m.getSparklines().size()).append(" sparkline(s), ").append(m.getCondRules().size())
                    .append(" rule(s)").append(m.getFilter() != null ? ", filter on" : "")
                    .append(m.isSheetProtected() ? ", protected" : "").append('\n');
        }
        JTextArea area = new JTextArea(sb.toString(), 12, 60);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Workbook Info", JOptionPane.PLAIN_MESSAGE);
    }

    private void showShortcuts() {
        ShortcutsDialog.open(this);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "Spreadsheet Pro " + AppInfo.version() + "\nA spreadsheet written in Java Swing.\n\n"
                        + "Formulas (128 functions), number formats, conditional formatting, sort & filter,\n"
                        + "pivot tables, pictures and drawing, VBA-style macros, sheet protection,\n"
                        + "encrypted workbooks, AutoSave, version history, read-only mode,\n"
                        + "print preview and page setup, and a command palette (Ctrl+Shift+P).",
                "About Spreadsheet Pro", JOptionPane.INFORMATION_MESSAGE, Icons.get("logo", 48, Theme.accent));
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    /** Quietly looks for a newer release once a day; only speaks up if there is one. */
    void checkForUpdatesInBackground() {
        if (!UpdateChecker.dueForAutoCheck()) return;
        checkForUpdates(false);
    }

    /** @param manual true when the user asked: then "up to date" and errors are reported too. */
    void checkForUpdates(boolean manual) {
        if (manual) status("Checking for updates...");
        Thread t = new Thread(() -> {
            UpdateChecker.Result r = null;
            String error = null;
            try {
                r = UpdateChecker.check(AppInfo.version());
                UpdateChecker.markChecked();
            } catch (IOException | RuntimeException ex) {
                error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            UpdateChecker.Result result = r;
            String err = error;
            SwingUtilities.invokeLater(() -> showUpdateResult(result, err, manual));
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    private void showUpdateResult(UpdateChecker.Result r, String error, boolean manual) {
        if (r == null) {
            if (manual) {
                status("Could not check for updates");
                JOptionPane.showMessageDialog(this, "Could not check for updates.\n" + error
                        + "\n\nCheck your internet connection and try again.", "Check for Updates", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }
        if (!r.newer) {
            if (manual) {
                status("Spreadsheet Pro is up to date");
                String v = AppInfo.version();
                JOptionPane.showMessageDialog(this, "dev".equals(v)
                                ? "This is a development build, so it does not compare versions.\nLatest release on GitHub: " + r.latest
                                : "You have the newest version (" + v + ").", "Check for Updates", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        if (!manual && UpdateChecker.isSkipped(r.latest)) return;
        String notes = r.notes.trim();
        if (notes.length() > 700) notes = notes.substring(0, 700) + "...";
        JTextArea area = new JTextArea("Version " + r.latest + " is ready (you have " + r.current + ").\n\n"
                + (notes.isEmpty() ? "See the release page for what is new." : "What is new:\n" + notes), 10, 44);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(UIManager.getColor("Panel.background"));
        String[] options = {"Download", "Later", "Skip this version"};
        int choice = JOptionPane.showOptionDialog(this, new JScrollPane(area), "A new version is available",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, Icons.get("logo", 48, Theme.accent), options, options[0]);
        if (choice == 0) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(r.downloadUrl));
                status("Downloading Spreadsheet Pro " + r.latest + " in your browser");
            } catch (IOException | RuntimeException ex) {
                JOptionPane.showMessageDialog(this, "Open this address in your browser:\n" + r.pageUrl, "Download", JOptionPane.INFORMATION_MESSAGE);
            }
        } else if (choice == 2) {
            UpdateChecker.skip(r.latest);
        }
    }

    // ------------------------------------------------------------------
    // Notes (cell comments)
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Hyperlinks
    // ------------------------------------------------------------------

    private FindReplaceDialog findDialog;

    private void showFind(boolean replace) {
        SheetView sv = currentSheet();
        if (sv == null) return;
        if (findDialog == null || !findDialog.isDisplayable()) {
            findDialog = new FindReplaceDialog(this, new FindReplaceDialog.Host() {
                @Override public int sheetCount() { return tabs.getTabCount(); }
                @Override public String sheetName(int i) { return tabs.getTitleAt(i); }
                @Override public SpreadsheetModel model(int i) { return ((SheetView) tabs.getComponentAt(i)).model; }
                @Override public int currentSheet() { return tabs.getSelectedIndex(); }
                @Override public int[] activeCell() {
                    SheetView c = currentSheet0();
                    return new int[]{Math.max(0, c.table.getSelectionModel().getLeadSelectionIndex()),
                            Math.max(0, c.table.getColumnModel().getSelectionModel().getLeadSelectionIndex())};
                }
                @Override public void go(int sheet, int row, int col) {
                    tabs.setSelectedIndex(sheet);
                    SheetView d = (SheetView) tabs.getComponentAt(sheet);
                    d.table.changeSelection(row, col, false, false);
                    d.table.scrollRectToVisible(d.table.getCellRect(row, col, true));
                }
                @Override public void changed() {
                    SheetView c = currentSheet0();
                    if (c != null) c.table.repaint();
                    markDirty();
                }
                @Override public void status(String t) { SpreadsheetApp.this.status(t); }
            }, replace);
        }
        if (!sv.table.isEditing() && sv.table.getSelectedRowCount() == 1 && sv.table.getSelectedColumnCount() == 1) {
            findDialog.setFindText(sv.model.getInput(sv.table.getSelectedRow(), sv.table.getSelectedColumn()));
        }
        findDialog.setVisible(true);
        findDialog.toFront();
        findDialog.focusFind();
    }

    private SheetView currentSheet0() {
        return currentSheet();
    }

    private void insertLink() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        if (sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        int r = Math.max(0, sv.table.getSelectionModel().getLeadSelectionIndex());
        int c = Math.max(0, sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
        String old = sv.model.explicitLinkAt(r, c);
        JTextField text = new JTextField(sv.model.getInput(r, c).startsWith("=") ? "" : sv.model.getInput(r, c), 26);
        JTextField addr = new JTextField(old == null ? "" : old, 26);
        JComboBox<String> sheetBox = new JComboBox<>();
        for (int i = 0; i < tabs.getTabCount(); i++) sheetBox.addItem(tabs.getTitleAt(i));
        JTextField cellField = new JTextField("A1", 6);
        JRadioButton web = new JRadioButton("Web page or e-mail address", old == null || !Hyperlinks.isInternal(old));
        JRadioButton place = new JRadioButton("Place in this workbook", old != null && Hyperlinks.isInternal(old));
        ButtonGroup bg = new ButtonGroup();
        bg.add(web);
        bg.add(place);
        if (old != null && Hyperlinks.isInternal(old)) {
            String[] sp = Hyperlinks.splitInternal(old);
            if (sp[0] != null) sheetBox.setSelectedItem(sp[0]);
            cellField.setText(sp[1]);
            addr.setText("");
        }
        sheetBox.setSelectedItem(sv == tabs.getSelectedComponent() && old == null ? tabs.getTitleAt(tabs.getSelectedIndex()) : sheetBox.getSelectedItem());
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
        g.insets = new java.awt.Insets(3, 3, 3, 3);
        g.anchor = java.awt.GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0; panel.add(new JLabel("Text to display:"), g);
        g.gridx = 1; g.gridwidth = 2; g.fill = java.awt.GridBagConstraints.HORIZONTAL; panel.add(text, g);
        g.gridwidth = 3; g.gridx = 0; g.gridy = 1; panel.add(web, g);
        g.gridwidth = 1; g.gridy = 2; g.gridx = 0; panel.add(new JLabel("Address:"), g);
        g.gridx = 1; g.gridwidth = 2; panel.add(addr, g);
        g.gridwidth = 3; g.gridx = 0; g.gridy = 3; panel.add(place, g);
        g.gridwidth = 1; g.gridy = 4; g.gridx = 0; panel.add(new JLabel("Sheet / cell:"), g);
        g.gridx = 1; panel.add(sheetBox, g);
        g.gridx = 2; panel.add(cellField, g);
        Object[] options = old == null ? new Object[]{"OK", "Cancel"} : new Object[]{"OK", "Remove Link", "Cancel"};
        int res = JOptionPane.showOptionDialog(this, panel, "Insert Link - " + FormulaEngine.cellRef(r, c), JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (res == 1 && old != null) {
            removeLinks();
            return;
        }
        if (res != 0) return;
        String target;
        if (place.isSelected()) {
            int[] cell = Hyperlinks.parseCell(cellField.getText());
            if (cell == null) {
                JOptionPane.showMessageDialog(this, "Type a cell such as B5.", "Insert Link", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String sn = String.valueOf(sheetBox.getSelectedItem());
            target = "#'" + sn.replace("'", "''") + "'!" + FormulaEngine.cellRef(cell[0], cell[1]);
        } else {
            target = Hyperlinks.normalize(addr.getText());
            if (target == null || Hyperlinks.isInternal(target)) {
                JOptionPane.showMessageDialog(this, "Type a web address (https://...) or an e-mail address.\nOnly http, https and mailto links are allowed.",
                        "Insert Link", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        boolean one = rows.length * cols.length <= 1;
        String shown = text.getText();
        if (one && shown.trim().isEmpty() && sv.model.getInput(r, c).isEmpty()) shown = target.startsWith("mailto:") ? target.substring(7) : target;
        if (one) {
            if (!shown.equals(sv.model.getInput(r, c)) && !shown.trim().isEmpty()) sv.model.setValueAt(shown, r, c);
            sv.model.setLink(r, c, target);
        } else {
            for (int rr : rows) for (int cc : cols) sv.model.setLink(rr, cc, target);
        }
        sv.table.repaint();
        markDirty();
        status("Link added - Ctrl+click the cell to follow it");
    }

    private void removeLinks() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) return;
        int n = sv.model.clearLinks(rows[0], cols[0], rows[rows.length - 1], cols[cols.length - 1]);
        sv.table.repaint();
        if (n > 0) markDirty();
        status(n == 0 ? "No links in the selection" : n + (n == 1 ? " link removed" : " links removed"));
    }

    /** Follows the link on a cell: web/mail opens the browser, a workbook place jumps there. */
    private void openLinkAt(SheetView sv, int r, int c) {
        String target = sv.model.linkAt(r, c);
        if (target == null) return;
        if (Hyperlinks.isInternal(target)) {
            String[] sp = Hyperlinks.splitInternal(target);
            int[] cell = Hyperlinks.parseCell(sp[1]);
            int idx = -1;
            if (sp[0] == null) idx = tabs.indexOfComponent(sv);
            else for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getTitleAt(i).equalsIgnoreCase(sp[0])) idx = i;
            if (idx < 0 || cell == null) {
                status("That link points to a place that does not exist");
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            tabs.setSelectedIndex(idx);
            SheetView dst = (SheetView) tabs.getComponentAt(idx);
            if (cell[0] < dst.table.getRowCount() && cell[1] < dst.table.getColumnCount()) {
                dst.table.changeSelection(cell[0], cell[1], false, false);
                dst.table.scrollRectToVisible(dst.table.getCellRect(cell[0], cell[1], true));
            }
            dst.table.requestFocusInWindow();
            status("Jumped to " + FormulaEngine.cellRef(cell[0], cell[1]));
            return;
        }
        String err = Hyperlinks.openExternal(target);
        status(err == null ? "Opening " + target : err);
    }

    private void editNote() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        if (sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        int r = Math.max(0, sv.table.getSelectionModel().getLeadSelectionIndex());
        int c = Math.max(0, sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
        String old = sv.model.commentAt(r, c);
        if (sv.model.isProtected()) {
            if (old == null) {
                status("This sheet is protected or read-only - notes cannot be added.");
                return;
            }
            JOptionPane.showMessageDialog(this, old, "Note on " + FormulaEngine.cellRef(r, c), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextArea area = new JTextArea(old == null ? "" : old, 6, 30);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(area);
        if (JOptionPane.showConfirmDialog(this, sp, "Note on " + FormulaEngine.cellRef(r, c), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String text = area.getText();
        if (!sv.model.setComment(r, c, text)) return;
        sv.table.repaint();
        markDirty();
        status(text.trim().isEmpty() ? "Note removed" : "Note saved on " + FormulaEngine.cellRef(r, c) + " - hover the red corner to read it");
    }

    private void deleteNotes() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] b = selectionBlock(sv);
        int n = sv.model.clearComments(b[0], b[1], b[2], b[3]);
        sv.table.repaint();
        if (n > 0) markDirty();
        status(n == 0 ? "No notes in the selection" : n + " note(s) removed");
    }

    private void nextNote() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        List<int[]> cells = sv.model.commentCells();
        if (cells.isEmpty()) {
            status("This sheet has no notes");
            return;
        }
        int r = Math.max(0, sv.table.getSelectionModel().getLeadSelectionIndex());
        int c = Math.max(0, sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
        int[] pick = cells.get(0);
        for (int[] p : cells) {
            if (p[0] > r || (p[0] == r && p[1] > c)) {
                pick = p;
                break;
            }
        }
        sv.table.changeSelection(pick[0], pick[1], false, false);
        sv.table.scrollRectToVisible(sv.table.getCellRect(pick[0], pick[1], true));
        status("Note on " + FormulaEngine.cellRef(pick[0], pick[1]) + ": " + sv.model.commentAt(pick[0], pick[1]).replace('\n', ' '));
    }

    // ------------------------------------------------------------------
    // PDF export
    // ------------------------------------------------------------------

    private void exportPdf() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        if (sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        PrintLayout layout = layoutFor(sv);
        if (layout.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There is nothing to export on this sheet.", "Export PDF", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        String base = currentFile == null ? "Untitled" : currentFile.getName().replaceFirst("\\.[^.]*$", "");
        int idx = tabs.indexOfComponent(sv);
        fc.setSelectedFile(new File(base + (idx >= 0 ? " - " + tabs.getTitleAt(idx) : "") + ".pdf"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF document (*.pdf)", "pdf"));
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".pdf")) f = new File(f.getParentFile(), f.getName() + ".pdf");
        if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " already exists. Replace it?", "Export PDF",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            List<java.awt.image.BufferedImage> pages = new ArrayList<>();
            for (int i = 0; i < layout.pageCount(); i++) pages.add(layout.render(i, 200 / 72.0));
            PdfWriter.write(f, pages, layout.paperWidth(), layout.paperHeight(), base);
            status("Saved " + layout.pageCount() + " page(s) to " + f.getName());
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the PDF:\n" + ex.getMessage(), "Export PDF", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    // Printing: page setup, print area, preview
    // ------------------------------------------------------------------

    private JComponent pageLayoutTab() {
        JPanel p = tabPanel();
        p.add(group("Page Setup",
                button("Page Setup", "pagesetup", "Paper, orientation, margins, header and footer", e -> pageSetupDialog()),
                button("Orientation", "pagesetup", "Switch this sheet between portrait and landscape", e -> toggleOrientation()),
                button("Fit Width", "sheetsize", "Shrink the printout so all columns fit on one page wide", e -> toggleFitWidth())));
        p.add(group("Print Area",
                button("Set Print Area", "printarea", "Print only the selected cells", e -> setPrintArea()),
                button("Clear Print Area", "trash", "Print everything that is used again", e -> clearPrintArea())));
        p.add(group("Sheet Options",
                button("Gridlines", "condfmt", "Print the cell gridlines (on or off)", e -> togglePrintOption(0)),
                button("Headings", "columns", "Print row numbers and column letters (on or off)", e -> togglePrintOption(1)),
                button("Repeat Row 1", "sort", "Repeat the first row at the top of every printed page (on or off)", e -> togglePrintOption(2))));
        p.add(group("Print",
                button("Print Preview", "preview", "See the pages before printing (Print... in the File menu goes straight to the printer)", e -> printPreview()),
                button("Print", "print", "Print this sheet (Ctrl+P)", e -> printSheet()),
                button("Export PDF", "pdf", "Save this sheet as a PDF file", e -> exportPdf())));
        return p;
    }

    private PrintLayout layoutFor(SheetView sv) {
        int idx = tabs.indexOfComponent(sv);
        String sheet = idx >= 0 ? tabs.getTitleAt(idx) : "";
        return new PrintLayout(sv.model, sv.model.getPageSetup(), sheet, currentFile == null ? "Untitled" : currentFile.getName());
    }

    /** The selected cells as {firstRow, firstCol, lastRow, lastCol}, or null if nothing is selected. */
    private int[] selectionRect(SheetView sv) {
        int r1 = sv.table.getSelectionModel().getMinSelectionIndex();
        int r2 = sv.table.getSelectionModel().getMaxSelectionIndex();
        int c1 = sv.table.getColumnModel().getSelectionModel().getMinSelectionIndex();
        int c2 = sv.table.getColumnModel().getSelectionModel().getMaxSelectionIndex();
        if (r1 < 0 || c1 < 0) return null;
        return new int[]{r1, c1, r2, c2};
    }

    /** Changes the page setup of a sheet. Printing settings are saved with the workbook (not in read-only mode). */
    private void updatePageSetup(SheetView sv, PageSetup ps) {
        if (ps.equals(sv.model.getPageSetup())) return;
        sv.model.setPageSetup(ps);
        if (!readOnlyMode) markDirty();
    }

    private boolean pageSetupDialog() {
        SheetView sv = currentSheet();
        if (sv == null) return false;
        PageSetup before = sv.model.getPageSetup();
        PageSetup res = PageSetupDialog.show(this, before, sv.model, selectionRect(sv));
        if (res == null || res.equals(before)) return false;
        updatePageSetup(sv, res);
        status("Page setup updated for this sheet");
        return true;
    }

    private void toggleOrientation() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        PageSetup ps = sv.model.getPageSetup().copy();
        ps.landscape = !ps.landscape;
        updatePageSetup(sv, ps);
        status("Printing in " + (ps.landscape ? "landscape" : "portrait"));
    }

    private void toggleFitWidth() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        PageSetup ps = sv.model.getPageSetup().copy();
        ps.fit = ps.fit == PageSetup.Fit.WIDTH ? PageSetup.Fit.SCALE : PageSetup.Fit.WIDTH;
        updatePageSetup(sv, ps);
        status(ps.fit == PageSetup.Fit.WIDTH ? "All columns will fit on one page wide" : "Printing at " + ps.scale + "%");
    }

    /** 0 = gridlines, 1 = headings, 2 = repeat the first row. */
    private void togglePrintOption(int which) {
        SheetView sv = currentSheet();
        if (sv == null) return;
        PageSetup ps = sv.model.getPageSetup().copy();
        String msg;
        if (which == 0) {
            ps.gridlines = !ps.gridlines;
            msg = ps.gridlines ? "Gridlines will be printed" : "Gridlines will not be printed";
        } else if (which == 1) {
            ps.headings = !ps.headings;
            msg = ps.headings ? "Row and column headings will be printed" : "Headings will not be printed";
        } else {
            ps.repeatRows = ps.repeatRows > 0 ? 0 : 1;
            msg = ps.repeatRows > 0 ? "Row 1 will repeat on every page" : "Row 1 will not repeat";
        }
        updatePageSetup(sv, ps);
        status(msg);
    }

    private void setPrintArea() {
        SheetView sv = currentSheet();
        int[] sel = sv == null ? null : selectionRect(sv);
        if (sel == null) return;
        PageSetup ps = sv.model.getPageSetup().copy();
        ps.setArea(sel[0], sel[1], sel[2], sel[3]);
        updatePageSetup(sv, ps);
        status("Print area set to " + PageSetupDialog.areaText(ps));
    }

    private void clearPrintArea() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        PageSetup ps = sv.model.getPageSetup().copy();
        if (!ps.hasArea()) {
            status("This sheet has no print area");
            return;
        }
        ps.clearArea();
        updatePageSetup(sv, ps);
        status("Print area cleared - everything that is used will print");
    }

    private void printPreview() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        if (sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        PrintPreviewDialog.open(this, new PrintPreviewDialog.Host() {
            @Override
            public PrintLayout layout() {
                return layoutFor(sv);
            }

            @Override
            public boolean pageSetup() {
                return pageSetupDialog();
            }
        });
    }

    /** Goes straight to the printer dialog. */
    private void printSheet() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        if (sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        PrintLayout layout = layoutFor(sv);
        if (layout.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There is nothing to print on this sheet.", "Print", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
        job.setJobName(currentFile == null ? "Spreadsheet Pro" : currentFile.getName());
        job.setPrintable(layout, layout.pageFormat());
        try {
            if (!job.printDialog()) return;
            job.print();
            status("Sent " + layout.pageCount() + " page(s) to the printer");
        } catch (java.awt.print.PrinterException | java.awt.HeadlessException ex) {
            JOptionPane.showMessageDialog(this, "Could not print:\n" + ex.getMessage(), "Print", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    // Read-only mode
    // ------------------------------------------------------------------

    private JComponent reviewTab() {
        JPanel p = tabPanel();
        p.add(group("Changes",
                readOnlyBtn = button("Read-Only", "readonly", "Block every edit in this workbook until you turn it off", e -> setReadOnly(!readOnlyMode))));
        p.add(group("Proofing",
                button("Spelling", "spell", "Check the spelling of the text in this sheet (F7)", e -> spellCheck())));
        p.add(group("Notes",
                button("New Note", "comment", "Add or edit a note on the active cell (hover a red corner to read it)", e -> editNote()),
                button("Delete Note", "trash", "Remove the notes from the selected cells", e -> deleteNotes()),
                button("Next Note", "history", "Jump to the next cell that has a note", e -> nextNote())));
        p.add(group("Versions",
                button("Version History", "history", "Go back to an earlier save of this workbook", e -> versionHistory()),
                button("Save Version", "snapshot", "Keep the workbook as it is now under a name", e -> saveNamedVersion()),
                button("Recover Unsaved", "recover", "Open workbooks that AutoSave kept after a crash", e -> recoverUnsaved())));
        return p;
    }

    private void spellCheck() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        if (!SpellChecker.available()) {
            status("The English dictionary is missing from this build, so spelling can not be checked.");
            return;
        }
        new SpellDialog(this, sv.model, rc -> {
            sv.table.changeSelection(rc[0], rc[1], false, false);
            syncFormulaBar();
        }, this::markDirty).setVisible(true);
    }

    private void setReadOnly(boolean on) {
        readOnlyMode = on;
        if (on) setDrawTool(DrawingLayer.Tool.NONE);
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component c = tabs.getComponentAt(i);
            if (!(c instanceof SheetView)) continue;
            SheetView sv = (SheetView) c;
            if (sv.table.isEditing()) sv.table.getCellEditor().cancelCellEditing();
            sv.model.setReadOnly(on);
            sv.table.repaint();
        }
        if (readOnlyItem != null) readOnlyItem.setSelected(on);
        if (readOnlyBtn != null) {
            readOnlyBtn.setActive(on);
            readOnlyBtn.setText(on ? "Edit Again" : "Read-Only");
        }
        updateTitle();
        refreshTabLocks();
        syncFormulaBar();
        status(on ? "Read-only mode is on - nothing can be changed. Turn it off in Review to edit."
                : "Read-only mode is off - you can edit again");
    }

    // ------------------------------------------------------------------
    // Version history and recovery
    // ------------------------------------------------------------------

    /** Keeps a copy of a just-saved workbook in its version history (never fails the save). */
    private void recordVersion(File saved, String label) {
        try {
            versions.add(VersionStore.keyFor(saved), saved, label);
        } catch (IOException | RuntimeException ignored) {
            // history is a convenience: a full disk must not turn a good save into an error
        }
    }

    /** Stores the workbook as it is right now (even unsaved changes) in the history of the open file. */
    private boolean snapshotNow(String label) {
        if (currentFile == null) return false;
        SheetView sv = currentSheet();
        if (sv != null && sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        File tmp = null;
        try {
            tmp = File.createTempFile("spx-version", ".spx");
            WorkbookIO.save(tmp, collectSheets(), tabs.getSelectedIndex(), workbookPassword);
            versions.add(VersionStore.keyFor(currentFile), tmp, label);
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not keep a version:\n" + ex.getMessage(), "Version History",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            if (tmp != null) //noinspection ResultOfMethodCallIgnored
                tmp.delete();
        }
    }

    private void versionHistory() {
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this, "Save the workbook first.\nVersion history starts with its first save.",
                    "Version History", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new VersionHistoryDialog(this, currentFile.getName(), versions, VersionStore.keyFor(currentFile),
                new VersionHistoryDialog.Host() {
                    @Override
                    public boolean saveVersion(String label) {
                        return snapshotNow(label);
                    }

                    @Override
                    public boolean restore(VersionStore.Version v) {
                        return restoreVersion(v);
                    }
                }).setVisible(true);
    }

    private void saveNamedVersion() {
        if (currentFile == null) {
            JOptionPane.showMessageDialog(this, "Save the workbook first.\nVersion history starts with its first save.",
                    "Save Version", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Name this version (for example \"Before the price change\"):",
                "Save Version", JOptionPane.QUESTION_MESSAGE);
        if (name == null) return;
        if (name.trim().isEmpty()) name = "Saved version";
        if (snapshotNow(name)) status("Kept version \"" + name.trim() + "\"");
    }

    /** Reads a workbook file, asking for its password if it has one. @return null if cancelled or unreadable */
    private WorkbookIO.WorkbookData loadWithPassword(File f, String title, String prompt, char[][] passwordOut) {
        char[] pw = null;
        while (true) {
            try {
                WorkbookIO.WorkbookData data = WorkbookIO.load(f, pw);
                passwordOut[0] = pw;
                return data;
            } catch (WorkbookIO.PasswordRequiredException | WorkbookIO.BadPasswordException ex) {
                boolean wrong = ex instanceof WorkbookIO.BadPasswordException;
                pw = askPassword(title, wrong ? "Incorrect password. Try again:" : prompt);
                if (pw == null) return null;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not read that version:\n" + ex.getMessage(), title,
                        JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }
    }

    private boolean restoreVersion(VersionStore.Version v) {
        if (readOnlyMode) {
            JOptionPane.showMessageDialog(this, "This workbook is read-only. Turn off Review > Read-Only Mode first.",
                    "Restore Version", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        char[][] pw = new char[1][];
        WorkbookIO.WorkbookData data = loadWithPassword(v.file, "Restore Version", "This version needs its password:", pw);
        if (data == null) return false;
        if (!snapshotNow("Before restoring " + v.when())) return false; // keep what is open now
        loading = true;
        try {
            tabs.removeAll();
            for (WorkbookIO.SheetData sd : data.sheets) addSheet(sd.name, sd.model);
            tabs.setSelectedIndex(data.activeIndex);
        } finally {
            loading = false;
            resetAllUndo();
        }
        sheetCounter = tabs.getTabCount();
        if (workbookPassword != null) Arrays.fill(workbookPassword, '\0');
        workbookPassword = pw[0];
        dirty = true; // the file on disk still holds the newer content until you save
        updateTitle();
        refreshTabLocks();
        syncFormulaBar();
        status("Restored the version from " + v.when() + " - save to make it the current file");
        return true;
    }

    private void recoverUnsaved() {
        while (true) {
            List<File> files = autoSave.findRecoveryFiles();
            if (files.isEmpty()) {
                JOptionPane.showMessageDialog(this, "There are no unsaved workbooks to recover.\n"
                        + "AutoSave keeps a copy of workbooks you have not saved yet.", "Recover Unsaved Workbooks",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            DefaultListModel<File> lm = new DefaultListModel<>();
            for (File f : files) lm.addElement(f);
            JList<File> list = new JList<>(lm);
            list.setSelectedIndex(0);
            list.setVisibleRowCount(Math.min(8, files.size()));
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                    File f = (File) value;
                    String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(f.lastModified()));
                    return super.getListCellRendererComponent(l, AutoSaveManager.originalName(f) + "   -   " + when
                            + "   (" + Math.max(1, f.length() / 1024) + " KB)", index, sel, focus);
                }
            });
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.add(new JLabel("Copies that AutoSave kept (newest first):"), BorderLayout.NORTH);
            panel.add(new JScrollPane(list), BorderLayout.CENTER);
            int answer = JOptionPane.showOptionDialog(this, panel, "Recover Unsaved Workbooks", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, new Object[]{"Recover", "Delete", "Close"}, "Recover");
            File chosen = list.getSelectedValue();
            if (answer == 0 && chosen != null) {
                if (recoverFile(chosen)) {
                    AutoSaveManager.discard(chosen);
                    return;
                }
            } else if (answer == 1 && chosen != null) {
                AutoSaveManager.discard(chosen);
            } else {
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Command palette (Ctrl+Shift+P)
    // ------------------------------------------------------------------

    private void openCommandPalette() {
        CommandPalette.open(this, collectCommands());
    }

    /** Every enabled menu item and ribbon button, plus "go to sheet" commands. */
    private List<CommandPalette.Command> collectCommands() {
        List<CommandPalette.Command> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        JMenuBar bar = getJMenuBar();
        if (bar != null) {
            for (int i = 0; i < bar.getMenuCount(); i++) {
                JMenu menu = bar.getMenu(i);
                if (menu != null) collectMenu(menu, menu.getText(), out, seen);
            }
        }
        for (int i = 0; i < RIBBON_TAB_NAMES.length && i < ribbonBody.getComponentCount(); i++) {
            collectButtons(ribbonBody.getComponent(i), RIBBON_TAB_NAMES[i], out, seen);
        }
        for (int i = 0; i < tabs.getTabCount(); i++) {
            int index = i;
            String title = tabs.getTitleAt(i);
            out.add(new CommandPalette.Command("Go to sheet: " + title, "Sheets", "Switch to this sheet", "",
                    () -> tabs.setSelectedIndex(index)));
        }
        return out;
    }

    private void collectMenu(JMenu menu, String category, List<CommandPalette.Command> out, java.util.Set<String> seen) {
        for (Component c : menu.getMenuComponents()) {
            if (c instanceof JMenu) {
                collectMenu((JMenu) c, category, out, seen);
            } else if (c instanceof JMenuItem) {
                JMenuItem item = (JMenuItem) c;
                String text = item.getText();
                if (text == null || !item.isEnabled()) continue;
                String name = text.endsWith("...") ? text.substring(0, text.length() - 3) : text;
                if (name.isEmpty() || name.matches("[0-9%]+") || !seen.add(name.toLowerCase())) continue;
                KeyStroke ks = item.getAccelerator();
                String keys = "";
                if (ks != null) {
                    String mods = InputEvent.getModifiersExText(ks.getModifiers());
                    String key = KeyEvent.getKeyText(ks.getKeyCode());
                    if ("Slash".equals(key)) key = "/";
                    else if ("Equals".equals(key)) key = "+";
                    else if ("Minus".equals(key)) key = "-";
                    keys = (mods.isEmpty() ? "" : mods + "+") + key;
                }
                out.add(new CommandPalette.Command(name, category, "", keys, item::doClick));
            }
        }
    }

    private void collectButtons(Component c, String category, List<CommandPalette.Command> out, java.util.Set<String> seen) {
        if (c instanceof RibbonButton) {
            RibbonButton b = (RibbonButton) c;
            String tip = b.getToolTipText() == null ? "" : b.getToolTipText();
            String name = b.getText() == null ? "" : b.getText().trim();
            name = name.replaceAll("[\\u25BC\\u25BE\\u25BF\\u25B8\\u25B6]", "").trim(); // drop the drop-down arrow
            if (name.isEmpty()) {
                int paren = tip.indexOf(" (");
                name = (paren > 0 ? tip.substring(0, paren) : tip).trim();
            }
            if (!name.isEmpty() && b.isEnabled() && seen.add(name.toLowerCase())) {
                out.add(new CommandPalette.Command(name, category, tip, "", b::doClick));
            }
        }
        if (c instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) c).getComponents()) collectButtons(child, category, out, seen);
        }
    }


    // ------------------------------------------------------------------
    // AutoSave
    // ------------------------------------------------------------------

    private void setAutoSave(boolean on) {
        autoSave.setEnabled(on);
        if (autoSaveItem != null) autoSaveItem.setSelected(on);
        if (autoSwitch != null) autoSwitch.repaint();
        status(on ? "AutoSave is on" : "AutoSave is off");
    }

    private void showAutoSaveSettings() {
        JCheckBox enabled = new JCheckBox("Turn on AutoSave", autoSave.isEnabled());
        JSpinner seconds = new JSpinner(new SpinnerNumberModel(autoSave.getIntervalSeconds(), 5, 3600, 5));
        JRadioButton recovery = new JRadioButton("Save a separate recovery copy (your file is never touched)",
                autoSave.getMode() == AutoSaveManager.Mode.RECOVERY);
        JRadioButton file = new JRadioButton("Save into the open file itself (needs the workbook to be saved once)",
                autoSave.getMode() == AutoSaveManager.Mode.FILE);
        ButtonGroup group = new ButtonGroup();
        group.add(recovery);
        group.add(file);
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 4, 6));
        panel.add(enabled);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(new JLabel("Save every"));
        row.add(seconds);
        row.add(new JLabel("seconds while there are unsaved changes"));
        panel.add(row);
        panel.add(recovery);
        panel.add(file);
        panel.add(new JLabel("Recovery copies live in: " + AutoSaveManager.folder()));
        if (JOptionPane.showConfirmDialog(this, panel, "AutoSave Settings", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        autoSave.configure(enabled.isSelected(), (Integer) seconds.getValue(),
                file.isSelected() ? AutoSaveManager.Mode.FILE : AutoSaveManager.Mode.RECOVERY);
        setAutoSave(autoSave.isEnabled());
        status("AutoSave settings saved");
    }

    /** Called once at start-up: offers workbooks that were AutoSaved but never saved (e.g. after a crash). */
    void offerRecovery() {
        for (File f : autoSave.findRecoveryFiles()) {
            String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(f.lastModified()));
            int answer = JOptionPane.showOptionDialog(this,
                    "Spreadsheet Pro found an AutoSaved copy of \"" + AutoSaveManager.originalName(f) + "\" from " + when
                            + ".\nIt was never saved. Recover it?",
                    "Recover Workbook", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new Object[]{"Recover", "Discard", "Later"}, "Recover");
            if (answer == 0) {
                if (recoverFile(f)) {
                    AutoSaveManager.discard(f);
                    return;
                }
            } else if (answer == 1) {
                AutoSaveManager.discard(f);
            } else {
                return;
            }
        }
    }

    private boolean recoverFile(File f) {
        if (!confirmDiscard()) return false;
        char[] pw = null;
        WorkbookIO.WorkbookData data;
        while (true) {
            try {
                data = WorkbookIO.load(f, pw);
                break;
            } catch (WorkbookIO.PasswordRequiredException | WorkbookIO.BadPasswordException ex) {
                boolean wrong = ex instanceof WorkbookIO.BadPasswordException;
                pw = askPassword("Recover Workbook",
                        wrong ? "Incorrect password. Try again:" : "This recovered workbook needs its password:");
                if (pw == null) return false;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not recover the workbook:\n" + ex.getMessage(),
                        "Recover", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        readOnlyMode = false;
        if (readOnlyItem != null) readOnlyItem.setSelected(false);
        if (readOnlyBtn != null) readOnlyBtn.setActive(false);
        loading = true;
        try {
            tabs.removeAll();
            for (WorkbookIO.SheetData sd : data.sheets) addSheet(sd.name, sd.model);
            tabs.setSelectedIndex(data.activeIndex);
        } finally {
            loading = false;
            resetAllUndo();
        }
        sheetCounter = tabs.getTabCount();
        currentFile = null;
        if (workbookPassword != null) Arrays.fill(workbookPassword, '\0');
        workbookPassword = pw;
        dirty = true;
        updateTitle();
        refreshTabLocks();
        syncFormulaBar();
        status("Recovered \"" + AutoSaveManager.originalName(f) + "\" - use Save As to keep it");
        return true;
    }

    /** "AutoSave [on/off]" switch in the ribbon's title strip. */
    private final class AutoSaveSwitch extends JComponent {
        AutoSaveSwitch() {
            setPreferredSize(new Dimension(112, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setAutoSave(!autoSave.isEnabled());
                }
            });
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            return autoSave.isEnabled()
                    ? "AutoSave is on: unsaved work is saved every " + autoSave.getIntervalSeconds() + " seconds. Click to turn off."
                    : "AutoSave is off. Click to turn it on.";
        }

        private int labelWidth() {
            return getFontMetrics(Theme.ui(Font.BOLD, -1)).stringWidth(I18n.t("AutoSave"));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(labelWidth() + 56, 26);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = autoSave.isEnabled();
            Font f = Theme.ui(Font.BOLD, -1);
            g2.setFont(f);
            int base = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
            g2.setColor(Theme.tabFg);
            g2.drawString(I18n.t("AutoSave"), 2, base);
            int w = 38;
            int h = 18;
            int x = labelWidth() + 10;
            int y = (getHeight() - h) / 2;
            Color track = Theme.accent;
            int dr = track.getRed() - Theme.stripBg.getRed(), dg = track.getGreen() - Theme.stripBg.getGreen(),
                    db = track.getBlue() - Theme.stripBg.getBlue();
            if (dr * dr + dg * dg + db * db < 6000) track = Theme.tabFg; // strip is accent-coloured: keep the switch visible
            g2.setColor(on ? track : Theme.alpha(Theme.tabFg, 70));
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(on ? Theme.stripBg : Theme.tabFg);
            g2.fillOval(on ? x + w - h + 2 : x + 2, y + 2, h - 4, h - 4);
            g2.setColor(on ? Theme.stripBg : Theme.tabFg);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
            g2.drawString(on ? "ON" : "OFF", on ? x + 5 : x + 17, y + 12);
            g2.dispose();
        }
    }

    private void clearSelection() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        sv.model.beginBatch();
        try {
            for (int r : rows) for (int c : cols) sv.model.clearContents(r, c);
        } finally {
            sv.model.endBatch();
        }
    }

    /**
     * AutoSum. Single cell: sums the contiguous numbers above (or, failing that, to the left).
     * Multi-cell selection: puts a SUM for each selected column in the row below the selection.
     */
    private void autoSum() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        SpreadsheetModel m = sv.model;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        if (rows.length == 0 || cols.length == 0) {
            status("Select a cell first");
            return;
        }
        int r1 = rows[0], r2 = rows[rows.length - 1], c1 = cols[0], c2 = cols[cols.length - 1];

        if (r1 == r2 && c1 == c2) {
            int top = r1;
            while (top - 1 >= 0 && m.isNumeric(top - 1, c1)) top--;
            if (top < r1) {
                m.setInput(r1, c1, "=SUM(" + FormulaEngine.cellRef(top, c1) + ":" + FormulaEngine.cellRef(r1 - 1, c1) + ")");
                return;
            }
            int left = c1;
            while (left - 1 >= 0 && m.isNumeric(r1, left - 1)) left--;
            if (left < c1) {
                m.setInput(r1, c1, "=SUM(" + FormulaEngine.cellRef(r1, left) + ":" + FormulaEngine.cellRef(r1, c1 - 1) + ")");
                return;
            }
            m.setInput(r1, c1, "=SUM()");
            status("No adjacent numbers found - type the range inside SUM( )");
            formulaField.requestFocusInWindow();
            formulaField.setCaretPosition(formulaField.getText().length() - 1);
        } else {
            int target = r2 + 1;
            if (target >= m.getRowCount()) {
                status("No room below the selection for the totals");
                return;
            }
            m.beginBatch();
            try {
                for (int c : cols) {
                    m.setInput(target, c, "=SUM(" + FormulaEngine.cellRef(r1, c) + ":" + FormulaEngine.cellRef(r2, c) + ")");
                }
            } finally {
                m.endBatch();
            }
        }
    }

    // ==================================================================
    // Dialogs
    // ==================================================================

    private void openInsertDialog() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        int[] rows = sv.table.getSelectedRows();
        int[] cols = sv.table.getSelectedColumns();
        int r = rows.length > 0 ? rows[0] : 0;
        int c = cols.length > 0 ? cols[0] : 0;
        new InsertDialog(this, sv.model, r, c, () -> {
            syncFormulaBar();
            sv.table.requestFocusInWindow();
            status("Inserted at " + FormulaEngine.cellRef(r, c));
        }).setVisible(true);
    }

    private void openMacroIde() {
        if (macroDialog == null) {
            macroDialog = new MacroIDEDialog(this, () -> {
                SheetView sv = currentSheet();
                return sv == null ? null : sv.model;
            });
        }
        macroDialog.setVisible(true);
        macroDialog.toFront();
    }

    // ==================================================================
    // CSV import / export
    // ==================================================================

    // ==================================================================
    // Sheet protection (Review menu)
    // ==================================================================

    /** @return false (with a hint in the status bar) when the sheet is protected. */
    private boolean guardEditable(SheetView sv) {
        if (sv != null && sv.model.isProtected()) {
            status("This sheet is protected. Use Review > Unprotect Sheet to make changes.");
            Toolkit.getDefaultToolkit().beep();
            return false;
        }
        return true;
    }

    private void updateProtectUi(SheetView sv) {
        boolean prot = sv != null && sv.model.isSheetProtected();
        boolean blocked = sv != null && sv.model.isProtected();
        if (protectButton != null) {
            protectButton.setText(prot ? "Unprotect" : "Protect Sheet");
            protectButton.setIconName(prot ? "unlock" : "lock");
        }
        protLabel.setText(prot ? "SHEET PROTECTED" : (blocked ? "READ-ONLY" : ""));
        formulaField.setEditable(!blocked);
    }

    private void refreshTabLocks() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component comp = tabs.getComponentAt(i);
            Component header = tabs.getTabComponentAt(i);
            if (comp instanceof SheetView && header instanceof TabHeader) {
                ((TabHeader) header).setLocked(((SheetView) comp).model.isSheetProtected());
            }
        }
    }

    private void toggleProtection() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        if (sv.model.isSheetProtected()) unprotectSheetDialog();
        else protectSheetDialog();
    }

    private void protectSheetDialog() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        if (sv.model.isSheetProtected()) {
            JOptionPane.showMessageDialog(this, "This sheet is already protected.", "Protect Sheet",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        char[] pw = askNewPassword("Protect Sheet",
                "Password (optional - leave blank to protect without a password):",
                "Protected sheets cannot be edited, formatted, cleared, changed by macros, or have pictures moved.");
        if (pw == null) return;
        if (sv.table.isEditing()) sv.table.getCellEditor().cancelCellEditing();
        sv.model.getProtection().protect(pw);
        Arrays.fill(pw, '\0');
        afterProtectionChange(sv, "Sheet protected");
    }

    private void unprotectSheetDialog() {
        SheetView sv = currentSheet();
        if (sv == null || !sv.model.isSheetProtected()) return;
        char[] pw = null;
        if (sv.model.getProtection().hasPassword()) {
            pw = askPassword("Unprotect Sheet", "Password for \"" + tabs.getTitleAt(tabs.getSelectedIndex()) + "\":");
            if (pw == null) return;
        }
        boolean ok = sv.model.getProtection().unprotect(pw);
        if (pw != null) Arrays.fill(pw, '\0');
        if (!ok) {
            JOptionPane.showMessageDialog(this, "Incorrect password.", "Unprotect Sheet", JOptionPane.ERROR_MESSAGE);
            return;
        }
        afterProtectionChange(sv, "Sheet unprotected");
    }

    private void afterProtectionChange(SheetView sv, String message) {
        refreshTabLocks();
        markDirty();
        sv.table.repaint();
        syncFormulaBar();
        status(message);
        sv.table.requestFocusInWindow();
    }

    // ---- password prompts ----

    /** Asks for a password once. @return the characters, or null if cancelled. */
    private char[] askPassword(String title, String message) {
        JPasswordField field = new JPasswordField(18);
        focusOnShow(field);
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel(message));
        panel.add(field);
        int answer = JOptionPane.showConfirmDialog(this, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return answer == JOptionPane.OK_OPTION ? field.getPassword() : null;
    }

    /**
     * Asks for a new password with confirmation.
     * @return the characters (possibly empty), or null if cancelled or the two entries differ
     */
    private char[] askNewPassword(String title, String message, String note) {
        JPasswordField first = new JPasswordField(18);
        JPasswordField second = new JPasswordField(18);
        focusOnShow(first);
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel(message));
        panel.add(first);
        panel.add(new JLabel("Confirm password:"));
        panel.add(second);
        panel.add(new JLabel("<html><small>" + note + "</small></html>"));
        int answer = JOptionPane.showConfirmDialog(this, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return null;
        char[] a = first.getPassword();
        char[] b = second.getPassword();
        boolean same = Arrays.equals(a, b);
        Arrays.fill(b, '\0');
        if (!same) {
            Arrays.fill(a, '\0');
            JOptionPane.showMessageDialog(this, "The passwords do not match.", title, JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return a;
    }

    /** Puts the keyboard cursor in the field when its dialog appears. */
    private static void focusOnShow(JComponent field) {
        field.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                SwingUtilities.invokeLater(field::requestFocusInWindow);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
    }

    /** Small padlock drawn on the tab of a protected sheet. */
    private static final class LockIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(new Color(0x8A6D00));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawArc(2, 0, 7, 8, 0, 180);
            g2.drawLine(2, 4, 2, 6);
            g2.drawLine(9, 4, 9, 6);
            g2.setColor(new Color(0xD9A900));
            g2.fillRoundRect(0, 6, 11, 7, 3, 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 11;
        }

        @Override
        public int getIconHeight() {
            return 13;
        }
    }

    // ==================================================================
    // Pictures
    // ==================================================================

    private void insertPictureFromFile() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Pictures (png, jpg, gif, bmp)",
                "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage img = ImageIO.read(fc.getSelectedFile());
            if (img == null) {
                JOptionPane.showMessageDialog(this, "That file is not a supported picture (use PNG, JPG, GIF or BMP).",
                        "Insert Picture", JOptionPane.ERROR_MESSAGE);
                return;
            }
            placePicture(sv, img);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not read the picture:\n" + ex.getMessage(),
                    "Insert Picture", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void insertPictureFromClipboard() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!cb.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                JOptionPane.showMessageDialog(this, "There is no picture on the clipboard.",
                        "Insert Picture", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            java.awt.Image im = (java.awt.Image) cb.getData(DataFlavor.imageFlavor);
            javax.swing.ImageIcon icon = new javax.swing.ImageIcon(im); // makes sure it is fully loaded
            int w = icon.getIconWidth();
            int h = icon.getIconHeight();
            if (w <= 0 || h <= 0) throw new IOException("The clipboard picture is empty");
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.drawImage(im, 0, 0, null);
            g.dispose();
            placePicture(sv, bi);
        } catch (java.awt.datatransfer.UnsupportedFlavorException | IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not paste the picture:\n" + ex.getMessage(),
                    "Insert Picture", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Puts the picture at the active cell and selects it so it can be moved or resized. */
    private void placePicture(SheetView sv, BufferedImage img) throws IOException {
        int r = Math.max(0, sv.table.getSelectionModel().getLeadSelectionIndex());
        int c = Math.max(0, sv.table.getColumnModel().getSelectionModel().getLeadSelectionIndex());
        Rectangle cell = sv.table.getCellRect(r, c, true);
        double zoom = sv.model.getZoomPercent() / 100.0; // pictures are stored in 100%-zoom units
        SheetImage si = SheetImage.create(img, (int) Math.round(cell.x / zoom), (int) Math.round(cell.y / zoom));
        sv.model.addImage(si);
        sv.layer.select(si);
        markDirty();
        sv.table.requestFocusInWindow();
        status("Picture inserted at " + FormulaEngine.cellRef(r, c)
                + " - drag to move, drag the corner square to resize, Delete to remove");
    }

    private void deleteSelectedPicture() {
        SheetView sv = currentSheet();
        if (sv == null || !guardEditable(sv)) return;
        if (sv.layer.deleteSelected()) status("Picture deleted");
        else status("Click a picture to select it first");
    }

    // ==================================================================
    // Workbook files (.spx), open password, unsaved-changes tracking
    // ==================================================================

    private void markDirty() {
        SheetView cur = currentSheet();
        if (cur != null && cur.undo != null && !loading) cur.undo.schedule();
        if (loading || dirty) return;
        dirty = true;
        updateTitle();
    }

    private void updateTitle() {
        String name = currentFile == null ? "Untitled" : currentFile.getName();
        if (currentFile != null && !readOnlyMode) RecentFiles.add(currentFile);
        setTitle("Spreadsheet Pro - " + name + (dirty ? " *" : "") + (readOnlyMode ? "  [Read-Only]" : ""));
    }

    /** @return true if it is fine to replace the current workbook (saved, discarded or unchanged). */
    private boolean confirmDiscard() {
        if (!dirty) return true;
        String name = currentFile == null ? "Untitled" : currentFile.getName();
        int answer = JOptionPane.showConfirmDialog(this, "Save changes to " + name + "?", "Unsaved Changes",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) return saveWorkbook();
        return answer == JOptionPane.NO_OPTION;
    }

    private void exitApp() {
        if (!confirmDiscard()) return;
        autoSave.stop();
        autoSave.clearOwn(); // clean exit: no recovery copy needed
        dispose();
        System.exit(0);
    }

    private void newWorkbook() {
        if (!confirmDiscard()) return;
        readOnlyMode = false;
        if (readOnlyItem != null) readOnlyItem.setSelected(false);
        if (readOnlyBtn != null) readOnlyBtn.setActive(false);
        loading = true;
        try {
            tabs.removeAll();
            sheetCounter = 0;
            addSheet(nextSheetName());
        } finally {
            loading = false;
            resetAllUndo();
        }
        currentFile = null;
        if (workbookPassword != null) Arrays.fill(workbookPassword, '\0');
        workbookPassword = null;
        dirty = false;
        updateTitle();
        refreshTabLocks();
        syncFormulaBar();
        status("New workbook");
    }

    private JFileChooser workbookChooser() {
        JFileChooser fc = new JFileChooser(currentFile != null ? currentFile.getParentFile() : null);
        fc.setFileFilter(new FileNameExtensionFilter("Spreadsheet Pro workbook (*.spx)", WorkbookIO.EXTENSION));
        return fc;
    }

    private List<WorkbookIO.SheetData> collectSheets() {
        List<WorkbookIO.SheetData> list = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component comp = tabs.getComponentAt(i);
            if (comp instanceof SheetView) {
                list.add(new WorkbookIO.SheetData(tabs.getTitleAt(i), ((SheetView) comp).model));
            }
        }
        return list;
    }

    /** Ctrl+S. @return true if the workbook was saved. */
    private boolean saveWorkbook() {
        if (readOnlyMode) {
            int a = JOptionPane.showConfirmDialog(this, "This workbook is open read-only, so it cannot be saved over the original.\n"
                    + "Save a copy with a new name instead?", "Read-Only", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            return a == JOptionPane.OK_OPTION && saveWorkbookAs();
        }
        if (currentFile == null) return saveWorkbookAs();
        return writeWorkbook(currentFile);
    }

    private boolean saveWorkbookAs() {
        JFileChooser fc = workbookChooser();
        fc.setSelectedFile(new File(currentFile != null ? currentFile.getName() : "Workbook.spx"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith("." + WorkbookIO.EXTENSION)) {
            f = new File(f.getParentFile(), f.getName() + "." + WorkbookIO.EXTENSION);
        }
        if (f.exists() && JOptionPane.showConfirmDialog(this, "\"" + f.getName() + "\" already exists. Replace it?",
                "Save As", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return false;
        }
        if (!writeWorkbook(f)) return false;
        currentFile = f;
        updateTitle();
        return true;
    }

    private boolean writeWorkbook(File f) {
        SheetView sv = currentSheet();
        if (sv != null && sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        try {
            WorkbookIO.save(f, collectSheets(), tabs.getSelectedIndex(), workbookPassword);
            dirty = false;
            updateTitle();
            status("Saved " + f.getName() + (workbookPassword != null ? " (encrypted)" : ""));
            recordVersion(f, "");
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the workbook:\n" + ex.getMessage(),
                    "Save", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void openWorkbook() {
        if (!confirmDiscard()) return;
        JFileChooser fc = workbookChooser();
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Excel workbook (*.xlsx)", XlsxIO.EXTENSION));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Workbooks (*.spx, *.xlsx)", WorkbookIO.EXTENSION, XlsxIO.EXTENSION));
        fc.setFileFilter(fc.getChoosableFileFilters()[fc.getChoosableFileFilters().length - 1]);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        openWorkbookFile(fc.getSelectedFile());
    }

    /** Opens a .spx or .xlsx file (the caller has already dealt with unsaved changes). */
    void openWorkbookFile(File f) {
        if (f.getName().toLowerCase().endsWith("." + XlsxIO.EXTENSION)) {
            openXlsx(f);
            return;
        }
        char[] pw = null;
        WorkbookIO.WorkbookData data;
        while (true) {
            try {
                data = WorkbookIO.load(f, pw);
                break;
            } catch (WorkbookIO.PasswordRequiredException | WorkbookIO.BadPasswordException ex) {
                boolean wrong = ex instanceof WorkbookIO.BadPasswordException;
                pw = askPassword("Open Workbook",
                        wrong ? "Incorrect password. Try again:" : "\"" + f.getName() + "\" needs a password to open:");
                if (pw == null) return;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not open the workbook:\n" + ex.getMessage(),
                        "Open", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        readOnlyMode = false;
        if (readOnlyItem != null) readOnlyItem.setSelected(false);
        if (readOnlyBtn != null) readOnlyBtn.setActive(false);
        loading = true;
        try {
            tabs.removeAll();
            for (WorkbookIO.SheetData sd : data.sheets) addSheet(sd.name, sd.model);
            tabs.setSelectedIndex(data.activeIndex);
        } finally {
            loading = false;
            resetAllUndo();
        }
        sheetCounter = tabs.getTabCount();
        currentFile = f;
        if (workbookPassword != null) Arrays.fill(workbookPassword, '\0');
        workbookPassword = pw;
        dirty = false;
        updateTitle();
        refreshTabLocks();
        syncFormulaBar();
        status("Opened " + f.getName());
        if (!f.canWrite()) {
            setReadOnly(true);
            status("Opened " + f.getName() + " read-only (the file is write-protected)");
        }
    }

    /** Opens an Excel file as a new workbook. Save As keeps it as .spx; Export to Excel writes .xlsx again. */
    private void openXlsx(File f) {
        XlsxIO.Result res;
        try {
            res = XlsxIO.read(f);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not open the Excel file:\n" + ex.getMessage(),
                    "Open Excel", JOptionPane.ERROR_MESSAGE);
            return;
        }
        readOnlyMode = false;
        if (readOnlyItem != null) readOnlyItem.setSelected(false);
        if (readOnlyBtn != null) readOnlyBtn.setActive(false);
        loading = true;
        try {
            tabs.removeAll();
            for (WorkbookIO.SheetData sd : res.sheets) addSheet(sd.name, sd.model);
            tabs.setSelectedIndex(res.activeIndex);
        } finally {
            loading = false;
            resetAllUndo();
        }
        sheetCounter = tabs.getTabCount();
        currentFile = null;
        if (workbookPassword != null) Arrays.fill(workbookPassword, '\0');
        workbookPassword = null;
        dirty = false;
        updateTitle();
        setTitle("Spreadsheet Pro - " + f.getName() + " (imported)");
        refreshTabLocks();
        syncFormulaBar();
        status("Opened " + f.getName() + " - use Export to Excel to save it as .xlsx again");
        if (!res.notes.isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n\n", res.notes), "Opened with changes",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openExcelDialog() {
        if (!confirmDiscard()) return;
        JFileChooser fc = new JFileChooser(currentFile != null ? currentFile.getParentFile() : null);
        fc.setFileFilter(new FileNameExtensionFilter("Excel workbook (*.xlsx)", XlsxIO.EXTENSION));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        openXlsx(fc.getSelectedFile());
    }

    private void exportExcel() {
        SheetView sv = currentSheet();
        if (sv != null && sv.table.isEditing()) sv.table.getCellEditor().stopCellEditing();
        JFileChooser fc = new JFileChooser(currentFile != null ? currentFile.getParentFile() : null);
        fc.setFileFilter(new FileNameExtensionFilter("Excel workbook (*.xlsx)", XlsxIO.EXTENSION));
        String base = currentFile != null ? currentFile.getName().replaceFirst("\\.[^.]*$", "") : "Workbook";
        fc.setSelectedFile(new File(base + "." + XlsxIO.EXTENSION));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith("." + XlsxIO.EXTENSION)) {
            f = new File(f.getParentFile(), f.getName() + "." + XlsxIO.EXTENSION);
        }
        if (f.exists() && JOptionPane.showConfirmDialog(this, "\"" + f.getName() + "\" already exists. Replace it?",
                "Export to Excel", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        List<WorkbookIO.SheetData> sheets = collectSheets();
        try {
            XlsxIO.write(f, sheets, tabs.getSelectedIndex());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not write the Excel file:\n" + ex.getMessage(),
                    "Export to Excel", JOptionPane.ERROR_MESSAGE);
            return;
        }
        status("Exported " + f.getName());
        List<String> lost = XlsxIO.unsupportedFeatures(sheets);
        if (!lost.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Saved " + f.getName() + ".\nNot included in the Excel file: "
                    + String.join(", ", lost) + ".\nSave as .spx to keep them.", "Export to Excel", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void setOpenPassword() {
        char[] pw = askNewPassword("Set Open Password",
                "Password needed to open this workbook (leave blank to remove it):",
                "The workbook is encrypted when you save it. If you forget the password the file cannot be recovered.");
        if (pw == null) return;
        if (workbookPassword != null) Arrays.fill(workbookPassword, '\0');
        workbookPassword = pw.length == 0 ? null : pw;
        markDirty();
        status(pw.length == 0 ? "Open password removed - save to apply"
                : "Open password set - save the workbook to encrypt it");
    }

    private void exportCsv() {
        SheetView sv = currentSheet();
        if (sv == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(tabs.getTitleAt(tabs.getSelectedIndex()) + ".csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        try {
            Files.write(file.toPath(), toCsv(sv.model).getBytes(StandardCharsets.UTF_8));
            status("Exported " + file.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not write file:\n" + ex.getMessage(),
                    "Export CSV", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importCsv() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String name = file.getName().replaceFirst("\\.[^.]*$", "");
            List<List<String>> rows = parseCsv(content);
            int widest = 0;
            for (List<String> row : rows) widest = Math.max(widest, row.size());
            SheetView sv = addSheet(name.isEmpty() ? nextSheetName() : name,
                    new SpreadsheetModel(Math.min(SpreadsheetModel.MAX_ROWS, Math.max(SpreadsheetModel.DEFAULT_ROWS, rows.size() + 20)),
                            Math.min(702, Math.max(SpreadsheetModel.DEFAULT_COLS, widest + 2))));
            sv.model.beginBatch();
            try {
                for (int r = 0; r < rows.size(); r++) {
                    List<String> row = rows.get(r);
                    for (int c = 0; c < row.size(); c++) sv.model.setInput(r, c, row.get(c));
                }
            } finally {
                sv.model.endBatch();
            }
            status("Imported " + file.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not read file:\n" + ex.getMessage(),
                    "Import CSV", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String toCsv(SpreadsheetModel m) {
        StringBuilder sb = new StringBuilder();
        int rows = m.getUsedRowCount();
        int cols = m.getUsedColumnCount();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c > 0) sb.append(',');
                String v = m.getInput(r, c);
                if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                    v = "\"" + v.replace("\"", "\"\"") + "\"";
                }
                sb.append(v);
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    static List<List<String>> parseCsv(String s) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
