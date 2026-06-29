---
name: youtube-to-srt-zh
description: '下載 YouTube 影片為 MP4，用 Azure Speech-to-Text 產生英文 SRT 字幕，再翻譯為繁體中文 SRT。USE FOR: download youtube video, youtube to mp4, youtube srt, youtube subtitle, youtube transcribe, 下載 YouTube 影片, YouTube 字幕, YouTube 轉字幕, 下載影片加字幕, youtube translate subtitle zh-TW, 影片翻譯字幕. DO NOT USE FOR: 已有字幕只需翻譯（用 subtitle-vtt-to-srt-zh）、已有影片只需轉錄（用 video-to-srt）、非 YouTube 來源影片。'
argument-hint: '[youtube-urls]，例如：https://www.youtube.com/watch?v=xxx 或多個 URL 空格分隔'
---

# YouTube 影片下載、轉錄與繁中字幕

從 YouTube 影片 URL 一站完成：下載 MP4 → 產生英文 SRT → 翻譯繁體中文 SRT。

## 何時使用

- 有一或多個 YouTube 影片需要下載並產生繁中字幕
- 需要為 YouTube 影片建立雙語字幕（英文 + 繁中）
- Demo 影片、技術演講、產品介紹需要加繁中字幕

## 不適用情境

- 已有 VTT/SRT 字幕，只需翻譯（用 `subtitle-vtt-to-srt-zh`）
- 已有本機影片，只需轉錄（用 `video-to-srt`）
- 非 YouTube 來源的影片下載

## 前置需求

- `yt-dlp`（YouTube 下載，建議保持最新版本）
- `ffmpeg` / `ffprobe`（音訊萃取）
- Azure Speech-to-Text API（憑證從 `web-fast-transcribe/.env` 讀取）
- `scripts/video-to-srt.py`（轉錄腳本）

## 輸出結構

所有檔案輸出至 `tmp/youtube/`：

```
tmp/youtube/
├── <video-id>.mp4           # 原始影片
├── <video-id>-en-US.srt     # 英文字幕
└── <video-id>-zh-TW.srt     # 繁體中文字幕
```

## 流程

### 1. 建立輸出目錄

```bash
mkdir -p tmp/youtube
```

### 2. 下載 YouTube 影片

使用 yt-dlp 下載 MP4 格式，以 video ID 命名：

```bash
cd tmp/youtube && yt-dlp \
  -f "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best" \
  --merge-output-format mp4 \
  -o "%(id)s.%(ext)s" \
  <youtube-urls>
```

**注意事項：**
- yt-dlp 版本過舊會導致 403 或下載失敗，遇到時先 `brew upgrade yt-dlp`
- 多個 URL 可一次傳入，空格分隔
- 下載失敗時自動重試，若持續失敗則更新 yt-dlp 後重試

### 3. 產生英文 SRT 字幕

對每個下載的 MP4 執行 `video-to-srt` skill 的腳本：

```bash
cd /path/to/my-tools
python scripts/video-to-srt.py tmp/youtube/<video-id>.mp4 --lang en-US
```

這會在同目錄產生 `<video-id>-en-US.srt`。

**Azure 憑證：**
- 預設從 `web-fast-transcribe/.env` 讀取 `AZURE_SPEECH_ENDPOINT` 和 `AZURE_SPEECH_API_KEY`
- 若端點無法解析，用 `az cognitiveservices account list` 找可用的 Speech 資源
- 可透過環境變數覆蓋：`AZURE_SPEECH_ENDPOINT=... AZURE_SPEECH_API_KEY=... python scripts/video-to-srt.py ...`

### 4. 翻譯為繁體中文 SRT

讀取每個英文 SRT，翻譯為繁體中文並寫入 `<video-id>-zh-TW.srt`。

**翻譯原則：**
- 繁體中文，語句流暢自然，適合閱讀
- 保留 Microsoft / GitHub 產品名稱英文（GitHub Copilot, Azure, VS Code, Teams 等）
- 技術術語首次出現可加英文括號，例如：「統一知識層（Unified Knowledge Layer）」
- 每條字幕不超過兩行，每行不超過 20 個中文字
- 時間碼完全不動，格式維持 SRT（`HH:MM:SS,mmm`）
- 數字、百分比、年份不可翻錯

**並行翻譯：** 若有多個 SRT 檔，使用 subagent 平行翻譯各檔以加速。

### 5. 驗證

- 確認每個影片都有 3 個檔案：`.mp4`、`-en-US.srt`、`-zh-TW.srt`
- SRT 序號連續、時間碼格式正確
- 繁中翻譯通順，產品名稱保留英文

## 完成標準

- [ ] 所有影片已下載為 MP4
- [ ] 每個影片都有對應的英文 SRT
- [ ] 每個影片都有對應的繁中 SRT
- [ ] 列出最終檔案清單（含檔名與大小）

## 範例提示詞

- `下載這個 YouTube 影片並產生繁中字幕：https://www.youtube.com/watch?v=xxx`
- `幫我下載這三個 YouTube 影片，產生英文和繁中 SRT`
- `Download YouTube videos and create zh-TW subtitles`
