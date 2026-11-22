import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** A saved link between a block of cells and an outside data source, so it can be refreshed with one click. */
public final class DataConnection {

    public enum Kind {
        TEXT("Text / CSV file"), JSON("JSON file"), WEB("Web address (URL)"), DATABASE("Database (JDBC)");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public Kind kind = Kind.TEXT;
    public String name = "";
    /** File path, web address or JDBC url. */
    public String source = "";
    /** Text files: delimiter (empty = detect). */
    public String delimiter = "";
    public boolean header = true;
    /** JSON / web: dotted path to the list, e.g. data.items (empty = detect). */
    public String path = "";
    /** Web pages with several tables: which one (1 = first). */
    public int tableIndex = 1;
    /** Database: the SELECT query, the driver .jar and the user name (the password is never saved). */
    public String query = "";
    public String driverJar = "";
    public String user = "";
    /** Where the data starts on the sheet, and how much room the last import used (so a refresh can clear it). */
    public int row, col, lastRows, lastCols;
    public long refreshed;

    public String title() {
        return name == null || name.isEmpty() ? kind.label + ": " + source : name;
    }

    public DataConnection copy() {
        DataConnection d = new DataConnection();
        d.kind = kind; d.name = name; d.source = source; d.delimiter = delimiter; d.header = header; d.path = path;
        d.tableIndex = tableIndex; d.query = query; d.driverJar = driverJar; d.user = user;
        d.row = row; d.col = col; d.lastRows = lastRows; d.lastCols = lastCols; d.refreshed = refreshed;
        return d;
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeByte(kind.ordinal());
        s(out, name); s(out, source); s(out, delimiter); out.writeBoolean(header); s(out, path);
        out.writeInt(tableIndex); s(out, query); s(out, driverJar); s(out, user);
        out.writeInt(row); out.writeInt(col); out.writeInt(lastRows); out.writeInt(lastCols); out.writeLong(refreshed);
    }

    public static DataConnection read(DataInputStream in) throws IOException {
        DataConnection d = new DataConnection();
        int k = in.readUnsignedByte();
        Kind[] ks = Kind.values();
        if (k >= ks.length) throw new IOException("Invalid data connection");
        d.kind = ks[k];
        d.name = r(in); d.source = r(in); d.delimiter = r(in); d.header = in.readBoolean(); d.path = r(in);
        d.tableIndex = in.readInt(); d.query = r(in); d.driverJar = r(in); d.user = r(in);
        d.row = in.readInt(); d.col = in.readInt(); d.lastRows = in.readInt(); d.lastCols = in.readInt(); d.refreshed = in.readLong();
        if (d.row < 0 || d.col < 0 || d.lastRows < 0 || d.lastCols < 0 || d.tableIndex < 1) throw new IOException("Invalid data connection");
        return d;
    }

    private static void s(DataOutputStream out, String v) throws IOException {
        byte[] b = (v == null ? "" : v).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    private static String r(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 100_000) throw new IOException("Invalid data connection text");
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }
}
