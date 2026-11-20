# How to use the Lightdash plugin

This sub-group of plugins contains tasks for interacting with Lightdash, an open-source BI platform powered by your dbt semantic layer.

## Authentication

All tasks and triggers in this plugin require authentication using a Lightdash Personal Access Token (API key). The token is transmitted securely via the `Authorization: ApiKey <token>` HTTP header.

The common connection properties across tasks and triggers are:
- `url`: The base URL of your Lightdash instance (defaults to `https://app.lightdash.cloud`).
- `apiToken`: The Personal Access Token generated from your Lightdash personal settings. It should always be stored securely in Kestra [secrets](https://kestra.io/docs/concepts/secret).
- `requestTimeout`: The HTTP request timeout duration (defaults to `PT30S`).

You can configure these connection properties globally or per namespace using [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

### Projects
- `io.kestra.plugin.lightdash.projects.List`: Lists all projects within the authenticated organization.
  - Optional: `url`, `apiToken`, `requestTimeout`.
- `io.kestra.plugin.lightdash.projects.Refresh`: Triggers a dbt compilation and cache refresh for a project and optionally waits for scheduler job completion.
  - Required: `projectUuid`.
  - Optional: `wait`, `pollInterval`, `maxWait`, `url`, `apiToken`, `requestTimeout`.

### Charts
- `io.kestra.plugin.lightdash.charts.List`: Lists all saved charts and visualizations in a project.
  - Required: `projectUuid`.
  - Optional: `url`, `apiToken`, `requestTimeout`.
- `io.kestra.plugin.lightdash.charts.Run`: Executes a saved chart query using the async execution flow and returns results as execution outputs (`FETCH`, `FETCH_ONE`) or streams them directly to Kestra internal storage (`STORE`).
  - Required: `projectUuid`, `chartUuid`.
  - Optional: `fetchType`, `pollInterval`, `maxWait`, `url`, `apiToken`, `requestTimeout`.

### Dashboards
- `io.kestra.plugin.lightdash.dashboards.List`: Lists all dashboards in a project.
  - Required: `projectUuid`.
  - Optional: `url`, `apiToken`, `requestTimeout`.
- `io.kestra.plugin.lightdash.dashboards.Get`: Retrieves the full dashboard JSON definition, including tiles, filters, and spaces.
  - Required: `projectUuid`, `dashboardUuid`.
  - Optional: `url`, `apiToken`, `requestTimeout`.

## Triggers

- `io.kestra.plugin.lightdash.dashboards.Trigger`: Polling trigger that detects created or modified dashboards within a project using namespace KV Store watermark tracking.
  - Required: `projectUuid`, `interval`.
  - Optional: `url`, `apiToken`, `requestTimeout`.
