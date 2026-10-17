-- ==============================================================================
-- WB AGENCY / WB SCOUTING - MIGRATION: RESTAURAÇÃO DE RLS E MENOR PRIVILÉGIO
-- Arquivo: supabase/migrations/20261017_restore_rls_and_least_privilege.sql
-- Data: 2026-10-17
-- Responsável: Administrador de Banco de Dados PostgreSQL & Especialista Supabase
--
-- Objetivos:
--   1. Ativar Row Level Security (RLS) nas tabelas 'models', 'model_media',
--      'candidate_submissions' e 'candidate_photos'.
--   2. Revogar permissões abertas concedidas para a role 'anon' (REVOKE ALL ... FROM anon).
--   3. Conceder permissão de SELECT para 'anon' apenas em 'models' e 'model_media'
--      condicionada a (is_active = TRUE) via políticas RLS.
--   4. No bucket 'candidates-uploads', remover qualquer política de SELECT para 'anon',
--      garantindo que o acesso a polaroides e fotos sensíveis ocorra exclusivamente via
--      'service_role' (Backend Spring Boot) através de Signed URLs (900s) ou admins ativos.
--   5. Garantir estrita idempotência com IF EXISTS / IF NOT EXISTS.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. ATIVAÇÃO DE ROW LEVEL SECURITY (RLS) NAS TABELAS PRINCIPAIS E AUXILIARES
-- ------------------------------------------------------------------------------
ALTER TABLE IF EXISTS public.models ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.model_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.candidate_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.candidate_photos ENABLE ROW LEVEL SECURITY;

-- Tabelas auxiliares do ecossistema
ALTER TABLE IF EXISTS public.candidates ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.admins ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.site_contents ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.admin_audit_logs ENABLE ROW LEVEL SECURITY;

-- ------------------------------------------------------------------------------
-- 2. REVOGAÇÃO DE PERMISSÕES ABERTAS PARA A ROLE 'anon' (DEFESA EM PROFUNDIDADE)
-- ------------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        -- Revogação total de privilégios em tabelas do catálogo e triagem
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'models') THEN
            REVOKE ALL ON TABLE public.models FROM anon;
        END IF;
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'model_media') THEN
            REVOKE ALL ON TABLE public.model_media FROM anon;
        END IF;
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'candidate_submissions') THEN
            REVOKE ALL ON TABLE public.candidate_submissions FROM anon;
        END IF;
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'candidate_photos') THEN
            REVOKE ALL ON TABLE public.candidate_photos FROM anon;
        END IF;
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'candidates') THEN
            REVOKE ALL ON TABLE public.candidates FROM anon;
        END IF;
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'admins') THEN
            REVOKE ALL ON TABLE public.admins FROM anon;
        END IF;
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'admin_audit_logs') THEN
            REVOKE ALL ON TABLE public.admin_audit_logs FROM anon;
        END IF;

        -- ----------------------------------------------------------------------
        -- 3. CONCESSÃO DE MENOR PRIVILÉGIO: SELECT APENAS EM 'models' E 'model_media'
        -- ----------------------------------------------------------------------
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'models') THEN
            GRANT SELECT ON TABLE public.models TO anon;
        END IF;
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'model_media') THEN
            GRANT SELECT ON TABLE public.model_media TO anon;
        END IF;
        IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'site_contents') THEN
            GRANT SELECT ON TABLE public.site_contents TO anon;
        END IF;
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 4. POLÍTICAS RLS IDEMPOTENTES: LEITURA PÚBLICA CONDICIONADA A (is_active = TRUE)
-- ------------------------------------------------------------------------------

-- 4.1. Catálogo de Modelos (Público apenas se is_active = TRUE)
DROP POLICY IF EXISTS "Public Read Active Models" ON public.models;
CREATE POLICY "Public Read Active Models"
    ON public.models
    FOR SELECT
    TO anon, authenticated
    USING (is_active = TRUE);

-- 4.2. Mídias de Modelos (Público apenas se mídia for ativa e modelo for ativo)
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

-- 4.3. Conteúdos Institucionais Dinâmicos
DROP POLICY IF EXISTS "Public Read Site Contents" ON public.site_contents;
CREATE POLICY "Public Read Site Contents"
    ON public.site_contents
    FOR SELECT
    TO anon, authenticated
    USING (TRUE);

