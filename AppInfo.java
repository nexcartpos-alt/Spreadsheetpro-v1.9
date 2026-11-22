/** Name, version and where updates come from. */
public final class AppInfo {

    private AppInfo() {
    }

    /** GitHub repository that publishes the releases (owner/name). */
    public static final String REPO = "nexcartpos-alt/spreadsheet-pro";

    /**
     * The version of the running app. The release build stamps it into the jar (taken from the git tag), so it
     * never has to be edited by hand. Running from loose class files gives "dev".
     */
    public static String version() {
        if (cached == null) cached = readVersion();
        return cached;
    }

    private static String cached;

    /** Reads Implementation-Version from the manifest of the jar this class was loaded from ("dev" if not run from a jar). */
    private static String readVersion() {
        try {
            java.security.CodeSource cs = AppInfo.class.getProtectionDomain().getCodeSource();
            if (cs == null) return "dev";
            java.io.File f = new java.io.File(cs.getLocation().toURI());
            if (!f.isFile()) return "dev";
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(f)) {
                java.util.jar.Manifest m = jar.getManifest();
                String v = m == null ? null : m.getMainAttributes().getValue("Implementation-Version");
                return v == null || v.trim().isEmpty() ? "dev" : v.trim();
            }
        } catch (java.io.IOException | java.net.URISyntaxException | RuntimeException e) {
            return "dev";
        }
    }

    /** True if {@code candidate} (like "1.2.0" or "v1.2") is a higher version than {@code current}. "dev" is never updated. */
    public static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        if (a == null || b == null) return false;
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }

    /** "v1.2.3" -> {1,2,3}; missing parts are 0; null if it is not a version number. */
    static int[] parse(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("v") || t.startsWith("V")) t = t.substring(1);
        if (!t.matches("\\d+(\\.\\d+){0,2}.*")) return null;
        String[] parts = t.split("[^0-9]+");
        int[] out = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }
}
