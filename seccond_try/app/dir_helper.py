#!/usr/bin/env python3
import sys
import os
import json

base_dir = sys.argv[1]  # Absolute path to base directory
relative = sys.argv[2] if len(sys.argv) > 2 else ""

folder = os.path.abspath(os.path.join(base_dir, relative))

if not os.path.exists(folder) or not os.path.isdir(folder):
    print(json.dumps({"error": "NOTFOUND"}))
    sys.exit(1)

result = []
for f in os.listdir(folder):
    fpath = os.path.join(folder, f)
    result.append({
        "name": f,
        "type": "dir" if os.path.isdir(fpath) else "file"
    })

print(json.dumps({"files": result}))
