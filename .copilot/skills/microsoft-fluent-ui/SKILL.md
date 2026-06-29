---
name: microsoft-fluent-ui
description: "Use when building, restyling, or prototyping any web UI, dashboard, demo page, or HTML prototype that should look clean and professional. Applies Microsoft.com / Fluent Design visual language with design tokens, component patterns, and content rules. WHEN: web UI, HTML page, demo, prototype, dashboard, static site, style, Fluent Design, Microsoft style, card layout, form design."
---

# Microsoft Fluent UI Design System

當你需要建立或修改 Web UI 時，遵循以下設計規範。此規範參考 microsoft.com/zh-tw/microsoft-365 的視覺風格，以 Fluent Design System 為基礎。

## Design Tokens

### Colors

```css
:root {
  /* Primary */
  --ms-blue: #0078D4;
  --ms-blue-hover: #106EBE;
  --ms-blue-dark: #004578;
  --ms-blue-light: #DEECF9;
  --ms-blue-bg: #F3F8FC;

  /* Neutral */
  --white: #FFFFFF;
  --gray-10: #FAF9F8;
  --gray-20: #F3F2F1;
  --gray-30: #EDEBE9;
  --gray-40: #D2D0CE;
  --gray-60: #A19F9D;
  --gray-90: #605E5C;
  --gray-130: #323130;
  --gray-160: #1B1A19;
  --black: #000000;

  /* Semantic */
  --green: #107C10;
  --green-bg: #DFF6DD;
  --yellow: #FFB900;
  --yellow-bg: #FFF4CE;
  --red: #D13438;
  --red-bg: #FDE7E9;
  --purple: #5C2D91;
}
```

### Typography

- **Font stack**: `'Segoe UI', 'Microsoft JhengHei', -apple-system, BlinkMacSystemFont, system-ui, sans-serif`
- **Monospace**: `'Cascadia Code', 'Consolas', 'SF Mono', monospace`
- **Body**: 15px, line-height 1.5, color `--gray-130`
- **Heading large**: 36px, weight 600, letter-spacing -0.5px
- **Heading section**: 28px, weight 600, letter-spacing -0.3px
- **Heading card**: 16px, weight 600
- **Caption / Label**: 12-13px, weight 600, color `--gray-60` or `--gray-90`
- **Uppercase label**: 11px, weight 600, letter-spacing 0.8px, text-transform uppercase

### Spacing & Radius

- **Border radius — buttons**: 4px
- **Border radius — cards / panels**: 8px
- **Border radius — badges / pills**: 12px
- **Card padding**: 20-24px
- **Grid gap**: 12-16px
- **Section spacing**: 32-48px

### Borders & Shadows

- **Card border**: `1px solid var(--gray-30)` (not shadow by default)
- **Card hover shadow**: `0 2px 8px rgba(0,0,0,0.08)`
- **Elevated shadow**: `0 4px 12px rgba(0,0,0,0.08)` (on hover or modal)
- **Modal shadow**: `0 8px 32px rgba(0,0,0,0.18)`
- **Divider**: `1px solid var(--gray-30)`
- **Input focus**: `border-color: var(--ms-blue); box-shadow: 0 0 0 1px var(--ms-blue);`

## Component Patterns

### Navigation Bar

- Height: 48px, white background, sticky top
- Bottom border: `1px solid var(--gray-30)`
- Logo: 4-color Windows squares (inline SVG, 20x20):
  ```html
  <svg viewBox="0 0 20 20" fill="none">
    <rect x="1" y="1" width="8.5" height="8.5" fill="#F25022"/>
    <rect x="10.5" y="1" width="8.5" height="8.5" fill="#7FBA00"/>
    <rect x="1" y="10.5" width="8.5" height="8.5" fill="#00A4EF"/>
    <rect x="10.5" y="10.5" width="8.5" height="8.5" fill="#FFB900"/>
  </svg>
  ```
- Divider between logo and product name: `1px solid var(--gray-40)`, height 24px
- Product name: 14px, weight 600, color `--gray-160`
- Nav links: 13px, weight 400; active state: weight 600, color `--ms-blue`

### Buttons

| Variant | Background | Color | Border |
|---------|-----------|-------|--------|
| Primary | `--ms-blue` | white | none |
| Outline | transparent | `--ms-blue` | `1px solid --ms-blue` |
| Subtle | `--gray-20` | `--gray-130` | `1px solid --gray-40` |
| Hero Primary | white | `--ms-blue` | none |
| Outline White | transparent | white | `1px solid rgba(255,255,255,0.7)` |

