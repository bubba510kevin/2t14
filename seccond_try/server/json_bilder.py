import json
import sys

def x(zStr, zstr2):
    data = {
        "command": zstr2
    }
    z = f"256GB/code/{zStr}/dat.json"

    with open(z, "w") as json_file:
        json.dump(data, json_file, indent=4)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python json.bulider.py <command> <target>")
        sys.exit(1)
    zStr = sys.argv[1]
    zstr2 = sys.argv[2]

    x(zStr, zstr2)
    