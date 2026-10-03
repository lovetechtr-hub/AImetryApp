import json, subprocess, os, sys
base = os.path.dirname(os.path.abspath(__file__))
os.chdir(base)
t = open("template.html", encoding="utf-8").read()
data = open("data_fake.json", encoding="utf-8").read()
geo = open("countries.geojson", encoding="utf-8").read()
open("page.html", "w", encoding="utf-8").write(t.replace("/*DATA*/null", data).replace("/*GEO*/null", geo))
os.makedirs("out/iphone", exist_ok=True); os.makedirs("out/ipad", exist_ok=True)
chrome = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
only = sys.argv[1:]  # например: iphone:1 ipad:3
for dev, (w, h) in {"iphone": (1290, 2796), "ipad": (2064, 2752)}.items():
    for n in range(1, 7):
        if only and f"{dev}:{n}" not in only:
            continue
        out = f"out/{dev}/{n:02d}.png"
        url = f"file://{base}/page.html?s={n}&d={dev}"
        subprocess.run([chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                        "--allow-file-access-from-files", f"--window-size={w},{h}", "--virtual-time-budget=15000",
                        f"--screenshot={base}/{out}", url], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
        print(out, os.path.exists(out))