All buttons: `padding: 10px 28px`, `font-size: 15px`, `font-weight: 600`, `border-radius: 4px`.
Small variant: `padding: 6px 16px`, `font-size: 13px`.

### Hero Banner

- Background: `linear-gradient(135deg, #0078D4 0%, #5C2D91 50%, #0078D4 100%)`
- Padding: 56px 48px
- Text: white, h1 36px weight 600
- Subtle radial glow overlay with `rgba(255,255,255,0.08)`

### Cards

- White background, `1px solid var(--gray-30)`, `border-radius: 8px`
- Padding: 20px
- Hover: `box-shadow: 0 2px 8px rgba(0,0,0,0.08)`
- Top accent bar (optional): 3px height via `::before`, colored by state
  - Default: `--gray-30`
  - Running: `--ms-blue`
  - Done: `--green`

### Progress Bars

- Track: 4px height (6px for large), `--gray-20` background, `border-radius: 2px`
- Fill: `--ms-blue`, `transition: width 0.4s ease`

### Badges / Pills

- Padding: `2px 10px`, `border-radius: 12px`, `font-size: 12px`, weight 600
- Waiting: bg `--gray-20`, color `--gray-90`
- Running: bg `--ms-blue-light`, color `--ms-blue`
- Done: bg `--green-bg`, color `--green`
- Error: bg `--red-bg`, color `--red`

### Tables

- Full width, collapse borders
- Header: bg `--gray-10`, font 12px uppercase weight 600, color `--gray-90`
- Cell padding: `10px 16px`
- Row border: `1px solid var(--gray-20)`
- Hover: bg `--gray-10`

### KPI Cards

- White background, `1px solid var(--gray-30)`, `border-radius: 8px`
- Value: 28-34px, weight 700
- Label: 12px, color `--gray-60`
- Accent variant: bg `--ms-blue`, white text

### Forms

- Input: `padding: 8px 12px`, `border: 1px solid var(--gray-40)`, `border-radius: 4px`
- Focus: `border-color: var(--ms-blue)`, `box-shadow: 0 0 0 1px var(--ms-blue)`
- Label: 13px, weight 600, color `--gray-90`

### Upload Area

- `border: 2px dashed var(--gray-40)`, `border-radius: 8px`
- `padding: 48px 24px`, text-align center
- Hover / dragover: border `--ms-blue`, bg `--ms-blue-bg`
- Upload link: color `--ms-blue`, weight 600

### Log / Terminal Panel

- Background: `--gray-160` (#1B1A19)
- Font: monospace, 12px, line-height 1.8
- Text: `rgba(255,255,255,0.8)`
- Max height: 280px with overflow-y auto
- Phase markers: `--yellow`
- Success: `#6BCB77`
- Warning: `#FFB347`
- Timestamp: `--gray-60`

### Modals

- Overlay: `rgba(0,0,0,0.5)`, centered flex
- Container: white, `border-radius: 8px`, max-width 900px, max-height 85vh
- Header: `border-bottom: 1px solid var(--gray-30)`, padding `16px 24px`
- Animation: fadeIn 0.15s + slideUp 0.2s

### Footer

- `border-top: 1px solid var(--gray-30)`
- Text: 12px, color `--gray-60`, centered, padding 24px

## Content Guidelines

- **No brand text** on the interface (no "Microsoft", no company names unless explicitly requested)
- **No decorative emojis** — use SVG icons or Unicode symbols (▶, ⬇, ✕) for functional indicators only
- **Language**: match user's language; default to Traditional Chinese (繁體中文) for zh-TW contexts
- **Tone**: professional, clean, understated — avoid exclamation marks and excitable phrasing

## Responsive Breakpoints

```css
@media (max-width: 768px) {
  /* Stack grids to single column */
  /* Reduce hero padding and font sizes */
  /* Reduce nav padding */
  /* Modal near full-width: 96vw */
}
```

## Implementation Notes

- Pure HTML/CSS/JS — no build tools, no frameworks required
- All styles via CSS custom properties for easy theming
- Cards use `1px solid` borders, not box-shadows, as primary visual boundary
- Hero uses CSS gradient + radial glow (no images)
- Logo is inline SVG (no external assets)
- All interactive states use `transition: 0.1s-0.15s` for snappy feel
- Prefer `font-weight: 600` for emphasis (not bold/700 except KPI values)

## Quick Start

When asked to build a UI, apply this design system by:

1. Set `:root` CSS variables per the Design Tokens section
2. Use the component patterns above for layout structure
3. Follow Content Guidelines (no emojis, no brand text)
4. Ensure responsive behavior at 768px breakpoint
5. Use `Segoe UI` font stack for all text
