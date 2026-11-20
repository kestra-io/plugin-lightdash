package io.kestra.plugin.lightdash.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardSummary {
    @Schema(description = """
        The UUID of the dashboard.
        """)
    private String uuid;

    @Schema(description = """
        The name of the dashboard.
        """)
    private String name;

    @Schema(description = """
        The description of the dashboard.
        """)
    private String description;

    @Schema(description = """
        The UUID of the space containing this dashboard.
        """)
    private String spaceUuid;

    @Schema(description = """
        The name of the space containing this dashboard.
        """)
    private String spaceName;

    @Schema(description = """
        Timestamp when the dashboard was last updated.
        """)
    private Instant updatedAt;
}
