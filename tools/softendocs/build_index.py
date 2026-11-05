"""Builds src/main/resources/softendocs/index.json from a crawl made by crawl.py.

Each page becomes one or more passages of ~1200 characters split on paragraph
boundaries. Pages without written text (only screenshots) are skipped: the
model cannot read them.

    python3 -I tools/softendocs/build_index.py /caminho/da/coleta src/main/resources/softendocs/index.json
"""
import json, re, sys, datetime

SRC, OUT = sys.argv[1], sys.argv[2]
MAX_CHARS = 1200
MIN_BODY = 80

PRODUCTS = [  # first match wins; slugs from docs.softensistemas.com.br/books
    (r"softenfood|i-restaurante", "SoftenFood"),
    (r"gerencie|^ga-|^emissornota|plug4market|tbx", "GerencieAqui"),
    (r"siem|^nfc-e-rb6|^nfenfce", "Soften SIEM"),
]


def product(book):
    for pattern, name in PRODUCTS:
        if re.search(pattern, book):
            return name
    return "Geral"


def passages(body):
    paras = [p.strip() for p in re.split(r"\n\s*\n|\n(?=\S)", body) if p.strip()]
    out, cur = [], ""
    for p in paras:
        while len(p) > MAX_CHARS:  # very long paragraph: cut on a sentence end
            cut = p.rfind(". ", 0, MAX_CHARS)
            cut = cut + 1 if cut > MAX_CHARS // 2 else MAX_CHARS
            if cur:
                out.append(cur); cur = ""
            out.append(p[:cut].strip()); p = p[cut:].strip()
        if cur and len(cur) + len(p) + 1 > MAX_CHARS:
            out.append(cur); cur = ""
        cur = f"{cur}\n{p}" if cur else p
    if cur:
        out.append(cur)
    return out


inv = json.load(open(f"{SRC}/inventory.json"))
chunks, skipped = [], 0
for page in inv["pages"]:
    text = open(f"{SRC}/{page['file']}", encoding="utf-8").read().strip()
    title, _, body = text.partition("\n")
    body = re.sub(r"[ \t]+", " ", body).strip()
    if len(body) < MIN_BODY:
        skipped += 1
        continue
    date = (page.get("updated") or page.get("created") or "")[:10]
    for i, part in enumerate(passages(body)):
        chunks.append({"t": title.strip()[:200], "b": page["book"], "p": product(page["book"]),
                       "u": page["url"], "d": date, "x": part})

json.dump({"source": "https://docs.softensistemas.com.br", "builtAt": datetime.date.today().isoformat(),
           "pages": len(inv["pages"]) - skipped, "chunks": chunks},
          open(OUT, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print(f"{len(chunks)} passages from {len(inv['pages']) - skipped} pages ({skipped} without text skipped)")
