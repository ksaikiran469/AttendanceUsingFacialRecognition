package com.attendance.recognition;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class RecognitionCameraService {
    private final Consumer<ImageIcon> preview;
    private final Consumer<String> status;
    private final boolean markAttendance;
    private final java.util.Map<String,Integer> consecutive = new java.util.HashMap<>();
    private final java.util.Set<String> recorded = new java.util.HashSet<>();
    private volatile boolean running;
    private Thread worker;
    public RecognitionCameraService(Consumer<ImageIcon> preview, Consumer<String> status) {
        this(preview,status,false);
    }
    public RecognitionCameraService(Consumer<ImageIcon> preview, Consumer<String> status, boolean markAttendance) {
        this.preview = preview; this.status = status; this.markAttendance = markAttendance;
    }
    private void message(String value) { SwingUtilities.invokeLater(() -> status.accept(value)); }
    public void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::loop, "recognition-camera");
        worker.setDaemon(true);
        worker.start();
    }
    private void loop() {
        Path xml = null;
        VideoCapture cap = new VideoCapture();
        Mat frame = new Mat(); Mat gray = new Mat(); MatOfRect faces = new MatOfRect();
        FaceModelService.LoadedModel model = null;
        try {
            model = FaceModelService.load();
            xml = Files.createTempFile("attendance-recognizer-", ".xml");
            try (InputStream in = getClass().getResourceAsStream("/haarcascade_frontalface_default.xml")) {
                if (in == null) throw new IllegalStateException("Haar Cascade resource missing");
                Files.copy(in, xml, StandardCopyOption.REPLACE_EXISTING);
            }
            CascadeClassifier detector = new CascadeClassifier(xml.toString());
            if (detector.empty()) throw new IllegalStateException("Could not load face detector");
            if (!cap.open(0, Videoio.CAP_DSHOW)) throw new IllegalStateException("Webcam unavailable. Close other camera windows.");
            message("Recognition started. Look toward the webcam.");
            while (running && cap.read(frame)) {
                if (frame.empty()) continue;
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.equalizeHist(gray, gray);
                detector.detectMultiScale(gray, faces, 1.1, 5, 0, new Size(80,80), new Size());
                Set<String> recognizedThisFrame = new HashSet<>();
                for (Rect r : faces.toArray()) {
                    Mat crop = new Mat(gray, r);
                    Mat resized = new Mat();
                    String name;
                    try { Imgproc.resize(crop, resized, new Size(200,200)); name = model.predict(resized); }
                    finally { crop.release(); resized.release(); }
                    if (markAttendance && !name.equals("Unknown")) {
                        String[] parts = name.split(" - ",2);
                        if (parts.length == 2 && recognizedThisFrame.add(parts[0])) {
                            int seen = consecutive.merge(parts[0], 1, Integer::sum);
                            if (seen >= 5 && !recorded.contains(parts[0])) {
                                try {
                                    boolean added = com.attendance.service.AttendanceService.mark(parts[0],parts[1]);
                                    recorded.add(parts[0]);
                                    message(added ? "Attendance marked: " + name : "Already marked today: " + name);
                                } catch (java.io.IOException ex) { message("ERROR saving attendance: " + ex.getMessage()); }
                            }
                        }
                    }
                    Scalar color = name.equals("Unknown") ? new Scalar(0,80,255) : new Scalar(50,220,80);
                    Imgproc.rectangle(frame, r.tl(), r.br(), color, 2);
                    Imgproc.putText(frame, name, new Point(r.x, Math.max(22, r.y - 8)), Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2);
                }
                if (markAttendance) consecutive.keySet().removeIf(id -> !recognizedThisFrame.contains(id));
                BufferedImage image = new BufferedImage(frame.cols(), frame.rows(), BufferedImage.TYPE_3BYTE_BGR);
                frame.get(0,0,((DataBufferByte)image.getRaster().getDataBuffer()).getData());
                SwingUtilities.invokeLater(() -> { if (running) preview.accept(new ImageIcon(image)); });
                Thread.sleep(70);
            }
        } catch (Exception e) { message("ERROR: " + e.getMessage()); }
        finally {
            running = false;
            cap.release(); frame.release(); gray.release(); faces.release();
            if (model != null) model.close();
            if (xml != null) try { Files.deleteIfExists(xml); } catch (Exception ignored) {}
        }
    }
    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }
}
