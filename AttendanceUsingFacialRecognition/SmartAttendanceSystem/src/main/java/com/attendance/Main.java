package com.attendance;

import nu.pattern.OpenCV;
import com.attendance.ui.Dashboard;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        OpenCV.loadLocally();
        SwingUtilities.invokeLater(() -> new Dashboard().setVisible(true));
    }
}
