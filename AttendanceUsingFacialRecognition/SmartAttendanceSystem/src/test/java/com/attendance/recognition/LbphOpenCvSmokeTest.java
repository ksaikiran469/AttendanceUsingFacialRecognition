package com.attendance.recognition;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.bytedeco.opencv.global.opencv_core.CV_32SC1;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LbphOpenCvSmokeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void trainsPredictsAndReloadsLbphModel() {
        nu.pattern.OpenCV.loadLocally();
        try (Mat first = image(false);
             Mat second = image(true);
             MatVector images = new MatVector(first, second);
             IntPointer labelData = new IntPointer(4, 9);
             Mat labels = new Mat(2, 1, CV_32SC1, labelData);
             LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create()) {
            recognizer.train(images, labels);

            int[] predictedLabel = {-1};
            double[] distance = {Double.MAX_VALUE};
            recognizer.predict(first, predictedLabel, distance);
            assertEquals(4, predictedLabel[0]);
            assertEquals(0.0, distance[0], 0.001);

            Path modelPath = temporaryDirectory.resolve("lbph-smoke-test.yml");
            recognizer.write(modelPath.toString());
            try (LBPHFaceRecognizer reloaded = LBPHFaceRecognizer.create()) {
                reloaded.read(modelPath.toString());
                reloaded.predict(first, predictedLabel, distance);
                assertEquals(4, predictedLabel[0]);
                assertEquals(0.0, distance[0], 0.001);
            }
        }
    }

    private static Mat image(boolean inverse) {
        int width = 64;
        byte[] pixels = new byte[width * width];
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < width; x++) {
                boolean white = ((x / 8) + (y / 8)) % 2 == 0;
                pixels[y * width + x] = (byte) ((white ^ inverse) ? 255 : 0);
            }
        }

        Mat image = new Mat(width, width, CV_8UC1);
        try (BytePointer data = image.data()) {
            data.put(pixels, 0, pixels.length);
        } catch (RuntimeException ex) {
            image.close();
            throw ex;
        }
        return image;
    }
}
