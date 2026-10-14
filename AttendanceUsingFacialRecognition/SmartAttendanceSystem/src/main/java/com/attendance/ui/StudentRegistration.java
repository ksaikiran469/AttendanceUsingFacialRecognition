package com.attendance.ui;

import com.attendance.recognition.PhotoCaptureService;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

public class StudentRegistration extends JDialog {
    private final JTextField id = new JTextField(16);
    private final JTextField name = new JTextField(16);
    private final JLabel preview = new JLabel("Starting webcam...", SwingConstants.CENTER);
    private final JLabel status = new JLabel("Capture 10 clear face photos", SwingConstants.CENTER);
    private final JButton capture = new JButton("Capture Photo");
    private final PhotoCaptureService camera;
    private int count = 0;

    public StudentRegistration(JFrame owner) {
        super(owner, "Register Student - Stage 2", false);
        setSize(800, 640);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        JPanel fields = new JPanel(new FlowLayout());
        fields.add(new JLabel("Student ID:")); fields.add(id);
        fields.add(new JLabel("Name:")); fields.add(name);
        add(fields, BorderLayout.NORTH);
        preview.setOpaque(true);
        preview.setBackground(new Color(27, 34, 45));
        preview.setForeground(Color.WHITE);
        add(preview, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        JCheckBox consent = new JCheckBox("Student has consented to face photo capture and storage");
        bottom.add(consent, BorderLayout.NORTH);
        JPanel buttons = new JPanel();
        capture.setEnabled(false);
        buttons.add(capture);
        JButton finish = new JButton("Finish / Close");
        buttons.add(finish);
        bottom.add(buttons, BorderLayout.CENTER);
        bottom.add(status, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
        camera = new PhotoCaptureService(icon -> { preview.setIcon(icon); preview.setText(""); },
            msg -> { status.setText(msg); if (msg.startsWith("ERROR")) capture.setEnabled(false); });
        consent.addActionListener(e -> capture.setEnabled(consent.isSelected()));
        capture.addActionListener(e -> {
            String studentId = id.getText().trim();
            String studentName = name.getText().trim();
            if (!studentId.matches("[A-Za-z0-9_-]{1,32}") || studentName.isBlank() || studentName.length() > 100) {
                JOptionPane.showMessageDialog(this, "Use an ID with 1-32 letters, digits, _ or - and a name of 1-100 characters.");
                return;
            }
            try {
                Path saved = camera.saveFace(studentId, studentName);
                count++;
                status.setText("Saved photo " + count + ": " + saved.getFileName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Capture failed", JOptionPane.ERROR_MESSAGE);
            }
        });
        finish.addActionListener(e -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { camera.stop(); }
            @Override public void windowClosed(WindowEvent e) { camera.stop(); }
        });
        camera.start();
    }
}
