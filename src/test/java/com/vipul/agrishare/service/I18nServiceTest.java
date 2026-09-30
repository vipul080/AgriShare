package com.vipul.agrishare.service;

import com.vipul.agrishare.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class I18nServiceTest {

    private final I18nService service = new I18nService();

    @Test
    void servesLanguageTextOverEnglishBase() {
        Map<String, String> hi = service.messages("hi");
        Map<String, String> en = service.messages("en");

        assertThat(hi).containsEntry("error.booking.notFound", "बुकिंग नहीं मिली।");
        assertThat(hi.keySet()).isEqualTo(en.keySet());
    }

    @Test
    void listsNineLanguagesWithNativeNames() {
        assertThat(service.languages()).hasSize(9)
                .anySatisfy(l -> assertThat(l.nativeName()).isEqualTo("ਪੰਜਾਬੀ"));
    }

    @Test
    void rejectsUnknownLanguage() {
        assertThatThrownBy(() -> service.messages("fr")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.messages("../application")).isInstanceOf(ApiException.class);
    }
}
