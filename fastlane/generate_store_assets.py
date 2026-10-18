"""Generate the Play Store graphics from the app's own icon assets.

Writes, for every locale under fastlane/metadata/android/:
  images/icon.png            512x512   (Play "App icon", 32-bit PNG)
  images/featureGraphic.png  1024x500  (Play "Feature graphic", brand emerald #10b981)

Run from the repo root:  python fastlane/generate_store_assets.py   (needs Pillow)
Screenshots are NOT generated: they must be captured on a device/emulator.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
METADATA = ROOT / "fastlane" / "metadata" / "android"
APP_ICON = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png"  # white bg, opaque
LOGO = ROOT / "composeApp/src/commonMain/composeResources/drawable/pushit_logo.png"  # transparent bg

EMERALD = (16, 185, 129)  # #10b981
EMERALD_DARK = (4, 120, 87)  # #047857
WHITE = (255, 255, 255)

TAGLINES = {
    "en-US": "Your push notifications, in one inbox.",
    "fr-FR": "Vos notifications push, dans une seule boîte.",
    "nl-NL": "Uw pushmeldingen, in één inbox.",
}

FONT_CANDIDATES_BOLD = [
    "C:/Windows/Fonts/segoeuib.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
]
FONT_CANDIDATES_REGULAR = [
    "C:/Windows/Fonts/segoeui.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/System/Library/Fonts/Supplemental/Arial.ttf",
]


def font(candidates: list[str], size: int) -> ImageFont.FreeTypeFont:
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    raise SystemExit(f"No usable TTF font found among {candidates}")


def make_icon() -> Image.Image:
    # Play applies its own rounded mask: deliver a full-bleed square.
    return Image.open(APP_ICON).convert("RGBA").resize((512, 512), Image.LANCZOS)


def make_feature_graphic(tagline: str) -> Image.Image:
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), EMERALD)
    draw = ImageDraw.Draw(img)

    # Subtle diagonal band for depth.
    draw.polygon([(620, 0), (w, 0), (w, h), (420, h)], fill=(13, 168, 117))

    # Logo on a white rounded card, left.
    card = 300
    cx, cy = 90, (h - card) // 2
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (cx + 6, cy + 10, cx + card + 6, cy + card + 10), radius=64, fill=(*EMERALD_DARK, 140)
    )
    img.paste(shadow, (0, 0), shadow)
    draw.rounded_rectangle((cx, cy, cx + card, cy + card), radius=64, fill=WHITE)
    logo = Image.open(LOGO).convert("RGBA").resize((card - 40, card - 40), Image.LANCZOS)
    img.paste(logo, (cx + 20, cy + 20), logo)

    # Wordmark + tagline, right.
    tx = cx + card + 60
    title_font = font(FONT_CANDIDATES_BOLD, 120)
    draw.text((tx, 130), "PushIT", font=title_font, fill=WHITE)
    max_width = w - tx - 50
    size = 40
    tag_font = font(FONT_CANDIDATES_REGULAR, size)
    lines = wrap(draw, tagline, tag_font, max_width)
    y = 290
    for line in lines:
        draw.text((tx, y), line, font=tag_font, fill=WHITE)
        y += size + 10
    return img


def wrap(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.FreeTypeFont, max_width: int) -> list[str]:
    lines, current = [], ""
    for word in text.split():
        candidate = f"{current} {word}".strip()
        if draw.textlength(candidate, font=fnt) <= max_width:
            current = candidate
        else:
            lines.append(current)
            current = word
    lines.append(current)
    return lines


def main() -> None:
    icon = make_icon()
    for locale, tagline in TAGLINES.items():
        out = METADATA / locale / "images"
        out.mkdir(parents=True, exist_ok=True)
        icon.save(out / "icon.png", optimize=True)
        make_feature_graphic(tagline).save(out / "featureGraphic.png", optimize=True)
        print(f"{locale}: icon.png + featureGraphic.png")


if __name__ == "__main__":
    main()
