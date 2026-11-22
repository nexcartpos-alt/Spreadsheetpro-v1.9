# Spreadsheet Pro (Java Swing)

Excel-style spreadsheet in pure Java (JDK 8+, no external libraries).

## Download and run
Get the latest files from the **Releases** page of this repository.

| You are on | Download | How to run |
|---|---|---|
| Windows | `SpreadsheetPro-Windows.zip` | unzip, open the `Spreadsheet Pro` folder, double-click `Spreadsheet Pro.exe` |
| macOS | `SpreadsheetPro-macOS.zip` | unzip, open `Spreadsheet Pro.app` (first time: right-click > Open) |
| Linux | `SpreadsheetPro-Linux.tar.gz` | extract, run `bin/Spreadsheet\ Pro` |
| Any (Java 11+ installed) | `SpreadsheetPro.jar` | double-click it, or `java -jar SpreadsheetPro.jar` |

The Windows, macOS and Linux downloads already contain Java, so nothing else has to be installed.
They are not code-signed, so Windows SmartScreen or macOS Gatekeeper may warn you the first time.

**For maintainers:** publishing a GitHub Release runs `.github/workflows/release.yml`, which builds all four files and attaches them to the release. `make-jar.sh` / `make-jar.bat` build the jar by hand.

## Compile and run

Linux / macOS:
    chmod +x build.sh && ./build.sh        # compiles and starts the app

Windows:
    build.bat

Manual:
    mkdir out
    javac -encoding UTF-8 -d out *.java
    java -cp out SpreadsheetApp

## Architecture

| File                        | Layer                                                       |
|-----------------------------|-------------------------------------------------------------|
| SpreadsheetApp.java         | UI: ribbon, formula bar, tabs, status bar, CSV import/export |
| SpreadsheetModel.java       | Data model (AbstractTableModel): raw / formula / style maps  |
| FormulaEngine.java          | Expression-tree formula parser, 128 functions, error codes   |
| Icons.java                  | Vector icons drawn with Java2D (no image files)              |
| DrawShape.java / DrawingLayer.java | Pen, highlighter, shapes and eraser painted over the sheet |
| FunctionCatalog.java        | Names, syntax and examples of every function                 |
| InsertFunctionDialog.java   | fx function picker (search + categories)                     |
| SpreadsheetCellRenderer.java| Cell rendering (bold, italic, alignment, colours, borders)   |
| CellStyle.java              | Per-cell formatting record                                   |
| InsertDialog.java           | Insert date / random data / sales table / chart placeholder  |
| MacroIDEDialog.java         | VBA editor + console                                         |
| MacroEngine.java            | VBA-subset interpreter                                       |
| WorkbookIO.java             | File layer: save / load .spx workbooks, optional encryption  |
| SheetProtection.java        | Sheet protection state + salted PBKDF2 password hash         |
| CellFormats.java            | Number formats: General, Number, Comma, Currency, Percent, Scientific |
| CondRule.java               | Conditional-formatting rules (greater, less, between, text, duplicates) |
| AutoFilter.java / FilterDialog.java | Filter state and the value-picker drop-down            |
| FormulaRefs.java            | Rewrites formulas when rows/columns are inserted, deleted or sorted |
| PivotBuilder.java / PivotDialog.java | PivotTable, column summary, calculated columns         |
| AutoSaveManager.java        | AutoSave timer, recovery copies, crash recovery              |
| XlsxIO.java                 | Excel (.xlsx) import and export (pure JDK)                   |
| ChartSpec / ChartData / ChartPainter / ChartLayer | Interactive charts (drawing, live data, hover, drag) |
| FillSeries.java / FillHandle.java | Fill handle: series, text, formulas, undo              |
| ValidationRule.java                | Data validation rules (list, whole, decimal, length)   |
| PageSetup.java                     | Paper, margins, scale, header/footer, print area       |
| PrintLayout.java                   | Cuts a sheet into pages; paints preview and printout   |
| PageSetupDialog.java / PrintPreviewDialog.java | Page Setup dialog and Print Preview window |
| VersionStore.java / VersionHistoryDialog.java | Saved copies of a workbook and the history window |
| CommandPalette.java / ShortcutsDialog.java | Ctrl+Shift+P command search; searchable shortcut list |
| SplashWindow.java                  | Start-up animation                                     |
| GoalSeek.java                      | What-If Goal Seek (secant + bisection)                 |
| Sparkline.java / SparklinePainter.java | Tiny in-cell line / column / win-loss charts       |
| SheetImage.java             | A picture on a sheet (PNG bytes + position + size)           |
| ImageLayer.java             | Draws pictures over the grid; select / drag / resize         |

