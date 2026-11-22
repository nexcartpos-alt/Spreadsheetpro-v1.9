import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads tables from text files, JSON, web pages and databases (no GUI; everything returns rows of strings). */
public final class DataImporter {
    public static final int MAX_BYTES = 25 * 1024 * 1024;
    public static final int MAX_ROWS = 200_000;
    public static final int MAX_COLS = 702;

    private DataImporter() {
    }

    /** The rows read, plus a note such as "truncated to 200000 rows". */
    public static final class Table {
        public final List<String[]> rows;
        public final String note;

        Table(List<String[]> rows, String note) {
            this.rows = rows;
            this.note = note;
        }
    }

    /** Fetches the data a connection points at. {@code password} is only used for databases. */
    public static Table fetch(DataConnection c, char[] password) throws IOException {
        switch (c.kind) {
            case TEXT: {
                String text = decode(readFile(new File(c.source)));
                return finish(fromText(text, c.delimiter), null);
            }
            case JSON: {
                String text = decode(readFile(new File(c.source)));
                return finish(fromJson(text, c.path), null);
            }
            case WEB: {
                String text = decode(download(c.source));
                return finish(fromWeb(text, c), null);
            }
            case DATABASE:
                return finish(fromDatabase(c, password), null);
            default:
                throw new IOException("Unknown connection type");
        }
    }

    private static Table finish(List<String[]> rows, String note) {
        String n = note;
        if (rows.size() > MAX_ROWS) {
            rows = new ArrayList<>(rows.subList(0, MAX_ROWS));
            n = "Only the first " + MAX_ROWS + " rows were kept.";
        }
        return new Table(rows, n);
    }

    // ---- bytes -> text ----

    private static byte[] readFile(File f) throws IOException {
        if (!f.isFile()) throw new IOException("File not found: " + f);
        if (f.length() > MAX_BYTES) throw new IOException("File is larger than " + (MAX_BYTES >> 20) + " MB");
        return Files.readAllBytes(f.toPath());
    }

