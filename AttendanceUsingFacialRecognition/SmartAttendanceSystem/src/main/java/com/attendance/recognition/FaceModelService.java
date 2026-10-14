package com.attendance.recognition;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;

import java.nio.file.*;
import java.util.*;

public final class FaceModelService {
    private static final Path ROOT = Path.of("data", "students");
    private static final Path MODEL = Path.of("data", "models", "lbph-model.yml");
    private static final Path LABELS = Path.of("data", "models", "labels.tsv");
    private FaceModelService() {}

    public static String train() throws Exception {
        if (!Files.isDirectory(ROOT)) throw new IllegalStateException("No student photos found. Register students first.");
        List<org.bytedeco.opencv.opencv_core.Mat> photos = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        try {
            List<Path> dirs;
            try (var stream = Files.list(ROOT)) { dirs = stream.filter(Files::isDirectory).sorted().toList(); }
            int label = 0;
            for (Path dir : dirs) {
                Path metadata = dir.resolve("student.txt");
                if (!Files.isRegularFile(metadata)) continue;
                String studentId = dir.getFileName().toString();
                String name = Files.readString(metadata).trim();
                if (name.isEmpty() || name.contains("\t") || name.contains("\n")) continue;
                List<Path> images;
                try (var stream = Files.list(dir)) { images = stream.filter(p -> p.getFileName().toString().endsWith(".png")).sorted().toList(); }
                int count = 0;
                for (Path file : images) {
                    Mat image = Imgcodecs.imread(file.toString(), Imgcodecs.IMREAD_GRAYSCALE);
                    if (image.empty()) { image.release(); continue; }
                    Mat normalized = new Mat();
                    try {
                        Imgproc.resize(image, normalized, new Size(200, 200));
                        photos.add(toContribMat(normalized));
                        ids.add(label);
                        count++;
                    } finally {
                        image.release();
                        normalized.release();
                    }
                }
                if (count > 0) { labels.add(label + "\t" + studentId + "\t" + name); label++; }
            }
            if (photos.size() < 2) throw new IllegalStateException("Capture at least 2 face photos before training (10 per student recommended).");

            try (MatVector trainingImages = new MatVector(photos.size());
                 IntPointer nativeLabels = new IntPointer(ids.stream().mapToInt(Integer::intValue).toArray());
                 org.bytedeco.opencv.opencv_core.Mat imageLabels =
                     new org.bytedeco.opencv.opencv_core.Mat(ids.size(), 1, org.bytedeco.opencv.global.opencv_core.CV_32SC1, nativeLabels);
                 LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create()) {
                for (int i = 0; i < photos.size(); i++) trainingImages.put(i, photos.get(i));
                Files.createDirectories(MODEL.getParent());
                recognizer.train(trainingImages, imageLabels);
                recognizer.write(MODEL.toString());
                Files.write(LABELS, labels);
            }
            return "Trained on " + photos.size() + " images from " + labels.size() + " students.";
        } finally {
            for (org.bytedeco.opencv.opencv_core.Mat photo : photos) photo.close();
        }
    }

    public static LoadedModel load() throws Exception {
        if (!Files.isRegularFile(MODEL) || !Files.isRegularFile(LABELS))
            throw new IllegalStateException("No trained model found. Click Train Model first.");
        Map<Integer, String> names = new HashMap<>();
        for (String line : Files.readAllLines(LABELS)) {
            String[] parts = line.split("\t", 3);
            if (parts.length == 3) names.put(Integer.parseInt(parts[0]), parts[1] + " - " + parts[2]);
        }
        LBPHFaceRecognizer recognizer = LBPHFaceRecognizer.create();
        try {
            recognizer.read(MODEL.toString());
            return new LoadedModel(recognizer, Map.copyOf(names));
        } catch (Exception ex) {
            recognizer.close();
            throw ex;
        }
    }

    private static org.bytedeco.opencv.opencv_core.Mat toContribMat(Mat image) {
        if (image.empty() || image.type() != CvType.CV_8UC1)
            throw new IllegalArgumentException("LBPH requires a non-empty 8-bit grayscale image.");
        byte[] pixels = new byte[(int) (image.total() * image.channels())];
        image.get(0, 0, pixels);
        org.bytedeco.opencv.opencv_core.Mat converted =
            new org.bytedeco.opencv.opencv_core.Mat(image.rows(), image.cols(), org.bytedeco.opencv.global.opencv_core.CV_8UC1);
        try (BytePointer destination = converted.data()) {
            destination.put(pixels, 0, pixels.length);
        } catch (RuntimeException ex) {
            converted.close();
            throw ex;
        }
        return converted;
    }

    public record LoadedModel(LBPHFaceRecognizer recognizer, Map<Integer, String> names) implements AutoCloseable {
        public String predict(Mat face) {
            org.bytedeco.opencv.opencv_core.Mat contribFace = toContribMat(face);
            try {
                int[] label = {-1};
                double[] distance = {Double.MAX_VALUE};
                recognizer.predict(contribFace, label, distance);
                // LBPH distance is not a calibrated probability. Conservative starting cutoff.
                if (label[0] < 0 || distance[0] > 65 || !names.containsKey(label[0])) return "Unknown";
                return names.get(label[0]);
            } finally {
                contribFace.close();
            }
        }

        @Override public void close() { recognizer.close(); }
    }
}
