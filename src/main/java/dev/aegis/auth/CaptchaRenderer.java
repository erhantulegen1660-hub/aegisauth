package dev.aegis.auth;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Random;

public class CaptchaRenderer extends MapRenderer {

    private static final int SIZE = 128;
    private final String code;
    private final int noiseLines;
    private final int noiseDots;
    private final int maxTiltDegrees;
    private boolean rendered = false;

    public CaptchaRenderer(String code, int noiseLines, int noiseDots, int maxTiltDegrees) {
        super(false);
        this.code = code;
        this.noiseLines = Math.max(0, noiseLines);
        this.noiseDots = Math.max(0, noiseDots);
        this.maxTiltDegrees = Math.max(0, maxTiltDegrees);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }
        BufferedImage image = buildImage();
        canvas.drawImage(0, 0, image);
        rendered = true;
    }

    private BufferedImage buildImage() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Random rnd = new Random();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(246, 242, 252));
        g.fillRect(0, 0, SIZE, SIZE);

        g.setColor(new Color(138, 79, 255));
        g.setStroke(new BasicStroke(3f));
        g.drawRect(3, 3, SIZE - 6, SIZE - 6);

        for (int i = 0; i < noiseDots; i++) {
            int x = rnd.nextInt(SIZE - 8) + 4;
            int y = rnd.nextInt(SIZE - 8) + 4;
            g.setColor(new Color(
                    110 + rnd.nextInt(120),
                    80 + rnd.nextInt(120),
                    170 + rnd.nextInt(85),
                    130
            ));
            int dotSize = 1 + rnd.nextInt(2);
            g.fillRect(x, y, dotSize, dotSize);
        }

        for (int i = 0; i < noiseLines; i++) {
            int x1 = rnd.nextInt(SIZE);
            int y1 = rnd.nextInt(SIZE);
            int x2 = rnd.nextInt(SIZE);
            int y2 = rnd.nextInt(SIZE);
            g.setColor(new Color(140, 95, 235, 95));
            g.setStroke(new BasicStroke(1.2f + rnd.nextFloat() * 1.2f));
            g.drawLine(x1, y1, x2, y2);
        }

        Font font = new Font("SansSerif", Font.BOLD, code.length() <= 5 ? 34 : 30);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        Color[] digitColors = {
                new Color(45, 16, 95),
                new Color(88, 28, 165),
                new Color(25, 42, 110),
                new Color(112, 34, 148)
        };

        if (code.length() <= 5) {
            int totalWidth = 0;
            for (int i = 0; i < code.length(); i++) {
                totalWidth += fm.charWidth(code.charAt(i)) + 3;
            }
            int cursorX = Math.max(10, (SIZE - totalWidth) / 2);
            int baseY = SIZE / 2 + fm.getAscent() / 2 - 2;

            for (int i = 0; i < code.length(); i++) {
                String ch = String.valueOf(code.charAt(i));
                int charW = fm.stringWidth(ch);
                int yOffset = rnd.nextInt(9) - 4;
                double angle = Math.toRadians((rnd.nextDouble() * 2 - 1) * maxTiltDegrees);

                AffineTransform old = g.getTransform();
                g.translate(cursorX + charW / 2.0, baseY + yOffset);
                g.rotate(angle);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));
                g.setColor(digitColors[i % digitColors.length]);
                g.drawString(ch, -charW / 2f, 0);
                g.setTransform(old);

                cursorX += charW + 3;
            }
        } else {
            String top = code.substring(0, 3);
            String bottom = code.substring(3);
            drawRow(g, fm, top, 46, digitColors, rnd);
            drawRow(g, fm, bottom, 96, digitColors, rnd);
        }

        g.dispose();
        return img;
    }

    private void drawRow(Graphics2D g, FontMetrics fm, String row, int yCenter, Color[] palette, Random rnd) {
        int totalW = 0;
        for (int i = 0; i < row.length(); i++) {
            totalW += fm.charWidth(row.charAt(i)) + 4;
        }
        int x = Math.max(10, (SIZE - totalW) / 2);
        for (int i = 0; i < row.length(); i++) {
            String ch = String.valueOf(row.charAt(i));
            int w = fm.stringWidth(ch);
            double angle = Math.toRadians((rnd.nextDouble() * 2 - 1) * maxTiltDegrees);
            AffineTransform old = g.getTransform();
            g.translate(x + w / 2.0, yCenter + (rnd.nextInt(7) - 3));
            g.rotate(angle);
            g.setColor(palette[i % palette.length]);
            g.drawString(ch, -w / 2f, 0);
            g.setTransform(old);
            x += w + 4;
        }
    }
}
