# Kalendrium — Copyright (C) 2026 Juscelino Be
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Gera o ícone "Dia e noite" do Kalendrium: sol e lua sobre uma faixa de semana (escala 2×2), estilo Material flat.
# Todas as coordenadas estão na grade de 108 unidades do ícone adaptativo; o desenho cabe na zona segura de 66 dp,
# então nenhuma máscara (círculo, squircle, quadrado) corta nada.
#
# Uso: python3 docs/icone/gerador/gerar_icones.py <app/src/main> <docs/icone>   (precisa de cairosvg)
import os, sys
import cairosvg

MAIN, DOCS = sys.argv[1], sys.argv[2]
BG = "#1E3A8A"   # azul profundo (fundo)
ACC = "#F5A524"  # âmbar (sol e dias de trabalho)
W = "#FFFFFF"

def circ(cx, cy, r):
    return f"M{cx-r},{cy}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0z"

SUN = circ(40, 46, 7)
RAYS = ("M50,46h3 M27,46h3 M40,36v-3 M40,56v3 "
        "M47.07,38.93l2.12,-2.12 M32.93,53.07l-2.12,2.12 "
        "M47.07,53.07l2.12,2.12 M32.93,38.93l-2.12,-2.12")
MOON = "M64.92,36.06A10,10 0 1,0 75.46,49.23A8.5,8.5 0 0,1 64.92,36.06z"
STRIP = "M35,62h38a5,5 0 0,1 5,5v4a5,5 0 0,1 -5,5h-38a5,5 0 0,1 -5,-5v-4a5,5 0 0,1 5,-5z"
WORK = [36, 45, 72]  # pontos âmbar: trabalho
OFF = [54, 63]       # pontos azuis: folga

# ---------- SVG (for PNG rendering) ----------
def svg_fg():
    s = [f'<path d="{SUN}" fill="{ACC}"/>',
         f'<path d="{RAYS}" stroke="{ACC}" stroke-width="2.6" stroke-linecap="round" fill="none"/>',
         f'<path d="{MOON}" fill="{W}"/>',
         f'<path d="{STRIP}" fill="{W}"/>']
    s += [f'<path d="{circ(x,69,2.5)}" fill="{ACC}"/>' for x in WORK]
    s += [f'<path d="{circ(x,69,2.5)}" fill="{BG}"/>' for x in OFF]
    return "".join(s)

def svg(viewbox, shape):
    x, y, w, h = viewbox
    if shape == "square":
        clip = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{w*0.22}"/>'
    elif shape == "round":
        clip = f'<circle cx="{x+w/2}" cy="{y+h/2}" r="{w/2}"/>'
    else:
        clip = f'<rect x="{x}" y="{y}" width="{w}" height="{h}"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x} {y} {w} {h}">'
            f'<defs><clipPath id="c">{clip}</clipPath></defs><g clip-path="url(#c)">'
            f'<rect x="0" y="0" width="108" height="108" fill="{BG}"/>{svg_fg()}</g></svg>')

# ---------- Android vector XML ----------
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
HEAD = (f'<vector {NS}\n    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n')

def p(d, fill=None, stroke=None, extra=""):
    a = f'    <path android:pathData="{d}"'
    if fill: a += f'\n        android:fillColor="{fill}"'
    if stroke:
        a += (f'\n        android:strokeColor="{stroke}"\n        android:strokeWidth="2.6"'
              '\n        android:strokeLineCap="round"')
    return a + extra + " />\n"

def fg_xml():
    x = HEAD + p(SUN, ACC) + p(RAYS, stroke=ACC) + p(MOON, W) + p(STRIP, W)
    x += "".join(p(circ(c, 69, 2.5), ACC) for c in WORK)
    x += "".join(p(circ(c, 69, 2.5), BG) for c in OFF)
    return x + "</vector>\n"

def mono_xml():
    holes = "".join(circ(c, 69, 2.5) for c in WORK)
    x = HEAD + p(SUN, "#FFFFFFFF") + p(RAYS, stroke="#FFFFFFFF") + p(MOON, "#FFFFFFFF")
    x += p(STRIP + holes, "#FFFFFFFF", extra='\n        android:fillType="evenOdd"')
    return x + "</vector>\n"

ADAPTIVE = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Ícone (docs/icone/gerador/gerar_icones.py): sol e lua sobre faixa de semana, fundo azul profundo. -->\n'
            f'<adaptive-icon {NS}>\n'
            '    <background android:drawable="@color/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n')

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f: f.write(text)

def png(path, svgtext, size):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cairosvg.svg2png(bytestring=svgtext.encode(), write_to=path, output_width=size, output_height=size)


res = os.path.join(MAIN, "res")
write(f"{res}/drawable/ic_launcher_foreground.xml", '<?xml version="1.0" encoding="utf-8"?>\n' + fg_xml())
write(f"{res}/drawable/ic_launcher_monochrome.xml", '<?xml version="1.0" encoding="utf-8"?>\n' + mono_xml())
write(f"{res}/mipmap-anydpi-v26/ic_launcher.xml", ADAPTIVE)
write(f"{res}/mipmap-anydpi-v26/ic_launcher_round.xml", ADAPTIVE)

LEG = (18, 18, 72, 72)  # Android 7: os ícones legados mostram os 72 dp centrais
for d, s in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    png(f"{res}/mipmap-{d}/ic_launcher.png", svg(LEG, "square"), s)
    png(f"{res}/mipmap-{d}/ic_launcher_round.png", svg(LEG, "round"), s)

png(os.path.join(MAIN, "ic_launcher-playstore.png"), svg((0, 0, 108, 108), "full"), 512)
png(os.path.join(DOCS, "ic_launcher-playstore.png"), svg((0, 0, 108, 108), "full"), 512)
png(os.path.join(DOCS, "previa-icone-dia-noite.png"), svg(LEG, "round"), 512)
# a cor de fundo (ic_launcher_background) fica em res/values/colors.xml
