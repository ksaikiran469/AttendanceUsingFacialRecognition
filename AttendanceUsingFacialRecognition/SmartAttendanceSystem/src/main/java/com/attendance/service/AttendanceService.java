package com.attendance.service;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AttendanceService {
    private static final Path FILE = Path.of("data", "attendance", "records.tsv");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private AttendanceService() {}
    public record Entry(String date, String time, String studentId, String studentName) {}
    public static synchronized boolean mark(String id, String name) throws IOException {
        if (!id.matches("[A-Za-z0-9_-]{1,50}") || name.isBlank() || name.contains("\t") || name.contains("\n"))
            throw new IllegalArgumentException("Invalid student details");
        String today = LocalDate.now().format(DATE);
        for (Entry e : read()) if (e.date().equals(today) && e.studentId().equals(id)) return false;
        Files.createDirectories(FILE.getParent());
        String line = String.join("\t", today, LocalTime.now().format(TIME), id, name) + System.lineSeparator();
        Files.writeString(FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return true;
    }
    public static synchronized List<Entry> read() throws IOException {
        List<Entry> result = new ArrayList<>();
        if (!Files.exists(FILE)) return result;
        for (String line : Files.readAllLines(FILE)) {
            String[] fields = line.split("\t", -1);
            if (fields.length == 4) result.add(new Entry(fields[0],fields[1],fields[2],fields[3]));
        }
        return result;
    }
}
