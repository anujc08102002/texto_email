package com.texto.emailplatform.template;

import com.texto.emailplatform.common.exception.ApiException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Replaces only {@code {{var_name}}} placeholders where names are alphanumeric/underscore
 * and declared in the variables schema. No expressions or code execution.
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_]+)\\}\\}");
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]+$");

    public record RenderedTemplate(String subject, String htmlContent, String textContent) {
    }

    public RenderedTemplate render(
            String subject,
            String htmlContent,
            String textContent,
            Map<String, Object> variablesSchema,
            Map<String, Object> variables
    ) {
        Map<String, Object> schema = variablesSchema == null ? Map.of() : variablesSchema;
        Map<String, Object> values = variables == null ? Map.of() : variables;

        for (String key : values.keySet()) {
            if (!VALID_NAME.matcher(key).matches() || !schema.containsKey(key)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_TEMPLATE_VARIABLE",
                        "Unknown or invalid template variable: " + key
                );
            }
        }

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String name = entry.getKey();
            if (!VALID_NAME.matcher(name).matches()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_VARIABLE_SCHEMA",
                        "Invalid variable name in schema: " + name
                );
            }
            if (isRequired(entry.getValue()) && isMissing(values.get(name))) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST.value(),
                        "MISSING_TEMPLATE_VARIABLE",
                        "Required template variable is missing: " + name
                );
            }
        }

        Set<String> referenced = new LinkedHashSet<>();
        referenced.addAll(findPlaceholders(subject));
        referenced.addAll(findPlaceholders(htmlContent));
        referenced.addAll(findPlaceholders(textContent));
        for (String name : referenced) {
            if (!schema.containsKey(name)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST.value(),
                        "UNKNOWN_TEMPLATE_PLACEHOLDER",
                        "Template references undeclared variable: " + name
                );
            }
        }

        Map<String, String> replacements = new LinkedHashMap<>();
        for (String name : schema.keySet()) {
            Object value = values.get(name);
            replacements.put(name, value == null ? "" : Objects.toString(value));
        }

        return new RenderedTemplate(
                replace(subject, replacements),
                replace(htmlContent, replacements),
                textContent == null ? null : replace(textContent, replacements)
        );
    }

    private static boolean isRequired(Object schemaNode) {
        if (!(schemaNode instanceof Map<?, ?> map)) {
            return false;
        }
        Object required = map.get("required");
        return Boolean.TRUE.equals(required) || "true".equalsIgnoreCase(String.valueOf(required));
    }

    private static boolean isMissing(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private static Set<String> findPlaceholders(String content) {
        Set<String> names = new LinkedHashSet<>();
        if (content == null) {
            return names;
        }
        Matcher matcher = PLACEHOLDER.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String replace(String content, Map<String, String> replacements) {
        if (content == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = Matcher.quoteReplacement(replacements.getOrDefault(name, ""));
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
