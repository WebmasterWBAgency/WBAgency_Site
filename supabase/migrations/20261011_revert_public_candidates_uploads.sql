-- ==============================================================================
-- Migration: Reverter Leitura Pública e Blindar candidates-uploads (LGPD Compliant)
-- Data: 2026-10-11
-- Auditoria Técnica: Audit-Lion
-- Objetivo: Tornar o bucket 'candidates-uploads' estritamente privado, bloquear
--           leituras anônimas via RLS e exigir chamadas authenticated/service_role
--           ou Signed URLs temporárias.
-- ==============================================================================

-- 1. Atualizar a tabela storage.buckets definindo public = false para o bucket candidates-uploads
UPDATE storage.buckets 
SET public = false 
WHERE id = 'candidates-uploads';

-- 2. Dropar quaisquer políticas de storage que permitam SELECT público/anônimo em storage.objects para candidates-uploads
DROP POLICY IF EXISTS "Public Read Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Candidates Uploads Public Read" ON storage.objects;
DROP POLICY IF EXISTS "Allow public read for candidates" ON storage.objects;
DROP POLICY IF EXISTS "Public Access Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Anon Read Candidates Uploads" ON storage.objects;

DO $$
DECLARE
    pol RECORD;
BEGIN
    FOR pol IN (
        SELECT policyname 
        FROM pg_policies 
        WHERE schemaname = 'storage' 
          AND tablename = 'objects' 
          AND ('anon' = ANY(roles) OR roles IS NULL)
          AND cmd = 'SELECT'
          AND (policyname ILIKE '%candidate%' OR qual ILIKE '%candidates-uploads%')
    ) LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON storage.objects', pol.policyname);
    END LOOP;
END $$;

-- 3. Criar política restritiva em storage.objects permitindo SELECT e INSERT apenas para a role authenticated ou chamadas com service_role
DROP POLICY IF EXISTS "Restricted Candidates Uploads Access" ON storage.objects;
CREATE POLICY "Restricted Candidates Uploads Access"
ON storage.objects
FOR ALL
TO authenticated, service_role
USING (
    bucket_id = 'candidates-uploads'
    AND (auth.role() = 'service_role' OR auth.role() = 'authenticated')
)
WITH CHECK (
    bucket_id = 'candidates-uploads'
    AND (auth.role() = 'service_role' OR auth.role() = 'authenticated')
);

-- 4. Garantir RLS ativo e revogar SELECT de roles anon nas tabelas candidates, candidate_submissions, candidate_photos e model_media
DO $$
BEGIN
    -- candidates
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'candidates') THEN
        ALTER TABLE public.candidates ENABLE ROW LEVEL SECURITY;
        REVOKE SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON public.candidates FROM anon;
    END IF;

    -- candidate_submissions
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'candidate_submissions') THEN
        ALTER TABLE public.candidate_submissions ENABLE ROW LEVEL SECURITY;
        REVOKE SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON public.candidate_submissions FROM anon;
    END IF;

    -- candidate_photos
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'candidate_photos') THEN
        ALTER TABLE public.candidate_photos ENABLE ROW LEVEL SECURITY;
        REVOKE SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON public.candidate_photos FROM anon;
    END IF;

    -- model_media
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'model_media') THEN
        ALTER TABLE public.model_media ENABLE ROW LEVEL SECURITY;
        REVOKE SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON public.model_media FROM anon;
    END IF;
END $$;
