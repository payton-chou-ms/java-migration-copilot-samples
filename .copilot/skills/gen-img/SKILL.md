---
name: gen-img
description: 'Use when generating or editing images with Azure OpenAI gpt-image-2 model; supports text-to-image generation and image editing with full parameter control.'
---

# GPT Image CLI

使用 Azure OpenAI 的 `gpt-image-2` 模型進行文字生圖和圖片編輯，支援完整的 API 參數配置。

## 功能特色

- **文字生圖**（`generate`）— 從 prompt 生成圖片
- **圖片編輯**（`edit`）— 上傳既有圖片 + prompt 進行修改/合成
- 完整暴露 gpt-image-2 所有可用參數（尺寸、品質、背景、壓縮等）
- 自動命名輸出檔案（含時間戳記）
- 支援多張圖片輸出（`--n` 參數）
- Microsoft Entra ID 驗證（無需 API key）

## 腳本位置

腳本與設定檔位於本 skill 目錄：

```
~/.copilot/skills/gen-img/
├── SKILL.md
├── .env              # 實際端點設定
├── .env.example      # 範本
└── scripts/
    ├── generate_image.py
    └── test_generate_image.py
```

## 使用方式

### 生成圖片

```bash
# 基本用法
python3 ~/.copilot/skills/gen-img/scripts/generate_image.py generate "一隻可愛的貓咪在草地上玩耍"

# 指定尺寸和品質
python3 ~/.copilot/skills/gen-img/scripts/generate_image.py generate "貓咪" --size 1536x1024 --quality high

# 完整參數
python3 ~/.copilot/skills/gen-img/scripts/generate_image.py generate "貓咪" \
  --size 1536x1024 \
  --quality high \
  --background transparent \
  --format png \
  --compression 80 \
  --n 2 \
  --output ./output/cat.png
```

### 編輯圖片

```bash
# 基本用法（單張輸入）
python3 ~/.copilot/skills/gen-img/scripts/generate_image.py edit --image photo.png "把背景換成海邊"

# 多張輸入 + 遮罩
python3 ~/.copilot/skills/gen-img/scripts/generate_image.py edit \
  --image item1.png --image item2.png \
  --mask mask.png \
  --input-fidelity high \
  --size 1024x1024 \
  --format webp \
  --output ./output/edited.webp \
  "把這些物品組合成一個禮物籃"
```

## 參數說明

### Generate 子命令

| 參數 | 簡寫 | 預設值 | 說明 |
|------|------|--------|------|
| `prompt` | - | 必填 | 圖片生成的文字描述 |
| `--output` | `-o` | `./output/output_YYYYMMDD_HHMMSS.png` | 輸出檔案路徑 |
| `--size` | `-s` | `auto` | 圖片尺寸：`auto`, `1024x1024`, `1536x1024`, `1024x1536` |
| `--quality` | `-q` | `auto` | 品質：`auto`, `high`, `medium`, `low` |
| `--background` | `-bg` | `auto` | 背景：`auto`, `transparent`, `opaque` |
| `--format` | `-f` | `png` | 輸出格式：`png`, `jpeg`, `webp` |
| `--compression` | - | `100` | 壓縮率 0-100（僅 jpeg/webp） |
| `--n` | - | `1` | 生成數量 1-10 |
| `--moderation` | - | `auto` | 內容過濾：`auto`, `low` |

### Edit 子命令

| 參數 | 簡寫 | 說明 |
|------|------|------|
| `prompt` | - | 編輯指令描述（必填） |
| `--image` | `-i` | 輸入圖片路徑，可重複指定（最多 16 張，必填） |
| `--mask` | `-m` | 遮罩圖片路徑（指定編輯區域） |
| `--output` | `-o` | 輸出檔案路徑 |
| `--size` | `-s` | 圖片尺寸（同 generate） |
| `--quality` | `-q` | 品質（同 generate） |
| `--background` | `-bg` | 背景（同 generate） |
| `--format` | `-f` | 輸出格式（同 generate） |
| `--compression` | - | 壓縮率（同 generate） |
| `--n` | - | 生成數量（同 generate） |
| `--moderation` | - | 內容過濾（同 generate） |
| `--input-fidelity` | - | 對原圖忠實度：`high`, `low` |

## 環境設定

`~/.copilot/skills/gen-img/.env` 檔案：

```bash
AZURE_OPENAI_ENDPOINT=https://your-resource.openai.azure.com/
AZURE_OPENAI_IMAGE_MODEL=gpt-image-2
```

或設定系統環境變數。

**驗證方式：** Microsoft Entra ID（`DefaultAzureCredential`），需先執行 `az login`。

**Python 套件：**
```bash
pip install openai azure-identity python-dotenv
```

## 輸出範例

### Generate

```
正在生成圖片...
  提示詞: 一隻可愛的貓咪
  尺寸: 1536x1024
  品質: high
  背景: auto
  格式: png
  模型: gpt-image-2
✅ 圖片已儲存至: ./output/output_20260123_143052.png
```

### Edit（多張輸出）

```
正在編輯圖片...
  編輯指令: 把這些物品組合成禮物籃
  輸入圖片數: 2
  尺寸: auto
  品質: auto
  格式: png
  模型: gpt-image-2
✅ 圖片已儲存至: ./output/edited_20260123_143052_001.png
✅ 圖片已儲存至: ./output/edited_20260123_143052_002.png
```

## 特殊行為

- **Transparent + 非 PNG 格式**：自動強制改為 PNG（因為 jpeg/webp 不支援透明）
- **PNG + 壓縮參數**：顯示警告（PNG 不支援壓縮）
- **多張輸出**：檔名加後綴 `_001`, `_002` 等
- **缺少輸入圖片**：清晰錯誤訊息並 exit 1

## API 參考

- [Azure OpenAI Images API](https://learn.microsoft.com/azure/ai-services/openai/reference#image-generation)
