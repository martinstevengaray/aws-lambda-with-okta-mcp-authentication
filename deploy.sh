#!/usr/bin/env bash
# Build the Lambda zip and deploy it. Extra args are passed to `terraform apply`
# (e.g. ./deploy.sh -auto-approve). One-time setup: see README "Deploy".
set -euo pipefail
cd "$(dirname "$0")"

./gradlew build
VERSION=$(./gradlew -q printVersion)

# Raw org-specific values (OKTA_URL_PREFIX, OKTA_WEB_CLIENT_ID/SECRET, AWS_ACCOUNT_ID, ...).
source local/config.sh

# Terraform reads TF_VAR_<name> env vars as input variables (see terraform/variables.tf).
# The browser OIDC flow needs a "Web Application" Okta app (OKTA_WEB_CLIENT_ID); the
# OKTA_API_CLIENT_ID service app used by client-curl.sh cannot access /authorize.
# Empty/unset OKTA_WEB_CLIENT_ID deploys with the browser flow disabled.
# The web client secret is NOT passed through terraform — it lives in SSM
# Parameter Store, pushed by ./deploy_secrets.sh (see README "Deploy").
export TF_VAR_okta_issuer="https://${OKTA_URL_PREFIX}.okta.com/oauth2/default"
export TF_VAR_okta_web_client_id="${OKTA_WEB_CLIENT_ID:-}"
export TF_VAR_okta_scopes="${OKTA_SCOPES:-}"

# JIRA MCP tools: empty JIRA_CLOUDID deploys with the tools disabled. The API
# token is NOT passed through terraform — ./deploy_secrets.sh pushes it to SSM.
export TF_VAR_jira_email="${JIRA_EMAIL:-}"
export TF_VAR_jira_cloud_id="${JIRA_CLOUDID:-}"
export TF_VAR_mcp_write_scope="${MCP_WRITE_SCOPE:-}"

# Skipped once initialized — if the backend or providers change, delete terraform/.terraform to re-init.
if [ ! -d terraform/.terraform ]; then
  terraform -chdir=terraform init -backend-config="bucket=tfstate-${AWS_ACCOUNT_ID}" -input=false
fi

terraform -chdir=terraform apply -var "app_version=$VERSION" "$@"
