package io.kestra.plugin.lightdash.dashboards;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class GetTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final String GET_JSON = """
        {
          "status": "ok",
          "results": {
            "uuid": "dash-export-1",
            "name": "Full Analytics Dashboard",
            "description": "Comprehensive analytics export",
            "tiles": [
              {
                "uuid": "tile-1",
                "type": "saved_chart",
                "properties": {
                  "savedChartUuid": "chart-1"
                }
              }
            ],
            "filters": {
              "dimensions": []
            }
          }
        }
        """;

    @Test
    void getDashboardSuccess(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            get(urlEqualTo("/api/v2/projects/proj-123/dashboards/dash-export-1"))
                .withHeader("Authorization", equalTo("ApiKey test-api-token"))
                .willReturn(okJson(GET_JSON))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = Get.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-api-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .dashboardUuid(Property.ofValue("dash-export-1"))
            .build();

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getDashboard(), notNullValue());
        assertThat(output.getDashboard().get("uuid"), is("dash-export-1"));
        assertThat(output.getDashboard().get("name"), is("Full Analytics Dashboard"));

        @SuppressWarnings("unchecked")
        var tiles = (List<Map<String, Object>>) output.getDashboard().get("tiles");
        assertThat(tiles, hasSize(1));
        assertThat(tiles.get(0).get("uuid"), is("tile-1"));
    }

    @Test
    void missingProjectUuidThrowsIllegalArgumentException() {
        var runContext = runContextFactory.of(Map.of());

        var task = Get.builder()
            .url(Property.ofValue("http://localhost:8080"))
            .apiToken(Property.ofValue("test-token"))
            .dashboardUuid(Property.ofValue("dash-1"))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("`projectUuid` is required"));
    }

    @Test
    void missingDashboardUuidThrowsIllegalArgumentException() {
        var runContext = runContextFactory.of(Map.of());

        var task = Get.builder()
            .url(Property.ofValue("http://localhost:8080"))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-1"))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("`dashboardUuid` is required"));
    }
}
