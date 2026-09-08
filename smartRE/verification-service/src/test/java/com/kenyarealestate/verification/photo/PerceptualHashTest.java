package com.kenyarealestate.verification.photo;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The point of a perceptual hash is that it survives the things a photo thief does to
 * evade a content digest. These tests do those things to a real image and check the
 * hash holds.
 */
class PerceptualHashTest {

    @Test
    @DisplayName("survives heavy JPEG re-compression")
    void survivesRecompression() throws Exception {
        BufferedImage original = house();
        BufferedImage recompressed = jpegRoundTrip(original, 0.3f);

        int distance = PerceptualHash.distance(
                PerceptualHash.of(original), PerceptualHash.of(recompressed));

        assertThat(distance).isLessThanOrEqualTo(PerceptualHash.DUPLICATE_THRESHOLD);
        assertThat(PerceptualHash.looksLikeSameImage(
                PerceptualHash.of(original), PerceptualHash.of(recompressed))).isTrue();
    }

    @Test
    @DisplayName("survives being resized")
    void survivesResizing() {
        BufferedImage original = house();
        BufferedImage halved = resize(original, original.getWidth() / 2, original.getHeight() / 2);

        assertThat(PerceptualHash.looksLikeSameImage(
                PerceptualHash.of(original), PerceptualHash.of(halved))).isTrue();
    }

    @Test
    @DisplayName("a different building is not a match")
    void distinguishesDifferentScenes() {
        int distance = PerceptualHash.distance(
                PerceptualHash.of(house()), PerceptualHash.of(apartmentBlock()));

        assertThat(distance).isGreaterThan(PerceptualHash.DUPLICATE_THRESHOLD);
        assertThat(PerceptualHash.looksLikeSameImage(
                PerceptualHash.of(house()), PerceptualHash.of(apartmentBlock()))).isFalse();
    }

    @Test
    @DisplayName("identical images hash identically")
    void identicalImagesMatchExactly() {
        assertThat(PerceptualHash.distance(
                PerceptualHash.of(house()), PerceptualHash.of(house()))).isZero();
    }

    @Test
    void distanceIsSymmetricAndBounded() {
        long a = PerceptualHash.of(house());
        long b = PerceptualHash.of(apartmentBlock());

        assertThat(PerceptualHash.distance(a, b)).isEqualTo(PerceptualHash.distance(b, a));
        assertThat(PerceptualHash.distance(a, b)).isBetween(0, 64);
    }

    // ---------------------------------------------------------------- fixtures ---

    /** A gabled house against sky: strong horizontal structure. */
    private static BufferedImage house() {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(135, 190, 235));
        g.fillRect(0, 0, 600, 400);
        g.setColor(new Color(110, 90, 70));
        g.fillRect(120, 180, 360, 200);
        g.setColor(new Color(70, 50, 40));
        g.fillPolygon(new int[] {100, 300, 500}, new int[] {180, 60, 180}, 3);
        g.setColor(new Color(240, 240, 200));
        g.fillRect(180, 230, 70, 70);
        g.fillRect(350, 230, 70, 70);
        g.setColor(new Color(60, 110, 60));
        g.fillRect(0, 360, 600, 40);
        g.dispose();
        return image;
    }

    /** A tower block: strong vertical repetition, a genuinely different scene. */
    private static BufferedImage apartmentBlock() {
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(200, 200, 205));
        g.fillRect(0, 0, 600, 400);
        g.setColor(new Color(90, 90, 100));
        g.fillRect(60, 20, 480, 380);
        g.setColor(new Color(30, 40, 60));
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 6; col++) {
                g.fillRect(90 + col * 75, 50 + row * 50, 50, 30);
            }
        }
        g.dispose();
        return image;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static BufferedImage jpegRoundTrip(BufferedImage source, float quality) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        try (var stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(source, null, null), params);
        } finally {
            writer.dispose();
        }
        return ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
    }
}
