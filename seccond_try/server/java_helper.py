
import sys
import json
import shutil
from pathlib import Path
from typing import Callable, Dict, List, Optional

# Path to JSON that defines commands & built-in/custom lists
COMMANDS_JSON = Path("commands.json")

# Base drop folder
DROP_BASE = Path("drop")

# Registry for custom command handlers and flag handlers
_custom_handlers: Dict[str, Callable[[dict, Path], None]] = {}
_flag_handlers: Dict[str, Callable[[dict, Path], None]] = {}

# ----------------- Decorators / registration -----------------
def custom_command(name: str):
    """Register a custom command handler."""
    def deco(func: Callable[[dict, Path], None]):
        _custom_handlers[name] = func
        return func
    return deco

def flag_handler(flag: str):
    """Register a flag-specific handler."""
    def deco(func: Callable[[dict, Path], None]):
        _flag_handlers[flag] = func
        return func
    return deco

# ----------------- Utilities -----------------
def load_commands_json(path: Path) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"{path} not found")
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def parse_raw_command(raw: str):
    """Split the raw command into: number, command, flags, extra"""
    toks = []
    cur = ""
    in_quote = False
    quote_char = ""
    for c in raw.strip():
        if c in ('"', "'"):
            if not in_quote:
                in_quote = True
                quote_char = c
                cur = ""
            elif in_quote and c == quote_char:
                in_quote = False
                toks.append(cur)
                cur = ""
                quote_char = ""
            else:
                cur += c
        elif c.isspace() and not in_quote:
            if cur != "":
                toks.append(cur)
                cur = ""
        else:
            cur += c
    if cur != "":
        toks.append(cur)

    if len(toks) < 2:
        raise ValueError("Command must contain at least a number and a command name")

    number = toks[0]
    command = toks[1]

    flags = []
    extras = []
    for t in toks[2:]:
        if t.startswith("-"):
            flags.append(t)
        else:
            extras.append(t)

    extra = extras[0] if extras else ""
    return number, command, flags, extra

def ensure_drop_folder(number: str) -> Path:
    folder = DROP_BASE / number
    folder.mkdir(parents=True, exist_ok=True)
    return folder

def copy_extra_file(extra: str, dest_folder: Path) -> Optional[str]:
    path = Path(extra)
    if not path.exists():
        return None
    dest = dest_folder / path.name
    shutil.copy2(path, dest)
    return str(dest.name)

# ----------------- Example Custom Handlers -----------------
@custom_command("download")
def handle_download(context: dict, folder: Path):
    """Custom 'download' command handler."""
    # Example: add a placeholder file
    placeholder = folder / "download_helper.txt"
    placeholder.write_text("Download helper logic goes here", encoding="utf-8")
    print(f"[download] helper file created: {placeholder.name}")

@flag_handler("-x")
def handle_flag_x(context: dict, folder: Path):
    note_file = folder / "flag_x_used.txt"
    note_file.write_text("flag -x was used\n", encoding="utf-8")
    print("[flag handler] -x processed and recorded.")

# ----------------- Main processing -----------------
def process_command(raw_cmd: str) -> dict:
    config = load_commands_json(COMMANDS_JSON)

    number, command, flags, extra = parse_raw_command(raw_cmd)
    print(f"Parsed -> number: {number}, command: {command}, flags: {flags}, extra: {extra}")

    builtin_list: List[str] = config.get("command", {}).get("CMD", [])
    custom_list: List[str] = config.get("command", {}).get("custom", [])

    is_builtin = command in builtin_list
    is_custom = command in custom_list

    if not (is_builtin or is_custom):
        print(f"Warning: command '{command}' not recognized.")

    # Only include flags present in the input; all definitions now come from Python
    valid_flags = [f for f in flags if f in _flag_handlers]
    invalid_flags = [f for f in flags if f not in _flag_handlers]
    if invalid_flags:
        print(f"Warning: unhandled flags: {invalid_flags}")

    # Build final JSON
    out_json = {
        "command1": command if is_builtin else "",
        "command2": command if is_custom else "",
        "flag": valid_flags,
        "extra": extra or ""
    }

    drop_folder = ensure_drop_folder(number)

    # Run custom command handler
    if is_custom and command in _custom_handlers:
        try:
            _custom_handlers[command]({"raw": raw_cmd}, drop_folder)
        except Exception as e:
            print(f"[handler error] custom command {command} handler failed: {e}")

    # Run flag handlers
    for f in valid_flags:
        if f in _flag_handlers:
            try:
                _flag_handlers[f]({"raw": raw_cmd}, drop_folder)
            except Exception as e:
                print(f"[handler error] flag {f} handler failed: {e}")

    # Copy extra file if exists
    if extra:
        copied_name = copy_extra_file(extra, drop_folder)
        if copied_name:
            out_json["extra"] = copied_name
            print(f"Extra file copied into drop folder as: {copied_name}")
        else:
            print(f"Extra file '{extra}' not found on server; leaving as-is.")

    # Save command.json
    json_path = drop_folder / "command.json"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(out_json, f, indent=4)
    print(f"Saved command JSON at {json_path}")

    return out_json

# ----------------- CLI -----------------
if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: command_processor.py \"<raw command>\"")
        sys.exit(1)

    raw = sys.argv[1]
    result = process_command(raw)
    print(json.dumps(result, indent=2))

    
