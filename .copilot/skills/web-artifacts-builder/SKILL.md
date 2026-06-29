---
name: web-artifacts-builder
description: Create interactive HTML artifacts (dashboards, visualizations, org charts, network maps, trackers, comparisons) as self-contained HTML files. Use whenever the user requests a web application, data visualization, interactive tool, or any HTML artifact. All output MUST use the Clawpilot theme variables defined in this skill. If the user will share the artifact or open it through OneDrive/SharePoint's in-browser preview, additionally follow the sandbox-safe rules in the "Sharing or previewing through OneDrive/SharePoint" section.
license: Complete terms in LICENSE.txt
---

# Web Artifacts Builder

## ⚠️ MANDATORY: Clawpilot Theme (read this FIRST)

**Every HTML artifact you generate MUST include all of the following.** Do not skip any part. Do not invent your own color scheme. Do not hardcode colors.

### 1. Theme detection script (put this FIRST in a `<script>` tag before any other JS)

```html
<script>
  (() => {
    const param = new URLSearchParams(window.location.search).get("scoutTheme");
    const theme =
      param || (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    document.documentElement.setAttribute("data-theme", theme);
  })();
</script>
```

### 2. CSS variables (copy this EXACTLY into your `<style>` block)

```css
:root {
  color-scheme: light;
  --cp-bg: #f7f4ef;
  --cp-bg-elevated: #fcfbf8;
  --cp-surface: #ffffff;
  --cp-surface-soft: #f5f5f5;
  --cp-border: #dedede;
  --cp-border-strong: #919191;
  --cp-text: #242424;
  --cp-text-muted: #5c5c5c;
  --cp-text-soft: #6f6f6f;
  --cp-accent: #b11f4b;
  --cp-accent-hover: #9a1a41;
  --cp-accent-soft: rgba(177, 31, 75, 0.08);
  --cp-accent-fg: #ffffff;
  --cp-success: #16a34a;
  --cp-danger: #dc2626;
  --cp-warning: #f59e0b;
  --cp-link: #0078d4;
  --cp-shadow: 0 18px 48px rgba(0, 0, 0, 0.12);
  --cp-overlay: rgba(255, 255, 255, 0.8);
  --cp-panel: rgba(255, 255, 255, 0.86);
  --cp-panel-strong: rgba(255, 255, 255, 0.96);
  --cp-sheen: rgba(255, 255, 255, 0.55);
  --cp-highlight: rgba(177, 31, 75, 0.12);
}
html[data-theme="dark"] {
  color-scheme: dark;
  --cp-bg: #3d3b3a;
  --cp-bg-elevated: #343231;
  --cp-surface: #292929;
  --cp-surface-soft: #2e2e2e;
  --cp-border: #474747;
  --cp-border-strong: #5f5f5f;
  --cp-text: #dedede;
  --cp-text-muted: #919191;
  --cp-text-soft: #b0b0b0;
  --cp-accent: #fd8ea1;
  --cp-accent-hover: #fb7b91;
  --cp-accent-soft: rgba(253, 142, 161, 0.14);
  --cp-accent-fg: #1a1a1a;
  --cp-success: #4ade80;
  --cp-danger: #f87171;
  --cp-warning: #fbbf24;
  --cp-link: #4da6ff;
  --cp-shadow: 0 18px 48px rgba(0, 0, 0, 0.32);
  --cp-overlay: rgba(41, 41, 41, 0.88);
  --cp-panel: rgba(41, 41, 41, 0.72);
  --cp-panel-strong: rgba(41, 41, 41, 0.96);
  --cp-sheen: rgba(255, 255, 255, 0.04);
  --cp-highlight: rgba(253, 142, 161, 0.12);
}
```

### 3. Use ONLY `var(--cp-*)` for all colors

- `body { background: var(--cp-bg); color: var(--cp-text); }`
- Borders: `var(--cp-border)`
- Cards/panels: `var(--cp-surface)`
- Muted text: `var(--cp-text-muted)`
- Accent/primary: `var(--cp-accent)`
- **NEVER hardcode hex/rgb/hsl color values in component styles**

### 4. Typography

- **Font**: `"Segoe UI", Aptos, Calibri, -apple-system, BlinkMacSystemFont, sans-serif`
- **Monospace**: `Consolas, "Courier New", Courier, monospace`
- Do NOT use Inter, Geist, or generic system-ui as the primary font.

### 5. Shape & Spacing

- Border radius: `0.625rem` (10px) for most UI elements, `16px` for cards
- Card shadow: `0 0 2px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.14)` — subtle, not dramatic
- Use consistent 4px-based spacing

### 6. Do / Don't

- ✅ Warm off-white backgrounds (light), dark charcoal backgrounds (dark)
- ✅ Deep rose/crimson as the single accent color
- ✅ Clean surfaces, subtle borders
- ❌ No purple gradients, teal, or generic "AI blue" accents
- ❌ No excessive rounded corners or glassmorphism
- ❌ No Inter font or heavy drop shadows
- ❌ No hardcoded colors — always use `var(--cp-*)` variables

---

## Building Artifacts

For complex multi-component apps, use the full React + shadcn/ui pipeline described below. For simpler artifacts (single-file visualizations, dashboards), generate a self-contained HTML file directly — but you **MUST still include the theme detection script and CSS variables above**.

**Stack**: React 19 + TypeScript + Vite + Parcel (bundling) + Tailwind CSS + shadcn/ui

### Step 1: Initialize Project

Run the initialization script to create a new React project:

```bash
bash scripts/init-artifact.sh <project-name>
cd <project-name>
```

This creates a fully configured project with:

