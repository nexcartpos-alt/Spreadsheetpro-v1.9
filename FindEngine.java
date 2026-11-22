import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The searching and replacing logic behind Find & Replace (no user interface, so it can be tested). */
public final class FindEngine {

    private final Pattern pattern;
    public final boolean wholeCell;
    public final boolean inFormulas;

    /** Wildcards: * is any run of characters, ? is any one character, ~ escapes them. */
    public FindEngine(String find, boolean matchCase, boolean wholeCell, boolean inFormulas) {
        this.wholeCell = wholeCell;
        this.inFormulas = inFormulas;
        StringBuilder re = new StringBuilder();
        StringBuilder lit = new StringBuilder();
        for (int i = 0; i < find.length(); i++) {
            char ch = find.charAt(i);
            if (ch == '~' && i + 1 < find.length() && "*?~".indexOf(find.charAt(i + 1)) >= 0) {
                lit.append(find.charAt(++i));
            } else if (ch == '*' || ch == '?') {
                if (lit.length() > 0) re.append(Pattern.quote(lit.toString()));
                lit.setLength(0);
                re.append(ch == '*' ? ".*?" : ".");
            } else {
                lit.append(ch);
            }
        }
        if (lit.length() > 0) re.append(Pattern.quote(lit.toString()));
        String body = wholeCell ? "^(?:" + re + ")$" : re.toString();
        int flags = Pattern.DOTALL | (matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        pattern = Pattern.compile(body, flags);
        empty = find.isEmpty();
    }

    private final boolean empty;

    public boolean matches(String text) {
        return !empty && text != null && pattern.matcher(text).find();
    }

    /** The text a search looks at for one cell. */
    public String searchText(SpreadsheetModel m, int r, int c) {
        if (inFormulas) return m.getInput(r, c);
        Object v = m.getValueAt(r, c);
        return v == null ? "" : String.valueOf(v);
    }

    /** True if the cell holds a hit. */
    public boolean cellMatches(SpreadsheetModel m, int r, int c) {
        return matches(searchText(m, r, c));
    }

    /** Whether Replace may change this cell (looking in values never rewrites a formula). */
    public boolean replaceable(SpreadsheetModel m, int r, int c) {
        return !m.isProtected() && (inFormulas || !m.getInput(r, c).startsWith("="));
    }

    /** The cell text after replacing every hit, or null when there is no hit. */
    public String replaced(String input, String replacement) {
        if (empty || input == null) return null;
        Matcher mt = pattern.matcher(input);
        if (!mt.find()) return null;
        if (wholeCell) return replacement;
        return mt.replaceAll(Matcher.quoteReplacement(replacement));
    }

    /** All hit cells in row-major order, as {row, col}. */
    public List<int[]> findAll(SpreadsheetModel m) {
        List<int[]> out = new ArrayList<>();
        if (empty) return out;
        for (int[] rc : m.storedCells()) {
            if (cellMatches(m, rc[0], rc[1])) out.add(rc);
        }
        return out;
    }
}
