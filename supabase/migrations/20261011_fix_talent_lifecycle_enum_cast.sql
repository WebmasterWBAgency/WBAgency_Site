-- ==============================================================================
-- MIGRAÇÃO POSTGRESQL / SUPABASE: CORREÇÃO DE CAST E TIPO DO STATUS EM MODELS
-- Elimina o erro: operator does not exist: talent_lifecycle_status = character varying
-- Assegura foto de capa para todos os modelos com mídia ativa
-- ==============================================================================

DO $$
BEGIN
    -- 1. Se a coluna status em models for do tipo ENUM (talent_lifecycle_status) ou USER-DEFINED,
    --    converte para VARCHAR(50) para total compatibilidade com Hibernate (@Enumerated(EnumType.STRING)).
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'models' 
          AND column_name = 'status'
          AND (udt_name = 'talent_lifecycle_status' OR data_type = 'USER-DEFINED')
    ) THEN
        ALTER TABLE public.models ALTER COLUMN status TYPE VARCHAR(50) USING status::varchar;
        RAISE NOTICE 'Coluna status em models convertida com sucesso para VARCHAR(50).';
    END IF;

    -- 2. Se a coluna status não existir na tabela models, cria como VARCHAR(50) NOT NULL DEFAULT 'CAST_PUBLIC'
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'models' 
          AND column_name = 'status'
    ) THEN
        ALTER TABLE public.models ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'CAST_PUBLIC';
        RAISE NOTICE 'Coluna status adicionada a models com default CAST_PUBLIC.';
    END IF;

    -- 3. Atualiza modelos ativos para CAST_PUBLIC ou CAST_STAR se estiverem nulos
    UPDATE public.models
    SET status = CASE WHEN is_star = TRUE THEN 'CAST_STAR' ELSE 'CAST_PUBLIC' END
    WHERE status IS NULL OR status = '';

    -- 4. Se o tipo customizado talent_lifecycle_status existir no Postgres,
    --    cria CAST bidirecional IMPLICIT para prevenir erros em views, triggers ou queries legadas.
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'talent_lifecycle_status') THEN
        BEGIN
            CREATE CAST (character varying AS talent_lifecycle_status) WITH INOUT AS IMPLICIT;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        BEGIN
            CREATE CAST (talent_lifecycle_status AS character varying) WITH INOUT AS IMPLICIT;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        RAISE NOTICE 'Casts implícitos para talent_lifecycle_status verificados/criados.';
    END IF;

    -- 5. Cria índice para buscas de modelos por status
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'models' AND indexname = 'idx_models_status') THEN
        CREATE INDEX idx_models_status ON public.models(status);
        RAISE NOTICE 'Índice idx_models_status criado na tabela models.';
    END IF;

    -- 6. Garantia de foto de capa (is_cover = true) para todo modelo com mídia ativa
    UPDATE public.model_media mm
    SET is_cover = TRUE
    WHERE mm.id IN (
        SELECT DISTINCT ON (m.id) m2.id
        FROM public.models m
        JOIN public.model_media m2 ON m2.model_id = m.id AND m2.is_active = TRUE
        WHERE NOT EXISTS (
            SELECT 1 FROM public.model_media mc 
            WHERE mc.model_id = m.id AND mc.is_cover = TRUE AND mc.is_active = TRUE
        )
        ORDER BY m.id, m2.display_order ASC, m2.created_at ASC
    );

    -- 7. Sincroniza primary_photo_url em models a partir da capa ativa
    UPDATE public.models m
    SET primary_photo_url = mm.file_url
    FROM public.model_media mm
    WHERE mm.model_id = m.id
      AND mm.is_cover = TRUE
      AND mm.is_active = TRUE
      AND (m.primary_photo_url IS NULL OR m.primary_photo_url = '');

    RAISE NOTICE 'Migração de status e mídias de modelos concluída com sucesso.';
END $$;
