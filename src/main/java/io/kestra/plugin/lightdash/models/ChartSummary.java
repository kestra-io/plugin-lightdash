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
public class ChartSummary {
    @Schema(description = """
        The UUID of the saved chart.
        """)
    private String uuid;

    @Schema(description = """
        The name of the saved chart.
        """)
    private String name;

    @Schema(description = """
        The description of the saved chart.
        """)
    private String description;

    @Schema(description = """
        The UUID of the space containing this chart.
        """)
    private String spaceUuid;

    @Schema(description = """
        The name of the space containing this chart.
        """)
    private String spaceName;

    @Schema(description = """
        The chart visualization type.
        """)
    private String chartType;

    @Schema(description = """
        Timestamp when the chart was last updated.
        """)
    private Instant updatedAt;
}
