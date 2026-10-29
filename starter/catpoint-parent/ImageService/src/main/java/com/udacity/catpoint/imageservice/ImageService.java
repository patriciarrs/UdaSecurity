package com.udacity.catpoint.imageservice;

import java.awt.image.BufferedImage;

/**
 * Analyzes images for cats.
 */
public interface ImageService {
    boolean imageContainsCat(BufferedImage image, float confidenceThreshold);
}
