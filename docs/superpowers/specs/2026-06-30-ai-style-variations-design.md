# AI Style Variations + Stripe UI — Design

**Date:** 2026-06-30
**Component:** `asset-manager` (web + worker)
**Status:** Approved

## Summary

Extend the existing Asset Manager upload pipeline so that, after the worker generates a
thumbnail, it also generates **three AI style variations** of the uploaded image using
Azure OpenAI **`gpt-image-2`** (image-edit endpoint): **Realistic (写真)**,
**Cyberpunk (賽博龐克)**, and **Manga (日本漫畫)**. The three style images are stored in
Azure Blob Storage alongside the original and thumbnail. The web UI is restyled with a
**Stripe-inspired** design system, and the image detail page shows the original on top with
the three style variations in a row below it.

No new infrastructure is introduced. We reuse the existing
Upload → Service Bus → Worker → Blob/PostgreSQL pipeline.

## Decisions (from brainstorming)

1. **Generation method:** Java worker calls Azure OpenAI `gpt-image-2` **`/images/edits`**
   directly (no Python dependency).
2. **Auth:** Managed identity (`DefaultAzureCredential`, scope
   `https://cognitiveservices.azure.com/.default`), consistent with the rest of the
   modernized app. Endpoint + deployment name come from config.
3. **Data model:** Add three explicit field pairs to `ImageMetadata`
   (`realisticKey/Url`, `cyberpunkKey/Url`, `mangaKey/Url`). JPA auto-creates the columns.
4. **Flow / failures:** Same worker handler, sequential, **best-effort**. After the
   thumbnail, loop the three styles; each style is generated/uploaded/persisted in its own
   try/catch. A style failure is logged and skipped; the others still persist. The Service
   Bus message completes as long as the thumbnail (core work) succeeded.
5. **UI scope:** Full Stripe restyle across upload, gallery (list), and detail (view) pages,
   plus the new variations display.
6. **Detail layout:** Layout **C** — original photo on top (full width), the three style
   cards in a row beneath it.

## Architecture & data flow

```
User uploads image (Web UI)
  → Web: store original in Blob, save ImageMetadata, send Service Bus message
  → Worker handler (AbstractFileProcessingService.processImage):
       1. download original
       2. generate thumbnail  → upload to Blob  → update metadata   (UNCHANGED)
       3. NEW: for each style in [realistic, cyberpunk, manga]:
            a. call gpt-image-2 /images/edits with the style prompt
            b. upload returned PNG to Blob as {base}_{style}.png
            c. set metadata.<style>Key / <style>Url, save
          (each style best-effort; failures logged & skipped)
       4. complete Service Bus message
  → Web detail page: original on top + 3 style cards (Stripe UI)
```

## Components

### Worker
- **`StyleVariation` enum** — the three styles, each with: id (`realistic`/`cyberpunk`/`manga`),
  prompt text, a `keyFor(originalKey)` helper, and an `apply(ImageMetadata, key, url)` setter.
- **`ImageGenerationService`** — given the original image file + a style prompt, fetches a
  bearer token via `DefaultAzureCredential`, POSTs multipart to
  `{endpoint}/openai/deployments/{deployment}/images/edits?api-version=...`, parses
  `data[0].b64_json`, returns decoded PNG bytes. Short-circuits (skips) when the endpoint is
  not configured.
- **`FileProcessor.uploadStyleImage(...)`** — new interface method implemented by the S3
  (Blob) and Local services to upload a style image and update the matching metadata row.
- **`AbstractFileProcessingService.processImage`** — orchestrates steps 3a–3c after the
  thumbnail step.
- **`ImageMetadata`** (worker copy) — +6 style fields.

### Web
- **`ImageMetadata`** (web copy) — +6 style fields (same columns).
- **`S3StorageItem`** — +3 style key fields (`realisticKey`, `cyberpunkKey`, `mangaKey`),
  populated on the detail page from `ImageMetadata`.
- **`S3Controller.viewObjectPage`** — looks up `ImageMetadata` by `s3Key` and copies the
  three style keys onto the `S3StorageItem`.
- **Stripe theme** — `static/css/stripe-theme.css` linked from `layout.html`; `layout.html`,
  `list.html`, `upload.html`, `view.html` rewritten with the new design system. Style images
  are served through the existing `/storage/view/{key}` streaming endpoint.

## Style prompts

- **Realistic (写真):** photorealistic studio portrait — natural soft lighting, shallow depth
  of field, sharp focus, realistic textures; keep subject/pose/composition.
- **Cyberpunk (賽博龐克):** neon magenta + electric indigo lighting, holographic signage,
  rain-slicked futuristic city, high-tech dystopian mood; keep subject/pose/composition.
- **Manga (日本漫畫):** black-and-white Japanese manga line art — bold ink outlines,
  screentone shading, dramatic hatching; keep subject/pose/composition.

## Configuration (worker)

```
azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT:}
azure.openai.image-deployment=${AZURE_OPENAI_IMAGE_DEPLOYMENT:gpt-image-2}
azure.openai.image-api-version=${AZURE_OPENAI_IMAGE_API_VERSION:2025-04-01-preview}
azure.openai.image-size=${AZURE_OPENAI_IMAGE_SIZE:1024x1024}
```

When `azure.openai.endpoint` is blank (e.g. local dev without a deployment), generation is
skipped and the detail page shows the styles as "Unavailable" — the rest of the pipeline is
unaffected.

## Edge cases

- gpt-image-2 not configured / unreachable → per-style failure caught, logged; thumbnail flow
  unaffected; UI shows "Unavailable".
- Partial success persists (e.g. 2 of 3 styles).
- Style blob keys contain no `/` so they pass the existing `isValidKey` check for streaming.
- Deleting an image best-effort deletes its style blobs too.

## Testing

- **Worker (unit):** `StyleVariation.keyFor` produces `{base}_{style}.{ext}`;
  `StyleVariation.apply` sets the correct field pair; `ImageGenerationService` response parser
  decodes `b64_json` to bytes; "endpoint not configured" path returns empty/skips.
- **Web:** detail page renders present styles and degrades gracefully when style keys are null
  (manual/build verification + existing tests stay green).

## Non-goals (YAGNI)

- No new queue or separate generation worker.
- No configurable/dynamic style list (exactly three, fixed).
- No regeneration/retry UI.
