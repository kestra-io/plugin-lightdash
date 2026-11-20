package io.kestra.plugin.lightdash.dashboards;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest
class TriggerTest {

    @Inject
    private RunContextFactory runContextFactory;

    @BeforeEach
    void setUp() {
        reset();
    }

    @Test
    void triggerLifecycleWithKvWatermark(WireMockRuntimeInfo wm) throws Exception {
        var initialDashboardsJson = """
            {
              "status": "ok",
              "results": [
                {
                  "uuid": "dash-1",
                  "name": "Initial Dashboard",
                  "updatedAt": "2026-11-16T10:00:00.000Z"
                }
              ]
            }
            """;

        stubFor(
            get(urlEqualTo("/api/v1/projects/proj-trigger-test/dashboards"))
                .willReturn(okJson(initialDashboardsJson))
        );

        var trigger = Trigger.builder()
            .id("lightdash-trigger-" + IdUtils.create())
            .type(Trigger.class.getName())
            .url(Property.ofValue(wm.getHttpBaseUrl()))
            .apiToken(Property.ofValue("test-token"))
            .projectUuid(Property.ofValue("proj-trigger-test"))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);

        // 1. First poll initializes watermark to 2026-11-16T10:00:00.000Z, returns empty
        var eval1 = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(eval1.isPresent(), is(false));

        // 2. Second poll without changes returns empty
        var eval2 = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(eval2.isPresent(), is(false));

        // 3. New dashboard updated at 2026-11-16T10:30:00.000Z
        var updatedDashboardsJson = """
            {
              "status": "ok",
              "results": [
                {
                  "uuid": "dash-2",
                  "name": "Updated Dashboard",
                  "updatedAt": "2026-11-16T10:30:00.000Z"
                },
                {
                  "uuid": "dash-1",
                  "name": "Initial Dashboard",
                  "updatedAt": "2026-11-16T10:00:00.000Z"
                }
              ]
            }
            """;

        stubFor(
            get(urlEqualTo("/api/v1/projects/proj-trigger-test/dashboards"))
                .willReturn(okJson(updatedDashboardsJson))
        );

        // Third poll fires for dash-2
        var eval3 = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(eval3.isPresent(), is(true));
        var execution = eval3.get();
        assertThat(execution.getTrigger(), notNullValue());
        assertThat(execution.getTrigger().getVariables(), notNullValue());
        assertThat(execution.getTrigger().getVariables().get("count"), is(1));
        @SuppressWarnings("unchecked")
        var dashboards = (java.util.List<?>) execution.getTrigger().getVariables().get("dashboards");
        assertThat(dashboards, hasSize(1));
    }
}
