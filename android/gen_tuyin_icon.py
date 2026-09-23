# -*- coding: utf-8 -*-
"""生成「图隐」应用图标：渐变圆角方块 + 白色「隐」字。"""
import zlib, struct, os, math
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "app", "res", "drawable", "ic_launcher.png")
SIZE = 512

def lerp(a, b, t):
    return int(a + (b - a) * t)

img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 渐变圆角方块（左上蓝 -> 右下紫）
radius = SIZE // 5
mask = Image.new("L", (SIZE, SIZE), 0)
md = ImageDraw.Draw(mask)
md.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=radius, fill=255)
grad = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
gd = ImageDraw.Draw(grad)
for y in range(SIZE):
    t = y / (SIZE - 1)
    r = lerp(0x0A, 0x8A, t)
    g = lerp(0x84, 0x55, t)
    b = lerp(0xFF, 0xF7, t)
    gd.line([(0, y), (SIZE, y)], fill=(r, g, b, 255))
img.paste(grad, (0, 0), mask)

# 白色「隐」字（跨平台字体查找，找不到用内置默认）
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    "/System/Library/Fonts/PingFang.ttc",
    "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
]
font = None
for fp in FONT_CANDIDATES:
    if os.path.exists(fp):
        try:
            font = ImageFont.truetype(fp, 300)
            break
        except Exception:
            pass
if font is None:
    font = ImageFont.load_default()
bbox = d.textbbox((0, 0), "隐", font=font)
tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
tx = (SIZE - tw) // 2 - bbox[0]
ty = (SIZE - th) // 2 - bbox[1] + 8
d.text((tx, ty), "隐", font=font, fill=(255, 255, 255, 255))

img.save(OUT, "PNG")
print("icon written:", os.path.getsize(OUT))
