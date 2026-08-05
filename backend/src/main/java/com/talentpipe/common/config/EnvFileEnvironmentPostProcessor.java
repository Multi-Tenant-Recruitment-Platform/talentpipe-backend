package com.talentpipe.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Loads a local .env file from the workspace into Spring's environment so the
 * backend can read local development values without requiring shell exports.
 */
public class EnvFileEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String[] SEARCH_LOCATIONS = {
            "infra/.env",
            "../infra/.env",
            ".env",
            "../.env"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (String location : SEARCH_LOCATIONS) {
            Path path = resolve(location);
            if (Files.isRegularFile(path)) {
                Map<String, Object> values = readEnvFile(path);
                if (!values.isEmpty()) {
                    PropertySource<?> propertySource = new MapPropertySource(
                            "envFile:" + path.toAbsolutePath(), values);
                    if (environment.getPropertySources().contains("systemEnvironment")) {
                        environment.getPropertySources().addAfter("systemEnvironment", propertySource);
                    } else if (environment.getPropertySources().contains("systemProperties")) {
                        environment.getPropertySources().addAfter("systemProperties", propertySource);
                    } else {
                        environment.getPropertySources().addFirst(propertySource);
                    }
                    return;
                }
            }
        }
    }

    private Path resolve(String location) {
        Path path = Paths.get(location);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        }
        return path;
    }

    private Map<String, Object> readEnvFile(Path path) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read environment file " + path, ex);
        }
        return values;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
