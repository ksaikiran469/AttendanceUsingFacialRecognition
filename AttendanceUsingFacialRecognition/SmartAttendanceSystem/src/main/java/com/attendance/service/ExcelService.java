package com.attendance.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.nio.file.*;
import java.util.List;

public final class ExcelService {
    private ExcelService() {}
    public static void export(Path destination, List<AttendanceService.Entry> entries) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance");
            String[] headers = {"Date", "Time", "Student ID", "Student Name"};
            Row header = sheet.createRow(0);
            for (int i=0; i<headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int rowNum=1;
            for (var e : entries) {
                Row row=sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(e.date());
                row.createCell(1).setCellValue(e.time());
                row.createCell(2).setCellValue(e.studentId());
                row.createCell(3).setCellValue(e.studentName());
            }
            for (int i=0;i<headers.length;i++) sheet.autoSizeColumn(i);
            try (OutputStream out=Files.newOutputStream(destination)) { workbook.write(out); }
        }
    }
}
