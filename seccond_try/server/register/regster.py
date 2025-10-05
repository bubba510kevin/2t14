from flask import Flask, jsonify, request
from threading import Lock
from itertools import count
import json
import os

lock = Lock()
n = {}  # maps ip -> assigned number
id_counter = count(1)
app = Flask(__name__)

json_dir = "/media/kevin/256GB/code/new project/assests/json"
os.makedirs(json_dir, exist_ok=True)

@app.route("/1000", methods=["POST"])
def register():
    data = request.get_json()
    ip = data.get("0")
    name = data.get("1")

    if not ip or not name:
        return jsonify({"error": "Missing ip or name"}), 400

    with lock:
        if ip not in n:
            n[ip] = next(id_counter)  # assign new unique number

    # save to file
    dump(ip, name, n[ip])
    return jsonify({"ip": ip, "name": name, "number": n[ip]}), 201


def dump(ip, name, number):
    data = {"ip": ip, "name": name, "number": number}
    file = os.path.join(json_dir, f"{ip}.json")
    with open(file, "w") as json_file:
        json.dump(data, json_file, indent=4)


if __name__ == "__main__":
    app.run(debug=True)