package com.vipul.agrishare.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTextTest {

    private NotificationText text;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        text = new NotificationText(source);
    }

    private static final Map<String, String> PARAMS = Map.of(
            "equipment", "Mahindra 575", "renter", "Ramesh", "owner", "Gurpreet",
            "start", "2026-10-03", "end", "2026-10-05");

    @Test
    void fillsNamedPlaceholdersAndFormatsDates() {
        String body = text.body("BOOKING_REQUESTED", PARAMS, Locale.ENGLISH);

        assertThat(body).isEqualTo("Ramesh wants to rent your Mahindra 575 from 3 Oct to 5 Oct.");
    }

    @Test
    void rendersInFarmersLanguage() {
        String title = text.title("BOOKING_CONFIRMED", PARAMS, Locale.forLanguageTag("hi"));
        String body = text.body("BOOKING_CONFIRMED", PARAMS, Locale.forLanguageTag("hi"));

        assertThat(title).isEqualTo("बुकिंग पक्की हुई");
        assertThat(body).contains("Gurpreet", "Mahindra 575").doesNotContain("{");
    }
}
