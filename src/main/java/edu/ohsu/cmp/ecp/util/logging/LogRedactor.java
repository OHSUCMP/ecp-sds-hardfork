package edu.ohsu.cmp.ecp.util.logging;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogRedactor {
    private static final String REDACTION = "[REDACTED]";

    private static final List<Pattern> REDACTION_PATTERNS = List.of(
            Pattern.compile("Patient/([^&\"\\s]+)"),
            Pattern.compile("patient=([^&\"\\s]+)"),
            Pattern.compile("subject=([^&\"\\s]+)")
    );

    private LogRedactor() {
    }

    public static String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String redacted = value;

        for (Pattern pattern : REDACTION_PATTERNS) {
            redacted = redactFirstGroup(redacted, pattern);
        }

        return redacted;
    }

    private static String redactFirstGroup(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder redacted = new StringBuilder();

        while (matcher.find()) {
            int groupStartInMatch = matcher.start(1) - matcher.start();
            int groupEndInMatch = matcher.end(1) - matcher.start();

            String match = matcher.group();
            String redactedMatch = match.substring(0, groupStartInMatch) +
                    REDACTION +
                    match.substring(groupEndInMatch);

            matcher.appendReplacement(redacted, Matcher.quoteReplacement(redactedMatch));
        }

        matcher.appendTail(redacted);
        return redacted.toString();
    }
}
