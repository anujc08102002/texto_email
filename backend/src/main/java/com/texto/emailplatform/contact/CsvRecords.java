package com.texto.emailplatform.contact;

import com.texto.emailplatform.common.exception.ApiException;
import java.io.*;
import java.util.*;

/** Bounded CSV records, including escaped quotes, CRLF, BOM and multiline quoted fields. */
final class CsvRecords {
    private CsvRecords() {}
    static List<List<String>> parse(Reader input) throws IOException {
        PushbackReader reader = new PushbackReader(input, 1);
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closedQuote = false, started = false, first = true;
        int value;
        while ((value = reader.read()) != -1) {
            char c = (char) value;
            if (first) { first = false; if (c == '\uFEFF') continue; }
            started = true;
            if (quoted) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; closedQuote = true; if (next != -1) reader.unread(next); }
                } else field.append(c);
            } else if (c == '"') {
                if (!field.isEmpty() || closedQuote) throw invalid("Unexpected quote in CSV");
                quoted = true;
            } else if (c == ',' || c == '\n' || c == '\r') {
                row.add(field.toString().trim()); field.setLength(0); closedQuote = false;
                if (row.size() > 200) throw invalid("CSV cannot exceed 200 columns");
                if (c != ',') {
                    if (!(row.size() == 1 && row.getFirst().isBlank())) records.add(row);
                    row = new ArrayList<>(); started = false;
                    if (records.size() > 10_001) throw invalid("CSV exceeds 10,000 data rows");
                    if (c == '\r') { int next = reader.read(); if (next != '\n' && next != -1) reader.unread(next); }
                }
            } else {
                if (closedQuote) { if (!Character.isWhitespace(c)) throw invalid("Unexpected text after quoted CSV field"); }
                else field.append(c);
            }
            if (field.length() > 50_000) throw invalid("CSV field exceeds 50,000 characters");
        }
        if (quoted) throw invalid("CSV contains an unterminated quoted field");
        if (started || !row.isEmpty()) { row.add(field.toString().trim()); records.add(row); }
        if (records.size() > 10_001) throw invalid("CSV exceeds 10,000 data rows");
        if (!records.isEmpty() && records.getLast().size() > 200) throw invalid("CSV cannot exceed 200 columns");
        return records;
    }
    private static ApiException invalid(String message) { return new ApiException(400, "INVALID_IMPORT", message); }
}
