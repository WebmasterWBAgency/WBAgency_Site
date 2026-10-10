-- ==============================================================================
-- FASE 2: Sincronização de Enums PostgreSQL (Supabase)
-- Evita erro 22P02 (invalid_text_representation) garantindo que os tipos
-- enum contenham todos os valores mapeados pelas entidades JPA do backend:
-- 'PENDING', 'APPROVED', 'DECLINED', 'REJECTED', 'PROMOTED'.
-- ==============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'submission_status') THEN
        ALTER TYPE public.submission_status ADD VALUE IF NOT EXISTS 'PENDING';
        ALTER TYPE public.submission_status ADD VALUE IF NOT EXISTS 'APPROVED';
        ALTER TYPE public.submission_status ADD VALUE IF NOT EXISTS 'DECLINED';
        ALTER TYPE public.submission_status ADD VALUE IF NOT EXISTS 'REJECTED';
        ALTER TYPE public.submission_status ADD VALUE IF NOT EXISTS 'PROMOTED';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'candidate_status') THEN
        ALTER TYPE public.candidate_status ADD VALUE IF NOT EXISTS 'PENDING';
        ALTER TYPE public.candidate_status ADD VALUE IF NOT EXISTS 'APPROVED';
        ALTER TYPE public.candidate_status ADD VALUE IF NOT EXISTS 'DECLINED';
        ALTER TYPE public.candidate_status ADD VALUE IF NOT EXISTS 'REJECTED';
        ALTER TYPE public.candidate_status ADD VALUE IF NOT EXISTS 'PROMOTED';
    END IF;
END $$;
