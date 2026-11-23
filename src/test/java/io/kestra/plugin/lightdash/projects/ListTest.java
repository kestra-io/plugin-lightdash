package io.kestra.plugin.lightdash.projects;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class ListTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final String PROJECTS_JSON = """
        {
          "status": "ok",
          "results": [
            {
              "projectUuid": "proj-uuid-1",
              "name": "Jaffle Shop Analytics",
              "type": "DEFAULT"
            },
            {
              "projectUuid": "proj-uuid-2",
              "name": "Marketing Attribution",
              "type": "DEFAULT"
            }
          ]
        }
        """;

    @Test
    void listProjectsSuccess(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            get(urlEqualTo("/api/v1/org/projects"))
                .withHeader("Authorization", equalTo("ApiKey test-api-token"))
                .willReturn(okJson(PROJECTS_JSON))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = List.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-api-token"))
            .build();

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getProjects(), hasSize(2));
        assertThat(output.getProjects().get(0).getProjectUuid(), is("proj-uuid-1"));
        assertThat(output.getProjects().get(0).getName(), is("Jaffle Shop Analytics"));
        assertThat(output.getProjects().get(1).getProjectUuid(), is("proj-uuid-2"));
        assertThat(output.getProjects().get(1).getName(), is("Marketing Attribution"));
    }

    @Test
    void listProjectsUnauthorizedThrowsException(WireMockRuntimeInfo wm) {
        stubFor(
            get(urlEqualTo("/api/v1/org/projects"))
                .willReturn(aResponse().withStatus(401).withBody("{\"status\":\"error\",\"message\":\"Unauthorized\"}"))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = List.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("invalid-token"))
            .build();

        var ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("401 Unauthorized"));
    }

    @Test
    void listProjectsNotFoundThrowsException(WireMockRuntimeInfo wm) {
        stubFor(
            get(urlEqualTo("/api/v1/org/projects"))
                .willReturn(aResponse().withStatus(404).withBody("{\"status\":\"error\",\"message\":\"Not Found\"}"))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = List.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .build();

        var ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("404 Not Found"));
    }

    @Test
    void listProjectsWithPebbleRendering(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            get(urlEqualTo("/api/v1/org/projects"))
                .withHeader("Authorization", equalTo("ApiKey rendered-token"))
                .willReturn(okJson(PROJECTS_JSON))
        );

        var runContext = runContextFactory.of(Map.of("myUrl", wm.getHttpBaseUrl(), "myToken", "rendered-token"));

        var task = List.builder()
            .url(Property.ofExpression("{{ myUrl }}"))
            .apiToken(Property.ofExpression("{{ myToken }}"))
            .build();

        var output = task.run(runContext);
        assertThat(output.getProjects(), hasSize(2));
    }

    @Test
    void missingApiTokenThrowsIllegalArgumentException(WireMockRuntimeInfo wm) {
        var runContext = runContextFactory.of(Map.of());

        var task = List.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .build();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }
}