## Ribbon tabs
Home, Insert, Draw, Page Layout, Data, Power Pivot, Developer, Review, View, Help.

- **Home**: font family + size, bold/italic, fill and text colour; Number group (format list, %, comma, more/fewer decimals; typing `25%` stores 0.25); Styles (Cell Styles gallery, Conditional formatting); Cells (insert/delete rows and columns, formulas are adjusted); Editing (AutoSum, Function, Sort & Filter).
- **Data**: Sort A-Z / Z-A (header row stays on top), Filter (click the arrow on a header cell; search and tick values), Clear, Remove Duplicates, Text to Columns, Import/Export CSV.
- **Power Pivot**: PivotTable (rows, optional columns, Sum/Count/Average/Min/Max, grand totals) on a new sheet; Summary statistics; Calculated Column such as `[Qty]*[Price]`.
- **Page Layout**: Page Setup, Orientation, Fit Width, Set / Clear Print Area, print Gridlines / Headings / Repeat Row 1, Print Preview, Print.
- **Review**: Read-Only mode, Version History, Save Version, Recover Unsaved.
- **Developer**: VBA macros, Show Formulas, Inspect Cell, Workbook Info.
- **Help**: formula reference, keyboard shortcuts, About, AutoSave settings.

## Excel files (.xlsx)
File > Open (choose *.xlsx) or Data > Open Excel reads an Excel workbook; File > Export to Excel or Data > Export Excel writes one. Carried over: sheets, values, formulas (with their last result), fonts, colours, number formats, alignment, column widths; dates become text (yyyy-MM-dd). Not carried over: pictures, drawings, charts, notes, conditional formatting, filters, protection. Formulas using things this app lacks (other-sheet references, newer functions) keep their last Excel value. Use .spx to keep everything.

## Updates (users get new versions automatically)
Spreadsheet Pro asks GitHub once a day, when it starts, whether a newer release exists. If so, a window shows what is new with **Download**, **Later** and **Skip this version**. Download opens the right file for the user's system (Windows, macOS or Linux) in the browser. Nothing is installed behind their back and no personal data is sent. Users can also use **Help > Check for Updates...**, and turn the start-up check off in the Help menu. Developers can disable it with `-Dspx.noupdate=true`.

### Publishing a new version (what you do)
1. Change the code, then `git add .`, `git commit -m "what changed"`, `git push`.
2. Tag it: `git tag v1.2.0` then `git push origin v1.2.0`.
3. GitHub Actions creates the release, builds the jar and the Windows, macOS and Linux apps (about 10 minutes) and attaches them. The version number comes from the tag, so there is nothing to edit by hand.
4. Every user running an older version is told on their next start.

The download links on your website (`.../releases/latest/download/SpreadsheetPro-Windows.zip`) always point at the newest release, so the site never needs editing.

## Charts
Insert > Chart: Column, Bar, Line, Area, Pie, Scatter, Doughnut, Stacked Column and Stacked Bar from the selected cells (first row = series names, first column = labels). Hover for exact values, click a legend entry to hide a series, click the chart to select it (source cells are outlined), drag to move, drag the corner to resize, double-click or Edit Chart to change type/title/range, right-click for a menu, Delete to remove. Charts update live as cells change and are saved in .spx.
- **Trendlines** (line, area, scatter and column charts): linear, exponential or a 3-point moving average, drawn dashed per series, with the equation and R² shown on the chart (turn that off in Edit Chart). Right-click a chart > Trendline, or use Edit Chart.
- **Data labels** print the value on every bar, point or slice (right-click > Data Labels).
- **Save as Picture...** (right-click a chart) writes a crisp 2x PNG.

