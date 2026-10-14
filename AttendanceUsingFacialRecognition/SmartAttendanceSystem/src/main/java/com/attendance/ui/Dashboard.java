package com.attendance.ui;

import com.attendance.recognition.CameraService;
import com.attendance.recognition.FaceModelService;
import com.attendance.recognition.RecognitionCameraService;
import org.opencv.core.Core;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class Dashboard extends JFrame {
    private final JLabel preview = new JLabel("Camera is off", SwingConstants.CENTER);
    private final JLabel status = new JLabel("Ready - OpenCV " + Core.VERSION);
    private final JButton start = new JButton("Start Camera");
    private final JButton recognize = new JButton("Recognize Faces");
    private final JButton stop = new JButton("Stop Camera");
    private CameraService camera;
    private RecognitionCameraService recognitionCamera;

    public Dashboard() {
        super("Smart Attendance System - Stage 5");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 680);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12,12));
        JLabel heading = new JLabel("Smart Attendance System", SwingConstants.CENTER);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
        add(heading, BorderLayout.NORTH);
        preview.setOpaque(true); preview.setBackground(new Color(28,33,42)); preview.setForeground(Color.WHITE);
        add(preview, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton registration = new JButton("Register Student / Capture Photos");
        JButton train = new JButton("Train Model");
        controls.add(start); controls.add(recognize); controls.add(stop);
        controls.add(registration); controls.add(train);
        JButton markAttendance = new JButton("Mark Attendance");
        JButton reports = new JButton("Attendance Reports / Export");
        controls.add(markAttendance); controls.add(reports);
        markAttendance.addActionListener(e -> {
            stopCamera(); setRunning(true);
            recognitionCamera = new RecognitionCameraService(icon -> { preview.setIcon(icon); preview.setText(""); }, this::updateStatus, true);
            recognitionCamera.start();
        });
        reports.addActionListener(e -> { stopCamera(); new AttendanceReport(this).setVisible(true); });
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(controls, BorderLayout.CENTER); bottom.add(status, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        stop.setEnabled(false);
        start.addActionListener(e -> {
            stopCamera(); setRunning(true);
            camera = new CameraService(icon -> { preview.setIcon(icon); preview.setText(""); }, this::updateStatus);
            camera.start();
        });
        recognize.addActionListener(e -> {
            stopCamera(); setRunning(true);
            recognitionCamera = new RecognitionCameraService(icon -> { preview.setIcon(icon); preview.setText(""); }, this::updateStatus);
            recognitionCamera.start();
        });
        stop.addActionListener(e -> stopCamera());
        registration.addActionListener(e -> {
            stopCamera();
            registration.setEnabled(false);
            StudentRegistration dialog = new StudentRegistration(this);
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) { registration.setEnabled(true); }
            });
            dialog.setVisible(true);
        });
        train.addActionListener(e -> {
            stopCamera();
            train.setEnabled(false); status.setText("Training model...");
            SwingWorker<String,Void> worker = new SwingWorker<>() {
                @Override protected String doInBackground() throws Exception { return FaceModelService.train(); }
                @Override protected void done() {
                    train.setEnabled(true);
                    try { status.setText(get()); }
                    catch (Exception ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        status.setText("Training failed");
                        JOptionPane.showMessageDialog(Dashboard.this, cause.getMessage(), "Training error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        });
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { stopCamera(); } });
    }
    private void updateStatus(String text) {
        status.setText(text);
        if (text.startsWith("ERROR")) setRunning(false);
    }
    private void setRunning(boolean running) {
        start.setEnabled(!running); recognize.setEnabled(!running); stop.setEnabled(running);
    }
    private void stopCamera() {
        if (camera != null) { camera.stop(); camera = null; }
        if (recognitionCamera != null) { recognitionCamera.stop(); recognitionCamera = null; }
        preview.setIcon(null); preview.setText("Camera is off");
        status.setText("Camera stopped"); setRunning(false);
    }
}
