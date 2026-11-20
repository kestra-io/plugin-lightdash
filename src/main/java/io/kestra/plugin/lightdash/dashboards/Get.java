package io.kestra.plugin.lightdash.dashboards;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.lightdash.AbstractLightdashTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a Lightdash dashboard definition",
    description = """
        Retrieves the complete configuration, tiles, filters, and metadata for a specific Lightdash dashboard."""
)
@Plugin(
    examples = {
        @Example(
            title = "Get a Lightdash dashboard definition",
            full = true,
            code = """
                id: get_lightdash_dashboard
                namespace: company.analytics

                tasks:
                  - id: get_dashboard
                    type: io.kestra.plugin.lightdash.dashboards.Get
                    url: "https://app.lightdash.cloud"
                    apiToken: "{{ secret('LIGHTDASH_API_TOKEN') }}"
                    projectUuid: "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"
                    dashboardUuid: "d4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a"
                """
        )
    }
)
public class Get extends AbstractLightdashTask implements RunnableTask<Get.Output> {

    @Schema(
        title = "Project UUID",
        description = """
            The unique identifier of the Lightdash project."""
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> projectUuid;

    @Schema(
        title = "Dashboard UUID or Slug",
        description = """
            The UUID or URL slug of the dashboard to retrieve."""
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> dashboardUuid;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rProjectUuid = runContext.render(this.projectUuid).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`projectUuid` is required for Get dashboard task."));
        var rDashboardUuid = runContext.render(this.dashboardUuid).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`dashboardUuid` is required for Get dashboard task."));

        runContext.logger().info(
            "Fetching dashboard '{}' from Lightdash project '{}'",
            rDashboardUuid,
            rProjectUuid
        );

        var dashboard = requestResults(
            runContext,
            "GET",
            "api/v2/projects/" + rProjectUuid + "/dashboards/" + rDashboardUuid,
            null,
            new TypeReference<Map<String, Object>>() {}
        );

        return Output.builder()
            .dashboard(dashboard)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            description = """
                The complete dashboard metadata, filters, and tile definitions.
                """
        )
        private Map<String, Object> dashboard;
    }
}
