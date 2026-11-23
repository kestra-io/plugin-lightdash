package io.kestra.plugin.lightdash.projects;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class RefreshTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void refreshAndWaitCompleted(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            post(urlEqualTo("/api/v1/projects/proj-123/refresh"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "jobUuid": "job-abc-456"
                      }
                    }
                    """))
        );

        stubFor(
            get(urlEqualTo("/api/v1/schedulers/job/job-abc-456/status"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "jobUuid": "job-abc-456",
                        "status": "completed",
                        "details": {
                          "modelsCount": 42
                        }
                      }
                    }
                    """))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = Refresh.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .wait(Property.ofValue(true))
            .pollInterval(Property.ofValue(Duration.ofMillis(50)))
            .build();

        var output = task.run(runContext);

        assertThat(output.getJobUuid(), is("job-abc-456"));
        assertThat(output.getStatus(), is("completed"));
        assertThat(output.getDetails(), notNullValue());
        assertThat(output.getDetails().get("modelsCount"), is(42));
    }

    @Test
    void refreshNoWait(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            post(urlEqualTo("/api/v1/projects/proj-123/refresh"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "jobUuid": "job-nowait-789"
                      }
                    }
                    """))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = Refresh.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .wait(Property.ofValue(false))
            .build();

        var output = task.run(runContext);

        assertThat(output.getJobUuid(), is("job-nowait-789"));
        assertThat(output.getStatus(), is("scheduled"));
    }

    @Test
    void refreshJobErrorThrowsException(WireMockRuntimeInfo wm) {
        stubFor(
            post(urlEqualTo("/api/v1/projects/proj-err/refresh"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "jobUuid": "job-fail-111"
                      }
                    }
                    """))
        );

        stubFor(
            get(urlEqualTo("/api/v1/schedulers/job/job-fail-111/status"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "jobUuid": "job-fail-111",
                        "status": "error",
                        "details": {
                          "error": "dbt compile syntax error"
                        }
                      }
                    }
                    """))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = Refresh.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-err"))
            .wait(Property.ofValue(true))
            .pollInterval(Property.ofValue(Duration.ofMillis(50)))
            .build();

        assertThrows(IllegalStateException.class, () -> task.run(runContext));
    }

    @Test
    void refreshTimeoutThrowsException(WireMockRuntimeInfo wm) {
        stubFor(
            post(urlEqualTo("/api/v1/projects/proj-timeout/refresh"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "jobUuid": "job-timeout-222"
                      }
                    }
                    """))
        );

        stubFor(
            get(urlEqualTo("/api/v1/schedulers/job/job-timeout-222/status"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "jobUuid": "job-timeout-222",
                        "status": "running"
                      }
                    }
                    """))
        );

        var runContext = runContextFactory.of(Map.of());

        var task = Refresh.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-timeout"))
            .wait(Property.ofValue(true))
            .pollInterval(Property.ofValue(Duration.ofMillis(30)))
            .maxWait(Property.ofValue(Duration.ofMillis(100)))
            .build();

        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("Timed out waiting for Lightdash refresh job"));
    }

    @Test
    void missingProjectUuidThrowsIllegalArgumentException() {
        var runContext = runContextFactory.of(Map.of());

        var task = Refresh.builder()
            .url(Property.ofValue("http://localhost:8080"))
            .apiToken(Property.ofValue("test-token"))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("`projectUuid` is required"));
    }
}
