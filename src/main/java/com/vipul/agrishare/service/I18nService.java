package com.vipul.agrishare.service;

import com.vipul.agrishare.exception.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves the same messages_*.properties bundles the server uses for errors,
 * so web UI, Android and API errors all share one set of translations.
 */
@Service
public class I18nService {

    /** Code → name in its own script, for the language switcher. Order = display order. */
    public static final Map<String, String> LANGUAGES;

    static {
        Map<String, String> languages = new LinkedHashMap<>();
        languages.put("en", "English");
        languages.put("hi", "हिन्दी");
        languages.put("pa", "ਪੰਜਾਬੀ");
        languages.put("mr", "मराठी");
        languages.put("gu", "ગુજરાતી");
        languages.put("bn", "বাংলা");
        languages.put("ta", "தமிழ்");
        languages.put("te", "తెలుగు");
        languages.put("kn", "ಕನ್ನಡ");
        LANGUAGES = Collections.unmodifiableMap(languages);
    }

    public record Language(String code, String nativeName) {}

    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    public List<Language> languages() {
        return LANGUAGES.entrySet().stream().map(e -> new Language(e.getKey(), e.getValue())).toList();
    }

    /** English base overlaid with the language's own texts, so a missing key still shows something. */
    public Map<String, String> messages(String lang) {
        if (!LANGUAGES.containsKey(lang)) {
            throw ApiException.notFound("error.notFound");
        }
        return cache.computeIfAbsent(lang, l -> {
            Map<String, String> merged = new TreeMap<>(load("messages.properties"));
            if (!"en".equals(l)) {
                merged.putAll(load("messages_" + l + ".properties"));
            }
            return Collections.unmodifiableMap(merged);
        });
    }

    private static Map<String, String> load(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> map = new TreeMap<>();
            props.stringPropertyNames().forEach(k -> map.put(k, props.getProperty(k)));
            return map;
        } catch (IOException e) {
            throw new UncheckedIOException("Missing message bundle " + resource, e);
        }
    }
}
