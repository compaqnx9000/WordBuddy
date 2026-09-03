"""Slice WordBuddy art into Android launcher icons.

The full artwork (including the WordBuddy wordmark) is placed in the
adaptive-icon safe zone (inner 72/108). Launchers mask the outer 18 dp,
which is why an edge-to-edge export clips the bottom text.
"""

from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw

SOURCE = Path(r"D:\wordbuddy.png")
RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
PLAYSTORE = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "ic_launcher-playstore.png"
LOGO = RES / "drawable-xxhdpi" / "ic_wordbuddy_logo.png"
COLORS = RES / "values" / "colors.xml"

LEGACY = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

# Android adaptive icons are 108dp; only the inner 72dp is guaranteed visible.
SAFE_RATIO = 72 / 108


def is_outside_white(pixel: tuple[int, int, int]) -> bool:
    return min(pixel) >= 246 and max(pixel) - min(pixel) <= 8


def is_outside_pixel(pixel: tuple[int, ...]) -> bool:
    if len(pixel) == 4 and pixel[3] < 16:
        return True
    return is_outside_white(pixel[:3])


def flood_outside(image: Image.Image) -> set[tuple[int, int]]:
    rgba = image.convert("RGBA")
    width, height = rgba.size
    pixels = rgba.load()
    seen: set[tuple[int, int]] = set()
    queue: deque[tuple[int, int]] = deque()
    seeds = [(x, 0) for x in range(width)]
    seeds += [(x, height - 1) for x in range(width)]
    seeds += [(0, y) for y in range(height)]
    seeds += [(width - 1, y) for y in range(height)]
    for start in seeds:
        if start not in seen and is_outside_pixel(pixels[start]):
            seen.add(start)
            queue.append(start)
    while queue:
        x, y = queue.popleft()
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < width and 0 <= ny < height and (nx, ny) not in seen:
                if is_outside_pixel(pixels[nx, ny]):
                    seen.add((nx, ny))
                    queue.append((nx, ny))
    return seen


def sample_blue(image: Image.Image, outside: set[tuple[int, int]]) -> tuple[int, int, int]:
    width, height = image.size
    pixels = image.load()
    x = max(8, width // 8)
    for y in range(height // 6, height // 2):
        if (x, y) not in outside:
            pixel = pixels[x, y]
            if pixel[0] < 80 and pixel[2] > 180:
                return pixel
    return (27, 132, 253)


def full_bleed(source: Image.Image) -> Image.Image:
    rgba = source.convert("RGBA")
    outside = flood_outside(rgba)
    rgb = rgba.convert("RGB")
    if not outside:
        return rgb
    # Content is everything not outside; crop to that bbox, then fill corners.
    width, height = rgb.size
    content = [(x, y) for y in range(height) for x in range(width) if (x, y) not in outside]
    minx = min(x for x, _ in content)
    miny = min(y for _, y in content)
    maxx = max(x for x, _ in content)
    maxy = max(y for _, y in content)
    cropped = rgb.crop((minx, miny, maxx + 1, maxy + 1))
    shifted = {(x - minx, y - miny) for x, y in outside if minx <= x <= maxx and miny <= y <= maxy}
    fill = sample_blue(rgb, outside)
    out = Image.new("RGB", cropped.size, fill)
    src_px = cropped.load()
    dst_px = out.load()
    cw, ch = cropped.size
    for y in range(ch):
        for x in range(cw):
            if (x, y) not in shifted:
                dst_px[x, y] = src_px[x, y]
    return out


def resize(image: Image.Image, size: int) -> Image.Image:
    return image.resize((size, size), Image.Resampling.LANCZOS)


def place_in_safe_zone(
    art: Image.Image,
    canvas_size: int,
    fill: tuple[int, int, int],
    ratio: float = SAFE_RATIO,
) -> Image.Image:
    canvas = Image.new("RGB", (canvas_size, canvas_size), fill)
    inner = max(1, int(round(canvas_size * ratio)))
    width, height = art.size
    scale = min(inner / width, inner / height)
    new_w = max(1, int(round(width * scale)))
    new_h = max(1, int(round(height * scale)))
    scaled = art.resize((new_w, new_h), Image.Resampling.LANCZOS)
    left = (canvas_size - new_w) // 2
    top = (canvas_size - new_h) // 2
    canvas.paste(scaled, (left, top))
    return canvas


def circular(image: Image.Image) -> Image.Image:
    size = image.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = image.convert("RGBA")
    out.putalpha(mask)
    return out


def export_logo(source: Image.Image) -> None:
    rgba = source.convert("RGBA")
    outside = flood_outside(rgba)
    width, height = rgba.size
    content = [(x, y) for y in range(height) for x in range(width) if (x, y) not in outside]
    if not content:
        cropped = rgba
    else:
        minx = min(x for x, _ in content)
        miny = min(y for _, y in content)
        maxx = max(x for x, _ in content)
        maxy = max(y for _, y in content)
        cropped = rgba.crop((minx, miny, maxx + 1, maxy + 1))
    canvas_size = 384
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    cw, ch = cropped.size
    scale = min(canvas_size / cw, canvas_size / ch)
    new_w = max(1, int(round(cw * scale)))
    new_h = max(1, int(round(ch * scale)))
    scaled = cropped.resize((new_w, new_h), Image.Resampling.LANCZOS)
    canvas.paste(scaled, ((canvas_size - new_w) // 2, (canvas_size - new_h) // 2), scaled)
    LOGO.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(LOGO, "PNG")
    print(f"logo {LOGO.name}: {canvas_size}x{canvas_size}")


def write_bg_color(rgb: tuple[int, int, int]) -> None:
    hex_color = f"#{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X}"
    COLORS.write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_bg">{hex}</color>
</resources>
""".format(hex=hex_color),
        encoding="utf-8",
    )
    print("ic_launcher_bg", hex_color)


def write_adaptive_xml() -> None:
    folder = RES / "mipmap-anydpi-v26"
    folder.mkdir(parents=True, exist_ok=True)
    xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_bg" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    (folder / "ic_launcher.xml").write_text(xml, encoding="utf-8")
    (folder / "ic_launcher_round.xml").write_text(xml, encoding="utf-8")


def main() -> None:
    source = Image.open(SOURCE)
    export_logo(source)
    bleed = full_bleed(source)
    fill = bleed.getpixel((bleed.size[0] // 2, 8))
    write_bg_color(fill)
    write_adaptive_xml()
    PLAYSTORE.parent.mkdir(parents=True, exist_ok=True)
    place_in_safe_zone(bleed, 512, fill).save(PLAYSTORE, "PNG")

    for folder, size in LEGACY.items():
        dest = RES / folder
        dest.mkdir(parents=True, exist_ok=True)
        square = place_in_safe_zone(bleed, size, fill)
        square.save(dest / "ic_launcher.png", "PNG")
        circular(square).save(dest / "ic_launcher_round.png", "PNG")
        print(f"{folder}/ic_launcher: {size}x{size}")

    for folder, size in FOREGROUND.items():
        dest = RES / folder
        dest.mkdir(parents=True, exist_ok=True)
        place_in_safe_zone(bleed, size, fill).save(dest / "ic_launcher_foreground.png", "PNG")
        print(f"{folder}/ic_launcher_foreground: {size}x{size}")


if __name__ == "__main__":
    main()
