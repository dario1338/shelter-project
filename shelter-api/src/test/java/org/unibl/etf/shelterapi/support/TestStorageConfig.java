package org.unibl.etf.shelterapi.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the Supabase storage in every test that starts the whole application. It is a plain
 * configuration class on purpose: component scanning finds it, so tests need no extra annotation.
 */
@Configuration
public class TestStorageConfig {

    @Bean
    @Primary
    public InMemoryImageStorage inMemoryImageStorage() {
        return new InMemoryImageStorage();
    }
}
