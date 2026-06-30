#!/bin/bash
export AZURE_TENANT_ID="b735b823-e3a1-47b1-bec4-e3bfca995285"
export AZURE_CLIENT_ID="ff8cecca-eccb-4001-95e4-c7eda82ccb9a"
export AZURE_CLIENT_SECRET="<YOUR_AZURE_CLIENT_SECRET>"
export AZURE_OPENAI_ENDPOINTS="https://202511-foundry-proj-resource.services.ai.azure.com/|gpt-image-2,https://202603-ai-summit-resource.services.ai.azure.com/|gpt-image-2-1,https://admin-mdxvkr77-westus3.services.ai.azure.com/|gpt-image-2,https://admin-7754-uaenorth-resource.services.ai.azure.com/|gpt-image-2,https://admin-8580-polandcentra-resource.services.ai.azure.com/|gpt-image-2"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../worker"
exec ../mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev
