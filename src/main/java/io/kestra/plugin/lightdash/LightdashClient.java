package io.kestra.plugin.lightdash;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import java.net.URI;
import java.time.Duration;

public final class LightdashClient {
    private LightdashClient() {
    }

    public static URI resolveBaseApiUri(RunContext runContext, Property<String> urlProp) throws IllegalVariableEvaluationException {
        var rawUrl = runContext.render(urlProp).as(String.class)
            .orElse("https://app.lightdash.cloud").trim();
        while (rawUrl.endsWith("/")) {
            rawUrl = rawUrl.substring(0, rawUrl.length() - 1);
        }
        if (!rawUrl.endsWith("/api/v1") && !rawUrl.endsWith("/api/v2")) {
            rawUrl = rawUrl + "/api/v1";
        }
        return URI.create(rawUrl + "/");
    }

    public static String errorMessage(int statusCode, URI uri, String errorBody) {
        return switch (statusCode) {
            case 401 -> "Lightdash authentication failed (401 Unauthorized). Please verify that your `apiToken` is valid and active.";
            case 403 -> "Lightdash permission denied (403 Forbidden). Ensure your personal access token has access to this project or resource.";
            case 404 -> "Lightdash resource not found (404 Not Found) at " + uri + ". Verify your `projectUuid`, `chartUuid`, or `dashboardUuid`.";
            default -> "Lightdash API request to " + uri + " failed with status " + statusCode + (errorBody == null || errorBody.isBlank() ? "" : ": " + errorBody);
        };
    }

    public static String execute(
        RunContext runContext,
        Property<String> urlProp,
        Property<String> apiTokenProp,
        Property<Duration> timeoutProp,
        String method,
        String endpointPath,
        Object body
    ) throws Exception {
        var baseUri = resolveBaseApiUri(runContext, urlProp);
        URI fullUri;
        if (endpointPath.startsWith("/api/") || endpointPath.startsWith("api/")) {
            var path = endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath;
            var rootUrl = baseUri.toString().replaceAll("/api/v[12]/$", "");
            fullUri = URI.create(rootUrl + path);
        } else {
            var resolvedPath = endpointPath.startsWith("/") ? endpointPath.substring(1) : endpointPath;
            fullUri = baseUri.resolve(resolvedPath);
        }

        var token = runContext.render(apiTokenProp).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`apiToken` is required for Lightdash API access."));
        var reqTimeout = runContext.render(timeoutProp).as(Duration.class).orElse(Duration.ofSeconds(30));
        if (reqTimeout.isNegative() || reqTimeout.isZero()) {
            throw new IllegalArgumentException("`requestTimeout` must be positive.");
        }

        var timeoutConfig = TimeoutConfiguration.builder()
            .connectTimeout(Property.ofValue(reqTimeout))
            .readIdleTimeout(Property.ofValue(reqTimeout))
            .build();

        var httpConfig = HttpConfiguration.builder()
            .timeout(timeoutConfig)
            .build();

        var reqBuilder = HttpRequest.builder()
            .uri(fullUri)
            .method(method)
            .addHeader("Authorization", "ApiKey " + token)
            .addHeader("Accept", "application/json");

        if (body != null) {
            reqBuilder.addHeader("Content-Type", "application/json");
            if (body instanceof String strBody) {
                reqBuilder.body(HttpRequest.StringRequestBody.builder().content(strBody).build());
            } else {
                var jsonBody = JacksonMapper.ofJson().writeValueAsString(body);
                reqBuilder.body(HttpRequest.StringRequestBody.builder().content(jsonBody).build());
            }
        }

        var request = reqBuilder.build();

        try (var client = new HttpClient(runContext, httpConfig)) {
            HttpResponse<String> response;
            try {
                response = client.request(request, String.class);
            } catch (HttpClientResponseException e) {
                var resp = e.getResponse();
                var statusCode = resp != null && resp.getStatus() != null ? resp.getStatus().getCode() : 0;
                var errorBody = resp != null && resp.getBody() != null ? resp.getBody().toString() : "";
                throw new IllegalStateException(errorMessage(statusCode, fullUri, errorBody), e);
            }

            var responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("Empty response body received from Lightdash API during " + method + " " + fullUri);
            }

            return responseBody;
        }
    }
}
