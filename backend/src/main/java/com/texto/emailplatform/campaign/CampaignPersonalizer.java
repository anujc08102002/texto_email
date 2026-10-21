package com.texto.emailplatform.campaign;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class CampaignPersonalizer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_]+|custom\\.[A-Za-z0-9_]+)\\}\\}");

    public String render(String content, Map<String, Object> variables, boolean escapeHtml) {
        if (content == null) return null;
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String value = resolve(matcher.group(1), variables);
            matcher.appendReplacement(output, Matcher.quoteReplacement(escapeHtml ? escape(value) : value));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String resolve(String name, Map<String, Object> variables) {
        Object value;
        if (name.startsWith("custom.")) {
            Object custom = variables.get("custom");
            value = custom instanceof Map<?, ?> map ? map.get(name.substring("custom.".length())) : null;
        } else {
            value = variables.get(name);
        }
        return value == null ? "" : value.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}