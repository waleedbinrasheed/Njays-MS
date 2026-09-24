package com.inventory.controller;

import com.inventory.config.AppProperties;
import com.inventory.config.AppProperties.Business;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only business details for printed documents (any signed-in user). */
@RestController
public class SettingsController {

    private final AppProperties properties;

    public SettingsController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/settings/business")
    public Business business() {
        return properties.business();
    }
}
