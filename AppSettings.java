import java.util.prefs.Preferences;

/** Appearance and language choices, remembered between runs (java.util.prefs). */
public final class AppSettings {
    private static final Preferences P = Preferences.userRoot().node("spreadsheet-pro-appearance");

    private AppSettings() {
    }

    public static String get(String key, String def) {
        try {
            return P.get(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void put(String key, String value) {
        try {
            P.put(key, value);
            P.flush();
        } catch (Exception ignored) {
            // settings are a convenience only
        }
    }

    public static int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Reads everything into {@link Theme} and {@link I18n}; call once before any window is created. */
    public static void load() {
        if (Boolean.getBoolean("spx.noprefs")) return;
        Theme.set(Theme.byId(get("theme", "light")));
        Theme.setUiFont(get("uiFamily", Theme.DEFAULT_UI_FAMILY), getInt("uiSize", Theme.DEFAULT_UI_SIZE));
        Theme.setCellFont(get("cellFamily", Theme.DEFAULT_CELL_FAMILY), getInt("cellSize", Theme.DEFAULT_CELL_SIZE));
        I18n.setLanguage(get("lang", "en"));
        Theme.setSmooth(!"false".equals(get("smooth", "true")));
    }

    public static void save() {
        if (Boolean.getBoolean("spx.noprefs")) return;
        put("theme", Theme.current().id);
        put("uiFamily", Theme.uiFamily());
        put("uiSize", String.valueOf(Theme.uiSize()));
        put("cellFamily", Theme.cellFamily());
        put("cellSize", String.valueOf(Theme.cellSize()));
        put("lang", I18n.code());
        put("smooth", String.valueOf(Theme.smooth()));
    }
}
