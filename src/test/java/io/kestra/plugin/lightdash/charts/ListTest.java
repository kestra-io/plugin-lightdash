package io.kestra.plugin.lightdash.charts;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class ListTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final String CHARTS_JSON = """
        {
          "status": "ok",
          "results": [
            {
              "uuid": "chart-uuid-1",
              "name": "Monthly Active Users",
              "description": "Unique active users aggregated per month",
              "spaceUuid": "space-uuid-1",
              "spaceName": "Product Metrics",
              "chartType": "line",
              "updatedAt": "2026-11-16T12:00:00.000Z"
            },
            {
              "uuid": "chart-uuid-2",
              "name": "ARR by Tier",
              "description": "Annual recurring revenue split by subscription plan",
              "spaceUuid": "space-uuid-2",
              "spaceName": "Revenue Operations",
              "chartType": "bar",
              "updatedAt": "2026-11-16T12:30:00.000Z"
            }
          ]
        }
        """;

    @Test
    void listChartsSuccess(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            get(urlEqualTo("/api/v1/projects/proj-123/charts"))
                .withHeader("Authorization", equalTo("ApiKey test-api-token"))
                .willReturn(okJson(CHARTS_JSON))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = List.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-api-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .build();

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getCharts(), hasSize(2));
        assertThat(output.getCharts().get(0).getUuid(), is("chart-uuid-1"));
        assertThat(output.getCharts().get(0).getName(), is("Monthly Active Users"));
        assertThat(output.getCharts().get(0).getChartType(), is("line"));
        assertThat(output.getCharts().get(0).getUpdatedAt(), is(java.time.Instant.parse("2026-11-16T12:00:00.000Z")));
        assertThat(output.getCharts().get(1).getUuid(), is("chart-uuid-2"));
        assertThat(output.getCharts().get(1).getName(), is("ARR by Tier"));
        assertThat(output.getCharts().get(1).getChartType(), is("bar"));
        assertThat(output.getCharts().get(1).getUpdatedAt(), is(java.time.Instant.parse("2026-11-16T12:30:00.000Z")));
    }

    @Test
    void missingProjectUuidThrowsIllegalArgumentException() {
        var runContext = runContextFactory.of(Map.of());

        var task = List.builder()
            .url(Property.ofValue("http://localhost:8080"))
            .apiToken(Property.ofValue("test-token"))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("`projectUuid` is required"));
    }
}
