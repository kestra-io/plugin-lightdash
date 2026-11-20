package io.kestra.plugin.lightdash.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Map;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobStatus {
    @Schema(description = """
        The UUID of the job.
        """)
    private String jobUuid;

    @Schema(description = """
        The current status of the job (`scheduled`, `started`, `completed`, or `error`).
        """)
    private String status;

    @Schema(description = """
        Additional details, error messages, or metadata about the job execution.
        """)
    private Map<String, Object> details;
}
