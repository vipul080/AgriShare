package com.vipul.agrishare.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders notification.<TYPE>.title/body with named placeholders ({equipment}, {start}…).
 * The web UI does the same substitution with the same keys, so push and in-app text match.
 */
@Component
@RequiredArgsConstructor
public class NotificationText {

    private static final Set<String> DATE_PARAMS = Set.of("start", "end");

    private final MessageSource messageSource;

    public String title(String type, Map<String, String> params, Locale locale) {
        return render("notification." + type + ".title", params, locale);
    }

    public String body(String type, Map<String, String> params, Locale locale) {
        return render("notification." + type + ".body", params, locale);
    }

    private String render(String key, Map<String, String> params, Locale locale) {
        // null args => MessageSource returns the raw pattern (no MessageFormat), so {name} survives
        String text = messageSource.getMessage(key, null, key, locale);
        for (Map.Entry<String, String> param : params.entrySet()) {
            String value = DATE_PARAMS.contains(param.getKey()) ? formatDate(param.getValue(), locale) : param.getValue();
            text = text.replace("{" + param.getKey() + "}", value == null ? "" : value);
        }
        return text;
    }

    private static String formatDate(String iso, Locale locale) {
        try {
            return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", locale));
        } catch (DateTimeParseException | NullPointerException e) {
            return iso;
        }
    }
}
