package com.vipul.agrishare.config;

import com.vipul.agrishare.payment.PaymentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(PaymentProperties.class)
public class AppConfig {

    /** Farmers think in Indian calendar days, so "today" is always IST. Injected so tests can pin time. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
