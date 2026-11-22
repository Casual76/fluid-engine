"""Make a small linear HDR studio environment without a third-party image."""
from math import cos, frexp, pi, sin
from pathlib import Path
from sys import argv

width, height = 128, 64
pixels = bytearray()
for y in range(height):
    theta = pi * (y + 0.5) / height
    for x in range(width):
        phi = 2 * pi * (x + 0.5) / width
        key = max(0, sin(theta) * cos(phi - 0.65) * 0.8 + cos(theta) * 0.6) ** 32
        fill = max(0, sin(theta) * cos(phi + 2.1) * 0.9 + cos(theta) * 0.4) ** 12
        ambient = 0.16 + 0.22 * max(0, cos(theta))
        rgb = (ambient + 4.4 * key + 0.80 * fill,
               ambient + 4.0 * key + 0.92 * fill,
               ambient + 3.5 * key + 1.15 * fill)
        mantissa, exponent = frexp(max(rgb))
        scale = 256 * mantissa / max(rgb)
        pixels.extend(int(channel * scale) for channel in rgb)
        pixels.append(exponent + 128)
header = f"#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y {height} +X {width}\n"
Path(argv[1]).write_bytes(header.encode("ascii") + pixels)
