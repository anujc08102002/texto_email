package com.texto.emailplatform.contact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.contact.api.ContactImportResponse;
import com.texto.emailplatform.suppression.EmailNormalizer;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContactImportService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ContactImportService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Transactional
    public ContactImportResponse importCsv(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5_000_000) throw invalid("CSV must be between 1 byte and 5 MB");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".csv")) throw invalid("Only CSV files are supported");
        UUID tenant = TenantContext.get().orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "Authentication is required"));
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            List<List<String>> records = CsvRecords.parse(reader);
            if (records.isEmpty()) throw invalid("CSV is empty");
            List<String> headings = records.removeFirst().stream().map(h -> h.trim().toLowerCase(Locale.ROOT).startsWith("custom.") ? "custom." + h.trim().substring(7) : h.trim().toLowerCase(Locale.ROOT)).toList();
            if (new HashSet<>(headings).size() != headings.size()) throw invalid("CSV column names must be unique");
            if (!headings.contains("email")) throw invalid("CSV requires an email column");
            int accepted = 0, skipped = 0, row = 1;
            List<String> errors = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (List<String> values : records) {
                row++;
                String email = EmailNormalizer.normalize(at(values, headings.indexOf("email")));
                String problem = values.size() != headings.size() ? "column count does not match header"
                        : email == null || email.length() > 320 || !EMAIL.matcher(email).matches() ? "invalid email" : null;
                String first = at(values, headings.indexOf("first_name")), last = at(values, headings.indexOf("last_name"));
                String company = at(values, headings.indexOf("company")), phone = at(values, headings.indexOf("phone"));
                if (length(first) > 120 || length(last) > 120 || length(company) > 200 || length(phone) > 64) problem = "contact field exceeds its maximum length";
                if (problem == null && !seen.add(email)) problem = "duplicate email";
                if (problem != null) { skipped++; if (errors.size() < 100) errors.add("Row " + row + ": " + problem); continue; }
                Map<String, Object> properties = new LinkedHashMap<>();
                for (int i = 0; i < headings.size(); i++) {
                    if (headings.get(i).matches("custom\\.[a-zA-Z0-9_]{1,64}")) properties.put(headings.get(i).substring(7), values.get(i));
                }
                int inserted = jdbc.update("""
                        INSERT INTO contacts (id, tenant_id, email, first_name, last_name, company, phone, source,
                            status, subscription_status, properties, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'csv_import', 'ACTIVE', 'SUBSCRIBED', CAST(? AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (tenant_id, email) DO NOTHING
                        """, UUID.randomUUID(), tenant, email, first, last, company, phone, json.writeValueAsString(properties));
                if (inserted > 0) accepted++;
                else { skipped++; if (errors.size() < 100) errors.add("Row " + row + ": duplicate email"); }
            }
            return new ContactImportResponse(accepted, skipped, errors);
        } catch (IOException exception) { throw invalid("Unable to read CSV"); }
    }
    private static String at(List<String> values, int index) { return index < 0 || index >= values.size() ? null : values.get(index); }
    private static int length(String value) { return value == null ? 0 : value.length(); }
    private static ApiException invalid(String message) { return new ApiException(400, "INVALID_IMPORT", message); }
}
