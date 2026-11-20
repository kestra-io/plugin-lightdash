package io.kestra.plugin.lightdash;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractLightdashTask extends Task implements LightdashConnection {
    @Schema(
        title = "Lightdash instance base URL",
        description = """
            The base URL of your Lightdash instance, e.g. `https://app.lightdash.cloud`.
            Can be provided with or without a trailing slash or `/api/v1` path."""
    )
    @NotNull
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<String> url = Property.ofValue("https://app.lightdash.cloud");

    @Schema(
        title = "Lightdash Personal Access Token (API Key)",
        description = """
            The Personal Access Token generated from your Lightdash personal settings.
            Passed via the `Authorization: ApiKey <token>` header."""
    )
    @NotNull
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Property<String> apiToken;

    @Schema(
        title = "HTTP request timeout",
        description = """
            The timeout duration for HTTP requests made to the Lightdash API. Defaults to `PT30S` (30 seconds). Must be positive."""
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    protected Property<Duration> requestTimeout = Property.ofValue(Duration.ofSeconds(30));

    @Override
    public Property<String> getUrl() {
        return this.url;
    }

    @Override
    public Property<String> getApiToken() {
        return this.apiToken;
    }

    @Override
    public Property<Duration> getRequestTimeout() {
        return this.requestTimeout;
    }

    protected String executeRequest(
        RunContext runContext,
        String method,
        String endpointPath,
        Object body
    ) throws Exception {
        return LightdashClient.execute(runContext, this.url, this.apiToken, this.requestTimeout, method, endpointPath, body);
    }

    private static JsonNode unwrapResults(String responseBody) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        var rootNode = JacksonMapper.ofJson().readTree(responseBody);
        if (rootNode.has("status") && !"ok".equalsIgnoreCase(rootNode.get("status").asText())) {
            throw new IllegalStateException("Lightdash API responded with non-ok status: " + responseBody);
        }
        return rootNode.has("results") ? rootNode.get("results") : rootNode;
    }

    protected <T> T requestResults(
        RunContext runContext,
        String method,
        String endpointPath,
        Object body,
        Class<T> responseType
    ) throws Exception {
        var responseBody = executeRequest(runContext, method, endpointPath, body);
        if (responseType == Void.class || responseBody == null || responseBody.isBlank()) {
            return null;
        }
        var resultsNode = unwrapResults(responseBody);
        return resultsNode != null ? JacksonMapper.ofJson().treeToValue(resultsNode, responseType) : null;
    }

    protected <T> T requestResults(
        RunContext runContext,
        String method,
        String endpointPath,
        Object body,
        TypeReference<T> typeRef
    ) throws Exception {
        var responseBody = executeRequest(runContext, method, endpointPath, body);
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        var resultsNode = unwrapResults(responseBody);
        return resultsNode != null ? JacksonMapper.ofJson().convertValue(resultsNode, typeRef) : null;
    }
}
