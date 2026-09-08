package com.kenyarealestate.verification.photo;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A 64-bit perceptual hash (dHash) of an image.
 *
 * <p>SHA-256 catches the same file uploaded twice. It does not catch the fraud that
 * actually happens: a seller lifts photographs from a genuine listing, re-saves them at
 * a different quality, crops ten pixels off the edge or drops the resolution, and posts
 * them as their own property. Every one of those changes every byte, so the content
 * digest is useless — while the picture remains obviously the same picture.
 *
 * <p>dHash compares each pixel with its right-hand neighbour after reducing the image to
 * 9×8 greyscale. What survives that reduction is the structure of the scene, not its
 * encoding, so re-compression, mild cropping and resizing leave the hash largely intact
 * while a genuinely different building does not.
 *
 * <p>Chosen over aHash (too easily fooled by brightness shifts) and pHash (needs a DCT
 * for accuracy this use does not require). The comparison is Hamming distance, so
 * near-duplicates are found by threshold rather than by exact match.
 */
public final class PerceptualHash {

    /** 9 columns so that 8 left-to-right comparisons fit each row. */
    private static final int WIDTH = 9;
    private static final int HEIGHT = 8;

    private PerceptualHash() {
    }

    public static long of(BufferedImage image) {
        BufferedImage small = reduce(image);
        long hash = 0L;
        int bit = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH - 1; x++) {
                boolean brighter = luminance(small, x, y) > luminance(small, x + 1, y);
                if (brighter) {
                    hash |= (1L << bit);
                }
                bit++;
            }
        }
        return hash;
    }

    /**
     * How many bits differ between two hashes.
     *
     * <p>0 means visually identical. Under about 10 means almost certainly the same
     * photograph re-encoded or lightly edited. Above roughly 20 means different scenes.
     */
    public static int distance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /**
     * Distance at or below which two images are treated as the same photograph.
     *
     * <p>Set at 10 of 64 bits. Lower misses re-compressed copies, which is the whole
     * point; higher starts matching different units in the same block of flats, which
     * would accuse honest developers of stealing their own photographs.
     */
    public static final int DUPLICATE_THRESHOLD = 10;

    public static boolean looksLikeSameImage(long a, long b) {
        return distance(a, b) <= DUPLICATE_THRESHOLD;
    }

    private static BufferedImage reduce(BufferedImage source) {
        BufferedImage small = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
        g.dispose();
        return small;
    }

    /** Rec. 601 luma, which weights green highest as human vision does. */
    private static int luminance(BufferedImage image, int x, int y) {
        int rgb = image.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }
}
