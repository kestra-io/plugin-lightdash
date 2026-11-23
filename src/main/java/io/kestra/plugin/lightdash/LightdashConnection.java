package io.kestra.plugin.lightdash;

import io.kestra.core.models.property.Property;

import java.time.Duration;

public interface LightdashConnection {
    Property<String> getUrl();

    Property<String> getApiToken();

    Property<Duration> getRequestTimeout();
}
