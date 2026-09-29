"""Собирает desktopApp/icons/djmetry.ico из PNG (формат ICO с PNG внутри, Windows Vista+)."""
import struct, pathlib
root = pathlib.Path(__file__).resolve().parents[2] / "desktopApp" / "icons"
sizes = [16, 24, 32, 48, 64, 128, 256]
images = [(root / "ico" / f"{s}.png").read_bytes() for s in sizes]
header = struct.pack("<HHH", 0, 1, len(images))
offset = 6 + 16 * len(images)
entries, data = b"", b""
for s, png in zip(sizes, images):
    dim = 0 if s == 256 else s  # 0 означает 256
    entries += struct.pack("<BBBBHHII", dim, dim, 0, 0, 1, 32, len(png), offset + len(data))
    data += png
(root / "djmetry.ico").write_bytes(header + entries + data)
print("", root / "djmetry.ico")
