import json
import sys
import shutil
from pathlib import Path
import os

def x(y , z):
    json_path = r"" 
    v = "t"
    with open(json_path, "r") as f:
        data = json.load(f)
        c3 = data.get("c3", {})
        c2 = data.get("c2", [])
    if y in c2:
       if y in c3:
           ex = c3[y]
           p = Path(y)
           pc = "commandpath"
           f = f"{pc}/{z}/commandfile.{p.suffix}"
           f2 = f"{pc}/{z}/"
           if z == "000":
               f2 = []
               with os.scandir("json_registry") as es:
                   for e in es:
                        f2.append(f"{pc}/{e.name}")
               for t in f2:
                    try:
                        os.remove(t)        
                    except FileNotFoundError:
                        pass                
                    shutil.copy(ex, t) 


           else:          
                for i in os.listdir(f2):
                    fp = os.path.join(f2 , i)
                    try:
                        os.remove(fp)
                    except FileNotFoundError:
                        pass  
                shutil.copy(ex, f"path/path/{z}")
       else:
           return False
    else:
        return False      

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python java_helper.py <json_path> <inputLine>")
        sys.exit(1)

    y = sys.argv[1]
    z = sys.argv[2]

    result = x(y , z)
    print(result) 
    

