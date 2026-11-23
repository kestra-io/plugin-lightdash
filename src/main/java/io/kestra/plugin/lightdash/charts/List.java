package io.kestra.plugin.lightdash.charts;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.lightdash.AbstractLightdashTask;
import io.kestra.plugin.lightdash.models.ChartSummary;
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
    title = "List saved charts in a Lightdash project",
    description = """
        Retrieves all saved charts and visualizations within a specific Lightdash project."""
)
@Plugin(
    examples = {
        @Example(
            title = "List all charts in a project",
            full = true,
            code = """
                id: list_lightdash_charts
                namespace: company.analytics

                tasks:
                  - id: list_charts
                    type: io.kestra.plugin.lightdash.charts.List
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
            .orElseThrow(() -> new IllegalArgumentException("`projectUuid` is required for List charts task."));

        var charts = requestResults(
            runContext,
            "GET",
            "projects/" + rProjectUuid + "/charts",
            null,
            new TypeReference<java.util.List<ChartSummary>>() {}
        );

        runContext.logger().info(
            "Retrieved {} chart(s) for Lightdash project '{}'.",
            charts != null ? charts.size() : 0,
            rProjectUuid
        );

        return Output.builder()
            .charts(charts)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            description = """
                The list of saved charts in the project.
                """
        )
        private java.util.List<ChartSummary> charts;
    }
}
