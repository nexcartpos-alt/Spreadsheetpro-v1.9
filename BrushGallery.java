import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/** The brush picker: a pop-up with one card per brush (instrument drawing + a live sample stroke). */
public final class BrushGallery {
    private BrushGallery() {
    }

    private static float[] wave(int w, int h) {
        int n = 36;
        float[] p = new float[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / (n - 1.0);
            p[2 * i] = (float) (12 + t * (w - 24));
            p[2 * i + 1] = (float) (h / 2.0 + Math.sin(t * Math.PI * 2.0) * h * 0.22);
        }
        return p;
    }

    private static float sampleWidth(DrawingLayer.Settings s) {
        return Math.max(2f, Math.min(s.width, 6f));
    }

    /** A live preview of the current brush, colour and thickness for the ribbon. */
    public static Icon thumbnail(DrawingLayer.Settings s, int w, int h) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.translate(x, y);
                g.setColor(Theme.alpha(Theme.ribbonFg, 28));
                g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 8, 8));
                g.setColor(Theme.alpha(Theme.ribbonFg, 90));
                g.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 8, 8));
                g.clipRect(1, 1, w - 2, h - 2);
                new DrawShape(DrawShape.Type.PEN, s.color.getRGB(), Math.min(sampleWidth(s), 4f), false, wave(w, h),
                        s.brush.ordinal()).paint(g);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return w;
            }

            @Override
            public int getIconHeight() {
                return h;
            }
        };
    }

    /** Builds the pop-up. {@code onPick} is called with the chosen brush after the pop-up closes. */
    public static JPopupMenu create(DrawingLayer.Settings draw, Consumer<Brush> onPick) {
        JPopupMenu pop = new JPopupMenu();
        pop.setBorder(BorderFactory.createLineBorder(Theme.border));
        pop.setBackground(Theme.fieldBg);
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.setBackground(Theme.fieldBg);
        box.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JLabel title = new JLabel(I18n.t("Brushes"));
        title.setFont(Theme.ui(Font.BOLD, 1));
        title.setForeground(Theme.fg);
        box.add(title, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(0, 3, 8, 8));
        grid.setOpaque(false);
        for (Brush b : Brush.values()) grid.add(new Card(b, draw, () -> {
            pop.setVisible(false);
            onPick.accept(b);
        }));
        box.add(grid, BorderLayout.CENTER);
        pop.add(box);
        return pop;
    }

    private static final class Card extends JComponent {
        private final Brush brush;
        private final DrawingLayer.Settings draw;
        private boolean hover;

        Card(Brush brush, DrawingLayer.Settings draw, Runnable pick) {
            this.brush = brush;
            this.draw = draw;
            setPreferredSize(new Dimension(132, 92));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(I18n.t(brush.description));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (contains(e.getPoint())) pick.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean sel = draw.brush == brush;
            g.setColor(hover ? Theme.headerSelBg : Theme.statusBg);
            g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 12, 12));
            g.setColor(sel ? Theme.accent : Theme.border);
            g.setStroke(new BasicStroke(sel ? 2.4f : 1f));
            g.draw(new RoundRectangle2D.Float(sel ? 1 : 0, sel ? 1 : 0, w - (sel ? 3 : 1), h - (sel ? 3 : 1), 12, 12));
            // instrument
            g.setClip(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 12, 12));
            glyph(g, brush, 6, 10, 36, draw.color);
            // sample stroke
            g.setClip(new java.awt.Rectangle(40, 4, w - 44, 46));
            float[] p = wave(w - 44, 44);
            for (int i = 0; i < p.length; i += 2) p[i] += 0;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(40, 4);
            new DrawShape(DrawShape.Type.PEN, draw.color.getRGB(), sampleWidth(draw), false, p, brush.ordinal()).paint(g2);
            g2.dispose();
            g.setClip(null);
            g.setFont(Theme.ui(sel ? Font.BOLD : Font.PLAIN, 0));
            g.setColor(Theme.fg);
            String name = I18n.t(brush.label);
            int tw = g.getFontMetrics().stringWidth(name);
            g.drawString(name, (w - tw) / 2, h - 10);
            g.dispose();
        }
    }

    /** A little drawing of the instrument (pen, pencil, marker, nib, crayon, spray can, brush, dashed pen, neon tube). */
    static void glyph(Graphics2D g0, Brush b, int x, int y, int size, Color ink) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        double s = size / 30.0;
        g.scale(s, s);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Color body = new Color(0xF2B94B);
        Color dark = new Color(0x4A4A4A);
        switch (b) {
            case PEN: { // fountain pen
                tilted(g, 15, 15, 6, 22, body, dark, 1);
                g.setColor(ink);
                g.fill(new Ellipse2D.Double(3, 24, 3.2, 3.2));
                break;
            }
            case PENCIL: // hexagon pencil with sharp tip
                tilted(g, 15, 15, 5, 22, new Color(0xF6D24A), new Color(0xE9B99A), 0);
                g.setColor(dark);
                g.fill(new Ellipse2D.Double(3, 25, 2.4, 2.4));
                break;
            case MARKER: // chunky felt tip
                tilted(g, 15, 15, 9, 20, new Color(0x4C8BF5), ink, 2);
                break;
            case CALLIGRAPHY: { // flat nib / feather
                Path2D.Double n = new Path2D.Double();
                n.moveTo(5, 27);
                n.lineTo(11, 11);
                n.lineTo(24, 3);
                n.lineTo(22, 16);
                n.closePath();
                g.setColor(new Color(0x2E3A59));
                g.fill(n);
                g.setColor(new Color(0xBFC7DA));
                g.drawLine(5, 27, 17, 10);
                break;
            }
            case CRAYON:
                tilted(g, 15, 15, 8, 18, ink, new Color(0xF3E3B3), 3);
                break;
            case SPRAY: { // spray can with a dotted cloud
                g.setColor(new Color(0x7A8794));
                g.fill(new RoundRectangle2D.Double(6, 12, 11, 17, 3, 3));
                g.setColor(dark);
                g.fill(new RoundRectangle2D.Double(8, 8, 7, 5, 2, 2));
                g.setColor(ink);
                for (int i = 0; i < 9; i++) g.fill(new Ellipse2D.Double(18 + (i % 3) * 3.4, 4 + (i / 3) * 4.4, 1.7, 1.7));
                break;
            }
            case WATERCOLOR: { // round brush with a drop
                tilted(g, 15, 15, 4.5, 19, new Color(0x8D6E63), dark, 4);
                g.setColor(ink);
                Path2D.Double d = new Path2D.Double();
                d.moveTo(5, 21);
                d.curveTo(1, 27, 6, 29, 8, 26);
                d.curveTo(9, 24, 7, 22, 5, 21);
                g.fill(d);
                break;
            }
            case DASHED:
                tilted(g, 17, 12, 6, 18, body, dark, 1);
                g.setColor(ink);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(2, 27, 6, 27);
                g.drawLine(10, 27, 14, 27);
                break;
            case NEON: { // glowing tube
                for (int i = 3; i >= 1; i--) {
                    g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 40 * (4 - i)));
                    g.setStroke(new BasicStroke(3f + i * 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(7, 24, 22, 7);
                }
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(7, 24, 22, 7);
                break;
            }
            default:
        }
        g.dispose();
    }

    /** A writing instrument lying at 45 degrees: body, tip and a coloured point. */
    private static void tilted(Graphics2D g, double cx, double cy, double width, double length,
                               Color body, Color tip, int style) {
        Graphics2D t = (Graphics2D) g.create();
        t.translate(cx, cy);
        t.rotate(Math.toRadians(45));
        double l = length / 2;
        double hw = width / 2;
        t.setColor(body);
        t.fill(new RoundRectangle2D.Double(-hw, -l, width, length - 5, 3, 3));
        t.setColor(new Color(0x00000030, true));
        t.draw(new RoundRectangle2D.Double(-hw, -l, width, length - 5, 3, 3));
        Path2D.Double cone = new Path2D.Double();
        if (style == 2) { // chisel
            cone.moveTo(-hw, l - 5);
            cone.lineTo(hw, l - 5);
            cone.lineTo(hw - 1.5, l);
            cone.lineTo(-hw + 1.5, l);
        } else if (style == 4) { // round brush bristles
            cone.moveTo(-hw, l - 5);
            cone.lineTo(hw, l - 5);
            cone.quadTo(hw, l + 1, 0, l + 3);
            cone.quadTo(-hw, l + 1, -hw, l - 5);
        } else {
            cone.moveTo(-hw, l - 5);
            cone.lineTo(hw, l - 5);
            cone.lineTo(0, l + (style == 0 ? 1 : 0));
        }
        cone.closePath();
        t.setColor(tip);
        t.fill(cone);
        t.setColor(new Color(0x00000030, true));
        t.draw(cone);
        if (style == 3) { // crayon paper wrapper lines
            t.setColor(new Color(0x00000040, true));
            t.drawLine((int) -hw, (int) (-l + 4), (int) hw, (int) (-l + 4));
            t.drawLine((int) -hw, (int) (-l + 8), (int) hw, (int) (-l + 8));
        }
        t.dispose();
    }
}
