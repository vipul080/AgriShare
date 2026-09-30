package com.vipul.agrishare.controller;

import com.vipul.agrishare.service.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/i18n")
@RequiredArgsConstructor
public class I18nController {

    private final I18nService i18nService;

    /** Supported languages with native-script names for the switcher. */
    @GetMapping
    public List<I18nService.Language> languages() {
        return i18nService.languages();
    }

    /** Every UI/error/notification text for one language, as key → text. */
    @GetMapping("/{lang}")
    public ResponseEntity<Map<String, String>> messages(@PathVariable String lang) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(i18nService.messages(lang));
    }
}
