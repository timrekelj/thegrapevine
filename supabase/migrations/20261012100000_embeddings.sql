-- pgvector embeddings for source/story similarity (clustering candidate retrieval)
create extension if not exists vector;

alter table public.sources add column if not exists embedding vector(1024);
alter table public.stories add column if not exists embedding vector(1024);

create index if not exists sources_embedding_idx on public.sources using hnsw (embedding vector_cosine_ops);
create index if not exists stories_embedding_idx on public.stories using hnsw (embedding vector_cosine_ops);
