package com.texto.emailplatform.attachment;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Pure matching primitive. Callers must supply metadata from controlled tenant documents only. */
public final class AttachmentRuleMatcher {
    private AttachmentRuleMatcher() {}
    public static boolean matches(String comparison, String source, String document) {
        if (source == null || document == null) return false;
        String left=source.trim(), right=document.trim();
        return switch(comparison) {
            case "EQUALS" -> left.equals(right);
            case "CONTAINS" -> right.toLowerCase(Locale.ROOT).contains(left.toLowerCase(Locale.ROOT));
            case "PREFIX" -> right.startsWith(left);
            case "SUFFIX" -> right.endsWith(left);
            default -> false;
        };
    }
    public static String renderFilenameTemplate(String template, Map<String,String> values) {
        if(template==null || template.length()>512) throw new IllegalArgumentException("Invalid filename template");
        String rendered=template;
        for(var entry:values.entrySet()) rendered=rendered.replace("{"+entry.getKey()+"}", sanitize(entry.getValue()));
        if(rendered.contains("..")||rendered.contains("/")||rendered.contains("\\")) throw new IllegalArgumentException("Unsafe filename template");
        return rendered;
    }
    private static String sanitize(String value){return value==null?"":value.replaceAll("[\\\\/]","").trim();}
}
