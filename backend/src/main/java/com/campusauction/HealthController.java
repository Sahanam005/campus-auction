package com.campusauction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        String database = jdbcTemplate.queryForObject(
                "SELECT DATABASE()", String.class);

        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1", Integer.class);

        return Map.of(
                "status", result != null && result == 1 ? "UP" : "DOWN",
                "database", database
        );
    }
}