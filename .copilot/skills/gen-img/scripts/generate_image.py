#!/usr/bin/env python3
"""
GPT Image CLI - 支援 generate 和 edit 的完整圖片工具

使用方式:
    python generate_image.py generate "圖片描述"
    python generate_image.py edit --image photo.png "修改指令"
"""

import argparse
import base64
import os
import sys
from datetime import datetime
from pathlib import Path

from azure.identity import DefaultAzureCredential, get_bearer_token_provider
from dotenv import load_dotenv
from openai import AzureOpenAI

# 載入 .env 檔案（從腳本目錄的上層）
script_dir = Path(__file__).parent
skill_dir = script_dir.parent
load_dotenv(skill_dir / ".env")


def create_client() -> AzureOpenAI:
    """建立 AzureOpenAI 客戶端"""
    endpoint = os.environ.get("AZURE_OPENAI_ENDPOINT")
    if not endpoint:
        print(
            "錯誤：需要 Azure OpenAI 端點。請設定 AZURE_OPENAI_ENDPOINT 環境變數。",
            file=sys.stderr,
        )
        sys.exit(1)

    credential = DefaultAzureCredential()
    token_provider = get_bearer_token_provider(
        credential, "https://cognitiveservices.azure.com/.default"
    )
    return AzureOpenAI(
        azure_endpoint=endpoint,
        azure_ad_token_provider=token_provider,
        api_version="2025-04-01-preview",
    )


