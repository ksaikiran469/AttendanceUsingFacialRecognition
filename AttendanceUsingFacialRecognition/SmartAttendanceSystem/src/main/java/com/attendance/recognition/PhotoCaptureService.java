package com.attendance.recognition;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;
import java.util.function.Consumer;

public class PhotoCaptureService {
    private final Consumer<ImageIcon> preview;
    private final Consumer<String> status;
    private volatile boolean running;
    private Thread worker;
    private final Object lock = new Object();
    private Mat latestFace;

    public PhotoCaptureService(Consumer<ImageIcon> preview, Consumer<String> status) {
        this.preview = preview; this.status = status;
    }
    private void message(String s) { SwingUtilities.invokeLater(() -> status.accept(s)); }
    public void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::loop, "registration-camera");
        worker.setDaemon(true);
        worker.start();
    }
    private void loop() {
        Path xml = null;
        VideoCapture cap = new VideoCapture();
        Mat frame = new Mat(); Mat gray = new Mat(); MatOfRect faces = new MatOfRect();
        try {
            xml = Files.createTempFile("attendance-face-", ".xml");
            try (InputStream input = getClass().getResourceAsStream("/haarcascade_frontalface_default.xml")) {
                if (input == null) throw new IllegalStateException("Haar Cascade file missing");
                Files.copy(input, xml, StandardCopyOption.REPLACE_EXISTING);
            }
            CascadeClassifier detector = new CascadeClassifier(xml.toString());
            if (detector.empty()) throw new IllegalStateException("Face detector could not load");
            if (!cap.open(0)) throw new IllegalStateException("Webcam unavailable. Close the dashboard camera and other camera apps.");
            message("Camera ready. Center one face, then click Capture Photo.");
            while (running && cap.read(frame)) {
                if (frame.empty()) continue;
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.equalizeHist(gray, gray);
                detector.detectMultiScale(gray, faces, 1.1, 5, 0, new Size(80,80), new Size());
                Rect[] found = faces.toArray();
                synchronized (lock) {
                    if (latestFace != null) { latestFace.release(); latestFace = null; }
                    if (found.length == 1) {
                        Rect r = found[0];
                        latestFace = new Mat(gray, r).clone();
                        Imgproc.rectangle(frame, r.tl(), r.br(), new Scalar(50,220,80), 2);
                    }
                }
                BufferedImage image = new BufferedImage(frame.cols(), frame.rows(), BufferedImage.TYPE_3BYTE_BGR);
                frame.get(0,0,((DataBufferByte)image.getRaster().getDataBuffer()).getData());
                SwingUtilities.invokeLater(() -> { if (running) preview.accept(new ImageIcon(image)); });
                Thread.sleep(80);
            }
        } catch (Exception e) { message("ERROR: " + e.getMessage()); }
        finally {
            running = false;
            cap.release(); frame.release(); gray.release(); faces.release();
            synchronized (lock) { if (latestFace != null) { latestFace.release(); latestFace = null; } }
            if (xml != null) try { Files.deleteIfExists(xml); } catch (Exception ignored) { }
        }
    }
    public Path saveFace(String id, String name) throws Exception {
        Mat face;
        synchronized (lock) {
            if (!running || latestFace == null) throw new IllegalStateException("Exactly one detectable face must be visible in the camera.");
            face = latestFace.clone();
        }
        try {
            Path dir = Path.of("data", "students", id);
            Files.createDirectories(dir);
            Path metadata = dir.resolve("student.txt");
            if (Files.exists(metadata)) {
                String old = Files.readString(metadata);
                if (!old.equals(name)) throw new IllegalStateException("Student ID already belongs to a different name.");
            } else Files.writeString(metadata, name, StandardOpenOption.CREATE_NEW);
            Mat resized = new Mat();
            try {
                Imgproc.resize(face, resized, new Size(200,200));
                Path file = dir.resolve("face-" + UUID.randomUUID() + ".png");
                if (!Imgcodecs.imwrite(file.toString(), resized)) throw new IllegalStateException("Could not save image");
                return file;
            } finally { resized.release(); }
        } finally { face.release(); }
    }
    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }
}
