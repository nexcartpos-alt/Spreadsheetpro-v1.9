import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Minimal PDF 1.4 writer with no libraries: each page is a high-resolution JPEG filling a page of the given size
 * (in points). The pages look exactly like the print preview; the text is part of the picture.
 */
public final class PdfWriter {

    private PdfWriter() {
    }

    /** Writes {@code pages} (each rendered for a page of widthPt x heightPt points) to {@code file}. */
    public static void write(File file, List<BufferedImage> pages, double widthPt, double heightPt, String title)
            throws IOException {
        if (pages.isEmpty()) throw new IOException("No pages to write");
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            out.write(build(pages, widthPt, heightPt, title));
        }
    }

    public static byte[] build(List<BufferedImage> pages, double widthPt, double heightPt, String title) throws IOException {
        int n = pages.size();
        // object numbers: 1 catalog, 2 page tree, 3 info, then per page: page, content, image
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        w(buf, "%PDF-1.4\n%âãÏÓ\n");

        offsets.add(buf.size());
        w(buf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(4 + i * 3).append(" 0 R ");
        offsets.add(buf.size());
        w(buf, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>\nendobj\n");

        offsets.add(buf.size());
        w(buf, "3 0 obj\n<< /Title " + pdfString(title) + " /Producer (Spreadsheet Pro) >>\nendobj\n");

        String wp = fmt(widthPt);
        String hp = fmt(heightPt);
        for (int i = 0; i < n; i++) {
            int pageObj = 4 + i * 3;
            int contentObj = pageObj + 1;
            int imageObj = pageObj + 2;
            offsets.add(buf.size());
            w(buf, pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + wp + " " + hp + "] "
                    + "/Resources << /XObject << /Im0 " + imageObj + " 0 R >> >> /Contents " + contentObj + " 0 R >>\nendobj\n");
            String content = "q " + wp + " 0 0 " + hp + " 0 0 cm /Im0 Do Q";
            offsets.add(buf.size());
            w(buf, contentObj + " 0 obj\n<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream\nendobj\n");

            BufferedImage img = pages.get(i);
            byte[] jpeg = jpeg(img);
            offsets.add(buf.size());
            w(buf, imageObj + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + img.getWidth() + " /Height " + img.getHeight()
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpeg.length + " >>\nstream\n");
            buf.write(jpeg);
            w(buf, "\nendstream\nendobj\n");
        }

        int xref = buf.size();
        int total = offsets.size() + 1;
        StringBuilder x = new StringBuilder("xref\n0 " + total + "\n0000000000 65535 f \n");
        for (int off : offsets) x.append(String.format("%010d 00000 n \n", off));
        x.append("trailer\n<< /Size ").append(total).append(" /Root 1 0 R /Info 3 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        w(buf, x.toString());
        return buf.toByteArray();
    }

    private static byte[] jpeg(BufferedImage src) throws IOException {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.92f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }

    private static void w(ByteArrayOutputStream b, String s) throws IOException {
        b.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String pdfString(String s) {
        StringBuilder sb = new StringBuilder("(");
        for (char c : (s == null ? "" : s).toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') sb.append('\\').append(c);
            else if (c >= 32 && c < 127) sb.append(c);
            else sb.append('?');
        }
        return sb.append(')').toString();
    }
}
