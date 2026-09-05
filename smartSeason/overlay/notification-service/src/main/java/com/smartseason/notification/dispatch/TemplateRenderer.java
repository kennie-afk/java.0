package com.smartseason.notification.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    public record Rendered(String text, List<String> missingVariables) {

        public boolean complete() {
            return missingVariables.isEmpty();
        }
    }

    public Rendered render(String template, Map<String, Object> variables) {
        if (template == null) {
            return new Rendered("", List.of());
        }

        List<String> missing = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables == null ? null : variables.get(key);
            if (value == null) {
                missing.add(key);
                matcher.appendReplacement(out, Matcher.quoteReplacement(""));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(String.valueOf(value)));
            }
        }
        matcher.appendTail(out);

        return new Rendered(out.toString().trim(), List.copyOf(missing));
    }
}
