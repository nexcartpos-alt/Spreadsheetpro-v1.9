import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The freehand brush styles of the Draw tab. Each one paints the same stroke differently. */
public enum Brush {
    PEN("Pen", "Clean, solid line", 1f),
    PENCIL("Pencil", "Thin graphite line with a grainy edge", 1f),
    MARKER("Marker", "Thick, nearly solid felt-tip", 2f),
    CALLIGRAPHY("Calligraphy", "Angled flat nib: thick and thin strokes", 2f),
    CRAYON("Crayon", "Rough, waxy texture", 1.8f),
    SPRAY("Spray", "Airbrush made of tiny dots", 5f),
    WATERCOLOR("Watercolor", "Soft, see-through layers", 2.8f),
    DASHED("Dashed", "Broken line", 1f),
    NEON("Neon", "Glowing line", 3.2f);

    public final String label;
    public final String description;
    /** How much wider than the chosen thickness the ink spreads (used for erasing and the brush ring). */
    public final float spread;

    Brush(String label, String description, float spread) {
        this.label = label;
        this.description = description;
        this.spread = spread;
    }

    public static Brush of(int ordinal) {
        Brush[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PEN;
    }

    private static Color alpha(Color c, int a) {
        int base = c.getAlpha();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a * base / 255)));
    }

    private static Color lighten(Color c, float f) {
        return new Color(Math.round(c.getRed() + (255 - c.getRed()) * f), Math.round(c.getGreen() + (255 - c.getGreen()) * f),
                Math.round(c.getBlue() + (255 - c.getBlue()) * f), c.getAlpha());
    }

    /** Points along the path, about {@code step} apart. */
    private static List<float[]> sample(Shape path, double step) {
        List<float[]> out = new ArrayList<>();
        PathIterator it = new FlatteningPathIterator(path.getPathIterator(new AffineTransform()), 0.4);
        double[] c = new double[6];
        double px = 0, py = 0;
        boolean first = true;
        while (!it.isDone()) {
            int t = it.currentSegment(c);
            if (t == PathIterator.SEG_MOVETO) {
                px = c[0];
                py = c[1];
                out.add(new float[]{(float) px, (float) py});
                first = false;
            } else if (t == PathIterator.SEG_LINETO && !first) {
                double dx = c[0] - px;
                double dy = c[1] - py;
                double len = Math.hypot(dx, dy);
                int n = (int) Math.floor(len / step);
                for (int i = 1; i <= n; i++) out.add(new float[]{(float) (px + dx * i / (n + 1.0)), (float) (py + dy * i / (n + 1.0))});
                out.add(new float[]{(float) c[0], (float) c[1]});
                px = c[0];
                py = c[1];
            }
            it.next();
        }
        return out;
    }

    private static Random seed(DrawShape s) {
        long h = 17;
        for (float f : s.pts) h = h * 31 + Float.floatToIntBits(f);
        return new Random(h);
    }

    /** Paints a freehand stroke (the path is given by {@code s}) in this brush style. */
    public void paint(Graphics2D g, DrawShape s) {
        Color c = new Color(s.argb, true);
        float w = s.width;
        Shape path = s.shape();
        BasicStroke round = new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        switch (this) {
            case PENCIL: {
                g.setColor(alpha(c, 175));
                g.setStroke(new BasicStroke(Math.max(1f, w * 0.75f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(path);
                Random r = seed(s);
                g.setColor(alpha(c, 110));
                for (float[] p : sample(path, 1.6)) {
                    for (int k = 0; k < 2; k++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        double d = r.nextDouble() * w * 0.75;
                        g.fill(new Ellipse2D.Double(p[0] + Math.cos(a) * d, p[1] + Math.sin(a) * d, 1, 1));
                    }
                }
                break;
            }
            case MARKER:
                g.setColor(alpha(c, 215));
                g.setStroke(new BasicStroke(w * 2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
                g.draw(path);
                break;
            case CALLIGRAPHY: {
                double nib = Math.max(3, w * 2.2);
                double nx = Math.cos(Math.toRadians(45)) * nib / 2;
                double ny = -Math.sin(Math.toRadians(45)) * nib / 2;
                g.setColor(c);
                List<float[]> pts = sample(path, 1.2);
                Path2D.Double all = new Path2D.Double(Path2D.WIND_NON_ZERO);
                for (int i = 0; i < pts.size(); i++) {
                    float[] a = pts.get(i);
                    float[] b = pts.get(Math.min(i + 1, pts.size() - 1));
                    all.moveTo(a[0] + nx, a[1] + ny);
                    all.lineTo(a[0] - nx, a[1] - ny);
                    all.lineTo(b[0] - nx, b[1] - ny);
                    all.lineTo(b[0] + nx, b[1] + ny);
                    all.closePath();
                }
                g.fill(all);
                break;
            }
            case CRAYON: {
                Random r = seed(s);
                for (float[] p : sample(path, 1.0)) {
                    for (int k = 0; k < 3; k++) {
                        if (r.nextFloat() < 0.45f) continue;
                        double jx = (r.nextDouble() - 0.5) * w * 1.6;
                        double jy = (r.nextDouble() - 0.5) * w * 1.6;
                        double d = Math.max(1.2, w * (0.45 + r.nextDouble() * 0.5));
                        g.setColor(alpha(c, 120 + r.nextInt(100)));
                        g.fill(new Ellipse2D.Double(p[0] + jx - d / 2, p[1] + jy - d / 2, d, d));
                    }
                }
                break;
            }
            case SPRAY: {
                Random r = seed(s);
                double radius = Math.max(4, w * 2.5);
                for (float[] p : sample(path, 2.0)) {
                    for (int k = 0; k < 6; k++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        double d = radius * Math.sqrt(r.nextDouble());
                        g.setColor(alpha(c, 90 + r.nextInt(120)));
                        g.fill(new Ellipse2D.Double(p[0] + Math.cos(a) * d, p[1] + Math.sin(a) * d, 1.4, 1.4));
                    }
                }
                break;
            }
            case WATERCOLOR: {
                float[] widths = {w * 2.8f, w * 2.0f, w * 1.2f};
                int[] alphas = {38, 48, 62};
                for (int i = 0; i < 3; i++) {
                    g.setColor(alpha(c, alphas[i]));
                    g.setStroke(new BasicStroke(widths[i], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(path);
                }
                break;
            }
            case DASHED:
                g.setColor(c);
                g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                        new float[]{Math.max(1f, w * 2.2f), Math.max(2f, w * 1.8f)}, 0f));
                g.draw(path);
                break;
            case NEON: {
                float[] widths = {w * 3.2f, w * 2.2f, w * 1.4f};
                int[] alphas = {32, 60, 120};
                for (int i = 0; i < 3; i++) {
                    g.setColor(alpha(c, alphas[i]));
                    g.setStroke(new BasicStroke(widths[i], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(path);
                }
                g.setColor(lighten(c, 0.75f));
                g.setStroke(new BasicStroke(Math.max(1f, w * 0.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(path);
                break;
            }
            default:
                g.setColor(c);
                g.setStroke(round);
                g.draw(path);
        }
    }
}
