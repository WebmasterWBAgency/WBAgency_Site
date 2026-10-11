-- ==============================================================================
-- Migração: Alinhamento de Foreign Key em candidate_photos -> candidate_submissions
-- Objetivo: Garantir integridade referencial com a tabela canônica de submissões
-- ==============================================================================

DO $$
BEGIN
    -- 1. Remove a constraint legada que aponta para candidates
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_candidate_photos_candidate'
    ) THEN
        ALTER TABLE public.candidate_photos DROP CONSTRAINT fk_candidate_photos_candidate;
    END IF;

    -- 2. Remove versão anterior se existir com outro nome
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_candidate_photos_submission'
    ) THEN
        ALTER TABLE public.candidate_photos DROP CONSTRAINT fk_candidate_photos_submission;
    END IF;
END $$;

-- Limpeza preventiva de registros em candidate_photos cujo candidate_id não exista em candidate_submissions
DELETE FROM public.candidate_photos 
WHERE candidate_id IS NOT NULL 
  AND NOT EXISTS (SELECT 1 FROM public.candidate_submissions cs WHERE cs.id = candidate_photos.candidate_id);

-- 3. Cria a chave estrangeira correta apontando para candidate_submissions com exclusão em cascata
ALTER TABLE public.candidate_photos
ADD CONSTRAINT fk_candidate_photos_submission
FOREIGN KEY (candidate_id) 
REFERENCES public.candidate_submissions(id) 
ON DELETE CASCADE;
