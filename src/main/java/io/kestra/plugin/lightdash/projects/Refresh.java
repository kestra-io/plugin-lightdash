package io.kestra.plugin.lightdash.projects;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.lightdash.AbstractLightdashTask;
import io.kestra.plugin.lightdash.models.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Refresh a Lightdash project",
    description = """
        Triggers a refresh of the dbt semantic layer and schema cache for a specific Lightdash project,
        optionally polling the scheduler until the refresh job completes."""
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger and wait for a project semantic layer refresh",
            full = true,
            code = """
                id: refresh_lightdash_project
                namespace: company.analytics

                tasks:
                  - id: refresh_project
                    type: io.kestra.plugin.lightdash.projects.Refresh
                    url: "https://app.lightdash.cloud"
                    apiToken: "{{ secret('LIGHTDASH_API_TOKEN') }}"
                    projectUuid: "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"
                    wait: true
                    pollInterval: PT3S
                    maxWait: PT10M
                """
        )
    }
)
public class Refresh extends AbstractLightdashTask implements RunnableTask<Refresh.Output> {

    @Schema(
        title = "Project UUID",
        description = """
            The unique identifier of the Lightdash project to refresh."""
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> projectUuid;

    @Schema(
        title = "Wait for completion",
        description = """
            Whether to poll and wait until the refresh background job completes or fails."""
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<Boolean> wait = Property.ofValue(true);

    @Schema(
        title = "Polling interval",
        description = """
            Interval between consecutive job status poll requests. Defaults to `PT2S` (2 seconds). Must be positive."""
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<Duration> pollInterval = Property.ofValue(Duration.ofSeconds(2));

    @Schema(
        title = "Maximum wait duration",
        description = """
            Maximum time to wait for the refresh job to complete before timing out. Defaults to `PT10M` (10 minutes). Must be positive."""
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<Duration> maxWait = Property.ofValue(Duration.ofMinutes(10));

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rProjectUuid = runContext.render(this.projectUuid).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`projectUuid` is required for Refresh task."));
        var shouldWait = runContext.render(this.wait).as(Boolean.class).orElse(true);
        var interval = runContext.render(this.pollInterval).as(Duration.class).orElse(Duration.ofSeconds(2));
        var maxTimeout = runContext.render(this.maxWait).as(Duration.class).orElse(Duration.ofMinutes(10));

        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("`pollInterval` must be positive.");
        }
        if (maxTimeout.isNegative() || maxTimeout.isZero()) {
            throw new IllegalArgumentException("`maxWait` must be positive.");
        }

        runContext.logger().info("Triggering refresh for Lightdash project '{}'", rProjectUuid);

        var initResult = requestResults(
            runContext,
            "POST",
            "projects/" + rProjectUuid + "/refresh",
            Map.of(),
            new TypeReference<Map<String, String>>() {}
        );

        var jobUuid = initResult != null ? initResult.get("jobUuid") : null;
        if (jobUuid == null || jobUuid.isBlank()) {
            throw new IllegalStateException("Lightdash refresh request did not return a valid jobUuid: " + initResult);
        }

        runContext.logger().info("Lightdash refresh job created with UUID '{}'", jobUuid);

        if (!shouldWait) {
            return Output.builder()
                .jobUuid(jobUuid)
                .status("scheduled")
                .build();
        }

        var deadline = Instant.now().plus(maxTimeout);
        JobStatus finalStatus = null;

        while (Instant.now().isBefore(deadline)) {
            TimeUnit.MILLISECONDS.sleep(interval.toMillis());

            finalStatus = requestResults(
                runContext,
                "GET",
                "schedulers/job/" + jobUuid + "/status",
                null,
                JobStatus.class
            );

            var status = finalStatus != null ? finalStatus.getStatus() : null;
            runContext.logger().debug("Polling Lightdash job '{}', current status: '{}'", jobUuid, status);

            if ("completed".equalsIgnoreCase(status)) {
                runContext.logger().info("Lightdash refresh job '{}' completed successfully.", jobUuid);
                return Output.builder()
                    .jobUuid(jobUuid)
                    .status(status)
                    .details(finalStatus.getDetails())
                    .build();
            }

            if ("error".equalsIgnoreCase(status)) {
                throw new IllegalStateException(
                    "Lightdash refresh job '" + jobUuid + "' failed with status 'error'. Details: " +
                    (finalStatus != null ? finalStatus.getDetails() : "none")
                );
            }
        }

        throw new IllegalStateException(
            "Timed out waiting for Lightdash refresh job '" + jobUuid + "' after " + maxTimeout
        );
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            description = """
                The UUID of the refresh job.
                """
        )
        private String jobUuid;

        @Schema(
            description = """
                The terminal execution status of the refresh job (e.g., `completed`, `error`, `scheduled`).
                """
        )
        private String status;

        @Schema(
            description = """
                Additional details, execution times, or metadata returned for the job.
                """
        )
        private Map<String, Object> details;
    }
}
