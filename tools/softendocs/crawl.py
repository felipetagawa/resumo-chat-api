"""Polite read-only crawler for the public BookStack at docs.softensistemas.com.br."""
import json, re, sys, time, html, urllib.request, os
from concurrent.futures import ThreadPoolExecutor
U = "https://docs.softensistemas.com.br"
OUT = sys.argv[1]
os.makedirs(f"{OUT}/pages", exist_ok=True)

def get(url, tries=4):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "AtendeAI-docs-inventory/1.0"})
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read().decode("utf-8", "replace")
        except Exception as e:
            if i == tries - 1: print("FAIL", url, e, file=sys.stderr); return ""
            time.sleep(3 * (i + 1))  # BookStack answers 429 when pushed

def links(src, pattern):
    return list(dict.fromkeys(re.findall(r'href="(%s)"' % pattern, src)))

books = []
for n in range(1, 30):
    src = get(f"{U}/books?page={n}")
    found = [b for b in links(src, re.escape(U) + r"/books/[^\"/?#]+") ]
    new = [b for b in found if b not in books]
    if not new: break
    books += new
print("books", len(books), file=sys.stderr)

page_urls = {}
for b in books:
    src = get(b)
    slug = b.rsplit("/", 1)[1]
    pages = links(src, re.escape(b) + r"/page/[^\"/?#]+")
    for ch in links(src, re.escape(b) + r"/chapter/[^\"/?#]+"):
        pages += links(get(ch), re.escape(b) + r"/page/[^\"/?#]+")
        time.sleep(0.2)
    for p in dict.fromkeys(pages): page_urls[p] = slug
    time.sleep(0.2)
print("pages", len(page_urls), file=sys.stderr)

def meta(url):
    src = get(url)
    t = re.search(r"<title>([^<]*)</title>", src)
    c = re.search(r'Created <span title="([^"]+)"', src)
    u = re.search(r'Updated <span title="([^"]+)"', src)
    text = get(url + "/export/plaintext")
    name = re.sub(r"[^a-z0-9-]", "_", url.split("/books/")[1].replace("/page/", "__"))
    with open(f"{OUT}/pages/{name}.txt", "w") as f: f.write(text)
    time.sleep(0.2)
    first = text.strip().split("\n", 1)[0].strip()
    return {"url": url, "book": page_urls[url],
            "title": first[:160] or (html.unescape(t.group(1)).split(" | ")[0].strip() if t else ""),
            "created": c.group(1) if c else None, "updated": u.group(1) if u else None,
            "chars": len(text), "images": src.count("<img") , "file": f"pages/{name}.txt"}

with ThreadPoolExecutor(2) as ex:
    rows = list(ex.map(meta, list(page_urls)))
json.dump({"books": books, "pages": rows}, open(f"{OUT}/inventory.json", "w"), ensure_ascii=False, indent=1)
print("done", len(rows), file=sys.stderr)
