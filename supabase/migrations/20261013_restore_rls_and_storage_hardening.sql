-- ==============================================================================
-- WB AGENCY / WB SCOUTING - MIGRATION DE SEGURANÇA E CONFORMIDADE LGPD
-- Arquivo: supabase/migrations/20261013_restore_rls_and_storage_hardening.sql
-- Data: 2026-10-13
-- Objetivo:
--   1. Reativação estrita de Row Level Security (RLS) nas tabelas críticas
--      (public.models, public.model_media, public.candidate_submissions,
--       public.candidate_photos, public.candidates, public.admins).
--   2. Bloqueio e revogação explícita de comandos DML da role 'anon'.
--   3. Hardening de Storage: isolamento estrito de 'candidates-uploads' (privado)
--      e reativação de RLS em storage.objects com políticas de menor privilégio.
--   4. Garantia de acesso operacional full para a role 'service_role' (Backend Java).
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. FUNÇÃO AUXILIAR DE VALIDAÇÃO ADMINISTRATIVA (SECURITY DEFINER)
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_active_admin()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    -- 1. Permite acesso total para chamadas administrativas com service_role (Backend Java / Spring Boot)
    IF auth.role() = 'service_role' THEN
        RETURN TRUE;
    END IF;

    -- 2. Verifica se o e-mail autenticado no JWT pertence a um administrador ativo
    RETURN EXISTS (
        SELECT 1 
        FROM public.admins a
        WHERE a.email = (auth.jwt() ->> 'email')
          AND a.is_active = TRUE
    );
END;
$$;

-- ------------------------------------------------------------------------------
-- 2. REATIVAÇÃO ESTRITA DE ROW LEVEL SECURITY (RLS) NAS TABELAS CRÍTICAS
-- ------------------------------------------------------------------------------
ALTER TABLE public.models ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.model_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.candidate_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.candidate_photos ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'candidates') THEN
        ALTER TABLE public.candidates ENABLE ROW LEVEL SECURITY;
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'admins') THEN
        ALTER TABLE public.admins ENABLE ROW LEVEL SECURITY;
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'site_contents') THEN
        ALTER TABLE public.site_contents ENABLE ROW LEVEL SECURITY;
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'admin_audit_logs') THEN
        ALTER TABLE public.admin_audit_logs ENABLE ROW LEVEL SECURITY;
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 3. REVOGAÇÃO EXPLÍCITA DE PRIVILÉGIOS PARA ROLE 'anon' (DEFESA EM PROFUNDIDADE)
-- ------------------------------------------------------------------------------
-- Bloqueio total de escrita (INSERT, UPDATE, DELETE, TRUNCATE) em tabelas sensíveis
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.models FROM anon;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.model_media FROM anon;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.candidate_submissions FROM anon;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.candidate_photos FROM anon;

-- Bloqueio total de leitura (SELECT) em dados privados/LGPD para 'anon'
REVOKE SELECT ON public.candidate_submissions FROM anon;
REVOKE SELECT ON public.candidate_photos FROM anon;

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'candidates') THEN
        REVOKE SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON public.candidates FROM anon;
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'admins') THEN
        REVOKE SELECT, INSERT, UPDATE, DELETE, TRUNCATE ON public.admins FROM anon;
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 4. POLÍTICAS RLS IDEMPOTENTES PARA TABELAS PUBLIC
-- ------------------------------------------------------------------------------

-- 4.1. POLÍTICAS SERVICE_ROLE (BACKEND SPRING BOOT)
DROP POLICY IF EXISTS "Service Role Full Access - models" ON public.models;
CREATE POLICY "Service Role Full Access - models"
    ON public.models
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service Role Full Access - model_media" ON public.model_media;
CREATE POLICY "Service Role Full Access - model_media"
    ON public.model_media
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service Role Full Access - candidate_submissions" ON public.candidate_submissions;
CREATE POLICY "Service Role Full Access - candidate_submissions"
    ON public.candidate_submissions
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "Service Role Full Access - candidate_photos" ON public.candidate_photos;
CREATE POLICY "Service Role Full Access - candidate_photos"
    ON public.candidate_photos
    TO service_role
    USING (true)
    WITH CHECK (true);

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'candidates') THEN
        DROP POLICY IF EXISTS "Service Role Full Access - candidates" ON public.candidates;
        CREATE POLICY "Service Role Full Access - candidates"
            ON public.candidates
            TO service_role
            USING (true)
            WITH CHECK (true);
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'admins') THEN
        DROP POLICY IF EXISTS "Service Role Full Access - admins" ON public.admins;
        CREATE POLICY "Service Role Full Access - admins"
            ON public.admins
            TO service_role
            USING (true)
            WITH CHECK (true);
    END IF;
END $$;

-- 4.2. POLÍTICAS DE LEITURA PÚBLICA (CATÁLOGO DE MODELOS ATIVOS E MÍDIAS)
DROP POLICY IF EXISTS "Public Read Active Models" ON public.models;
CREATE POLICY "Public Read Active Models"
    ON public.models
    FOR SELECT
    TO anon, authenticated
    USING (is_active = TRUE);

