package com.texto.emailplatform.segment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.common.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class SegmentConditionQueryBuilder {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_CONDITIONS = 100;
    private static final Map<String, String> CONTACT_FIELDS = Map.ofEntries(
            Map.entry("email", "c.email"),
            Map.entry("firstname", "c.first_name"),
            Map.entry("lastname", "c.last_name"),
            Map.entry("company", "c.company"),
            Map.entry("phone", "c.phone"),
            Map.entry("status", "c.status"),
            Map.entry("subscriptionstatus", "c.subscription_status"),
            Map.entry("source", "c.source"),
            Map.entry("createdat", "c.created_at::text"),
            Map.entry("updatedat", "c.updated_at::text")
    );

    private final ObjectMapper objectMapper;

    public SegmentConditionQueryBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SqlPredicate build(String definition) {
        try {
            JsonNode root = objectMapper.readTree(definition);
            BuildState state = new BuildState();
            String sql = buildGroup(root, 0, state);
            if (state.conditionCount == 0) {
                throw invalid("A segment must contain at least one condition");
            }
            return new SqlPredicate(sql, Map.copyOf(state.parameters));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Segment definition must be valid JSON");
        }
    }

    private String buildGroup(JsonNode group, int depth, BuildState state) {
        if (depth > MAX_DEPTH) {
            throw invalid("Segment groups cannot be nested more than 8 levels");
        }
        if (group == null || !group.isObject() || !group.path("conditions").isArray()) {
            throw invalid("Each segment group must contain a conditions array");
        }

        String operator = group.path("operator").asText("").toUpperCase(Locale.ROOT);
        if (!operator.equals("AND") && !operator.equals("OR")) {
            throw invalid("Segment group operator must be AND or OR");
        }

        List<String> parts = new ArrayList<>();
        for (JsonNode condition : group.path("conditions")) {
            if (condition.has("conditions")) {
                parts.add(buildGroup(condition, depth + 1, state));
            } else {
                if (++state.conditionCount > MAX_CONDITIONS) {
                    throw invalid("A segment cannot contain more than 100 conditions");
                }
                parts.add(buildCondition(condition, state));
            }
        }
        if (parts.isEmpty()) {
            throw invalid("Segment groups cannot be empty");
        }
        return "(" + String.join(" " + operator + " ", parts) + ")";
    }

    private String buildCondition(JsonNode condition, BuildState state) {
        if (!condition.isObject()) {
            throw invalid("Each segment condition must be an object");
        }

        String field = condition.path("field").asText("").trim();
        String operator = condition.path("operator").asText("")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z]", "");
        String type = condition.path("type").asText("TEXT").toUpperCase(Locale.ROOT);
        String column = resolveField(field, state);
        JsonNode value = condition.get("value");

        return switch (operator) {
            case "ISEMPTY" -> "(" + column + " IS NULL OR BTRIM(" + column + ") = '')";
            case "ISNOTEMPTY" -> "(" + column + " IS NOT NULL AND BTRIM(" + column + ") <> '')";
            case "EQUALS", "NOTEQUALS" -> compare(column, type, operator, requireValue(value), state);
            case "CONTAINS", "STARTSWITH", "ENDSWITH" -> textMatch(column, type, operator, requireValue(value), state);
            case "GREATERTHAN", "GREATERTHANOREQUAL", "LESSTHAN", "LESSTHANOREQUAL" ->
                    orderedCompare(column, type, operator, requireValue(value), state);
            case "BETWEEN" -> between(column, type, value, state);
            default -> throw invalid("Unsupported segment operator: " + operator);
        };
    }

    private String compare(String column, String type, String operator, JsonNode value, BuildState state) {
        String expression = typedColumn(column, type);
        Object normalizedValue = typedValue(value.asText(), type);
        String parameter = state.parameter(normalizedValue);
        String left = type.equals("TEXT") ? "LOWER(COALESCE(" + expression + ", ''))" : expression;
        String right = type.equals("TEXT") ? "LOWER(" + parameter + ")" : parameter;
        String comparison = operator.equals("EQUALS") ? " = " : " IS DISTINCT FROM ";
        if (!type.equals("TEXT")) {
            return "(" + left + " IS NOT NULL AND " + left + comparison + right + ")";
        }
        return "(" + left + comparison + right + ")";
    }

    private String textMatch(String column, String type, String operator, JsonNode value, BuildState state) {
        if (!type.equals("TEXT")) {
            throw invalid("Text matching operators require a TEXT condition");
        }
        String parameter = state.parameter(value.asText().toLowerCase(Locale.ROOT));
        String normalizedColumn = "LOWER(COALESCE(" + column + ", ''))";
        return switch (operator) {
            case "CONTAINS" -> "POSITION(" + parameter + " IN " + normalizedColumn + ") > 0";
            case "STARTSWITH" -> "LEFT(" + normalizedColumn + ", CHAR_LENGTH(" + parameter + ")) = " + parameter;
            case "ENDSWITH" -> "RIGHT(" + normalizedColumn + ", CHAR_LENGTH(" + parameter + ")) = " + parameter;
            default -> throw invalid("Unsupported text match operator");
        };
    }

    private String orderedCompare(String column, String type, String operator, JsonNode value, BuildState state) {
        String expression = typedColumn(column, type);
        String parameter = state.parameter(typedValue(value.asText(), type));
        String sqlOperator = switch (operator) {
            case "GREATERTHAN" -> " > ";
            case "GREATERTHANOREQUAL" -> " >= ";
            case "LESSTHAN" -> " < ";
            case "LESSTHANOREQUAL" -> " <= ";
            default -> throw invalid("Unsupported ordered comparison");
        };
        if (type.equals("TEXT")) {
            expression = "LOWER(COALESCE(" + expression + ", ''))";
            parameter = "LOWER(" + parameter + ")";
        }
        return "(" + expression + sqlOperator + parameter + ")";
    }

    private String between(String column, String type, JsonNode value, BuildState state) {
        List<String> bounds = readBounds(value);
        String expression = typedColumn(column, type);
        String lower = state.parameter(typedValue(bounds.get(0), type));
        String upper = state.parameter(typedValue(bounds.get(1), type));
        if (type.equals("TEXT")) {
            expression = "LOWER(COALESCE(" + expression + ", ''))";
            lower = "LOWER(" + lower + ")";
            upper = "LOWER(" + upper + ")";
        }
        return "(" + expression + " BETWEEN " + lower + " AND " + upper + ")";
    }

    private String typedColumn(String column, String type) {
        return switch (type) {
            case "TEXT" -> column;
            case "NUMBER" -> "(CASE WHEN " + column
                    + " ~ '^-?[0-9]+([.][0-9]+)?$' THEN CAST(" + column + " AS NUMERIC) END)";
            case "DATE" -> "(CASE WHEN pg_input_is_valid(" + column
                    + ", 'date') THEN CAST(" + column + " AS DATE) END)";
            default -> throw invalid("Condition type must be TEXT, NUMBER, or DATE");
        };
    }

    private Object typedValue(String value, String type) {
        try {
            return switch (type) {
                case "TEXT" -> value.toLowerCase(Locale.ROOT);
                case "NUMBER" -> new BigDecimal(value);
                case "DATE" -> LocalDate.parse(value);
                default -> throw invalid("Condition type must be TEXT, NUMBER, or DATE");
            };
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Condition value does not match its declared type");
        }
    }

    private String resolveField(String field, BuildState state) {
        String normalized = field.replaceAll("[_\\s-]", "").toLowerCase(Locale.ROOT);
        String column = CONTACT_FIELDS.get(normalized);
        if (column != null) {
            return column;
        }

        int separator = field.indexOf('.');
        if (separator > 0) {
            String prefix = field.substring(0, separator).toLowerCase(Locale.ROOT);
            String property = field.substring(separator + 1);
            if ((prefix.equals("custom") || prefix.equals("properties"))
                    && !property.isBlank()
                    && property.length() <= 128
                    && property.chars().noneMatch(Character::isISOControl)) {
                return "jsonb_extract_path_text(c.properties, " + state.parameter(property) + ")";
            }
        }
        throw invalid("Unsupported contact field: " + field);
    }

    private static JsonNode requireValue(JsonNode value) {
        if (value == null || value.isNull() || value.isContainerNode()) {
            throw invalid("Condition requires a scalar value");
        }
        return value;
    }

    private static List<String> readBounds(JsonNode value) {
        if (value != null && value.isArray() && value.size() == 2) {
            return List.of(value.get(0).asText(), value.get(1).asText());
        }
        if (value != null && value.isObject() && value.has("from") && value.has("to")) {
            return List.of(value.get("from").asText(), value.get("to").asText());
        }
        throw invalid("BETWEEN conditions require two values");
    }

    private static ApiException invalid(String message) {
        return new ApiException(400, "INVALID_SEGMENT", message);
    }

    public record SqlPredicate(String sql, Map<String, Object> parameters) {}

    private static final class BuildState {
        private final Map<String, Object> parameters = new LinkedHashMap<>();
        private int conditionCount;

        private String parameter(Object value) {
            String name = "segmentParam" + parameters.size();
            parameters.put(name, value);
            return ":" + name;
        }
    }
}