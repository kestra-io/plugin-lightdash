package io.kestra.plugin.lightdash.dashboards;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.lightdash.LightdashClient;
import io.kestra.plugin.lightdash.LightdashConnection;
import io.kestra.plugin.lightdash.models.DashboardSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(title = "Trigger execution on Lightdash dashboard update", description = """
        Periodically polls Lightdash for dashboards that have been created or modified in a project,
        using the namespace KV store to advance an update timestamp watermark for exactly-once firing.""")
@Plugin(examples = {
        @Example(title = "Trigger a flow whenever a dashboard is modified in Lightdash", full = true, code = """
                id: on_dashboard_updated
                namespace: company.analytics

                triggers:
                  - id: watch_dashboards
                    type: io.kestra.plugin.lightdash.dashboards.Trigger
                    url: "https://app.lightdash.cloud"
                    apiToken: "{{ secret('LIGHTDASH_API_TOKEN') }}"
                    projectUuid: "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"
                    interval: PT1M

                tasks:
                  - id: notify
                    type: io.kestra.plugin.core.log.Log
                    message: "Dashboard updated: {{ trigger.dashboards[0].name }} (UUID: {{ trigger.dashboards[0].uuid }})"
                """)
})
public class Trigger extends AbstractTrigger
        implements PollingTriggerInterface, TriggerOutput<Trigger.Output>, LightdashConnection {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Schema(
        title = "Lightdash instance base URL",
        description = """
            The base URL of your Lightdash instance, e.g. `https://app.lightdash.cloud`.
            Can be provided with or without a trailing slash or `/api/v1` path."""
    )
    @NotNull
    @PluginProperty(group = "connection")
    @Builder.Default
    private Property<String> url = Property.ofValue("https://app.lightdash.cloud");

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
    private Property<String> apiToken;

    @Schema(
        title = "HTTP request timeout",
        description = """
            The timeout duration for HTTP requests made to the Lightdash API. Defaults to `PT30S` (30 seconds). Must be positive."""
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<Duration> requestTimeout = Property.ofValue(Duration.ofSeconds(30));

    @Schema(title = "Project UUID", description = """
            The UUID of the Lightdash project to monitor for dashboard updates.""")
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> projectUuid;

    @Schema(
        title = "Polling interval",
        description = """
            The interval between consecutive polls for dashboard modifications. Defaults to `PT1M` (1 minute). Must be positive."""
    )
    @NotNull
    @PluginProperty(group = "execution")
    @Builder.Default
    private Duration interval = Duration.ofMinutes(1);

    @Override
    public Duration getInterval() {
        return this.interval;
    }

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

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        if (this.interval != null && (this.interval.isNegative() || this.interval.isZero())) {
            throw new IllegalArgumentException("`interval` must be positive.");
        }

        var runContext = conditionContext.getRunContext();
        var logger = runContext.logger();

        var rProjectUuid = runContext.render(this.projectUuid).as(String.class)
                .orElseThrow(() -> new IllegalArgumentException("`projectUuid` is required for Trigger."));

        List<DashboardSummary> dashboards;
        try {
            var responseBody = LightdashClient.execute(
                runContext,
                this.url,
                this.apiToken,
                this.requestTimeout,
                "GET",
                "projects/" + rProjectUuid + "/dashboards",
                null
            );

            var root = MAPPER.readTree(responseBody);
            var resultsNode = root.has("results") ? root.get("results") : root;
            dashboards = MAPPER.convertValue(resultsNode, new TypeReference<List<DashboardSummary>>() {
            });
        } catch (Exception e) {
            logger.warn("Lightdash trigger poll failed for project '{}': {}", rProjectUuid, e.getMessage());
            return Optional.empty();
        }

        if (dashboards == null || dashboards.isEmpty()) {
            return Optional.empty();
        }

        var kvStore = runContext.namespaceKv(context.getNamespace());
        var flowId = context.getFlowId() != null ? context.getFlowId() : "default";
        var triggerId = this.getId() != null ? this.getId() : "lightdash";
        var stateKey = "lightdash_watermark_" + flowId.length() + "_" + flowId + "_" + triggerId.length() + "_"
                + triggerId + "_" + sha256Hex(rProjectUuid);

        Instant watermark = null;
        try {
            var kvVal = kvStore.getValue(stateKey);
            if (kvVal.isPresent() && kvVal.get().value() != null) {
                var val = kvVal.get().value();
                var raw = val instanceof byte[] b ? new String(b, StandardCharsets.UTF_8) : val.toString();
                watermark = Instant.parse(raw);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse Lightdash trigger watermark for key '{}': {}", stateKey, e.getMessage());
        }

        var maxCurrent = dashboards.stream()
                .map(DashboardSummary::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        if (watermark == null) {
            kvStore.put(
                    stateKey,
                    new KVValueAndMetadata(
                            new KVMetadata("Lightdash trigger watermark", (Duration) null),
                            maxCurrent.toString()));
            logger.info("Initialized Lightdash trigger watermark for project '{}' at {}", rProjectUuid, maxCurrent);
            return Optional.empty();
        }

        final var currentWatermark = watermark;
        var newlyUpdated = dashboards.stream()
                .filter(d -> d.getUpdatedAt() != null && d.getUpdatedAt().isAfter(currentWatermark))
                .sorted(Comparator.comparing(DashboardSummary::getUpdatedAt))
                .toList();

        if (newlyUpdated.isEmpty()) {
            return Optional.empty();
        }

        var newWatermark = newlyUpdated.getLast().getUpdatedAt();
        if (newWatermark != null && newWatermark.isAfter(currentWatermark)) {
            kvStore.put(
                    stateKey,
                    new KVValueAndMetadata(
                            new KVMetadata("Lightdash trigger watermark", (Duration) null),
                            newWatermark.toString()));
        }

        var output = Output.builder()
                .dashboards(newlyUpdated)
                .count(newlyUpdated.size())
                .build();

        logger.info("Lightdash trigger firing: {} modified dashboard(s) found in project '{}'", newlyUpdated.size(),
                rProjectUuid);
        return Optional.of(TriggerService.generateExecution(this, conditionContext, context, output));
    }

    private static String sha256Hex(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(description = """
                The list of all modified dashboards detected in this polling window.
                """)
        private List<DashboardSummary> dashboards;

        @Schema(description = """
                The count of modified dashboards detected.
                """)
        private Integer count;
    }
}
