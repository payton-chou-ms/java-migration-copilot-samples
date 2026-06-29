#!/usr/bin/env python3
"""
簡單的 generate_image.py 測試

驗證:
- CLI 幫助文字
- Argument 解析
- 錯誤處理（缺少環境變數）
"""

import subprocess
import sys
from pathlib import Path

script_path = Path(__file__).parent / "generate_image.py"


def test_help_generate():
    """測試 generate 子命令幫助"""
    result = subprocess.run(
        [sys.executable, str(script_path), "generate", "--help"],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, f"generate --help 應成功，但得到: {result.stderr}"
    assert "提示詞" in result.stdout, "help 應包含 '提示詞'"
    assert "--size" in result.stdout, "help 應包含 '--size'"
    assert "--quality" in result.stdout, "help 應包含 '--quality'"
    print("✅ test_help_generate 通過")


def test_help_edit():
    """測試 edit 子命令幫助"""
    result = subprocess.run(
        [sys.executable, str(script_path), "edit", "--help"],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, f"edit --help 應成功，但得到: {result.stderr}"
    assert "--image" in result.stdout, "help 應包含 '--image'"
    assert "--mask" in result.stdout, "help 應包含 '--mask'"
    assert "--input-fidelity" in result.stdout, "help 應包含 '--input-fidelity'"
    print("✅ test_help_edit 通過")


def test_no_endpoint_error():
    """測試缺少 AZURE_OPENAI_ENDPOINT 或認證時的錯誤"""
    import os
    env = os.environ.copy()
    # 移除 AZURE_OPENAI_ENDPOINT
    env.pop("AZURE_OPENAI_ENDPOINT", None)

    result = subprocess.run(
        [sys.executable, str(script_path), "generate", "test"],
        capture_output=True,
        text=True,
        env=env,
    )
    # 應該失敗（無論是環境變數還是認證）
    assert result.returncode == 1, f"無認證應 exit 1，但得到 exit {result.returncode}"
    # 錯誤訊息應在 stderr
    assert len(result.stderr) > 0, "應有錯誤訊息"
    print("✅ test_no_endpoint_error 通過")


def test_main_help():
    """測試主命令幫助"""
    result = subprocess.run(
        [sys.executable, str(script_path), "--help"],
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, f"--help 應成功，但得到: {result.stderr}"
    assert "generate" in result.stdout, "help 應提及 generate 子命令"
    assert "edit" in result.stdout, "help 應提及 edit 子命令"
    print("✅ test_main_help 通過")


if __name__ == "__main__":
    try:
        test_main_help()
        test_help_generate()
        test_help_edit()
        test_no_endpoint_error()
        print("\n✅ 所有測試通過")
    except AssertionError as e:
        print(f"\n❌ 測試失敗: {e}", file=sys.stderr)
        sys.exit(1)
