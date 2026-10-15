#!/usr/bin/env python3
"""Writes Assets/icon.png (512x512): dark rounded tile with a 4x4 pad grid. Pure stdlib."""
import math, struct, zlib, os

N = 512
def px(x, y):
    # rounded-square mask
    r, cx, cy = 112, N / 2, N / 2
    dx, dy = abs(x - cx) - (N / 2 - r), abs(y - cy) - (N / 2 - r)
    d = math.hypot(max(dx, 0), max(dy, 0)) - r
    if d > 0.5:
        return (0, 0, 0, 0)
    a = 255 if d < -0.5 else int(255 * (0.5 - d))
    t = y / N
    base = (int(28 + 14 * (1 - t)), int(30 + 14 * (1 - t)), int(36 + 16 * (1 - t)))
    gx, gy = (x - 76) // 90, (y - 76) // 90
    if 0 <= gx < 4 and 0 <= gy < 4 and (x - 76) % 90 < 80 and (y - 76) % 90 < 80:
        lx, ly = (x - 76) % 90, (y - 76) % 90
        k = min(lx, 79 - lx, ly, 79 - ly)
        if k < 14 and math.hypot(max(14 - min(lx, 79 - lx), 0), max(14 - min(ly, 79 - ly), 0)) > 14:
            return base + (a,)
        lit = {(0, 3): (255, 122, 26), (2, 1): (47, 214, 200), (3, 2): (214, 75, 224), (1, 0): (123, 227, 91)}.get((gx, gy))
        if lit:
            sh = 1.0 - 0.25 * (ly / 80)
            return (int(lit[0] * sh), int(lit[1] * sh), int(lit[2] * sh), a)
        sh = 1.0 - 0.3 * (ly / 80)
        return (int(66 * sh), int(71 * sh), int(82 * sh), a)
    return base + (a,)

raw = bytearray()
for y in range(N):
    raw.append(0)
    for x in range(N):
        raw += bytes(px(x, y))
def chunk(t, d):
    c = struct.pack('>I', len(d)) + t + d
    return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', N, N, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
os.makedirs(os.path.join(os.path.dirname(__file__), '..', 'Assets'), exist_ok=True)
open(os.path.join(os.path.dirname(__file__), '..', 'Assets', 'icon.png'), 'wb').write(png)
print('icon.png', len(png), 'bytes')
