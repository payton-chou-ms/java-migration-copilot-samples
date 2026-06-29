"""
Azure AI Content Understanding — Invoice Analyzer Template
SDK: azure-ai-contentunderstanding v1.1.0+

Usage:
    export CONTENT_UNDERSTANDING_ENDPOINT="https://<resource>.services.ai.azure.com"
    export CONTENT_UNDERSTANDING_KEY="your-api-key"   # or omit for Entra ID
    python analyze_invoice_template.py [invoice_url_or_local_path]
"""

import os
import sys

from azure.ai.contentunderstanding import ContentUnderstandingClient
from azure.ai.contentunderstanding.models import (
    AnalysisInput,
    AnalysisResult,
    ArrayField,
    DocumentContent,
    ObjectField,
)
from azure.core.credentials import AzureKeyCredential

SAMPLE_INVOICE_URL = (
    "https://raw.githubusercontent.com/"
    "Azure-Samples/azure-ai-content-understanding-assets/"
    "main/document/invoice.pdf"
)

SIMPLE_FIELDS = [
    "CustomerName", "CustomerId", "CustomerAddress",
    "VendorName", "VendorAddress",
    "InvoiceId", "InvoiceDate", "DueDate", "PurchaseOrder",
    "SubTotal", "TotalTax", "InvoiceTotal", "AmountDue",
    "BillingAddress", "ShippingAddress",
    "ServiceStartDate", "ServiceEndDate",
]


def build_client() -> ContentUnderstandingClient:
    endpoint = os.environ.get("CONTENT_UNDERSTANDING_ENDPOINT", "")
    if not endpoint:
        raise ValueError("Set CONTENT_UNDERSTANDING_ENDPOINT env var")

    key = os.environ.get("CONTENT_UNDERSTANDING_KEY")
    if key:
        return ContentUnderstandingClient(endpoint=endpoint, credential=AzureKeyCredential(key))

    from azure.identity import DefaultAzureCredential
    return ContentUnderstandingClient(endpoint=endpoint, credential=DefaultAzureCredential())


def analyze_invoice(source: str) -> AnalysisResult:
    client = build_client()

    if source.startswith(("http://", "https://")):
        poller = client.begin_analyze(
            analyzer_id="prebuilt-invoice",
            inputs=[AnalysisInput(url=source)],
        )
    else:
        with open(source, "rb") as f:
            poller = client.begin_analyze(
                analyzer_id="prebuilt-invoice",
                body=f.read(),
                content_type="application/octet-stream",
            )
    return poller.result()


def print_field(name: str, field) -> None:
    if field is None:
        return
    if isinstance(field, ObjectField) and field.value:
        amount = field.value.get("Amount")
        currency = field.value.get("CurrencyCode")
        val = f"{currency.value if currency and currency.value else ''}{amount.value}" if amount else "N/A"
    else:
        val = field.value if field.value is not None else "N/A"
    conf = getattr(field, "confidence", None)
    suffix = f"  (confidence: {conf:.2f})" if conf is not None else ""
    print(f"  {name}: {val}{suffix}")


def print_line_items(items_field) -> None:
    if not isinstance(items_field, ArrayField) or not items_field.value:
        return
    print(f"\n  Line Items ({len(items_field.value)}):")
    for i, item in enumerate(items_field.value, 1):
        if not isinstance(item, ObjectField) or not item.value:
            continue
        f = item.value
        desc = f.get("Description")
        qty = f.get("Quantity")
        unit = f.get("UnitPrice")
        amt = f.get("Amount")
        print(f"  [{i}] {desc.value if desc and desc.value else 'N/A'}")
        if qty and qty.value is not None:
            print(f"      Qty: {qty.value}")
        if unit and unit.value is not None:
            print(f"      Unit Price: {unit.value}")
        if amt and amt.value is not None:
            print(f"      Amount: {amt.value}")


def print_results(result: AnalysisResult) -> None:
    if not result.contents:
        print("No content extracted.")
        return
    for idx, content in enumerate(result.contents):
        if not isinstance(content, DocumentContent):
            continue
        print(f"\n{'=' * 50}")
        print(f"  Document {idx + 1}")
        print(f"{'=' * 50}")
        if not content.fields:
            print("  No fields extracted.")
            continue
        for name in SIMPLE_FIELDS:
            print_field(name, content.fields.get(name))
        print_field("TotalAmount", content.fields.get("TotalAmount"))
        line_items = content.fields.get("LineItems")
        if line_items:
            print_line_items(line_items)


def main() -> None:
    source = sys.argv[1] if len(sys.argv) > 1 else SAMPLE_INVOICE_URL
    print(f"Analyzing: {source}\n")
    result = analyze_invoice(source)
    print_results(result)


if __name__ == "__main__":
    main()
