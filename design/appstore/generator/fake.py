# Вымышленные артисты с фото Unsplash (лицензия Unsplash: бесплатно, в т.ч. коммерчески, без указания автора).
# Концерты — настоящие площадки, города и даты из API DJMetry, подписанные вымышленными именами.
import json, os, urllib.request

PHOTOS = [
    "photo-1648090322527-138e464a299e", "photo-1720782201912-2e5518145a01", "photo-1648090326914-fcf7ad3f5aa1",
    "photo-1654134155454-0ac29533f52d", "photo-1642344741786-97a10415a0ba", "photo-1648090330497-2709a9c7801a",
    "photo-1720782202042-81026d378345", "photo-1617424969039-a0a00d63f5e2", "photo-1673131892007-2da44ca87dcc",
    "photo-1611119267731-def592cb04d5", "photo-1648090327161-7be068ca3790", "photo-1752178316956-274af6c1ee81",
    "photo-1587116288076-1e58625bb1eb", "photo-1659150140178-d672b4763fd0", "photo-1723913228439-d5601fc36894",
    "photo-1638397157669-97faf770dfc4",
]
NAMES = [("Nova Lune", "melodic techno"), ("KAIRO", "afro house"), ("Mara Vex", "techno"), ("Sol & Echo", "progressive house"),
         ("Juno Rae", "house"), ("Atlas K", "trance"), ("Lumi Saint", "tech house"), ("ODESSA", "deep house"),
         ("Riven", "drum & bass"), ("Ivy Mercer", "melodic house"), ("Teo Vance", "techno"), ("NØRTH", "progressive house"),
         ("Selene Ray", "organic house"), ("Dax Moreno", "afro house"), ("Kiko Blue", "tech house"), ("Pia Rossa", "electro")]

os.makedirs("stock", exist_ok=True)
def dl(pid, w=900):
    path = f"stock/{pid}.jpg"
    if not os.path.exists(path):
        urllib.request.urlretrieve(f"https://images.unsplash.com/{pid}?w={w}&q=80&fm=jpg&fit=crop&crop=faces", path)
    return path

real = json.load(open("data.json", encoding="utf-8"))
artists = []
for i, ((name, genre), pid) in enumerate(zip(NAMES, PHOTOS)):
    artists.append({"id": f"fake{i}", "name": name, "genre": genre, "img": dl(pid),
                    "score": round(87.4 - i * 1.37 - (i % 3) * 0.21, 2),
                    "followers": [1840000, 1320000, 986000, 742000, 655000, 512000, 431000, 388000, 296000, 241000, 205000, 176000, 154000, 132000, 118000, 97000][i]})
trend = [{"name": a["name"], "img": a["img"], "genre": a["genre"], "score": a["score"], "d24": round(6.4 - i * 0.7, 1)}
         for i, a in enumerate(artists[6:13])]
# настоящие концерты: площадка, город, дата — артисты вымышленные
events = []
for i, e in enumerate(real["events"]):
    a = artists[i % len(artists)]
    events.append({**e, "artist": a["name"], "img": a["img"]})
# «новые релизы»: вымышленные названия, обложки — градиенты (в шаблоне), без чужих обложек
TITLES = [("Midnight Signal", "Single"), ("Glass Horizon", "EP"), ("Low Tide", "Single"), ("Neon Rituals", "Album"), ("Afterglow", "Single"), ("Pulse Theory", "EP")]
tracks = [{"artist": artists[i]["name"], "name": t, "album": kind, "cover": None, "grad": i} for i, (t, kind) in enumerate(TITLES)]
points = real["points"]
k = 0
for p in points:
    if p.get("img"):
        p["img"] = artists[k % len(artists)]["img"]; k += 1
json.dump({"artists": artists, "trend": trend, "events": events, "tracks": tracks, "points": points},
          open("data_fake.json", "w"), ensure_ascii=False, indent=1)
print(len(artists), len(events), len(os.listdir("stock")))
