package com.vipul.agrishare.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Every literal t('ui.x') in the web UI must exist in messages.properties (and so, via
 *  MessagesConsistencyTest, in all eight regional bundles). */
class UiKeysTest {

    private static final Pattern KEY = Pattern.compile("t\\(['`](ui\\.[A-Za-z0-9_.]+[A-Za-z0-9_])['`]");

    @Test
    void everyUiKeyUsedInJavascriptIsTranslated() throws IOException {
        Set<String> bundle = MessagesConsistencyTest.keys("messages.properties");
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/static/js"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".js")).toList()) {
                Matcher m = KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) {
                    used.add(m.group(1));
                }
            }
        }
        assertThat(used).isNotEmpty();
        used.removeAll(bundle);
        assertThat(used).as("UI keys missing from messages.properties").isEmpty();
    }
}
