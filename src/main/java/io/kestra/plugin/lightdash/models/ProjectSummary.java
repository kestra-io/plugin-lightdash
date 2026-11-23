package io.kestra.plugin.lightdash.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectSummary {
    @Schema(description = """
        The UUID of the project.
        """)
    private String projectUuid;

    @Schema(description = """
        The name of the project.
        """)
    private String name;

    @Schema(description = """
        The type of the project.
        """)
    private String type;
}
