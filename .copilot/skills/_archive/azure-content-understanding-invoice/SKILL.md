---
name: azure-content-understanding-invoice
description: "Build Python scripts to analyze invoices using Azure AI Content Understanding service. Use when: invoice extraction, Content Understanding analyzer, prebuilt-invoice, invoice OCR, extract invoice fields, Azure invoice processing, Content Understanding SDK, analyze receipt, parse invoice PDF."
argument-hint: "Describe the invoice source (URL or local file) and any custom fields to extract"
---

# Azure AI Content Understanding — Invoice Analyzer

## When to Use

- User asks to analyze, extract, or parse invoices using Azure AI
- User mentions Azure Content Understanding or `prebuilt-invoice`
- User wants to build an invoice processing pipeline on Azure
- User needs to extract structured fields (CustomerName, LineItems, TotalAmount, etc.) from invoice PDFs/images

## Prerequisites

1. **Azure subscription** with a Microsoft Foundry resource
2. **Model deployments** configured as defaults on the Foundry resource:
   - `GPT-4.1`
   - `GPT-4.1-mini`
   - `text-embedding-3-large`
3. **Python packages**: `azure-ai-contentunderstanding`, `azure-identity`

## Procedure

### Step 1 — Confirm Endpoint and Auth

Ask the user for:
- **Foundry resource endpoint** (resource-level, e.g. `https://<resource>.services.ai.azure.com`)
  - If user provides a project URL (`/api/projects/<name>`), strip the project path — Content Understanding uses the resource-level endpoint
- **Authentication method**: API Key (`Ocp-Apim-Subscription-Key`) or Entra ID (`DefaultAzureCredential`)

### Step 2 — Verify Model Deployments

Content Understanding requires default model deployments. Either:
- Direct user to [Content Understanding Settings UI](https://contentunderstanding.ai.azure.com/settings) to auto-deploy, OR
- Configure via REST:

```bash
curl -X PATCH "{endpoint}/contentunderstanding/defaults?api-version=2025-11-01" \
  -H "Ocp-Apim-Subscription-Key: {key}" \
  -H "Content-Type: application/json" \
  -d '{
    "modelDeployments": {
      "gpt-4.1": "{deployment-name}",
      "gpt-4.1-mini": "{deployment-name}",
      "text-embedding-3-large": "{deployment-name}"
    }
  }'
```

### Step 3 — Install Dependencies

```bash
pip install azure-ai-contentunderstanding azure-identity
```

### Step 4 — Write the Analyzer Script

Use the [template script](./scripts/analyze_invoice_template.py) as a starting point. Key components:

1. **Client creation**: `ContentUnderstandingClient(endpoint, credential)`
   - API Key: `AzureKeyCredential(key)`
   - Entra ID: `DefaultAzureCredential()`

2. **Submit analysis**: `client.begin_analyze(analyzer_id="prebuilt-invoice", inputs=[AnalysisInput(url=...)])`
   - For local files: pass `body=file_bytes, content_type="application/octet-stream"`

3. **Poll result**: `poller.result()` returns `AnalysisResult`

4. **Extract fields** from `result.contents[0].fields`:

| Field | Type | Description |
|-------|------|-------------|
| `CustomerName` | string | Customer name |
| `VendorName` | string | Vendor/supplier name |
| `InvoiceId` | string | Invoice number |
| `InvoiceDate` | date | Invoice issue date |
| `DueDate` | date | Payment due date |
| `SubTotal` | currency | Subtotal before tax |
| `TotalTax` | currency | Tax amount |
| `InvoiceTotal` | currency | Total amount |
| `AmountDue` | currency | Amount due |
| `LineItems` | array | Line items (Description, Quantity, UnitPrice, Amount) |
| `BillingAddress` | string | Billing address |
| `ShippingAddress` | string | Shipping address |
| `PurchaseOrder` | string | PO number |

5. **Handle currency fields**: Currency fields like `TotalAmount` are `ObjectField` with `Amount` and `CurrencyCode` sub-fields
6. **Handle line items**: `LineItems` is `ArrayField` of `ObjectField`, each with Description, Quantity, UnitPrice, Amount, ProductCode

### Step 5 — Test

Use the sample invoice for testing:
```
https://raw.githubusercontent.com/Azure-Samples/azure-ai-content-understanding-assets/main/document/invoice.pdf
```

### Step 6 — Customize (Optional)

If user needs custom fields beyond the prebuilt schema:
1. Copy prebuilt to a custom analyzer: `POST /contentunderstanding/analyzers/{id}:copy` with `{"source": "prebuilt-invoice"}`
2. Add custom field definitions to the analyzer
3. Use the custom analyzer ID instead of `prebuilt-invoice`

## API Reference

| Item | Value |
|------|-------|
| GA API Version | `2025-11-01` |
| SDK Package | `azure-ai-contentunderstanding` (v1.1.0+) |
| Analyze endpoint | `POST /contentunderstanding/analyzers/{analyzerId}:analyze` |
| Get result | `GET /contentunderstanding/analyzerResults/{requestId}` |
| Prebuilt invoice | `prebuilt-invoice` |
| Other prebuilts | `prebuilt-receipt`, `prebuilt-creditCard`, `prebuilt-creditMemo`, `prebuilt-check.us`, `prebuilt-bankStatement.us` |
| Docs | [Content Understanding docs](https://learn.microsoft.com/en-us/azure/ai-services/content-understanding/) |

## Common Pitfalls

- **Wrong endpoint**: Use the resource-level endpoint, NOT the project endpoint (`/api/projects/...`)
- **Missing model deployments**: Content Understanding silently fails without GPT-4.1/mini + embedding defaults
- **Currency fields are ObjectField**: Don't treat `TotalAmount` as a simple value — access `.value["Amount"]` and `.value["CurrencyCode"]`
- **Scope for Entra ID**: Uses `https://cognitiveservices.azure.com/.default` scope (handled by SDK automatically)
