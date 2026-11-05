# SoftenDocs crawler

Lê, só para leitura, todas as páginas públicas de https://docs.softensistemas.com.br (BookStack):
livros → capítulos → páginas, com datas de criação/edição e o texto exportado de cada página.

```bash
python3 -I tools/softendocs/crawl.py /caminho/de/saida
```

Gera `inventory.json` (livro, título, URL, datas, tamanho do texto, nº de imagens) e `pages/*.txt`.
Leva cerca de 15 minutos; o site responde 429 se as requisições forem rápidas demais.

Inventário de 05/11/2026: 98 livros, 1.127 páginas — 552 em dia, 100 para revisar (1–2 anos),
390 sem edição há mais de 2 anos e 85 só com imagem (sem texto que a IA consiga ler).
