import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks on GitHub for a newer release. It only reads the public "latest release" page of the repository, sends no
 * personal data and never installs anything: the user is shown the news and chooses to download.
 */
public final class UpdateChecker {

    /** What the check found. */
    public static final class Result {
        public final boolean newer;
        public final String current;
        public final String latest;
        public final String notes;
        public final String pageUrl;
        /** Direct download for this computer's system, or the release page if no file matches. */
        public final String downloadUrl;

        Result(boolean newer, String current, String latest, String notes, String pageUrl, String downloadUrl) {
            this.newer = newer;
            this.current = current;
            this.latest = latest;
            this.notes = notes;
            this.pageUrl = pageUrl;
            this.downloadUrl = downloadUrl;
        }
    }

    private static final long CHECK_EVERY_MS = 24L * 60 * 60 * 1000;
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern PAGE = Pattern.compile("\"html_url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern BODY = Pattern.compile("\"body\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern ASSET = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private UpdateChecker() {
    }

    /** Where the latest release is read from; tests can point it elsewhere with -Dspx.update.url=... */
    static String apiUrl() {
        String o = System.getProperty("spx.update.url");
        return o != null && !o.isEmpty() ? o : "https://api.github.com/repos/" + AppInfo.REPO + "/releases/latest";
    }

    /** Asks GitHub (blocking, a few seconds at most). @throws IOException if it cannot be reached or understood */
    public static Result check(String currentVersion) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(apiUrl()).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "SpreadsheetPro/" + currentVersion);
        int code = c.getResponseCode();
        if (code == 404) throw new IOException("No release has been published yet.");
        if (code != 200) throw new IOException("GitHub answered with code " + code + ".");
        String json;
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 2_000_000) throw new IOException("The answer was too large.");
                bos.write(buf, 0, n);
            }
            json = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
        return parse(json, currentVersion, System.getProperty("os.name", ""));
    }

    /** Reads the GitHub release JSON (public so tests can feed it text). */
    static Result parse(String json, String currentVersion, String osName) throws IOException {
        String tag = first(TAG, json);
        if (tag == null) throw new IOException("The answer from GitHub was not understood.");
        String page = first(PAGE, json);
        String body = first(BODY, json);
        List<String> assets = new ArrayList<>();
        Matcher m = ASSET.matcher(json);
        while (m.find()) assets.add(unescape(m.group(1)));
        String download = pickAsset(assets, osName);
        if (download == null) download = page != null ? page : "https://github.com/" + AppInfo.REPO + "/releases/latest";
        if (page == null) page = "https://github.com/" + AppInfo.REPO + "/releases/latest";
        return new Result(AppInfo.isNewer(tag, currentVersion), currentVersion, tag.replaceFirst("^[vV]", ""),
                body == null ? "" : body, page, download);
    }

    /** The release file made for this operating system, or null. */
    static String pickAsset(List<String> urls, String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        String want = os.contains("win") ? "windows" : os.contains("mac") || os.contains("darwin") ? "macos" : "linux";
        for (String u : urls) if (u.toLowerCase(Locale.ROOT).contains(want)) return u;
        return null;
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? unescape(m.group(1)) : null;
    }

    /** Undoes JSON string escapes (newline, quote, backslash, slash and 4-digit hex codes). */
    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '\\' || i + 1 >= s.length()) {
                sb.append(ch);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            sb.append('u');
                        }
                    }
                    break;
                default:
                    sb.append(n);
            }
        }
        return sb.toString();
    }

    // ---- when to check, what the user said no to ----

    private static Preferences prefs() {
        return Preferences.userRoot().node("spreadsheet-pro-updates");
    }

    /** True if the automatic start-up check is allowed now (switch it off with -Dspx.noupdate=true). */
    public static boolean dueForAutoCheck() {
        if (Boolean.getBoolean("spx.noupdate") || "dev".equals(AppInfo.version())) return false;
        try {
            Preferences p = prefs();
            if (!p.getBoolean("auto", true)) return false;
            return System.currentTimeMillis() - p.getLong("last", 0) > CHECK_EVERY_MS;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void markChecked() {
        try {
            prefs().putLong("last", System.currentTimeMillis());
        } catch (RuntimeException ignored) {
            // preferences unavailable: the check simply runs again next time
        }
    }

    public static boolean isSkipped(String version) {
        try {
            return version.equals(prefs().get("skip", ""));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void skip(String version) {
        try {
            prefs().put("skip", version);
        } catch (RuntimeException ignored) {
            // nothing to do
        }
    }

    public static boolean autoCheckEnabled() {
        try {
            return prefs().getBoolean("auto", true);
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static void setAutoCheck(boolean on) {
        try {
            prefs().putBoolean("auto", on);
        } catch (RuntimeException ignored) {
            // nothing to do
        }
    }
}
