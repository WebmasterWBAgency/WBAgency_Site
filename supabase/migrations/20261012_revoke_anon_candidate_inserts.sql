-- ==============================================================================
-- WB SCOUTING - HARDENING RLS POSTGREST: REVOGAÇÃO DE INSERÇÃO ANÔNIMA DIRETA
-- Migration: 20261012_revoke_anon_candidate_inserts.sql
-- Justificativa: Submissão de candidaturas é estritamente mediada pela API Spring Boot
-- com credenciais seguras (service_role), prevenindo spam e DoS via PostgREST direto.
-- ==============================================================================

-- 1. Remove políticas de inserção pública/anônima em candidates
DROP POLICY IF EXISTS "Public Insert Candidate Application" ON public.candidates;

-- 2. Remove políticas de inserção pública/anônima em candidate_photos
DROP POLICY IF EXISTS "Public Insert Candidate Photos" ON public.candidate_photos;

-- 3. Confirma que inserções são restritas à service_role (Backend Java)
-- As políticas de 'service_role' já existentes garantem full access para o backend.
