# Kestra Lightdash Plugin

## What

- Provides integration tasks and triggers for [Lightdash](https://www.lightdash.com/), the open-source BI platform powered by dbt semantic layers, under `io.kestra.plugin.lightdash`.
- Implements tasks across three core packages:
  - `io.kestra.plugin.lightdash.projects`: Discover organization projects and trigger asynchronous semantic layer/dbt compilation refreshes.
  - `io.kestra.plugin.lightdash.charts`: List saved charts and execute v2 asynchronous chart queries with scalar row flattening into execution outputs or Kestra internal storage.
  - `io.kestra.plugin.lightdash.dashboards`: List dashboards, retrieve complete dashboard JSON configurations, and monitor dashboard modifications via a polling trigger with KV Store watermark isolation.

## Why

- **What user problem does this solve?** Data teams orchestrating dbt transformations in Kestra need automated mechanisms to refresh Lightdash semantic layer caches immediately after data models update, extract chart query results for downstream alerting, and trigger workflows upon dashboard changes.
- **Why would a team adopt this plugin in a workflow?** It bridges data pipeline orchestration with business intelligence, eliminating stale BI dashboard caches and manual export workflows.
- **What operational/business outcome does it enable?** Guarantees business stakeholders always see up-to-date BI metrics following pipeline runs and enables data-driven automation directly triggered by BI dashboard updates.

## How

### Architecture

Single-module Kestra plugin communicating with the Lightdash REST API via Kestra's core `HttpClient` (`io.kestra.core.http.client.HttpClient`), with zero external HTTP dependencies.

- **Connection & Transport:**
  - `LightdashConnection`: Interface defining standardized connection properties (`url`, `apiToken`, `requestTimeout`).
  - `LightdashClient`: Shared HTTP transport helper handling URL normalization, secret header injection (`Authorization: ApiKey <token>`), request serialization, and unified error mapping.
  - `AbstractLightdashTask`: Abstract base task extending `Task` and implementing `LightdashConnection`.

- **Source Packages:**
  - `io.kestra.plugin.lightdash.projects`:
    - `List`: Lists all projects within the authenticated organization.
    - `Refresh`: Triggers a dbt compilation / cache refresh for a project (`POST /api/v1/projects/{projectUuid}/refresh`) with optional job status polling (`GET /api/v1/schedulers/job/{jobId}/status`).
  - `io.kestra.plugin.lightdash.charts`:
    - `List`: Lists all saved charts and visualizations within a project.
    - `Run`: Executes a saved chart query using the v2 async API (`POST /api/v2/projects/{projectUuid}/query/chart` and `GET /api/v2/projects/{projectUuid}/query/{queryUuid}`), flattening `ResultRow = Record<string, { value: { raw, formatted } }>` into scalar maps for Pebble usability (`FETCH`, `FETCH_ONE`, or streaming to Kestra storage via `STORE`).
  - `io.kestra.plugin.lightdash.dashboards`:
    - `List`: Lists all dashboards in a project.
    - `Get`: Retrieves the full dashboard JSON definition, tiles, filters, and metadata (`GET /api/v2/projects/{projectUuid}/dashboards/{dashboardUuidOrSlug}`).
    - `Trigger`: Polling trigger detecting created or modified dashboards using namespace KV store watermarks.

### Project Structure

```
plugin-lightdash/
├── src/main/java/io/kestra/plugin/lightdash/
│   ├── AbstractLightdashTask.java
│   ├── LightdashClient.java
│   ├── LightdashConnection.java
│   ├── charts/
│   │   ├── List.java
│   │   └── Run.java
│   ├── dashboards/
│   │   ├── Get.java
│   │   ├── List.java
│   │   └── Trigger.java
│   ├── models/
│   │   ├── ChartSummary.java
│   │   ├── DashboardSummary.java
│   │   ├── JobStatus.java
│   │   └── ProjectSummary.java
│   └── projects/
│       ├── List.java
│       └── Refresh.java
├── src/main/resources/
│   ├── doc/
│   ├── icons/
│   └── metadata/
├── src/test/java/io/kestra/plugin/lightdash/
└── src/test/resources/
    └── flows/
```

## Local Rules & Invariants

- **Hermetic Testing:** 100% of unit tests run offline against embedded WireMock servers (`WireMockRuntimeInfo`).
- **Secret Redaction:** All API tokens use `@PluginProperty(group = "connection", secret = true)`, `@ToString.Exclude`, and `@EqualsAndHashCode.Exclude`.
- **Property Conventions:** All rendered dynamic variables follow the `r*` prefix convention (`rChartUuid`, `rProjectUuid`, `rDashboardUuid`).
- **Strict Backward Compatibility:** Omitted parameters preserve existing behavior; outputs never leak credentials.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
- https://docs.lightdash.com/api-reference/v2/execute-saved-chart
