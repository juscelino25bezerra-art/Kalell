# Gera os ícones do app a partir do ícone de referência (docs/icone/kalendrium-icone-original.png, 192×192).
# A referência é pequena e sem suavização; para ficar nítida em todos os tamanhos, o desenho foi medido nela
# (grade de 192 unidades) e é redesenhado aqui com a mesma geometria e a mesma cor.
#
# Uso: python3 docs/icone/gerador/gerar_icones.py <app/src/main> <docs/icone>   (precisa de Pillow)
import sys
from PIL import Image, ImageDraw

AZUL = (0x8E, 0xB4, 0xD9, 255)   # cor do calendário na referência
BRANCO = (255, 255, 255, 255)    # fundo do ícone adaptativo e detalhes (faixa e pontos)
SS = 8                           # supersample


def mascara(n, escala, ox, oy, furos=True):
    """Calendário em unidades da referência (0..192) desenhado num canvas n×n (px), já suavizado."""
    N = n * SS
    m = Image.new('L', (N, N), 0)
    d = ImageDraw.Draw(m)
    k = escala * SS
    X = lambda u: ox * SS + u * k
    Y = lambda u: oy * SS + u * k
    # corpo: retângulo arredondado (0,16)-(192,192), raio 40
    d.rounded_rectangle((X(0), Y(16), X(192), Y(192)), radius=40 * k, fill=255)
    # argolas: largura 16, topo semicircular (raio 8)
    for cx in (56, 136):
        d.ellipse((X(cx - 8), Y(0), X(cx + 8), Y(16)), fill=255)
        d.rectangle((X(cx - 8), Y(8), X(cx + 8), Y(20)), fill=255)
    if furos:
        # faixa vazada entre o cabeçalho e o corpo (y 64..80)
        d.rectangle((X(-1), Y(64), X(193), Y(80)), fill=0)
        # três pontos: raio 12, centros em x 56/96/136, y 128
        for cx in (56, 96, 136):
            d.ellipse((X(cx - 12), Y(116), X(cx + 12), Y(140)), fill=0)
    return m.resize((n, n), Image.LANCZOS)


def calendario(n, escala, ox, oy, fundo_furos=None):
    """RGBA: calendário azul; furos transparentes ou preenchidos com `fundo_furos`."""
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    if fundo_furos:
        img.paste(Image.new('RGBA', (n, n), fundo_furos), (0, 0), mascara(n, escala, ox, oy, furos=False))
    img.paste(Image.new('RGBA', (n, n), AZUL), (0, 0), mascara(n, escala, ox, oy))
    return img


# Ícone adaptativo (canvas 108 dp): o calendário ocupa 54 dp de largura, centralizado; o ponto mais distante do
# centro fica a ~30,6 dp, dentro da zona segura (círculo de 66 dp), então nenhuma máscara corta o desenho.
ADAPT_W = 54.0


def adaptativo(n, fundo=None):
    e = n / 108.0 * ADAPT_W / 192.0
    o = (n - 192 * e) / 2
    img = Image.new('RGBA', (n, n), fundo or (0, 0, 0, 0))
    cal = calendario(n, e, o, o)
    img.alpha_composite(cal)
    return img


def legado_quadrado(n):
    # o próprio desenho da referência, com 2 dp de margem (44 de 48 dp), faixa e pontos em branco
    e = n * 44 / 48 / 192.0
    o = (n - 192 * e) / 2
    return calendario(n, e, o, o, fundo_furos=BRANCO)


def legado_redondo(n):
    # círculo branco (44 de 48 dp) com o calendário dentro
    N = n * SS
    m = Image.new('L', (N, N), 0)
    r = N * 22 / 48
    ImageDraw.Draw(m).ellipse((N / 2 - r, N / 2 - r, N / 2 + r, N / 2 + r), fill=255)
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    img.paste(Image.new('RGBA', (n, n), BRANCO), (0, 0), m.resize((n, n), Image.LANCZOS))
    e = n * 18.5 / 48 / 108.8      # ponto mais distante do centro do desenho = 108,8 unidades
    o = (n - 192 * e) / 2
    img.alpha_composite(calendario(n, e, o, o))
    return img


if __name__ == '__main__':
    main, docs = sys.argv[1], sys.argv[2]
    res = f'{main}/res'
    for k, s in {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}.items():
        d = f'{res}/mipmap-{k}'
        legado_quadrado(int(48 * s)).save(f'{d}/ic_launcher.png', optimize=True)
        legado_redondo(int(48 * s)).save(f'{d}/ic_launcher_round.png', optimize=True)
        adaptativo(int(108 * s)).save(f'{d}/ic_launcher_foreground.png', optimize=True)
    # Play Store (512×512, quadrado cheio; a loja aplica os cantos): os 72 dp visíveis do ícone adaptativo
    big = adaptativo(2048 * 108 // 72, BRANCO)
    o = (big.width - 2048) // 2
    play = big.crop((o, o, o + 2048, o + 2048)).convert('RGB').resize((512, 512), Image.LANCZOS)
    play.save(f'{main}/ic_launcher-playstore.png', optimize=True)
    play.save(f'{docs}/ic_launcher-playstore.png', optimize=True)
    # versão grande do desenho (fundo transparente, faixa e pontos em branco)
    calendario(1024, 1024 / 192, 0, 0, fundo_furos=BRANCO).save(f'{docs}/kalendrium-icone-1024.png', optimize=True)
