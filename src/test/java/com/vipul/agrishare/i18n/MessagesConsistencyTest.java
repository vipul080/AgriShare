package com.vipul.agrishare.i18n;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Every regional bundle must carry exactly the keys of the default (English) bundle. */
class MessagesConsistencyTest {

    @ParameterizedTest
    @ValueSource(strings = {"hi", "pa", "mr", "gu", "bn", "ta", "te", "kn"})
    void regionalBundleHasSameKeysAsDefault(String lang) throws IOException {
        Set<String> expected = keys("messages.properties");
        Set<String> actual = keys("messages_" + lang + ".properties");

        assertThat(difference(expected, actual)).as("keys missing from messages_%s", lang).isEmpty();
        assertThat(difference(actual, expected)).as("keys only in messages_%s", lang).isEmpty();
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> diff = new TreeSet<>(a);
        diff.removeAll(b);
        return diff;
    }

    static Set<String> keys(String resource) throws IOException {
        try (InputStream in = MessagesConsistencyTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return new TreeSet<>(props.stringPropertyNames());
        }
    }
}
