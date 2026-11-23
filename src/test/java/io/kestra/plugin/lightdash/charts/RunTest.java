package io.kestra.plugin.lightdash.charts;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@WireMockTest
class RunTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static final String EXEC_RESPONSE_JSON = """
        {
          "status": "ok",
          "results": {
            "queryUuid": "q-uuid-456"
          }
        }
        """;

    private static final String PAGE_1_JSON = """
        {
          "status": "ok",
          "results": {
            "status": "ready",
            "queryUuid": "q-uuid-pagination",
            "page": 1,
            "pageSize": 1,
            "totalResults": 2,
            "nextPage": 2,
            "rows": [
              {
                "country": {
                  "value": {
                    "raw": "US",
                    "formatted": "United States"
                  }
                }
              }
            ]
          }
        }
        """;

    private static final String PAGE_2_JSON = """
        {
          "status": "ok",
          "results": {
            "status": "ready",
            "queryUuid": "q-uuid-pagination",
            "page": 2,
            "pageSize": 1,
            "totalResults": 2,
            "nextPage": null,
            "rows": [
              {
                "country": {
                  "value": {
                    "raw": "FR",
                    "formatted": "France"
                  }
                }
              }
            ]
          }
        }
        """;

    private static final String RESULTS_JSON = """
        {
          "status": "ok",
          "results": {
            "status": "ready",
            "queryUuid": "q-uuid-456",
            "totalResults": 2,
            "rows": [
              {
                "country": {
                  "value": {
                    "raw": "US",
                    "formatted": "United States"
                  }
                },
                "total_users": {
                  "value": {
                    "raw": 1500,
                    "formatted": "1,500"
                  }
                }
              },
              {
                "country": {
                  "value": {
                    "raw": "FR",
                    "formatted": "France"
                  }
                },
                "total_users": {
                  "value": {
                    "raw": 850,
                    "formatted": "850"
                  }
                }
              }
            ],
            "cacheMetadata": {
              "cacheHit": true
            },
            "metricQuery": {
              "exploreName": "users"
            }
          }
        }
        """;

    @Test
    void runChartFetch(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            post(urlEqualTo("/api/v2/projects/proj-123/query/chart"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(EXEC_RESPONSE_JSON)));

        stubFor(
            get(urlPathEqualTo("/api/v2/projects/proj-123/query/q-uuid-456"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(RESULTS_JSON)));

        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .chartUuid(Property.ofValue("chart-abc"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        var output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getQueryUuid(), is("q-uuid-456"));
        assertThat(output.getRows(), hasSize(2));
        assertThat(output.getRows().getFirst().get("country"), is("US"));
        assertThat(output.getRows().getFirst().get("total_users"), is(1500));
        assertThat(output.getRows().getLast().get("country"), is("FR"));
        assertThat(output.getRows().getLast().get("total_users"), is(850));
    }

    @Test
    void runChartFetchOne(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            post(urlEqualTo("/api/v2/projects/proj-123/query/chart"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(EXEC_RESPONSE_JSON)));

        stubFor(
            get(urlPathEqualTo("/api/v2/projects/proj-123/query/q-uuid-456"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(RESULTS_JSON)));

        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .chartUuid(Property.ofValue("chart-abc"))
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        var output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getQueryUuid(), is("q-uuid-456"));
        assertThat(output.getRow(), notNullValue());
        assertThat(output.getRow().get("country"), is("US"));
        assertThat(output.getRow().get("total_users"), is(1500));
    }

    @Test
    void runChartStore(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            post(urlEqualTo("/api/v2/projects/proj-123/query/chart"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(EXEC_RESPONSE_JSON)));

        stubFor(
            get(urlPathEqualTo("/api/v2/projects/proj-123/query/q-uuid-456"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(RESULTS_JSON)));

        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .chartUuid(Property.ofValue("chart-abc"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        var output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getUri(), notNullValue());

        var storedRows = new ArrayList<Object>();
        try (var is = runContext.storage().getFile(output.getUri())) {
            FileSerde.read(is, storedRows::add);
        }

        assertThat(storedRows, hasSize(2));
        @SuppressWarnings("unchecked")
        var firstRow = (Map<String, Object>) storedRows.getFirst();
        assertThat(firstRow.get("country"), is("US"));
        assertThat(firstRow.get("total_users"), is(1500));
    }

    @Test
    void missingProjectUuidThrowsIllegalArgumentException() {
        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue("http://localhost:8080"))
            .apiToken(Property.ofValue("test-token"))
            .chartUuid(Property.ofValue("chart-abc"))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("`projectUuid` is required"));
    }

    @Test
    void missingChartUuidThrowsIllegalArgumentException() {
        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue("http://localhost:8080"))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .build();

        var ex = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("`chartUuid` is required"));
    }

    @Test
    void runChartTwoPagesPagination(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            post(urlEqualTo("/api/v2/projects/proj-123/query/chart"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "queryUuid": "q-uuid-pagination"
                      }
                    }
                    """)));

        stubFor(
            get(urlEqualTo("/api/v2/projects/proj-123/query/q-uuid-pagination?page=1&pageSize=500"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(PAGE_1_JSON)));

        stubFor(
            get(urlEqualTo("/api/v2/projects/proj-123/query/q-uuid-pagination?page=2&pageSize=500"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(PAGE_2_JSON)));

        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .chartUuid(Property.ofValue("chart-abc"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        var output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getQueryUuid(), is("q-uuid-pagination"));
        assertThat(output.getRows(), hasSize(2));
        assertThat(output.getRows().getFirst().get("country"), is("US"));
        assertThat(output.getRows().getLast().get("country"), is("FR"));
    }

    @Test
    void runChartTwoPagesPaginationStore(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            post(urlEqualTo("/api/v2/projects/proj-123/query/chart"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "queryUuid": "q-uuid-pagination-store"
                      }
                    }
                    """)));

        stubFor(
            get(urlEqualTo("/api/v2/projects/proj-123/query/q-uuid-pagination-store?page=1&pageSize=500"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(PAGE_1_JSON)));

        stubFor(
            get(urlEqualTo("/api/v2/projects/proj-123/query/q-uuid-pagination-store?page=2&pageSize=500"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(PAGE_2_JSON)));

        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .chartUuid(Property.ofValue("chart-abc"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .build();

        var output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getUri(), notNullValue());

        var storedRows = new ArrayList<Object>();
        try (var is = runContext.storage().getFile(output.getUri())) {
            FileSerde.read(is, storedRows::add);
        }

        assertThat(storedRows, hasSize(2));
        @SuppressWarnings("unchecked")
        var firstRow = (Map<String, Object>) storedRows.getFirst();
        assertThat(firstRow.get("country"), is("US"));
        @SuppressWarnings("unchecked")
        var secondRow = (Map<String, Object>) storedRows.getLast();
        assertThat(secondRow.get("country"), is("FR"));
    }

    @Test
    void runChartCancelledStatusThrowsException(WireMockRuntimeInfo wm) {
        stubFor(
            post(urlEqualTo("/api/v2/projects/proj-123/query/chart"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(EXEC_RESPONSE_JSON)));

        stubFor(
            get(urlPathEqualTo("/api/v2/projects/proj-123/query/q-uuid-456"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "status": "cancelled",
                        "queryUuid": "q-uuid-456",
                        "error": "Query was cancelled by user"
                      }
                    }
                    """)));

        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .chartUuid(Property.ofValue("chart-abc"))
            .build();

        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("terminated with status 'cancelled'"));
        assertThat(ex.getMessage(), containsString("Query was cancelled by user"));
    }

    @Test
    void runChartErrorStatusThrowsException(WireMockRuntimeInfo wm) {
        stubFor(
            post(urlEqualTo("/api/v2/projects/proj-123/query/chart"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(EXEC_RESPONSE_JSON)));

        stubFor(
            get(urlPathEqualTo("/api/v2/projects/proj-123/query/q-uuid-456"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "status": "error",
                        "queryUuid": "q-uuid-456",
                        "error": "Warehouse query syntax error: column 'unknown' not found"
                      }
                    }
                    """)));

        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .chartUuid(Property.ofValue("chart-abc"))
            .build();

        var ex = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(ex.getMessage(), containsString("terminated with status 'error'"));
        assertThat(ex.getMessage(), containsString("Warehouse query syntax error"));
    }

    @Test
    void runChartKillCancelsQuery(WireMockRuntimeInfo wm) throws Exception {
        stubFor(
            post(urlEqualTo("/api/v2/projects/proj-123/query/chart"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson(EXEC_RESPONSE_JSON)));

        stubFor(
            get(urlPathEqualTo("/api/v2/projects/proj-123/query/q-uuid-456"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {
                        "status": "running",
                        "queryUuid": "q-uuid-456"
                      }
                    }
                    """)));

        stubFor(
            post(urlEqualTo("/api/v2/projects/proj-123/query/q-uuid-456/cancel"))
                .withHeader("Authorization", equalTo("ApiKey test-token"))
                .willReturn(okJson("""
                    {
                      "status": "ok",
                      "results": {}
                    }
                    """)));

        var runContext = runContextFactory.of(Map.of());

        var task = Run.builder()
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-123"))
            .chartUuid(Property.ofValue("chart-abc"))
            .pollInterval(Property.ofValue(Duration.ofMillis(100)))
            .build();

        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(task::kill, 200, TimeUnit.MILLISECONDS);

        try {
            assertThrows(IllegalStateException.class, () -> task.run(runContext));
            verify(postRequestedFor(urlEqualTo("/api/v2/projects/proj-123/query/q-uuid-456/cancel")));
        } finally {
            executor.shutdownNow();
        }
    }
}
