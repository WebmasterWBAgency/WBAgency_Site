-- ==============================================================================
-- MIGRAÇÃO IDEMPOTENTE: CONSOLIDAÇÃO DO MODELO CANÔNICO DE TALENTOS (SINGLE-TABLE LIFECYCLE)
-- Consolidação de colunas de candidatos na tabela models e sincronização de mídias
-- ==============================================================================

DO $$
BEGIN
    -- 1. Garante a coluna status na tabela models com o ciclo de vida TalentLifecycleStatus
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'models' 
          AND column_name = 'status'
          AND (udt_name = 'talent_lifecycle_status' OR data_type = 'USER-DEFINED')
    ) THEN
        ALTER TABLE public.models ALTER COLUMN status TYPE VARCHAR(50) USING status::varchar;
        RAISE NOTICE 'Coluna status em models convertida para VARCHAR(50).';
    ELSIF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'models' 
          AND column_name = 'status'
    ) THEN
        ALTER TABLE public.models 
        ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'CAST_PUBLIC';
        RAISE NOTICE 'Coluna status adicionada à tabela models com default CAST_PUBLIC.';
    END IF;

    -- 2. Atualiza modelos existentes com is_star = TRUE para status = CAST_STAR
    UPDATE public.models
    SET status = 'CAST_STAR'
    WHERE is_star = TRUE AND (status IS NULL OR status = 'CAST_PUBLIC');

    -- 3. Adiciona campos de submissão/candidato na tabela models
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'protocol') THEN
        ALTER TABLE public.models ADD COLUMN protocol VARCHAR(50) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'full_name') THEN
        ALTER TABLE public.models ADD COLUMN full_name VARCHAR(200) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'email') THEN
        ALTER TABLE public.models ADD COLUMN email VARCHAR(255) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'phone') THEN
        ALTER TABLE public.models ADD COLUMN phone VARCHAR(50) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'age') THEN
        ALTER TABLE public.models ADD COLUMN age INTEGER NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'state') THEN
        ALTER TABLE public.models ADD COLUMN state VARCHAR(50) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'weight_kg') THEN
        ALTER TABLE public.models ADD COLUMN weight_kg NUMERIC(5,2) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'legal_guardian_name') THEN
        ALTER TABLE public.models ADD COLUMN legal_guardian_name VARCHAR(200) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'legal_guardian_contact') THEN
        ALTER TABLE public.models ADD COLUMN legal_guardian_contact VARCHAR(50) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'lgpd_accepted') THEN
        ALTER TABLE public.models ADD COLUMN lgpd_accepted BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'models' AND column_name = 'internal_notes') THEN
        ALTER TABLE public.models ADD COLUMN internal_notes TEXT NULL;
    END IF;

    -- 4. Cria índices para consultas rápidas por status e protocolo
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'models' AND indexname = 'idx_models_status') THEN
        CREATE INDEX idx_models_status ON public.models(status);
        RAISE NOTICE 'Índice idx_models_status criado na tabela models.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'models' AND indexname = 'idx_models_protocol') THEN
        CREATE INDEX idx_models_protocol ON public.models(protocol);
        RAISE NOTICE 'Índice idx_models_protocol criado na tabela models.';
    END IF;

    -- 5. Sincroniza foto de capa: se houver mídia com is_cover = TRUE, assegura primary_photo_url
    UPDATE public.models m
    SET primary_photo_url = mm.file_url
    FROM public.model_media mm
    WHERE mm.model_id = m.id
      AND mm.is_cover = TRUE
      AND mm.is_active = TRUE
      AND (m.primary_photo_url IS NULL OR m.primary_photo_url = '');

    RAISE NOTICE 'Consolidação de Single-Table Lifecycle concluída com sucesso.';
END $$;
