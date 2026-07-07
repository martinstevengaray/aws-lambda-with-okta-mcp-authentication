# mcp-server

MCP (Model Context Protocol) server for JIRA, running as an AWS Lambda behind a public
Function URL. Okta gates access: every `/mcp` request needs a valid Okta bearer token.
JIRA calls use a service Atlassian API token (Basic auth) against the Jira Cloud REST API v3.

# quick start

export JAVA_HOME=$(/usr/libexec/java_home -v 21)  
./deploy.sh  
add functionUrl/callback as "Sign-in redirect URIs" on okta-admin for webapp  
./deploy_secrets.sh   # pushes the Okta web client secret AND the JIRA API token to SSM

# MCP endpoint

`POST <functionUrl>/mcp` — JSON-RPC 2.0, MCP protocol `2025-06-18` (Streamable HTTP,
plain JSON responses, no SSE). Requires `Authorization: Bearer <okta access token>`;
bad/missing tokens get `401` (the browser redirect flow only runs on other paths).

Connect from Claude Code:

    claude mcp add --transport http jira "<functionUrl>/mcp" --header "Authorization: Bearer <token>"

To get a token manually: open the function URL in a browser, sign in via Okta, and copy
the `okta_token` cookie (or use the client-credentials call in `client-curl.sh`).

## Tools

| Tool | Write | Does |
|---|---|---|
| `jira_whoami` | no | Service account profile (`GET /myself`) — auth check |
| `jira_search` | no | JQL search; returns `nextPageToken` when more results exist |
| `jira_get_issue` | no | Read one issue; ADF descriptions flattened to plain text |
| `jira_create_issue` | yes | Create issue (plain-text description converted to ADF) |
| `jira_add_comment` | yes | Comment on an issue (plain text converted to ADF) |
| `jira_transition_issue` | yes | List available transitions, or execute one by id/name |

Write tools can be gated on an Okta scope: set `MCP_WRITE_SCOPE` (e.g. `jira:write`) in
`local/config.sh` before deploying, and tokens whose `scp` claim lacks it get an
`isError` tool result. Empty (default) disables the gate.

## Configuration

`local/config.sh` (gitignored) needs, in addition to the Okta values:

    export JIRA_EMAIL="<atlassian account email>"
    export JIRA_CLOUDID="<atlassian cloud id>"     # empty deploys with JIRA tools disabled
    export JIRA_TOKEN="<atlassian api token>"      # pushed to SSM by deploy_secrets.sh

Cloud id discovery: `curl https://<site>.atlassian.net/_edge/tenant_info`.
The token never goes through terraform — terraform creates the SSM parameter shell
(`/mcp-server-lambda/jira-api-token`) and `./deploy_secrets.sh` pushes the value; the
Lambda reads it at cold start through the AWS Parameters & Secrets extension.

# local development

Run the MCP server locally against real JIRA, without AWS or Okta (auth is stubbed;
the server lives in test sources so the bypass never ships in the Lambda zip):

    source local/config.sh
    ./gradlew runLocal

    curl -s localhost:8080/mcp -H 'content-type: application/json' \
      -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
    curl -s localhost:8080/mcp -H 'content-type: application/json' \
      -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"jira_whoami","arguments":{}}}'

Tests: `./gradlew test`