-- ------------------------------------------------------------------------------
-- 5. POLÍTICAS DE SERVICE_ROLE (BACKEND SPRING BOOT OPERACIONAL FULL)
-- ------------------------------------------------------------------------------
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

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'site_contents') THEN
        DROP POLICY IF EXISTS "Service Role Full Access - site_contents" ON public.site_contents;
        CREATE POLICY "Service Role Full Access - site_contents"
            ON public.site_contents
            TO service_role
            USING (true)
            WITH CHECK (true);
    END IF;

    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'admin_audit_logs') THEN
        DROP POLICY IF EXISTS "Service Role Full Access - admin_audit_logs" ON public.admin_audit_logs;
        CREATE POLICY "Service Role Full Access - admin_audit_logs"
            ON public.admin_audit_logs
            TO service_role
            USING (true)
            WITH CHECK (true);
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 6. REMOÇÃO DE POLÍTICAS DE ACESSO ANÔNIMO EM CANDIDATURAS (LGPD & SIGILO)
-- ------------------------------------------------------------------------------
DROP POLICY IF EXISTS "Public Read Candidate Submissions" ON public.candidate_submissions;
DROP POLICY IF EXISTS "Public Read Candidate Photos" ON public.candidate_photos;
DROP POLICY IF EXISTS "Anon Read Candidate Submissions" ON public.candidate_submissions;
DROP POLICY IF EXISTS "Anon Read Candidate Photos" ON public.candidate_photos;
DROP POLICY IF EXISTS "Allow anon read candidate submissions" ON public.candidate_submissions;
DROP POLICY IF EXISTS "Allow anon read candidate photos" ON public.candidate_photos;
DROP POLICY IF EXISTS "Public Insert Candidate Application" ON public.candidates;
DROP POLICY IF EXISTS "Public Insert Candidate Photos" ON public.candidate_photos;

-- ------------------------------------------------------------------------------
-- 7. SUPABASE STORAGE: ISOLAMENTO TOTAL DO BUCKET 'candidates-uploads'
-- ------------------------------------------------------------------------------

-- 7.1. Forçar bucket candidates-uploads como PRIVADO
UPDATE storage.buckets
SET public = FALSE
WHERE id = 'candidates-uploads';

-- 7.2. Ativar RLS em storage.objects
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- 7.3. Eliminação irrestrita de qualquer política pública/anônima no storage de candidatos
DROP POLICY IF EXISTS "Public Read Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Candidates Uploads Public Read" ON storage.objects;
DROP POLICY IF EXISTS "Allow public read for candidates" ON storage.objects;
DROP POLICY IF EXISTS "Public Access Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Anon Read Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Public Read Polaroids" ON storage.objects;
DROP POLICY IF EXISTS "Public Read Access for Candidates" ON storage.objects;
DROP POLICY IF EXISTS "Public Select Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Candidates Uploads Public Insert" ON storage.objects;
DROP POLICY IF EXISTS "Public Intake Insert for Candidates Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Allow anon upload" ON storage.objects;
DROP POLICY IF EXISTS "Anon Uploads" ON storage.objects;
DROP POLICY IF EXISTS "Anon Insert" ON storage.objects;
DROP POLICY IF EXISTS "Anon Delete" ON storage.objects;
DROP POLICY IF EXISTS "Anon Update" ON storage.objects;

-- 7.4. Política de Leitura Pública restrita aos buckets de exibição (site-assets e models-media)
DROP POLICY IF EXISTS "Public Read Access for Public Buckets" ON storage.objects;
CREATE POLICY "Public Read Access for Public Buckets"
    ON storage.objects
    FOR SELECT
    TO anon, authenticated
    USING (bucket_id IN ('site-assets', 'models-media'));

-- 7.5. Política de Leitura Exclusiva para candidates-uploads:
-- Apenas service_role (Backend Java / Signed URLs de 900s) e administradores autenticados e ativos
DROP POLICY IF EXISTS "Restricted Admin Read for Candidates Uploads" ON storage.objects;
CREATE POLICY "Restricted Admin Read for Candidates Uploads"
    ON storage.objects
    FOR SELECT
    TO authenticated, service_role
    USING (
        bucket_id = 'candidates-uploads'
        AND (auth.role() = 'service_role' OR public.is_active_admin())
    );

-- 7.6. Inserção em candidates-uploads exclusiva via backend service_role ou admin ativo
DROP POLICY IF EXISTS "Restricted Intake Insert for Candidates Uploads" ON storage.objects;
CREATE POLICY "Restricted Intake Insert for Candidates Uploads"
    ON storage.objects
    FOR INSERT
    TO service_role, authenticated
    WITH CHECK (
        bucket_id = 'candidates-uploads'
        AND (auth.role() = 'service_role' OR public.is_active_admin())
    );