def build_parser() -> argparse.ArgumentParser:
    """建立 argparse 解析器，包含 generate 和 edit 兩個子命令"""
    parser = argparse.ArgumentParser(
        description="GPT Image CLI - 使用 gpt-image-2 生成和編輯圖片",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
範例:
  # 生成圖片
  python generate_image.py generate "一隻可愛的貓咪"
  python generate_image.py generate "貓咪" --size 1536x1024 --quality high -o cat.png

  # 編輯圖片
  python generate_image.py edit --image photo.png "把背景換成海邊"
  python generate_image.py edit --image photo1.png --image photo2.png -m mask.png "合成"
        """,
    )

    subparsers = parser.add_subparsers(dest="command", help="子命令")

    # Generate 子命令
    gen_parser = subparsers.add_parser(
        "generate", help="從文字描述生成圖片"
    )
    gen_parser.add_argument("prompt", help="圖片生成提示詞")
    gen_parser.add_argument(
        "--output", "-o",
        default=None,
        help="輸出路徑 (預設: ./output/output_YYYYMMDD_HHMMSS.png)",
    )
    gen_parser.add_argument(
        "--size", "-s",
        choices=["auto", "1024x1024", "1536x1024", "1024x1536"],
        default="auto",
        help="圖片尺寸 (預設: auto)",
    )
    gen_parser.add_argument(
        "--quality", "-q",
        choices=["auto", "high", "medium", "low"],
        default="auto",
        help="圖片品質 (預設: auto)",
    )
    gen_parser.add_argument(
        "--background", "-bg",
        choices=["auto", "transparent", "opaque"],
        default="auto",
        help="背景類型 (預設: auto)",
    )
    gen_parser.add_argument(
        "--format", "-f",
        choices=["png", "jpeg", "webp"],
        default="png",
        help="輸出格式 (預設: png)",
    )
    gen_parser.add_argument(
        "--compression",
        type=int,
        default=100,
        help="壓縮率 0-100，僅 jpeg/webp (預設: 100)",
    )
    gen_parser.add_argument(
        "--n",
        type=int,
        default=1,
        help="生成數量 1-10 (預設: 1)",
    )
    gen_parser.add_argument(
        "--moderation",
        choices=["auto", "low"],
        default="auto",
        help="內容過濾 (預設: auto)",
    )

    # Edit 子命令
    edit_parser = subparsers.add_parser(
        "edit", help="編輯既有圖片"
    )
    edit_parser.add_argument("prompt", help="編輯指令描述")
    edit_parser.add_argument(
        "--image", "-i",
        action="append",
        dest="images",
        required=True,
        help="輸入圖片路徑 (可重複，最多16張)",
    )
    edit_parser.add_argument(
        "--mask", "-m",
        default=None,
        help="遮罩圖片路徑（指定編輯區域）",
    )
    edit_parser.add_argument(
        "--output", "-o",
        default=None,
        help="輸出路徑 (預設: ./output/edited_YYYYMMDD_HHMMSS.png)",
    )
    edit_parser.add_argument(
        "--size", "-s",
        choices=["auto", "1024x1024", "1536x1024", "1024x1536"],
        default="auto",
        help="圖片尺寸 (預設: auto)",
    )
    edit_parser.add_argument(
        "--quality", "-q",
        choices=["auto", "high", "medium", "low"],
        default="auto",
        help="圖片品質 (預設: auto)",
    )
    edit_parser.add_argument(
        "--background", "-bg",
        choices=["auto", "transparent", "opaque"],
        default="auto",
        help="背景類型 (預設: auto)",
    )
    edit_parser.add_argument(
        "--format", "-f",
        choices=["png", "jpeg", "webp"],
        default="png",
        help="輸出格式 (預設: png)",
    )
    edit_parser.add_argument(
        "--compression",
        type=int,
        default=100,
        help="壓縮率 0-100，僅 jpeg/webp (預設: 100)",
    )
    edit_parser.add_argument(
        "--n",
        type=int,
        default=1,
        help="生成數量 1-10 (預設: 1)",
    )
    edit_parser.add_argument(
        "--moderation",
        choices=["auto", "low"],
        default="auto",
        help="內容過濾 (預設: auto)",
    )
    edit_parser.add_argument(
        "--input-fidelity",
        choices=["high", "low"],
        default=None,
        help="對原圖的忠實度 (high/low)",
    )

    return parser


def get_output_path(default_prefix: str, args) -> Path:
    """取得輸出路徑"""
    if args.output:
        path = Path(args.output)
    else:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        fmt = args.format
        path = Path(f"./output/{default_prefix}_{timestamp}.{fmt}")

    # 確保輸出目錄存在
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def save_images(response_data, output_path: Path, n: int, fmt: str) -> None:
    """儲存圖片，支援多張輸出"""
    if n == 1:
        image_bytes = base64.b64decode(response_data[0].b64_json)
        with open(output_path, "wb") as f:
            f.write(image_bytes)
        print(f"✅ 圖片已儲存至: {output_path}")
    else:
        stem = output_path.stem
        suffix = output_path.suffix
        parent = output_path.parent
        for i, img_data in enumerate(response_data, 1):
            image_bytes = base64.b64decode(img_data.b64_json)
            numbered_path = parent / f"{stem}_{i:03d}{suffix}"
            with open(numbered_path, "wb") as f:
                f.write(image_bytes)
            print(f"✅ 圖片已儲存至: {numbered_path}")


def cmd_generate(args, client: AzureOpenAI) -> None:
    """執行 generate 子命令"""
    model = os.environ.get("AZURE_OPENAI_IMAGE_MODEL", "gpt-image-2")

    # 警告：background=transparent 時應強制使用 png
    if args.background == "transparent" and args.format != "png":
        print(
            f"⚠️  警告: transparent 背景需要 PNG 格式。已將格式改為 png。",
            file=sys.stderr,
        )
        args.format = "png"

    # 警告：compression 只對 jpeg/webp 有效
    if args.compression < 100 and args.format == "png":
        print(
            f"⚠️  警告: PNG 不支援壓縮。compression 參數將被忽略。",
            file=sys.stderr,
        )

    print(f"正在生成圖片...")
    print(f"  提示詞: {args.prompt}")
    print(f"  尺寸: {args.size}")
    print(f"  品質: {args.quality}")
    print(f"  背景: {args.background}")
    print(f"  格式: {args.format}")
    print(f"  模型: {model}")

    try:
        img_response = client.images.generate(
            model=model,
            prompt=args.prompt,
            size=args.size,
            quality=args.quality,
            background=args.background,
            output_format=args.format,
            output_compression=args.compression,
            n=args.n,
            moderation=args.moderation,
        )

        output_path = get_output_path("output", args)
        save_images(img_response.data, output_path, args.n, args.format)

    except Exception as e:
        print(f"❌ 錯誤: {e}", file=sys.stderr)
        sys.exit(1)


def cmd_edit(args, client: AzureOpenAI) -> None:
    """執行 edit 子命令"""
    model = os.environ.get("AZURE_OPENAI_IMAGE_MODEL", "gpt-image-2")

    # 驗證輸入圖片存在
    images_list = []
    for img_path in args.images:
        path = Path(img_path)
        if not path.exists():
            print(f"❌ 錯誤: 圖片檔案不存在: {img_path}", file=sys.stderr)
            sys.exit(1)
        images_list.append(open(path, "rb"))

    mask_file = None
    if args.mask:
        mask_path = Path(args.mask)
        if not mask_path.exists():
            print(f"❌ 錯誤: 遮罩檔案不存在: {args.mask}", file=sys.stderr)
            sys.exit(1)
        mask_file = open(mask_path, "rb")

    # 警告：background=transparent 時應強制使用 png
    if args.background == "transparent" and args.format != "png":
        print(
            f"⚠️  警告: transparent 背景需要 PNG 格式。已將格式改為 png。",
            file=sys.stderr,
        )
        args.format = "png"

    # 警告：compression 只對 jpeg/webp 有效
    if args.compression < 100 and args.format == "png":
        print(
            f"⚠️  警告: PNG 不支援壓縮。compression 參數將被忽略。",
            file=sys.stderr,
        )

    print(f"正在編輯圖片...")
    print(f"  編輯指令: {args.prompt}")
    print(f"  輸入圖片數: {len(images_list)}")
    print(f"  尺寸: {args.size}")
    print(f"  品質: {args.quality}")
    print(f"  格式: {args.format}")
    print(f"  模型: {model}")

    try:
        # 構建 API 呼叫參數
        api_params = {
            "model": model,
            "images": images_list,
            "prompt": args.prompt,
            "size": args.size,
            "quality": args.quality,
            "background": args.background,
            "output_format": args.format,
            "output_compression": args.compression,
            "n": args.n,
            "moderation": args.moderation,
        }

        if mask_file:
            api_params["mask"] = mask_file

        if args.input_fidelity:
            api_params["input_fidelity"] = args.input_fidelity

        img_response = client.images.edit(**api_params)

        output_path = get_output_path("edited", args)
        save_images(img_response.data, output_path, args.n, args.format)

    except Exception as e:
        print(f"❌ 錯誤: {e}", file=sys.stderr)
        sys.exit(1)
    finally:
        # 關閉檔案
        for img in images_list:
            img.close()
        if mask_file:
            mask_file.close()


def main():
    parser = build_parser()
    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    client = create_client()

    if args.command == "generate":
        cmd_generate(args, client)
    elif args.command == "edit":
        cmd_edit(args, client)


if __name__ == "__main__":
    main()
