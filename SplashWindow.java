import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;

/**
 * The start-up animation: a small sheet builds itself cell by cell, a column total counts up, the name slides in
 * and a progress bar runs while the real window is prepared behind it. Click it to skip.
 */
public final class SplashWindow extends JWindow {

    public static final int WIDTH = 600;
    public static final int HEIGHT = 360;
    /** Length of the animation in milliseconds. */
    public static final int DURATION = 2600;
    private static final int FADE = 260;

    private static final String[] STEPS = {"Loading the formula engine...", "Building the grid...",
            "Setting up the ribbon...", "Ready"};

    private static final int COLS = 5;
    private static final int ROWS = 4;
    private static final String[][] CELLS = {
            {"Item", "Q1", "Q2", "Q3", "Total"},
            {"North", "42", "55", "61", "158"},
            {"South", "38", "47", "52", "137"},
            {"Sum", "80", "102", "113", "295"}};

    private long start;
    private final Timer timer;
    private boolean skipped;
    private Runnable whenDone;

    private SplashWindow() {
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                double t = skipped ? 1.0 : Math.min(1.0, (System.currentTimeMillis() - start) / (double) DURATION);
                paintFrame((Graphics2D) g, getWidth(), getHeight(), t);
            }
        };
        panel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                skipped = true;
            }
        });
        setContentPane(panel);
        timer = new Timer(16, e -> tick());
    }

    private void tick() {
        long elapsed = System.currentTimeMillis() - start;
        if (skipped) elapsed = Math.max(elapsed, DURATION);
        if (elapsed >= DURATION) {
            double f = Math.min(1.0, (elapsed - DURATION) / (double) FADE);
            setOpacitySafe(1f - (float) f);
            if (f >= 1.0) {
                timer.stop();
                dispose();
                if (whenDone != null) whenDone.run();
            }
        }
        getContentPane().repaint();
    }

    private void setOpacitySafe(float o) {
        try {
            setOpacity(Math.max(0f, Math.min(1f, o)));
        } catch (UnsupportedOperationException | java.awt.IllegalComponentStateException ignored) {
            // some systems cannot fade windows: the splash then simply disappears
        }
    }

    /**
     * Shows the splash and keeps it up for {@link #DURATION} ms.
     * @param prepare  runs right after the first frame (build the main window here, but keep it hidden)
     * @param finished runs when the splash has faded away (show the main window here)
     */
    public static void play(Runnable prepare, Runnable finished) {
        SplashWindow s = new SplashWindow();
        s.whenDone = finished;
        s.start = System.currentTimeMillis();
        s.setVisible(true);
        ((javax.swing.JComponent) s.getContentPane()).paintImmediately(0, 0, WIDTH, HEIGHT);
        s.timer.start();
        if (prepare != null) javax.swing.SwingUtilities.invokeLater(prepare);
    }

    // ------------------------------------------------------------------
    // The picture (pure drawing, so tests can render any moment)
    // ------------------------------------------------------------------

    /** Renders the splash at time {@code t} (0..1 of the animation) for tests and thumbnails. */
    public static BufferedImage frame(double t) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            paintFrame(g, WIDTH, HEIGHT, t);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** Which message belongs to the progress bar at time t. */
    public static String stepText(double t) {
        double p = progress(t);
        if (p < 0.25) return STEPS[0];
        if (p < 0.55) return STEPS[1];
        if (p < 0.88) return STEPS[2];
        return STEPS[3];
    }

    /** Progress bar fill (0..1): quick at first, slower in the middle, done at the end. */
    public static double progress(double t) {
        t = clamp01(t / 0.92);
        return t * t * (3 - 2 * t);
    }

    static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double easeOutBack(double x) {
        x = clamp01(x);
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    private static double easeOutCubic(double x) {
        x = clamp01(x);
        return 1 - Math.pow(1 - x, 3);
    }

    static void paintFrame(Graphics2D g0, int w, int h, double t) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Color green = new Color(0x107C41);
            // clean white background with the faintest tint at the bottom
            g.setPaint(new GradientPaint(0, 0, Color.WHITE, 0, h, new Color(0xF3F8F5)));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(0xE2E8E4));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(0, 0, w - 1, h - 1);

            double ms = t * DURATION;
            int cx = w / 2;

            // ---- the logo: a green tile whose 3x3 grid lights up cell by cell ----
            double pop = easeOutBack(ms / 520.0);
            double size = 84;
            int cy = 118;
            if (pop > 0) {
                // soft ring that expands once when the tile lands
                double ring = clamp01((ms - 380) / 700.0);
                if (ring > 0 && ring < 1) {
                    double rr = size / 2 + 6 + 34 * easeOutCubic(ring);
                    g.setColor(withAlpha(green, (float) (0.22 * (1 - ring))));
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new java.awt.geom.Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
                }
                AffineTransform old = g.getTransform();
                g.translate(cx, cy);
                g.scale(pop, pop);
                // shadow
                g.setColor(new Color(16, 124, 65, 36));
                g.fill(new RoundRectangle2D.Double(-size / 2 + 2, -size / 2 + 8, size, size, 22, 22));
                g.setPaint(new GradientPaint(0, (float) (-size / 2), new Color(0x1E9A57), 0, (float) (size / 2), green));
                g.fill(new RoundRectangle2D.Double(-size / 2, -size / 2, size, size, 22, 22));
                double cell = 16;
                double gap = 6;
                double start = -(3 * cell + 2 * gap) / 2;
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        double local = clamp01((ms - 420 - (r + c) * 110) / 300.0);
                        if (local <= 0) continue;
                        double sc = easeOutBack(local);
                        double x = start + c * (cell + gap) + cell / 2;
                        double y = start + r * (cell + gap) + cell / 2;
                        boolean header = r == 0;
                        g.setColor(withAlpha(header ? Color.WHITE : new Color(0xDDF3E5), (float) clamp01(local * 1.5)));
                        double cs = cell * sc;
                        g.fill(new RoundRectangle2D.Double(x - cs / 2, y - cs / 2, cs, cs, 5, 5));
                    }
                }
                g.setTransform(old);
            }

            // ---- name ----
            double tt = easeOutCubic((ms - 900) / 700.0);
            if (tt > 0) {
                String title = "Spreadsheet Pro";
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
                FontMetrics fm = g.getFontMetrics();
                int ty = 232 + (int) ((1 - tt) * 14);
                g.setColor(withAlpha(new Color(0x1B2B22), (float) tt));
                g.drawString(title, cx - fm.stringWidth(title) / 2, ty);
                double st = easeOutCubic((ms - 1200) / 700.0);
                if (st > 0) {
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                    fm = g.getFontMetrics();
                    String sub = "Formulas, charts, pivots and macros";
                    g.setColor(withAlpha(new Color(0x6B7A71), (float) st));
                    g.drawString(sub, cx - fm.stringWidth(sub) / 2, ty + 24);
                }
            }

            // ---- thin progress line ----
            int bw = 180;
            int bx = cx - bw / 2;
            int by = h - 52;
            g.setColor(new Color(0xE3EAE5));
            g.fill(new RoundRectangle2D.Double(bx, by, bw, 3, 3, 3));
            double p = progress(t);
            g.setColor(green);
            g.fill(new RoundRectangle2D.Double(bx, by, bw * p, 3, 3, 3));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            FontMetrics sf = g.getFontMetrics();
            String step = stepText(t);
            g.setColor(new Color(0x8A978F));
            g.drawString(step, cx - sf.stringWidth(step) / 2, by + 22);
        } finally {
            g.dispose();
        }
    }

    private static Color withAlpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, Math.round(a * 255))));
    }
}
