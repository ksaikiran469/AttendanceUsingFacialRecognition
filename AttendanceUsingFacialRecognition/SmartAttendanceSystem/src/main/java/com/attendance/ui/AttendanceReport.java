package com.attendance.ui;

import com.attendance.service.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class AttendanceReport extends JDialog {
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Date","Time","Student ID","Student Name"},0) {
        @Override public boolean isCellEditable(int r,int c) { return false; }
    };
    private List<AttendanceService.Entry> entries = List.of();
    public AttendanceReport(JFrame owner) {
        super(owner,"Attendance Records",true);
        setSize(800,500); setLocationRelativeTo(owner); setLayout(new BorderLayout());
        JTable table = new JTable(model); table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel actions = new JPanel(); JButton refresh = new JButton("Refresh"); JButton export = new JButton("Export Excel (.xlsx)");
        actions.add(refresh);actions.add(export);add(actions,BorderLayout.SOUTH);
        refresh.addActionListener(e -> reload());
        export.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("attendance.xlsx"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            java.io.File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".xlsx")) file = new java.io.File(file.getAbsolutePath()+".xlsx");
            if (file.exists() && JOptionPane.showConfirmDialog(this,"Overwrite existing file?","Confirm",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
            try { ExcelService.export(file.toPath(), entries); JOptionPane.showMessageDialog(this,"Saved: "+file); }
            catch(IOException ex) { JOptionPane.showMessageDialog(this,ex.getMessage(),"Export failed",JOptionPane.ERROR_MESSAGE); }
        });
        reload();
    }
    private void reload() {
        try { entries=AttendanceService.read(); model.setRowCount(0);
            for (var e : entries) model.addRow(new Object[]{e.date(),e.time(),e.studentId(),e.studentName()});
        } catch(IOException ex) { JOptionPane.showMessageDialog(this,ex.getMessage(),"Read failed",JOptionPane.ERROR_MESSAGE); }
    }
}