    static String decode(byte[] b) {
        int off = (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(b, off, b.length - off)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, off, b.length - off, StandardCharsets.ISO_8859_1);
        }
    }

    /** Google Sheets "edit" links are turned into CSV export links. */
    static String normalizeUrl(String url) {
        Matcher m = Pattern.compile("^(https://docs\\.google\\.com/spreadsheets/d/[A-Za-z0-9_-]+)/(?:edit|view)[^#]*(?:#gid=(\\d+))?.*$").matcher(url.trim());
        if (m.matches()) return m.group(1) + "/export?format=csv" + (m.group(2) != null ? "&gid=" + m.group(2) : "");
        return url.trim();
    }

    private static byte[] download(String address) throws IOException {
        String current = normalizeUrl(address);
        for (int hop = 0; hop < 5; hop++) {
            URL url = new URL(current);
            String proto = url.getProtocol();
            if (!proto.equals("http") && !proto.equals("https")) throw new IOException("Only http:// and https:// addresses are allowed");
            HttpURLConnection h = (HttpURLConnection) url.openConnection();
            h.setInstanceFollowRedirects(false);
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            h.setRequestProperty("User-Agent", "SpreadsheetPro/" + AppInfo.version());
            h.setRequestProperty("Accept", "application/json, text/csv, text/html, */*");
            int code = h.getResponseCode();
            if (code >= 300 && code < 400 && h.getHeaderField("Location") != null) {
                current = new URL(url, h.getHeaderField("Location")).toString();
                h.disconnect();
                continue;
            }
            if (code != 200) throw new IOException("The server answered " + code + " " + h.getResponseMessage());
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) throw new IOException("The answer is larger than " + (MAX_BYTES >> 20) + " MB");
                }
                return out.toByteArray();
            } finally {
                h.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }

    // ---- delimited text ----

    public static List<String[]> fromText(String text, String delimiter) {
        char d = delimiter != null && !delimiter.isEmpty()
                ? ("\\t".equals(delimiter) || "tab".equalsIgnoreCase(delimiter) ? '\t' : delimiter.charAt(0)) : detectDelimiter(text);
        return parseDelimited(text, d);
    }

    static char detectDelimiter(String text) {
        String first = text.length() > 4000 ? text.substring(0, 4000) : text;
        int nl = first.indexOf('\n');
        String line = nl > 0 ? first.substring(0, nl) : first;
        char best = ',';
        int bestCount = -1;
        for (char c : new char[]{',', ';', '\t', '|'}) {
            int n = 0;
            boolean q = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch == '"') q = !q;
                else if (ch == c && !q) n++;
            }
            if (n > bestCount) { best = c; bestCount = n; }
        }
        return best;
    }

    static List<String[]> parseDelimited(String s, char delim) {
        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else inQuotes = false;
                } else cell.append(ch);
            } else if (ch == '"' && cell.length() == 0) {
                inQuotes = true; any = true;
            } else if (ch == delim) {
                row.add(cell.toString()); cell.setLength(0); any = true;
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                if (any || cell.length() > 0 || !row.isEmpty()) {
                    row.add(cell.toString());
                    rows.add(clip(row));
                }
                row = new ArrayList<>(); cell.setLength(0); any = false;
            } else {
                cell.append(ch); any = true;
            }
            if (rows.size() > MAX_ROWS + 1) break;
        }
        if (any || cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(clip(row));
        }
        return rows;
    }

    private static String[] clip(List<String> row) {
        int n = Math.min(row.size(), MAX_COLS);
        return row.subList(0, n).toArray(new String[0]);
    }

    // ---- web ----

    static List<String[]> fromWeb(String text, DataConnection c) throws IOException {
        String t = text.trim();
        if (t.startsWith("{") || t.startsWith("[")) return fromJson(t, c.path);
        String lower = t.length() > 3000 ? t.substring(0, 3000).toLowerCase(Locale.ROOT) : t.toLowerCase(Locale.ROOT);
        if (lower.contains("<table") || lower.contains("<html") || lower.contains("<!doctype html")) {
            return fromHtmlTable(text, c.tableIndex);
        }
        return fromText(text, c.delimiter);
    }

    private static final Pattern TABLE = Pattern.compile("<table\\b.*?</table>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TR = Pattern.compile("<tr\\b.*?(?=<tr\\b|</table>|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TD = Pattern.compile("<t[dh]\\b[^>]*>(.*?)(?=<t[dh]\\b|</tr>|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** The n-th (1 = first) {@code <table>} of a web page. */
    public static List<String[]> fromHtmlTable(String html, int index) throws IOException {
        String clean = html.replaceAll("(?is)<script\\b.*?</script>", " ").replaceAll("(?is)<style\\b.*?</style>", " ")
                .replaceAll("(?s)<!--.*?-->", " ");
        Matcher tm = TABLE.matcher(clean);
        int seen = 0;
        while (tm.find()) {
            if (++seen < Math.max(1, index)) continue;
            List<String[]> rows = new ArrayList<>();
            Matcher rm = TR.matcher(tm.group());
            while (rm.find()) {
                List<String> cells = new ArrayList<>();
                Matcher dm = TD.matcher(rm.group());
                while (dm.find()) {
                    String cell = dm.group(1).replaceAll("(?is)<br\\s*/?>", " ").replaceAll("(?s)<[^>]*>", "");
                    cells.add(unescape(cell).replaceAll("\\s+", " ").trim());
                }
                if (!cells.isEmpty()) rows.add(clip(cells));
                if (rows.size() >= MAX_ROWS) break;
            }
            return rows;
        }
        throw new IOException(seen == 0 ? "No table was found on that page" : "The page only has " + seen + " table(s)");
    }

    private static String unescape(String s) {
        return s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&");
    }

    // ---- JSON ----

    public static List<String[]> fromJson(String text, String path) throws IOException {
        Object root = new Json(text).parseAll();
        if (path != null && !path.trim().isEmpty()) {
            for (String part : path.trim().split("\\.")) {
                if (part.isEmpty()) continue;
                String key = part;
                List<Integer> idx = new ArrayList<>();
                Matcher m = Pattern.compile("^(.*?)((?:\\[\\d+\\])*)$").matcher(part);
                if (m.matches()) {
                    key = m.group(1);
                    Matcher im = Pattern.compile("\\[(\\d+)\\]").matcher(m.group(2));
                    while (im.find()) idx.add(Integer.parseInt(im.group(1)));
                }
                if (!key.isEmpty()) {
                    if (!(root instanceof Map) || !((Map<?, ?>) root).containsKey(key)) throw new IOException("Path \"" + path + "\" was not found (missing \"" + key + "\")");
                    root = ((Map<?, ?>) root).get(key);
                }
                for (int i : idx) {
                    if (!(root instanceof List) || i >= ((List<?>) root).size()) throw new IOException("Path \"" + path + "\" was not found (no item " + i + ")");
                    root = ((List<?>) root).get(i);
                }
            }
        } else if (root instanceof Map) { // auto: a single list inside an object is the table
            List<Object> lists = new ArrayList<>();
            for (Object v : ((Map<?, ?>) root).values()) if (v instanceof List) lists.add(v);
            if (lists.size() == 1) root = lists.get(0);
        }
        return jsonToRows(root);
    }

    private static List<String[]> jsonToRows(Object root) {
        List<String[]> rows = new ArrayList<>();
        if (root instanceof List) {
            List<?> list = (List<?>) root;
            boolean objects = !list.isEmpty();
            boolean arrays = !list.isEmpty();
            for (Object o : list) { if (!(o instanceof Map)) objects = false; if (!(o instanceof List)) arrays = false; }
            if (objects) {
                LinkedHashMap<String, Integer> cols = new LinkedHashMap<>();
                List<Map<String, String>> flat = new ArrayList<>();
                for (Object o : list) {
                    Map<String, String> f = new LinkedHashMap<>();
                    flatten("", o, f, 0);
                    for (String k : f.keySet()) if (!cols.containsKey(k) && cols.size() < MAX_COLS) cols.put(k, cols.size());
                    flat.add(f);
                    if (flat.size() > MAX_ROWS) break;
                }
                rows.add(cols.keySet().toArray(new String[0]));
                for (Map<String, String> f : flat) {
                    String[] r = new String[cols.size()];
                    Arrays.fill(r, "");
                    for (Map.Entry<String, String> e : f.entrySet()) { Integer i = cols.get(e.getKey()); if (i != null) r[i] = e.getValue(); }
                    rows.add(r);
                }
            } else if (arrays) {
                for (Object o : list) {
                    List<?> l = (List<?>) o;
                    String[] r = new String[Math.min(l.size(), MAX_COLS)];
                    for (int i = 0; i < r.length; i++) r[i] = scalar(l.get(i));
                    rows.add(r);
                    if (rows.size() > MAX_ROWS) break;
                }
            } else {
                rows.add(new String[]{"value"});
                for (Object o : list) { rows.add(new String[]{scalar(o)}); if (rows.size() > MAX_ROWS) break; }
            }
        } else if (root instanceof Map) {
            Map<String, String> f = new LinkedHashMap<>();
            flatten("", root, f, 0);
            rows.add(new String[]{"key", "value"});
            for (Map.Entry<String, String> e : f.entrySet()) rows.add(new String[]{e.getKey(), e.getValue()});
        } else {
            rows.add(new String[]{"value"});
            rows.add(new String[]{scalar(root)});
        }
        return rows;
    }

    private static void flatten(String prefix, Object o, Map<String, String> out, int depth) {
        if (o instanceof Map && depth < 6) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                flatten(prefix.isEmpty() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey(), e.getValue(), out, depth + 1);
            }
        } else if (o instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object x : (List<?>) o) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(x instanceof Map || x instanceof List ? Json.write(x) : scalar(x));
            }
            out.put(prefix.isEmpty() ? "value" : prefix, sb.toString());
        } else {
            out.put(prefix.isEmpty() ? "value" : prefix, scalar(o));
        }
    }

    private static String scalar(Object o) {
        if (o == null) return "";
        if (o instanceof Double) {
            double d = (Double) o;
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : new java.math.BigDecimal(d).round(new java.math.MathContext(15)).stripTrailingZeros().toPlainString();
        }
        if (o instanceof Map || o instanceof List) return Json.write(o);
        return String.valueOf(o);
    }

    /** A small strict JSON reader (depth and size limited). */
    static final class Json {
        private final String s;
        private int i;

        Json(String s) {
            this.s = s;
        }

        Object parseAll() throws IOException {
            ws();
            Object v = value(0);
            ws();
            if (i < s.length()) throw err("Unexpected text after the JSON data");
            return v;
        }

        private IOException err(String m) {
            return new IOException("Not valid JSON: " + m + " (position " + i + ")");
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object value(int depth) throws IOException {
            if (depth > 64) throw err("nested too deeply");
            if (i >= s.length()) throw err("unexpected end");
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    if (i >= s.length() || s.charAt(i) != '"') throw err("expected a key");
                    String k = str();
                    ws();
                    if (i >= s.length() || s.charAt(i++) != ':') throw err("expected ':'");
                    ws();
                    m.put(k, value(depth + 1));
                    ws();
                    if (i >= s.length()) throw err("unexpected end");
                    char n = s.charAt(i++);
                    if (n == '}') return m;
                    if (n != ',') throw err("expected ',' or '}'");
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    ws();
                    l.add(value(depth + 1));
                    ws();
                    if (i >= s.length()) throw err("unexpected end");
                    char n = s.charAt(i++);
                    if (n == ']') return l;
                    if (n != ',') throw err("expected ',' or ']'");
                }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw err("unexpected character '" + c + "'");
            try {
                return Double.parseDouble(s.substring(st, i));
            } catch (NumberFormatException e) {
                throw err("bad number");
            }
        }

        private String str() throws IOException {
            i++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) break;
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw err("bad \\u escape");
                            try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); } catch (NumberFormatException x) { throw err("bad \\u escape"); }
                            i += 4;
                            break;
                        default: sb.append(e);
                    }
                } else sb.append(c);
            }
            throw err("unterminated string");
        }

        static String write(Object o) {
            if (o instanceof Map) {
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append(e.getKey()).append("\":").append(write(e.getValue()));
                }
                return sb.append('}').toString();
            }
            if (o instanceof List) {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Object x : (List<?>) o) { if (!first) sb.append(','); first = false; sb.append(write(x)); }
                return sb.append(']').toString();
            }
            if (o instanceof String) return "\"" + ((String) o).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            return o instanceof Double ? scalar(o) : String.valueOf(o);
        }
    }

    // ---- database ----

    private static final Pattern READ_ONLY = Pattern.compile("^\\s*(select|with)\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    static List<String[]> fromDatabase(DataConnection c, char[] password) throws IOException {
        String q = c.query == null ? "" : c.query.trim();
        if (q.endsWith(";")) q = q.substring(0, q.length() - 1).trim();
        if (!READ_ONLY.matcher(q).matches() || q.contains(";")) {
            throw new IOException("Only a single SELECT query is allowed (no changes to the database).");
        }
        try {
            java.sql.Driver driver = loadDriver(c);
            Properties p = new Properties();
            if (c.user != null && !c.user.isEmpty()) p.setProperty("user", c.user);
            if (password != null && password.length > 0) p.setProperty("password", new String(password));
            java.sql.Connection con = driver.connect(c.source, p);
            if (con == null) throw new IOException("The driver does not accept this connection string: " + c.source);
            try {
                try { con.setReadOnly(true); } catch (java.sql.SQLException ignored) { }
                try (java.sql.Statement st = con.createStatement()) {
                    st.setMaxRows(MAX_ROWS);
                    st.setQueryTimeout(60);
                    try (java.sql.ResultSet rs = st.executeQuery(q)) {
                        java.sql.ResultSetMetaData md = rs.getMetaData();
                        int n = Math.min(md.getColumnCount(), MAX_COLS);
                        List<String[]> rows = new ArrayList<>();
                        String[] head = new String[n];
                        for (int i = 0; i < n; i++) head[i] = md.getColumnLabel(i + 1);
                        rows.add(head);
                        while (rs.next()) {
                            String[] r = new String[n];
                            for (int i = 0; i < n; i++) { Object o = rs.getObject(i + 1); r[i] = o == null ? "" : String.valueOf(o); }
                            rows.add(r);
                        }
                        return rows;
                    }
                }
            } finally {
                try { con.close(); } catch (java.sql.SQLException ignored) { }
            }
        } catch (java.sql.SQLException e) {
            throw new IOException("Database error: " + e.getMessage(), e);
        }
    }

    private static java.sql.Driver loadDriver(DataConnection c) throws IOException {
        ClassLoader loader = DataImporter.class.getClassLoader();
        if (c.driverJar != null && !c.driverJar.trim().isEmpty()) {
            File jar = new File(c.driverJar.trim());
            if (!jar.isFile()) throw new IOException("Driver file not found: " + jar);
            loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, DataImporter.class.getClassLoader());
        }
        for (java.sql.Driver d : ServiceLoader.load(java.sql.Driver.class, loader)) {
            try {
                if (d.acceptsURL(c.source)) return d;
            } catch (java.sql.SQLException ignored) {
            }
        }
        throw new IOException("No database driver found for " + c.source + ". Choose the driver .jar file of your database (for example sqlite-jdbc or the PostgreSQL driver).");
    }
}
