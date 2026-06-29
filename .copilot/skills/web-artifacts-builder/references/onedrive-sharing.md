# Sharing or previewing HTML artifacts through OneDrive/SharePoint

Read this only when an HTML artifact will be **rendered by OneDrive/SharePoint's in-browser preview** — i.e. the user **shares a link** to it, or **opens it from the OneDrive/SharePoint website**. For ordinary in-app artifacts — and for files merely **stored** in OneDrive and opened locally — ignore this file; its rules are stricter than needed.

## Why the extra rules exist

When an `.html` file is opened from OneDrive/SharePoint, it isn't served as a normal web page. It's rendered through OneDrive's built-in file preview, which runs the HTML inside a heavily **sandboxed iframe with a strict Content-Security-Policy**. The sandbox is roughly "scripts allowed, nothing else": no outbound network, no storage, no navigation, no pop-ups. Crucially, **it fails silently** — disallowed things are dropped with no console error and no message to the viewer, so a non-compliant artifact just renders blank or partially broken.

The effective policy the preview applies is approximately:

```
default-src 'none';
script-src  'unsafe-inline' 'unsafe-eval';
style-src   'unsafe-inline';
img-src     data: blob:;
font-src    data: blob:;
connect-src 'none';
worker-src  'none';
object-src  'none';
base-uri    'none';
form-action 'none';
```

plus an iframe `sandbox="allow-scripts"` (no `allow-popups`, no `allow-forms`, no `allow-same-origin` → opaque origin, which is why storage is unavailable). The eight rules in `SKILL.md` are the direct consequence of this policy.

## Authoring patterns

- **Data baked in.** If the data is known at generation time, inline it: `const DATA = { … };` (or a `<script type="application/json">` block you read with `JSON.parse`). No `fetch`.
- **Behavior.** Inline `<script>` + DOM APIs only. Inline `onclick="namedFn()"` with no data templating is fine; use `addEventListener` for any handler that touches computed/fetched/user-derived values (see rule 7 — templating untrusted data into an inline handler is an injection sink).
- **Styling.** Inline `<style>` and `style="…"`. Icons: inline SVG (`<svg viewBox=…><path d=…/></svg>`) or Unicode. Charts: draw on `<canvas>` with the 2D context — you can't import a charting library, so either render vanilla on canvas or pre-render the image and embed it as a `data:` URI.
- **Persistence across reloads.** No `localStorage`. Encode filter/view state into the URL fragment by assigning `location.hash` directly (most robust under the sandbox's opaque origin). `history.replaceState` can throw a `SecurityError` on a `null`-origin document, so if you use it, wrap it in `try/catch` and fall back to `location.hash`. Whether the fragment survives a reload depends on the host preserving it — treat in-memory state as the baseline and the hash as a best-effort restore. Keep everything else in memory.
- **Fonts.** No remote fonts. The skill's mandatory system-font stack (Segoe UI / Aptos / Calibri / system) needs no embedding, so you rarely need to do anything. If you genuinely need a custom face, embed it as a base64 `@font-face { src: url(data:font/woff2;base64,…) }`.
- **Form-shaped UI.** `<form>` renders as a widget, but `action`-based submission does nothing. Call `event.preventDefault()` on `submit` and read field values via JS.
- **Links.** Most outbound `<a href>` clicks are dropped by the sandbox; `javascript:` / `vbscript:` / `data:` hrefs are always rejected. Don't rely on navigation. In-page `#anchor` links work.

## Live OneDrive file data (the only sanctioned "fetch")

If the artifact must read **live contents of OneDrive files at view time** (not bake-time data), there is one supported mechanism instead of `fetch`: a **live-data manifest**. Embed one or more:

```html
<script type="application/json" class="ka-livedata-manifest">
  [{ "spItemUrl": "<sharepoint item url>", "fileName": "report.csv" }]
</script>
```

The preview host fetches each item server-side **with the viewing user's credentials** and injects the results as `window.__LD_RESULTS__`, **keyed by the `spItemUrl` string** (any other key field is ignored). Read the manifest at runtime to discover which URL to look up, so changing the manifest doesn't require a second edit in the consumer. Limits: **≤ 32 items per page, ≤ 50 MB per item**; oversize fails closed. Always handle the `content == null` / error case — don't assume a result exists.

To refresh, don't poll — post `window.parent.postMessage({ type: 'ka-html-viewer-refresh' }, '*')`; the host debounces (~5 s) and reloads the iframe with fresh data.

> These limits and key names are enforced by OneDrive infrastructure and can drift. If live data behaves unexpectedly, treat the live behavior as the source of truth over this doc.

## Available at runtime

Full DOM + modern JS syntax (ES2020+), `Date` / `Math` / `JSON` / `Intl` / `URL` / `Map` / `Set` / `Promise`, Canvas 2D, `setTimeout` / `requestAnimationFrame`, `crypto.getRandomValues`, and `navigator.clipboard` (on a user gesture). Everything network/storage/navigation related in the eight rules is **not** available.

## Pre-save checklist

Walk this before handing the file to the save step. Each unchecked box is a silent-failure mode the preview won't report.

- [ ] Starts with `<!DOCTYPE html>` and parses as HTML5; `<head>` opens with `<meta charset="UTF-8">`.
- [ ] No `<script src=…>`, `<link rel="stylesheet">`, or `<link rel="preconnect|preload|dns-prefetch">`.
- [ ] No `<img src>` outside `data:` / `blob:`.
- [ ] No `url(…)` or `@import` in CSS outside `data:` / `blob:`.
- [ ] No `fetch`, `XMLHttpRequest`, `WebSocket`, `EventSource`, `navigator.sendBeacon`.
- [ ] No `localStorage`, `sessionStorage`, `indexedDB`, `document.cookie`.
- [ ] No `window.open`, `location.href =`, `location.assign`, `location.replace`.
- [ ] No `new Worker`, `new SharedWorker`, `navigator.serviceWorker`.
- [ ] No `<iframe>`, `<frame>`, `<object>`, `<embed>`, `<base>`, `<meta http-equiv="refresh">`.
- [ ] No `<form action="…">` expected to actually submit.
- [ ] No `import …` / `import(…)` syntax.
- [ ] No inline `on*=` handler that templates non-author-controlled data (use `addEventListener`).
- [ ] If using live data: valid `spItemUrl` entries (≤ 32 total), and every consumer handles the null/error result.
- [ ] Saved as UTF-8 with `<meta charset="UTF-8">` present (browsers detect UTF-8 from the meta tag; a leading BOM, `0xEF 0xBB 0xBF`, is optional belt-and-suspenders for hosts that ignore it — e.g. PowerShell `Set-Content -Encoding utf8BOM`).
- [ ] Final deliverable is the saved sandbox-safe `.html` file in the workspace (which syncs to OneDrive), not the raw HTML pasted into chat. There is no programmatic share-link tool here, so the user shares/opens it from OneDrive themselves — don't fabricate a URL.
