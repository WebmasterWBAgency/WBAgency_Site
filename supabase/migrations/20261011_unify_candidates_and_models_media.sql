-- ==============================================================================
-- MIGRAÇÃO IDEMPOTENTE: UNIFICAÇÃO DE MODELOS E CANDIDATOS + SINCRONIZAÇÃO DE MÍDIAS
-- ==============================================================================

DO $$
BEGIN
    -- 1. Garante que a coluna converted_to_model_id existe na tabela candidates
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'candidates' 
          AND column_name = 'converted_to_model_id'
    ) THEN
        ALTER TABLE public.candidates 
        ADD COLUMN converted_to_model_id UUID NULL 
        REFERENCES public.models(id) ON DELETE SET NULL;
        RAISE NOTICE 'Coluna converted_to_model_id adicionada à tabela candidates.';
    END IF;

    -- 2. Garante que a coluna archived_at existe na tabela candidates
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'candidates' 
          AND column_name = 'archived_at'
    ) THEN
        ALTER TABLE public.candidates 
        ADD COLUMN archived_at TIMESTAMPTZ NULL;
        RAISE NOTICE 'Coluna archived_at adicionada à tabela candidates.';
    END IF;

    -- 3. Cria índice na chave de promoção
    IF NOT EXISTS (
        SELECT 1 
        FROM pg_indexes 
        WHERE schemaname = 'public' 
          AND tablename = 'candidates' 
          AND indexname = 'idx_candidates_converted_model'
    ) THEN
        CREATE INDEX idx_candidates_converted_model ON public.candidates(converted_to_model_id);
        RAISE NOTICE 'Índice idx_candidates_converted_model criado.';
    END IF;

    -- 4. Alinha foreign key em candidate_photos para apontar para a tabela canônica candidates
    IF EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_schema = 'public' 
          AND table_name = 'candidate_photos' 
          AND constraint_name = 'fk_candidate_photos_submission'
    ) THEN
        ALTER TABLE public.candidate_photos 
        DROP CONSTRAINT fk_candidate_photos_submission;
        RAISE NOTICE 'Constraint legada fk_candidate_photos_submission removida.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_schema = 'public' 
          AND table_name = 'candidate_photos' 
          AND constraint_name = 'fk_candidate_photos_candidate'
    ) THEN
        ALTER TABLE public.candidate_photos 
        ADD CONSTRAINT fk_candidate_photos_candidate 
        FOREIGN KEY (candidate_id) 
        REFERENCES public.candidates(id) 
        ON DELETE CASCADE;
        RAISE NOTICE 'Constraint canônica fk_candidate_photos_candidate criada com sucesso.';
    END IF;

    -- 5. Garante que primary_photo_url de models reflete a foto definida como capa em model_media
    UPDATE public.models m
    SET primary_photo_url = mm.file_url
    FROM public.model_media mm
    WHERE mm.model_id = m.id
      AND mm.is_cover = TRUE
      AND mm.is_active = TRUE
      AND (m.primary_photo_url IS NULL OR m.primary_photo_url = '');

    RAISE NOTICE 'Sincronização de fotos de capa de modelos concluída com sucesso.';

END $$;