- ✅ React + TypeScript (via Vite)
- ✅ Tailwind CSS 3.4.1 with shadcn/ui theming system
- ✅ Path aliases (`@/`) configured
- ✅ 40+ shadcn/ui components pre-installed
- ✅ All Radix UI dependencies included
- ✅ Parcel configured for bundling (via .parcelrc)
- ✅ Node 18+ compatibility (auto-detects and pins Vite version)

### Step 2: Develop Your Artifact

Edit the generated files. All components must use the `--cp-*` CSS variables defined above for colors — do not use shadcn/ui's default color tokens or hardcoded values.

### Step 3: Bundle to Single HTML File

```bash
bash scripts/bundle-artifact.sh
```

This creates `bundle.html` — a self-contained artifact with all JavaScript, CSS, and dependencies inlined.

**Requirements**: Your project must have an `index.html` in the root directory.

**What the script does**:

- Installs bundling dependencies (parcel, @parcel/config-default, parcel-resolver-tspaths, html-inline)
- Creates `.parcelrc` config with path alias support
- Builds with Parcel (no source maps)
- Inlines all assets into single HTML using html-inline

### Step 4: Share Artifact with User

Share the bundled HTML file with the user so they can view it as an artifact. Then apply the offer/decision logic in "Sharing or previewing through OneDrive/SharePoint" below (offer once if there was no sharing signal; don't nag).

### Step 5: Testing/Visualizing the Artifact (Optional)

Only perform if necessary or requested. Use available tools (Playwright, Puppeteer, or other browser automation). Avoid testing upfront as it adds latency — test after presenting the artifact, if requested or if issues arise.

## Sharing or previewing through OneDrive/SharePoint

By default these artifacts are for **in-app preview**, and the Clawpilot theme + React pipeline above is right for that. The extra rules here apply **only** when the HTML will be rendered by **OneDrive/SharePoint's in-browser preview** — i.e. the user **shares a link** to it, or **opens it from the OneDrive/SharePoint website**. That preview runs the HTML in a locked-down sandbox that **silently drops** external requests, network calls, stored data, and pop-ups, so a normal artifact often renders **blank or half-broken with no error**.

**Decide which case applies — and never block ordinary in-app work to ask:**

1. **Explicit share / online-view signal** (_"share"_, _"send a link"_, _"so my team/manager can see it"_, _"view it in OneDrive/SharePoint"_, _"email a link"_) → ask one short confirming question, then build sandbox-safe **from the start** (avoids a rewrite).
2. **Explicit local/in-app use, or storage-only** (_"save this to my OneDrive"_, _"back it up"_) → build normally; apply **none** of these rules. Storing a file in OneDrive and opening it locally is just a normal HTML file.
3. **No signal** → build normally, then **offer once** after delivering: _"If you'll share this or open it from OneDrive/SharePoint in the browser, I can re-save it so the online preview renders it correctly."_

Ask or offer **once per artifact** — never nag on follow-up tweaks, and never silently over-constrain an in-app artifact.

### Sandbox-safe rules (share / online-preview case only)

Apply all of these, then run the pre-save checklist in `references/onedrive-sharing.md`:

1. **No external resources** — no `<script src>`, `<link>`, remote `<img src>`, `@import`, or `url(https://…)`. Inline everything, or use `data:` / `blob:`.
2. **Scripts inline only** — no `import` / `import()` / module scripts / `<script src>`.
3. **Styles inline only** — `<style>` / `style="…"`; icons as inline SVG or Unicode.
4. **No network APIs** — no `fetch`, `XMLHttpRequest`, `WebSocket`, `EventSource`, `sendBeacon`. Bake data in as `const DATA = …`. (Live OneDrive file data → the live-data manifest; see companion.)
5. **No storage** — `localStorage` / `sessionStorage` / `indexedDB` / `document.cookie` are unavailable. Persist via `location.hash` (see companion).
6. **No top-level navigation, pop-ups, submitting forms, workers, or plugins** — `window.open`, `<form action>`, Web/Service Workers, `<object>`/`<embed>` are blocked; `alert`/`confirm`/`prompt` are no-ops. `<form>` works as a widget if you `preventDefault()` and read values in JS.
7. **Inline `on*=` handlers are an XSS sink when templated with non-author data** — `onclick="greet('${name}')"` breaks (or runs as code) the moment `name` isn't author-controlled. Use `addEventListener` + a JS variable for any handler touching computed/fetched/user data.
8. **Encoding** — keep `<meta charset="UTF-8">` in `<head>`; the app's file-save writes UTF-8, so a BOM is rarely needed. If you write the bytes yourself and want belt-and-suspenders against hosts that ignore the meta tag, save with a BOM (PowerShell: `Set-Content -Encoding utf8BOM`).

**Precedence over the rest of this skill.** In share-mode, prefer **hand-authoring a single-file HTML** over the React/Vite/Parcel pipeline: bundling satisfies only rule 1, while Parcel can emit module scripts (breaks rule 2) and app code may use `fetch`/`localStorage` (breaks rules 4–5). Keep the mandatory theme-detection script from the top of this skill — it's sandbox-safe and falls back to `prefers-color-scheme` in the preview. The required system-font stack needs no embedding, so share-mode rarely needs font work.

**Deliverable.** There is no programmatic OneDrive share-link tool here (`shareLink` is unsupported on the local workspace). Save the finished sandbox-safe `.html` into the workspace folder (it syncs to OneDrive) and tell the user they can share it or open it from OneDrive themselves. Don't fabricate a URL, and don't fall back to pasting the raw HTML into chat as the deliverable.

For per-rule detail, the live-data manifest, the hash-persistence pattern, and the full pre-save checklist, read `references/onedrive-sharing.md`.

## Reference

- **shadcn/ui components**: https://ui.shadcn.com/docs/components
