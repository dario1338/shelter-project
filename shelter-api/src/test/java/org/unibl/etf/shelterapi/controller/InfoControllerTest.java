package org.unibl.etf.shelterapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class InfoControllerTest {

    @Test
    void returnsRevisionAndVersionFromBuildProperties() {
        Properties entries = new Properties();
        entries.setProperty("version", "1.2.3");
        entries.setProperty("revision", "abc123");
        InfoController controller = new InfoController(Optional.of(new BuildProperties(entries)));

        Map<String, String> result = controller.info();

        assertEquals("abc123", result.get("revision"));
        assertEquals("1.2.3", result.get("version"));
    }

    @Test
    void returnsUnknownWhenBuildInfoIsMissing() {
        InfoController controller = new InfoController(Optional.empty());

        Map<String, String> result = controller.info();

        assertEquals("unknown", result.get("revision"));
        assertEquals("unknown", result.get("version"));
    }
}