DROP POLICY IF EXISTS "Public Read Active Media" ON public.model_media;
CREATE POLICY "Public Read Active Media"
    ON public.model_media
    FOR SELECT
    TO anon, authenticated
    USING (
        is_active = TRUE
        AND EXISTS (
            SELECT 1 FROM public.models m
            WHERE m.id = model_media.model_id
              AND m.is_active = TRUE
        )
    );

-- 4.3. ELIMINAÇÃO DE POLÍTICAS DE INSERÇÃO DIRETA ANÔNIMA EM CANDIDATOS
DROP POLICY IF EXISTS "Public Insert Candidate Application" ON public.candidates;
DROP POLICY IF EXISTS "Public Insert Candidate Photos" ON public.candidate_photos;

-- ------------------------------------------------------------------------------
-- 5. HARDENING DE STORAGE (BUCKETS & storage.objects)
-- ------------------------------------------------------------------------------

-- 5.1. Configuração rigorosa de privacidade e quotas dos buckets
UPDATE storage.buckets 
SET public = FALSE,
    file_size_limit = 5242880,  -- 5 MB
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp']
WHERE id = 'candidates-uploads';

UPDATE storage.buckets 
SET public = TRUE,
    file_size_limit = 8388608,  -- 8 MB
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp']
WHERE id = 'models-media';

UPDATE storage.buckets 
SET public = TRUE,
    file_size_limit = 26214400, -- 25 MB
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/webm']
WHERE id = 'site-assets';

-- 5.2. Reativação estrita de RLS na tabela storage.objects
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- 5.3. Remoção de políticas inseguras / permissivas no storage
DROP POLICY IF EXISTS "Public Read Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Candidates Uploads Public Read" ON storage.objects;
DROP POLICY IF EXISTS "Allow public read for candidates" ON storage.objects;
DROP POLICY IF EXISTS "Public Access Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Anon Read Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Candidates Uploads Public Insert" ON storage.objects;
DROP POLICY IF EXISTS "Public Intake Insert for Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Allow anon upload" ON storage.objects;
DROP POLICY IF EXISTS "Anon Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Anon Insert" ON storage.objects;
DROP POLICY IF EXISTS "Anon Delete" ON storage.objects;
DROP POLICY IF EXISTS "Anon Update" ON storage.objects;

-- 5.4. Políticas de Leitura (SELECT) em storage.objects
-- Leitura pública irrestrita para buckets públicos (CDN)
DROP POLICY IF EXISTS "Public Read Access for Public Buckets" ON storage.objects;
CREATE POLICY "Public Read Access for Public Buckets"
    ON storage.objects
    FOR SELECT
    TO anon, authenticated
    USING (bucket_id IN ('site-assets', 'models-media'));

-- Leitura restrita para candidatos (exige backend service_role ou admin ativo)
DROP POLICY IF EXISTS "Restricted Admin Read for Candidates Uploads" ON storage.objects;
CREATE POLICY "Restricted Admin Read for Candidates Uploads"
    ON storage.objects
    FOR SELECT
    TO authenticated, service_role
    USING (
        bucket_id = 'candidates-uploads' 
        AND public.is_active_admin()
    );

-- 5.5. Políticas de Upload (INSERT) em storage.objects
-- Upload de candidatos exclusivo via backend (service_role) ou admin autenticado
DROP POLICY IF EXISTS "Restricted Intake Insert for Candidates Uploads" ON storage.objects;
CREATE POLICY "Restricted Intake Insert for Candidates Uploads"
    ON storage.objects
    FOR INSERT
    TO service_role, authenticated
    WITH CHECK (
        bucket_id = 'candidates-uploads'
        AND (auth.role() = 'service_role' OR public.is_active_admin())
    );

-- Upload de mídias de modelos e assets do site exclusivo para admins e backend
DROP POLICY IF EXISTS "Admin Insert for Models Media and Site Assets" ON storage.objects;
CREATE POLICY "Admin Insert for Models Media and Site Assets"
    ON storage.objects
    FOR INSERT
    TO authenticated, service_role
    WITH CHECK (
        bucket_id IN ('site-assets', 'models-media')
        AND public.is_active_admin()
    );

-- 5.6. Políticas de Modificação e Exclusão (UPDATE / DELETE) em storage.objects
DROP POLICY IF EXISTS "Admin Update for All Managed Buckets" ON storage.objects;
CREATE POLICY "Admin Update for All Managed Buckets"
    ON storage.objects
    FOR UPDATE
    TO authenticated, service_role
    USING (
        bucket_id IN ('site-assets', 'models-media', 'candidates-uploads')
        AND public.is_active_admin()
    )
    WITH CHECK (
        bucket_id IN ('site-assets', 'models-media', 'candidates-uploads')
        AND public.is_active_admin()
    );

DROP POLICY IF EXISTS "Admin Delete for All Managed Buckets" ON storage.objects;
CREATE POLICY "Admin Delete for All Managed Buckets"
    ON storage.objects
    FOR DELETE
    TO authenticated, service_role
    USING (
        bucket_id IN ('site-assets', 'models-media', 'candidates-uploads')
        AND public.is_active_admin()
    );
