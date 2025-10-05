#!/usr/bin/env python3
import base64
import json
import time
from pathlib import Path
import logging

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s: %(message)s")

def binary_to_hex(binary_str: str) -> str:
    s = "".join(ch for ch in binary_str if ch in "01")
    if not s:
        return ""
    rem = len(s) % 4
    if rem != 0:
        s = s + ("0" * (4 - rem))
    return "".join(format(int(s[i:i+4], 2), 'X') for i in range(0, len(s), 4))

def hex_to_bytes(hex_str: str) -> bytes:
    hs = hex_str.strip()
    if len(hs) % 2 != 0:
        hs = "0" + hs
    try:
        return bytes.fromhex(hs)
    except ValueError as e:
        logging.error("Invalid hex string: %s", e)
        raise

def decode_message(binary_input: str) -> str:
    hex_str = binary_to_hex(binary_input)
    if not hex_str:
        return ""
    b64_bytes = hex_to_bytes(hex_str)
    try:
        base64_str = b64_bytes.decode("ascii")
    except UnicodeDecodeError:
        base64_str = b64_bytes.decode("ascii", errors="replace")
    base64_str = "".join(base64_str.split())
    try:
        utf32_bytes = base64.b64decode(base64_str)
    except (base64.binascii.Error, ValueError):
        return ""
    try:
        return utf32_bytes.decode("utf-32")
    except UnicodeDecodeError:
        try:
            return utf32_bytes.decode("utf-32-le")
        except UnicodeDecodeError:
            return utf32_bytes.decode("utf-32-be", errors="replace")

def process_file(file_path: Path, output_folder: Path, json_folder: Path):
    logging.info("Processing file: %s", file_path)
    text = file_path.read_text(encoding="utf-8", errors="ignore")
    binary_encoded_text = "".join(ch for ch in text if ch in "01")
    if not binary_encoded_text:
        logging.warning("File %s contained no binary data", file_path)
        return

    decoded_text = decode_message(binary_encoded_text)

    # Try JSON first
    try:
        data = json.loads(decoded_text)
        # JSON -> save with numeric filename
        existing = [f.stem for f in json_folder.iterdir() if f.is_file() and f.stem.isdigit()]
        last_num = max((int(x) for x in existing), default=0)
        next_num = last_num + 1
        filename = json_folder / f"{next_num:03}.json"
        filename.write_text(json.dumps(data, ensure_ascii=False, indent=4), encoding="utf-8")
        logging.info("Wrote decoded JSON to: %s", filename)
    except json.JSONDecodeError:
        # Not JSON -> treat as ZIP
        try:
            zip_bytes = base64.b64decode(decoded_text)
            filename = output_folder / f"{file_path.stem}.zip"
            with open(filename, "wb") as f:
                f.write(zip_bytes)
            logging.info("Wrote decoded ZIP to: %s", filename)
        except Exception as e:
            logging.error("Failed to decode ZIP for %s: %s", file_path, e)

def main():
    input_folder = Path("/media/kevin/256GB/code/new project/assests/json")
    zip_output_folder = Path("/media/kevin/256GB/code/new project/assests/decrypt_zip") # make an unzipper
    json_output_folder = Path("/media/kevin/256GB/code/new project/assests/decrypt_json")

    zip_output_folder.mkdir(parents=True, exist_ok=True)
    json_output_folder.mkdir(parents=True, exist_ok=True)

    processed_files = set()
    logging.info("Watching %s", input_folder)

    while True:
        for file_path in input_folder.iterdir():
            if file_path.is_file() and file_path not in processed_files:
                try:
                    process_file(file_path, zip_output_folder, json_output_folder)
                except Exception as e:
                    logging.exception("Failed to process %s: %s", file_path, e)
                processed_files.add(file_path)
        time.sleep(300)  # 5 minutes

if __name__ == "__main__":
    main()

