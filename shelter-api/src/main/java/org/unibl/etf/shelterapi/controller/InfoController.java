package org.unibl.etf.shelterapi.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InfoController {

    private final Optional<BuildProperties> buildProperties;

    public InfoController(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/api/info")
    public Map<String, String> info() {
        return Map.of(
                "revision", buildProperties.map(p -> String.valueOf(p.get("revision"))).orElse("unknown"),
                "version", buildProperties.map(BuildProperties::getVersion).orElse("unknown"));
    }
}
