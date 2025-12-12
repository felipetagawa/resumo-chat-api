CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS public.vector_store (
    id VARCHAR(255) NOT NULL,
    content TEXT,
    metadata JSONB,
    embedding vector(1536), -- 1536 é o tamanho padrão de embeddings do Gemini
    PRIMARY KEY (id)
);