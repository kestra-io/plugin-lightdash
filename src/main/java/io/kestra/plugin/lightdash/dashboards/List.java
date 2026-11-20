package io.kestra.plugin.lightdash.dashboards;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.lightdash.AbstractLightdashTask;
import io.kestra.plugin.lightdash.models.DashboardSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List dashboards in a Lightdash project",
    description = """
        Retrieves all dashboards created within a specific Lightdash project."""
)
@Plugin(
    examples = {
        @Example(
            title = "List all dashboards in a project",
            full = true,
            code = """
                id: list_lightdash_dashboards
                namespace: company.analytics

                tasks:
                  - id: list_dashboards
                    type: io.kestra.plugin.lightdash.dashboards.List
                    url: "https://app.lightdash.cloud"
                    apiToken: "{{ secret('LIGHTDASH_API_TOKEN') }}"
                    projectUuid: "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"
                """
        )
    }
)
public class List extends AbstractLightdashTask implements RunnableTask<List.Output> {

    @Schema(
        title = "Project UUID",
        description = """
            The unique identifier of the Lightdash project."""
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> projectUuid;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rProjectUuid = runContext.render(this.projectUuid).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`projectUuid` is required for List dashboards task."));

        var dashboards = requestResults(
            runContext,
            "GET",
            "projects/" + rProjectUuid + "/dashboards",
            null,
            new TypeReference<java.util.List<DashboardSummary>>() {}
        );

        runContext.logger().info(
            "Retrieved {} dashboard(s) for Lightdash project '{}'.",
            dashboards != null ? dashboards.size() : 0,
            rProjectUuid
        );

        return Output.builder()
            .dashboards(dashboards)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            description = """
                The list of dashboards in the project.
                """
        )
        private java.util.List<DashboardSummary> dashboards;
    }
}
