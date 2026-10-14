package com.attendance.service;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsAttendanceRowsToAnExcelWorkbook() throws Exception {
        Path destination = temporaryDirectory.resolve("attendance.xlsx");
        AttendanceService.Entry entry =
            new AttendanceService.Entry("2026-10-09", "09:15:30", "student-01", "Sample Student");

        ExcelService.export(destination, List.of(entry));

        try (var workbook = WorkbookFactory.create(destination.toFile())) {
            var sheet = workbook.getSheet("Attendance");
            assertEquals("Date", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Student ID", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("2026-10-09", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("student-01", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("Sample Student", sheet.getRow(1).getCell(3).getStringCellValue());
        }
    }
}
