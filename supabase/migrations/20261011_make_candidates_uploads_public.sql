-- Migration: Garantir Leitura Pública no Supabase para o bucket candidates-uploads
-- Data: 2026-10-11
-- Permite que os polaroids e fotos de candidatos sejam lidos sem token de autenticação

-- 1. Torna o bucket publicamente acessível para leitura
UPDATE storage.buckets 
SET public = true 
WHERE id = 'candidates-uploads';

-- 2. Cria política de leitura irrestrita para visualização das fotos
DROP POLICY IF EXISTS "Public Read Candidates Uploads" ON storage.objects;
CREATE POLICY "Public Read Candidates Uploads"
ON storage.objects FOR SELECT
USING (bucket_id = 'candidates-uploads');