## Notes (cell comments)
Review > New Note (or right-click a cell > Add Note...) attaches a note to a cell. A red corner marks it; hover to read it. Next Note jumps between notes, Delete Note removes them from the selection. Notes move with their cell when rows or columns are inserted or deleted, are saved in .spx, are blocked on protected or read-only sheets, and are not part of the .xlsx export.

## PDF export
File > Export as PDF... (or Page Layout > Export PDF) saves the active sheet exactly as the print preview shows it, using your page setup, print area, headers and footers. Pages are stored as 200 dpi pictures, so the PDF looks identical everywhere, but its text cannot be selected or searched.

## Fill handle
Drag the small square at the corner of the selection down, up, left or right: 1, 2 continues 3, 4 ...; Item 1, Mon, Jan continue; formulas copy with relative references ($A$1 stays); other text repeats. Double-click the square to fill down as far as the neighbouring column. Ctrl+Z undoes the last fill.

## Data tools (Data tab)
- **Validation**: select cells, pick Allow (List, Whole number, Decimal, Text length), the condition, an optional input message and a Stop / Warning / Information alert. Lists can be typed (`Yes,No,Maybe`) or a range (`=$A$1:$A$5`) and show a dropdown arrow in the cell. Bad entries are refused when you type them. **Circle Invalid** rings cells that already break a rule; **Clear Rules** removes them. Rules are saved in .spx and in .xlsx.
- **Custom Sort**: sort by several columns, each ascending or descending, with a "my data has headers" option.
- **Goal Seek**: choose a formula cell, the value you want and the number cell to change; the app finds the input and asks whether to keep it.
- **Sparklines** (Insert tab): tiny line, column or win/loss charts drawn inside cells from a row or column of numbers. They are saved in .spx; .xlsx files note that sparklines are not exported.
- **Sheet Size**: grow a sheet to 100,000, 500,000 or 1,048,576 rows. Only used cells take memory, and row numbers are made on demand. Tested with 200,000 rows saved and loaded as .spx and .xlsx.

## Printing (Page Layout tab, File menu)
- **Print Preview** shows the pages exactly as they will print (Page Up / Page Down to flip, zoom, Print... and Page Setup... buttons). **Print...** (Ctrl+P) goes straight to the printer dialog.
- **Page Setup**: Letter / Legal / A4 / A3 / A5, portrait or landscape, margins, scale 10-400% or *fit all columns on one page wide* / *whole area on one page*, center on page, page order, repeat top rows, gridlines and row/column headings.
- **Header and footer** have left, center and right parts with codes: `&P` page, `&N` pages, `&D` date, `&T` time, `&F` file name, `&A` sheet name.
- **Print area**: select cells and press *Set Print Area*; only those cells print. Pictures, charts and sparklines print too, and rows hidden by a filter are left out.
- Page setup is saved per sheet inside the .spx file. (It is not part of the .xlsx export yet.)

## Safety (Review tab, File menu)
- **Version History**: every save keeps a copy (the newest 30 plain copies, plus any you name with *Save Version*). Open the history to restore one; what you had open is kept as a version first, so a restore can always be undone. Copies live in `~/.spreadsheet-pro/versions`.
- **Recover Unsaved Workbooks**: lists the copies AutoSave kept after a crash so you can open or delete them any time (they are also offered at start-up).
- **Read-Only Mode**: blocks every edit (typing, paste, sort, pictures, drawing, sheets) until you turn it off; Save becomes Save As so the original stays untouched. Workbooks whose file is write-protected open read-only automatically.

