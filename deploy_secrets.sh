#!/usr/bin/env bash
# Push secrets from local/config.sh to SSM Parameter Store: the Okta web app
# client secret and the JIRA API token. Run after the first ./deploy.sh
# (terraform creates the parameter shells) and again whenever a secret rotates.
set -euo pipefail
cd "$(dirname "$0")"

source local/config.sh

# Terraform owns each parameter's existence; creating it here instead would make
# the first terraform apply fail with ParameterAlreadyExists.
push_param() {
  local param_name="$1" value="$2" description="$3"

  if [ -z "$value" ]; then
    echo "$description is empty — nothing to push for $param_name."
    return 0
  fi

  local current
  if ! current=$(aws ssm get-parameter --name "$param_name" --with-decryption \
    --query Parameter.Value --output text 2>/dev/null); then
    echo "Parameter $param_name not found — run ./deploy.sh first (terraform creates it)." >&2
    exit 1
  fi

  # Only write when the value changed, so parameter versions stay meaningful.
  if [ "$current" = "$value" ]; then
    echo "$param_name already up to date."
    return 0
  fi

  aws ssm put-parameter --name "$param_name" --type SecureString --overwrite \
    --value "$value" > /dev/null
  echo "$param_name updated."
}

# Must match terraform: /<aws_lambda_function_name>/...
push_param "/mcp-server-lambda/okta-web-client-secret" "${OKTA_WEB_CLIENT_SECRET:-}" "OKTA_WEB_CLIENT_SECRET"
push_param "/mcp-server-lambda/jira-api-token" "${JIRA_TOKEN:-}" "JIRA_TOKEN"
