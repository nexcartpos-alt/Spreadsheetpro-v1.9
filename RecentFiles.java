import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The most recently used workbooks (newest first), remembered between runs. */
public final class RecentFiles {
    private static final int MAX = 12;
    private static List<String> memory;

    private RecentFiles() {
    }

    private static boolean persist() {
        return !Boolean.getBoolean("spx.noprefs");
    }

    private static synchronized List<String> list() {
        if (memory == null) {
            memory = new ArrayList<>();
            if (persist()) {
                for (String s : AppSettings.get("recent", "").split("\n")) if (!s.trim().isEmpty()) memory.add(s.trim());
            }
        }
        return memory;
    }

    public static synchronized void add(File f) {
        if (f == null) return;
        String p = f.getAbsolutePath();
        if (p.length() > 480) return;
        List<String> l = list();
        if (!l.isEmpty() && l.get(0).equals(p)) return;
        l.remove(p);
        l.add(0, p);
        while (l.size() > MAX) l.remove(l.size() - 1);
        save();
    }

    public static synchronized void remove(File f) {
        list().remove(f.getAbsolutePath());
        save();
    }

    public static synchronized void clear() {
        list().clear();
        save();
    }

    /** Only files that still exist. */
    public static synchronized List<File> files() {
        List<File> out = new ArrayList<>();
        for (String s : list()) {
            File f = new File(s);
            if (f.isFile()) out.add(f);
        }
        return out;
    }

    private static void save() {
        if (persist()) AppSettings.put("recent", String.join("\n", list()));
    }
}
