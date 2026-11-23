package io.kestra.plugin.lightdash.projects;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.lightdash.AbstractLightdashTask;
import io.kestra.plugin.lightdash.models.ProjectSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List Lightdash projects",
    description = """
        Retrieves all projects belonging to the authenticated organization in Lightdash."""
)
@Plugin(
    examples = {
        @Example(
            title = "List all Lightdash projects in an organization",
            full = true,
            code = """
                id: list_lightdash_projects
                namespace: company.analytics

                tasks:
                  - id: list_projects
                    type: io.kestra.plugin.lightdash.projects.List
                    url: "https://app.lightdash.cloud"
                    apiToken: "{{ secret('LIGHTDASH_API_TOKEN') }}"
                """
        )
    }
)
public class List extends AbstractLightdashTask implements RunnableTask<List.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        var projects = requestResults(
            runContext,
            "GET",
            "org/projects",
            null,
            new TypeReference<java.util.List<ProjectSummary>>() {}
        );

        runContext.logger().info("Retrieved {} Lightdash project(s).", projects != null ? projects.size() : 0);

        return Output.builder()
            .projects(projects)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            description = """
                The list of projects in the authenticated organization.
                """
        )
        private java.util.List<ProjectSummary> projects;
    }
}
