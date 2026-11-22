import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

/** Helpers for cell hyperlinks: normalising what the user typed, and safely opening it. */
public final class Hyperlinks {
    private Hyperlinks() {
    }

    /** "#Sheet2!B5" / "#B5" mean a place in this workbook. */
    public static boolean isInternal(String target) {
        return target != null && target.startsWith("#");
    }

    /**
     * Cleans what the user typed: bare domains get https://, bare e-mail addresses get mailto:.
     * Only http, https, mailto and in-workbook places are allowed; anything else returns null.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("#")) return t.length() > 1 ? t : null;
        String low = t.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://") || low.startsWith("mailto:")) return t;
        if (low.matches("^[a-z][a-z0-9+.-]*:.*") && !low.matches("^[a-z0-9.-]+:\\d+.*")) return null; // file:, javascript:, ...
        if (t.contains("@") && !t.contains("/") && !t.contains(" ")) return "mailto:" + t;
        if (t.contains(" ")) return null;
        return "https://" + t;
    }

    /** "B5" -> {4, 1}, or null. */
    public static int[] parseCell(String ref) {
        if (ref == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\$?([A-Za-z]{1,3})\\$?(\\d{1,7})$").matcher(ref.trim());
        if (!m.matches()) return null;
        return new int[]{Integer.parseInt(m.group(2)) - 1, FormulaEngine.parseColumn(m.group(1))};
    }

    /** Splits "#Sheet 2!B5" into {"Sheet 2", "B5"} (sheet may be null for the current sheet). */
    public static String[] splitInternal(String target) {
        String t = target.substring(1);
        if (t.startsWith("'")) {
            int end = t.lastIndexOf("'!");
            if (end > 0) return new String[]{t.substring(1, end).replace("''", "'"), t.substring(end + 2)};
        }
        int bang = t.lastIndexOf('!');
        if (bang < 0) return new String[]{null, t};
        return new String[]{t.substring(0, bang), t.substring(bang + 1)};
    }

    /** Opens a web / mail link in the system's default program. @return an error text, or null when started. */
    public static String openExternal(String target) {
        String safe = normalize(target);
        if (safe == null || isInternal(safe)) return "That link type is not allowed.";
        try {
            if (!Desktop.isDesktopSupported()) return "Cannot open links on this system.";
            Desktop d = Desktop.getDesktop();
            URI uri = new URI(safe);
            if (safe.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
                if (!d.isSupported(Desktop.Action.MAIL)) return "No mail program is available.";
                d.mail(uri);
            } else {
                if (!d.isSupported(Desktop.Action.BROWSE)) return "No web browser is available.";
                d.browse(uri);
            }
            return null;
        } catch (Exception ex) {
            return "Could not open the link: " + ex.getMessage();
        }
    }
}
