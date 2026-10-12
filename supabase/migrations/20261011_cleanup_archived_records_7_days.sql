-- Função para expurgar registros do arquivo morto com mais de 7 dias
CREATE OR REPLACE FUNCTION public.fn_cleanup_archived_records_7_days()
RETURNS void AS $$
BEGIN
    -- 1. Remove fotos e candidaturas arquivadas há mais de 7 dias
    DELETE FROM public.candidate_submissions
    WHERE (archived_at IS NOT NULL AND archived_at < NOW() - INTERVAL '7 days')
       OR (status = 'DECLINED' AND updated_at < NOW() - INTERVAL '7 days');

    -- 2. Remove modelos arquivados há mais de 7 dias
    DELETE FROM public.models
    WHERE (status = 'ARCHIVED' OR is_active = false)
      AND updated_at < NOW() - INTERVAL '7 days';
END;
$$ LANGUAGE plpgsql;

-- Agendamento semanal via pg_cron (se habilitado no Supabase)
-- SELECT cron.schedule('cleanup-arquivo-morto-7dias', '0 3 * * 0', 'SELECT public.fn_cleanup_archived_records_7_days();');
