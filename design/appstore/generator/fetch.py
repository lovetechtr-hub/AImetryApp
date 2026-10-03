import json, os, urllib.request, hashlib

API = "https://djmetry.com/api"
os.makedirs("img", exist_ok=True)

def get(path):
    with urllib.request.urlopen(API + path, timeout=30) as r:
        return json.load(r)

def img(url):
    if not url:
        return None
    name = "img/" + hashlib.md5(url.encode()).hexdigest() + ".jpg"
    if not os.path.exists(name):
        try:
            urllib.request.urlretrieve(url, name)
        except Exception:
            return None
    return name

top = get("/artists/top100")["artists"]
artists = []
for a in top[:14]:
    artists.append({
        "id": a["spotify_artist_id"], "name": a["name"], "score": a.get("score"),
        "genre": (a.get("genres") or [None])[0] or "electronic",
        "img": img(a.get("image_url")), "followers": a.get("followers"),
        "change": a.get("positionChange") or a.get("rankChange"),
    })

growing = get("/artists/trends?category=growing&limit=12&sortBy=score24h")["artists"]
trend = [{"name": g["name"], "img": img(g.get("imageUrl")), "d24": (g.get("trend") or {}).get("score24h"),
          "genre": (g.get("genres") or [None])[0] or "electronic", "score": g.get("aimetryScore")} for g in growing]

events = []
for a in artists[:8]:
    try:
        ev = get(f"/artists/{a['id']}/events").get("events", [])[:2]
    except Exception:
        ev = []
    for e in ev:
        v = e.get("venue") or {}
        events.append({"artist": a["name"], "img": a["img"], "date": e.get("datetime"), "title": e.get("title"),
                       "venue": v.get("name"), "city": v.get("city"), "country": v.get("country")})

tracks = []
for a in artists[:6]:
    try:
        t = get(f"/artists/spotify/{a['id']}/tracks?limit=2").get("tracks", [])[:1]
    except Exception:
        t = []
    for x in t:
        tracks.append({"artist": a["name"], "name": x.get("name"), "album": x.get("albumName"), "cover": img(x.get("albumImageUrl"))})

pts = get("/map/performances?limit=400").get("points", [])
points = [{"lat": p["lat"], "lng": p["lng"], "img": None, "name": p.get("artist_name")} for p in pts if p.get("lat") is not None]
# фото на маркерах — только для части точек (как кластеры в приложении)
seen = set()
for p, src in zip(points, pts):
    n = src.get("artist_name")
    if n not in seen and len(seen) < 24:
        p["img"] = img(src.get("artist_image_url")); seen.add(n)

json.dump({"artists": artists, "trend": trend, "events": events, "tracks": tracks, "points": points},
          open("data.json", "w"), ensure_ascii=False, indent=1)
print(len(artists), len(trend), len(events), len(tracks), len(points), len(os.listdir("img")))
