import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/** English (US) spell checker backed by a bundled word list (SCOWL / Hunspell en_US) plus a personal dictionary. */
public final class SpellChecker {

    /** A word found in a text. */
    public static final class Token {
        public final int start, end;
        public final String word;

        Token(int start, int end, String word) {
            this.start = start;
            this.end = end;
            this.word = word;
        }
    }

    private static Set<String> dict;
    private static final Set<String> custom = new TreeSet<>();
    private static final Set<String> session = new HashSet<>();
    private static boolean customLoaded;

    private SpellChecker() {
    }

    private static synchronized Set<String> dict() {
        if (dict == null) {
            Set<String> d = new HashSet<>(260000);
            try (InputStream in = SpellChecker.class.getResourceAsStream("/dict/en_US.txt")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) if (!line.isEmpty()) d.add(line);
                }
            } catch (IOException ignored) {
            }
            dict = d;
        }
        return dict;
    }

    /** False when the word list is missing from the jar (the checker then accepts everything). */
    public static boolean available() {
        return !dict().isEmpty();
    }

    private static Path customFile() {
        return Paths.get(System.getProperty("user.home", "."), ".spreadsheet-pro-words.txt");
    }

    private static synchronized void loadCustom() {
        if (customLoaded) return;
        customLoaded = true;
        try {
            Path f = customFile();
            if (Files.exists(f)) for (String s : Files.readAllLines(f, StandardCharsets.UTF_8)) if (!s.trim().isEmpty()) custom.add(s.trim().toLowerCase());
        } catch (IOException ignored) {
        }
    }

    public static synchronized void addToDictionary(String word) {
        loadCustom();
        custom.add(word.toLowerCase());
        try {
            Files.write(customFile(), custom, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void ignoreAll(String word) {
        session.add(word.toLowerCase());
    }

    public static void resetIgnored() {
        session.clear();
    }

    /** True when the word is spelt correctly (or is something we do not check). */
    public static boolean isCorrect(String word) {
        loadCustom();
        Set<String> d = dict();
        if (d.isEmpty()) return true;
        String w = word.toLowerCase().replace('’', '\'');
        if (w.length() < 2 || d.contains(w) || custom.contains(w) || session.contains(w)) return true;
        if (w.endsWith("'s") && (d.contains(w.substring(0, w.length() - 2)) || custom.contains(w.substring(0, w.length() - 2)))) return true;
        if (w.endsWith("'") && d.contains(w.substring(0, w.length() - 1))) return true;
        if (w.indexOf('-') > 0) {
            for (String part : w.split("-")) if (!part.isEmpty() && !isCorrect(part)) return false;
            return true;
        }
        return false;
    }

    /** The misspelt words of a text (skips numbers, ACRONYMS, camelCase, e-mail addresses and links). */
    public static List<Token> misspelled(String text) {
        List<Token> out = new ArrayList<>();
        if (text == null || text.isEmpty() || text.startsWith("=")) return out;
        if (text.contains("@") || text.contains("://") || text.contains("www.")) return out;
        int n = text.length();
        int i = 0;
        while (i < n) {
            if (!Character.isLetter(text.charAt(i))) { i++; continue; }
            int s = i;
            while (i < n && (Character.isLetter(text.charAt(i))
                    || ((text.charAt(i) == '\'' || text.charAt(i) == '’' || text.charAt(i) == '-') && i + 1 < n && Character.isLetter(text.charAt(i + 1))))) i++;
            int e = i;
            boolean glued = (s > 0 && Character.isDigit(text.charAt(s - 1))) || (e < n && (Character.isDigit(text.charAt(e)) || text.charAt(e) == '_'));
            String w = text.substring(s, e);
            if (glued || !checkable(w)) continue;
            if (!isCorrect(w)) out.add(new Token(s, e, w));
        }
        return out;
    }

    private static boolean checkable(String w) {
        if (w.length() < 2) return false;
        boolean hasLower = false, hasUpper = false;
        for (int k = 1; k < w.length(); k++) {
            char c = w.charAt(k);
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isLowerCase(c)) hasLower = true;
        }
        if (hasUpper && !hasLower) return false;                          // ACRONYM
        if (hasUpper && Character.isLowerCase(w.charAt(0))) return false; // camelCase
        if (hasUpper && hasLower) return false;                           // McDonald, iPhone
        return true;
    }

    /** Up to {@code max} likely corrections, best first, with the original capitalisation. */
    public static List<String> suggest(String word, int max) {
        Set<String> d = dict();
        String w = word.toLowerCase().replace('’', '\'');
        LinkedHashMap<String, Integer> found = new LinkedHashMap<>();
        // distance 1 by generating candidates
        String alpha = "abcdefghijklmnopqrstuvwxyz'";
        for (int i = 0; i <= w.length(); i++) {
            if (i < w.length()) {
                add(found, d, w.substring(0, i) + w.substring(i + 1), 1);
                for (int k = 0; k < alpha.length(); k++) add(found, d, w.substring(0, i) + alpha.charAt(k) + w.substring(i + 1), 1);
            }
            if (i + 1 < w.length()) add(found, d, w.substring(0, i) + w.charAt(i + 1) + w.charAt(i) + w.substring(i + 2), 0);
            for (int k = 0; k < alpha.length(); k++) add(found, d, w.substring(0, i) + alpha.charAt(k) + w.substring(i), 1);
        }
        if (found.size() < max && w.length() > 3) { // distance 2 by scanning similar-length words
            for (String c : d) {
                if (Math.abs(c.length() - w.length()) > 2 || found.containsKey(c)) continue;
                if (c.charAt(0) != w.charAt(0) && c.charAt(c.length() - 1) != w.charAt(w.length() - 1)) continue;
                if (distance(w, c, 2) <= 2) found.put(c, 2);
            }
        }
        List<String> list = new ArrayList<>(found.keySet());
        final String base = w;
        list.sort((a, b) -> {
            int da = found.get(a), db = found.get(b);
            if (da != db) return Integer.compare(da, db);
            int pa = prefix(base, a), pb = prefix(base, b);
            if (pa != pb) return Integer.compare(pb, pa);
            if (a.length() != b.length()) return Integer.compare(Math.abs(a.length() - base.length()), Math.abs(b.length() - base.length()));
            return a.compareTo(b);
        });
        if (list.size() > max) list = new ArrayList<>(list.subList(0, max));
        List<String> out = new ArrayList<>();
        boolean allCaps = word.length() > 1 && word.equals(word.toUpperCase());
        boolean cap = Character.isUpperCase(word.charAt(0));
        for (String s : list) out.add(allCaps ? s.toUpperCase() : cap ? Character.toUpperCase(s.charAt(0)) + s.substring(1) : s);
        return out;
    }

    private static void add(Map<String, Integer> found, Set<String> d, String c, int dist) {
        if (!c.isEmpty() && d.contains(c) && !found.containsKey(c)) found.put(c, dist);
    }

    private static int prefix(String a, String b) {
        int n = Math.min(a.length(), b.length()), i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    /** Damerau-Levenshtein distance, giving up (returning limit+1) when it must exceed the limit. */
    static int distance(String a, String b, int limit) {
        int la = a.length(), lb = b.length();
        int[][] m = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) m[i][0] = i;
        for (int j = 0; j <= lb; j++) m[0][j] = j;
        for (int i = 1; i <= la; i++) {
            int rowMin = Integer.MAX_VALUE;
            for (int j = 1; j <= lb; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(m[i - 1][j] + 1, m[i][j - 1] + 1), m[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) v = Math.min(v, m[i - 2][j - 2] + 1);
                m[i][j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > limit) return limit + 1;
        }
        return m[la][lb];
    }
}
