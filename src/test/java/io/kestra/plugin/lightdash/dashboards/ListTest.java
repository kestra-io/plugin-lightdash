package io.kestra.plugin.lightdash.dashboards;

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

    private static final String DASHBOARDS_JSON = """
        {
          "status": "ok",
          "results": [
            {
              "uuid": "dash-1",
              "name": "Executive KPI Overview",
              "description": "High-level summary for executive leadership",
              "spaceUuid": "space-1",
              "spaceName": "Company KPIs",
              "updatedAt": "2026-11-16T11:00:00.000Z"
            },
            {
              "uuid": "dash-2",
              "name": "Sales Performance",
              "description": "Weekly sales metrics and pipeline",
              "spaceUuid": "space-2",
              "spaceName": "Sales",
              "updatedAt": "2026-11-16T11:30:00.000Z"
            }
          ]
        }
        """;

    @Test
    void listDashboardsSuccess(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            get(urlEqualTo("/api/v1/projects/proj-123/dashboards"))
                .withHeader("Authorization", equalTo("ApiKey test-api-token"))
                .willReturn(okJson(DASHBOARDS_JSON))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = List.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-api-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .build();

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getDashboards(), hasSize(2));
        assertThat(output.getDashboards().get(0).getUuid(), is("dash-1"));
        assertThat(output.getDashboards().get(0).getName(), is("Executive KPI Overview"));
        assertThat(output.getDashboards().get(0).getUpdatedAt(), is(java.time.Instant.parse("2026-11-16T11:00:00.000Z")));
        assertThat(output.getDashboards().get(1).getUuid(), is("dash-2"));
        assertThat(output.getDashboards().get(1).getName(), is("Sales Performance"));
        assertThat(output.getDashboards().get(1).getUpdatedAt(), is(java.time.Instant.parse("2026-11-16T11:30:00.000Z")));
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
