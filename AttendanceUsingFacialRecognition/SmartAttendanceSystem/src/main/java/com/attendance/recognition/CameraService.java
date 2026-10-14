package com.attendance.recognition;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class CameraService {
    private final Consumer<ImageIcon> frameConsumer;
    private final Consumer<String> statusConsumer;
    private volatile boolean running;
    private volatile VideoCapture capture;
    private Thread worker;

    public CameraService(Consumer<ImageIcon> frameConsumer, Consumer<String> statusConsumer) {
        this.frameConsumer = frameConsumer;
        this.statusConsumer = statusConsumer;
    }

    public void start() {
        if (running) return;
        running = true;
        worker = new Thread(this::runCamera, "attendance-camera");
        worker.setDaemon(true);
        worker.start();
    }

    private void status(String text) { SwingUtilities.invokeLater(() -> statusConsumer.accept(text)); }

    private void runCamera() {
        Mat frame = new Mat();
        Mat gray = new Mat();
        MatOfRect faces = new MatOfRect();
        CascadeClassifier detector = new CascadeClassifier();
        Path cascadeFile = null;
        VideoCapture localCapture = null;
        try {
            try (InputStream in = CameraService.class.getResourceAsStream("/haarcascade_frontalface_default.xml")) {
                if (in == null) throw new IllegalStateException("Missing Haar Cascade resource");
                cascadeFile = Files.createTempFile("attendance-cascade-", ".xml");
                Files.copy(in, cascadeFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (!detector.load(cascadeFile.toString())) throw new IllegalStateException("Could not load Haar Cascade");
            localCapture = new VideoCapture(0, Videoio.CAP_DSHOW);
            capture = localCapture;
            if (!localCapture.isOpened()) throw new IllegalStateException("Could not open webcam. Check Windows camera permissions and close other camera apps.");
            status("Camera running — face detection enabled");
            while (running && localCapture.read(frame)) {
                if (frame.empty()) continue;
                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
                Imgproc.equalizeHist(gray, gray);
                detector.detectMultiScale(gray, faces, 1.1, 5, 0, new org.opencv.core.Size(60, 60), new org.opencv.core.Size());
                for (Rect face : faces.toArray()) {
                    Imgproc.rectangle(frame, face.tl(), face.br(), new Scalar(50, 220, 80), 2);
                }
                BufferedImage image = new BufferedImage(frame.cols(), frame.rows(), BufferedImage.TYPE_3BYTE_BGR);
                byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                frame.get(0, 0, pixels);
                ImageIcon icon = new ImageIcon(image);
                SwingUtilities.invokeLater(() -> { if (running) frameConsumer.accept(icon); });
                try { Thread.sleep(40); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
        } catch (Exception ex) {
            status("ERROR: " + ex.getMessage());
        } finally {
            running = false;
            if (localCapture != null) localCapture.release();
            capture = null;
            frame.release(); gray.release(); faces.release();
            if (cascadeFile != null) try { Files.deleteIfExists(cascadeFile); } catch (Exception ignored) { }
        }
    }

    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }
}