## Command palette and shortcuts
- **Ctrl+Shift+P** opens a search box over every menu command and ribbon button (and "Go to sheet"). Type a few letters, Up / Down, Enter.
- **Ctrl+/** (Help > Keyboard Shortcuts) opens a searchable list of every shortcut.

## Start-up animation
A short, clean white splash (a green logo tile whose grid lights up, then the name and a thin progress line) plays while the real window loads. Click it to skip, or start with `--no-splash` (or `-Dspx.nosplash=true`).

## AutoSave
The AutoSave switch (top right of the ribbon) saves unsaved work every N seconds (default 60). Mode 1 (default) writes a separate recovery copy in `~/.spreadsheet-pro/autosave` and never touches your file; mode 2 saves into the open file. Encrypted workbooks stay encrypted. After a crash the app offers to recover the copy on the next start. Settings: File > AutoSave Settings or Help tab.

## Formulas
Start with `=`. Operators `+ - * / ^ %` and parentheses; references `A1`; ranges `A1:A10`.
128 functions. Click **fx** (or Insert > Function, Shift+F3) to search and insert them; Help > Formula Reference lists all.

- Math & Statistics: SUM AVERAGE MIN MAX COUNT COUNTA COUNTBLANK PRODUCT MEDIAN STDEV VAR LARGE SMALL ABS SQRT ROUND ROUNDUP ROUNDDOWN INT MOD POWER EXP LN LOG LOG10 SIGN PI RAND RANDBETWEEN
- Logical: IF AND OR NOT IFERROR ISNUMBER ISTEXT ISBLANK ISERROR COUNTIF SUMIF AVERAGEIF
- Text: CONCATENATE CONCAT LEN UPPER LOWER PROPER TRIM LEFT RIGHT MID SUBSTITUTE FIND REPT VALUE (and the & operator)
- Lookup: VLOOKUP MATCH INDEX
- Date: TODAY NOW YEAR MONTH DAY DAYS
- **New in this version (64 more):**
  - Math: SUMPRODUCT SUMSQ CEILING FLOOR TRUNC EVEN ODD FACT COMBIN GCD SIN COS QUOTIENT
  - Statistics: STDEVP VARP MODE RANK PERCENTILE QUARTILE CORREL SLOPE INTERCEPT COUNTIFS SUMIFS AVERAGEIFS MAXIFS MINIFS
  - Logical: IFS SWITCH XOR IFNA ISEVEN ISODD
  - Text: TEXTJOIN EXACT CHAR CODE SEARCH REPLACE TEXT TEXTBEFORE TEXTAFTER
  - Date: DATE DATEVALUE DATEDIF EDATE EOMONTH WEEKDAY NETWORKDAYS WORKDAY
  - Lookup: XLOOKUP HLOOKUP LOOKUP CHOOSE ROWS COLUMNS ROW COLUMN
  - Financial: PMT FV PV NPV IRR NPER (money you pay out is negative, like Excel)

Also comparison operators (= <> < > <= >=), text in "quotes", and TRUE/FALSE values. IF only evaluates the branch it picks, so `=IF(A1=0,0,10/A1)` is safe. Extra error: `#N/A` (lookup not found).

## Zoom and column width

- Zoom 25%-300%: View menu, status-bar slider / - / + buttons, Ctrl+mouse wheel, Ctrl +/-/0. Each sheet remembers its own zoom.
- Resize a column by dragging the right edge of its header; double-click the edge to fit the content. View > Reset Column Widths restores the default.
- Zoom, column widths and pictures are saved in the .spx file (older files still open).
Errors: #DIV/0!, #SYNTAX!, #NAME?, #ERROR! (bad reference, text used as number, circular reference).

## Macro example
    Sub Demo()
        For i = 1 To 10
            Cells(i, 1).Value = i * i
            If i Mod 2 = 0 Then Cells(i, 1).Interior.Color = RGB(198, 229, 209)
        Next i
        Range("A11").Formula = "=SUM(A1:A10)"
        Range("A11").Font.Bold = True
    End Sub
Supported: Dim, For/Next (Step, Exit For), If/ElseIf/Else/End If (and single-line If),
Cells(r,c) / Range("A1:B3") with .Value .Formula .Font.Bold/Italic/Size/Color .Interior.Color
.HorizontalAlignment, ClearContents, Clear, MsgBox, Debug.Print, and Int Abs Sqr Rnd Round
Len UCase LCase Str Val Mid RGB.

## Shortcuts
Ctrl+B bold, Ctrl+I italic, Ctrl+L/E/R align, Delete clear, F2 / double-click edit,
Ctrl+N new sheet. Double-click a sheet tab to rename it; the x closes it.

## Files (File menu)
- New Workbook (Ctrl+N), Open... (Ctrl+O), Save (Ctrl+S), Save As... (Ctrl+Shift+S).
- Workbooks are saved as `.spx` and keep all sheets, values, formulas, formatting, pictures
  and sheet protection. The title bar shows `*` for unsaved changes and you are asked
  before anything is discarded.
- Set Open Password...: encrypts the saved file (AES-256-GCM, key from PBKDF2). The password is
  asked when the file is opened. A forgotten password cannot be recovered.
- Import CSV / Export CSV still work as before.

## Pictures
- Insert > Picture from File... (or the Picture button): PNG, JPG, GIF, BMP.
- Insert > Picture from Clipboard: pastes an image copied from another program.
- Click a picture to select it, drag to move, drag the small square in its corner to resize
  (hold Shift to stretch freely), press Delete to remove it.

## Sheet protection (Review menu or the Protect Sheet button)
- Protect with a password, or leave it blank to protect without one.
- A protected sheet cannot be edited, formatted, cleared, auto-summed, filled by a macro or
  have its pictures changed or deleted, and cannot be deleted. A padlock shows on its tab.
- Unprotect Sheet asks for the password. Passwords are never stored, only a salted hash.
- Sheet protection stops changes inside the app (like Excel). To keep a file's contents
  private use Set Open Password, which encrypts it.

## Ribbon, icons and interactivity

- The ribbon has tabs: **Home**, **Insert**, **Draw** and **View**. Every button has an icon and a tooltip with its shortcut.
- Bold / Italic / alignment buttons light up to match the selected cell.
- The cell, column header and row header under the mouse get a faint highlight.
- Right-click a cell for a menu (insert function, clear, bold/italic, alignment, fill colour, fit column, insert picture).
- Menus have icons too. The window and task-bar icon is drawn in code.

## Drawing and painting (Draw tab)

Tools: Pen, Highlighter, Line, Rectangle, Ellipse, Eraser (the arrow, "Select", goes back to normal cell selection; switching to another ribbon tab also turns drawing off).

- Pick one of 8 quick colours or any colour with **More**; the slider sets the brush thickness; **Fill shapes** fills rectangles and ellipses.
- Hold **Shift** for lines at 45 degree steps and perfect squares / circles.
- The eraser removes the whole stroke or shape you touch.
- **Undo** (Ctrl+Z), **Redo** (Ctrl+Y) and **Clear All** (also undoable).
- Drawings follow the zoom level, are saved in the .spx file (format v3; older files still open) and are locked while the sheet is protected.

## Appearance, languages and fonts (v1.2)

Open **View > Appearance & Language...** (or the **Appearance** button on the View tab, or Ctrl+Shift+P and type "Appearance").

- **Themes:** Light (clean white, default), Classic green, Dark, Ocean blue, Purple, High contrast.
- **Languages:** English, اردو (Urdu), العربية (Arabic), Español, Français. Urdu and Arabic switch the menus and ribbon to right-to-left; the grid and formula bar stay left-to-right. Translations cover the menus, ribbon, tooltips and settings window; some status messages and dialogs stay in English. Native speakers are welcome to improve `LangUr.java`, `LangAr.java`, `LangEs.java`, `LangFr.java` (same order as `LangEn.java`).
- **Fonts:** pick the interface font and size (whole app) and a separate default font and size for spreadsheet cells.
- Everything applies instantly and is remembered between runs. **Reset to defaults** puts it all back.

## Formula suggestions and smooth movement (v1.3)

- Type `=` and the first letters of a function (for example `=su`) and a list of matching functions appears under the cell or formula bar. Up/Down choose, **Tab** or **Enter** insert the name and an opening bracket, **Esc** closes it. Once you are inside a call, a hint line shows its arguments, e.g. `VLOOKUP(lookup_value, table, column, [exact_match])`.
- Moving between cells with the keyboard and scrolling with the mouse wheel now glide instead of jumping. Turn it off in **View > Appearance & Language... > Smooth scrolling**.

## Undo / redo, copy / paste, brushes (v1.4)

- **Undo / Redo** (Ctrl+Z / Ctrl+Y, Edit menu, Home > Clipboard): covers typing, formulas, formatting, sort, insert/delete rows and columns, notes, rules, fills, pastes. Each sheet has its own history. While the Draw tab is active, Ctrl+Z undoes drawing strokes instead.
- **Copy / Cut / Paste** (Ctrl+C / X / V): formulas keep working (references shift like in Excel), formatting comes along, one copied cell fills a selected range, cut moves cells. **Ctrl+Shift+V** (or Edit > Paste Values) pastes values only. Text copied from Excel, Google Sheets or Notepad pastes in; other programs receive your cells as tab-separated text. A picture on the clipboard pastes as a picture. The copied range gets a moving dashed frame (Esc removes it).
- **Brushes** (Draw tab > Brushes, opens on click or hover): Pen, Pencil, Marker, Calligraphy, Crayon, Spray, Watercolor, Dashed, Neon, each with its own icon and a live sample in your current colour and thickness.
- **Fonts:** the Home font box lists every font installed on the computer, each shown in its own style.
- The selection frame now glides from cell to cell (switch off under Appearance > Smooth scrolling).

## Excel-style helpers (v1.5)

- **Range pointing:** while typing a formula (after `=`, `(`, `,` or an operator) click a cell or drag a range to insert `B2` or `B2:B9`. Every reference gets its own colour, both in the text and as an outline on the grid. Works in the cell and in the formula bar.
- **Quick Analysis** (Ctrl+Q, Edit menu, or the small button under a selected range): Charts, Totals (sum/average/count/max/min below, or sum to the right), Tables and Sparklines in one popup.
- **Format as Table** (Home > Styles): 9 table styles with header colour, banded rows and a built-in filter. **Clear table style** removes it.
- **Charts:** new Radar and Combo (columns + line) types, plus Scatter; charts now follow the dark themes. Exports and printing stay light.
- **Spelling** (F7, Review tab): English (US) checker with suggestions, Ignore / Ignore All / Change / Change All / Add to Dictionary. Your added words live in `~/.spreadsheet-pro-words.txt`. ACRONYMS, camelCase, numbers, e-mails and links are skipped.
- **Sheet tabs:** double-click to rename in place, right-click for Tab Color, Duplicate, Move Left/Right; drag a tab to reorder, Ctrl+drag to copy. Tab colours are saved in the file (format v9; older files still open).

### Dictionary licence
The word list in `resources/dict/en_US.txt` is derived from the en_US Hunspell dictionary (SCOWL, Kevin Atkinson). Its copyright notice, which must stay with it, is in `resources/dict/LICENSE-dictionary.txt`.

## Data connections (v1.6)

**Data tab > Get Data** brings outside data into the sheet and can keep a link so one click refreshes it (**Refresh All**, or **Connections** to refresh, edit or remove one).

- **Text / CSV file:** comma, semicolon, tab or pipe (detected automatically), quotes handled, UTF-8 or Latin-1.
- **JSON file or web address:** a list of objects becomes a table (nested fields become `a.b` columns). Give a path like `data.items` when the list is buried inside; otherwise a single list is found automatically.
- **Web page:** http/https only. A page with tables imports the table number you pick; CSV and JSON links work too, and Google Sheets "edit" links are turned into CSV links.
- **Database (JDBC):** choose your database's driver `.jar` (sqlite-jdbc, PostgreSQL, MySQL...), a connection string and a single `SELECT` query. Anything that is not a SELECT is refused and the connection is read-only. The password is asked for on every refresh and is never saved.
- Every dialog has a **Preview** of the first rows. A refresh clears what the last import wrote, then writes the new rows (limits: 200,000 rows, 702 columns, 25 MB).
- Safety: values from outside that start with `=` are stored as text, never run as formulas; refreshing always asks first and shows the addresses it will read. Web addresses are saved in the file, so do not put secret tokens in them.
- Connections are saved in the .spx file (format v10; older files still open).

## The File screen (v1.7)

Click the green **File** button (or Alt+F, File > File Screen) for a full-window Backstage view: **Info** (properties, password, version history), **New**, **Open** (Recent Workbooks list, Computer, Excel files), **Save**, **Save As** (.spx, .xlsx, PDF, CSV), **Print**, **Share**, **Export**, **Close**, **Account** (version, updates) and **Options** (appearance, AutoSave). Esc or the round back arrow returns to your sheet. It follows the theme and language you chose. The last 12 workbooks you opened or saved are remembered.

## Auto-fill (v1.8)

- **Fill handle** (small square at the corner of the selection): drag to continue a series. `1, 2` then drag gives 3, 4, 5, 6 ...; Mon, Jan, `Item 1`, `Q3` and dates-as-text continue too; formulas move with their cells.
- **One number, count up:** hold **Ctrl** while dragging a single `1` to get 1, 2, 3, 4, 5, 6 (a plain drag copies it, like Excel). Ctrl on a series copies instead.
- **Auto Fill Options:** after a fill a small button appears at the corner. Choose *Copy Cells*, *Fill Series*, *Fill Formatting Only* or *Fill Without Formatting*; the fill is redone with your choice.
- **Fill > Series...** (Home > Editing, or Edit menu): linear or growth, in rows or columns, with a step and a stop value. Type `1`, open Series, stop `6`, and it fills 1 to 6.
- **Ctrl+D / Ctrl+R** fill down / right (copy the top row or left column over the selection, or from the cell above / left when one cell is selected).

## Visual formatting, links and Find & Replace (v1.9)

- **Colour scales, icon sets, data bars:** Home > Conditional > *Color Scales* (8 presets, 2 or 3 colours), *Icon Sets* (3/4/5 arrows, traffic lights, symbols, flags, 4/5 ratings) and *Data Bars* (5 colours). They follow the lowest and highest number in the selected range, update live as you type, and survive save/reload. Applying the same kind to the same range again replaces it. *Clear Rules* removes them.
- **Hyperlinks:** **Ctrl+K** (Insert > Link, or right-click > Link...) links a cell to a web page, an e-mail address or a place in the workbook (sheet + cell). **Ctrl+click** follows it. Links show blue and underlined, hovering shows the address. `=HYPERLINK("https://...","Text")` works too. Only `http`, `https` and `mailto` are allowed - `file:` and `javascript:` links are refused. Links move with their cells when rows/columns are inserted or deleted, and are saved in the .spx file (format v11; older files still open).
- **Find & Replace:** **Ctrl+F** to find, **Ctrl+H** to replace (Home > Find). Options: match case, match entire cell, look in *Values* or *Formulas*, within this *Sheet* or the whole *Workbook*. Wildcards `*` and `?` work (`~*` for a real asterisk). *Find Next*, *Find All* (click a result to jump), *Replace* and *Replace All*. Protected sheets are never changed, and when looking in values a formula result is never overwritten. Replacing in other sheets cannot be undone with Ctrl+Z of the current sheet.
