source local/api-curl-config.sh

TOKEN=$(curl -s "https://$OKTA_URL_PREFIX.okta.com/oauth2/default/v1/token" \
  -u "$OKTA_API_CLIENT_ID:$OKTA_API_CLIENT_SECRET" \
  -d "grant_type=client_credentials&scope=$OKTA_SCOPES" | jq -r .access_token)

curl -s "$AWS_LAMBDA_URL/hello?who=world" \
      -H "Authorization: Bearer $TOKEN" \
      -H "content-type: application/json" \
      -d '{"greeting": "hi from curl"}' | jq