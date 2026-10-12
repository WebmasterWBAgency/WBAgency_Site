-- ==============================================================================
-- WB SCOUTING - RESTRIÇÃO DE WEBMASTER ÚNICO & SUPORTE A REDEFINIÇÃO DE SENHA
-- Migration: 20261012_enforce_single_webmaster_and_pwd_reset.sql
-- ==============================================================================

-- 1. Garante a existência das colunas para ciclo de vida de senha em public.admins
ALTER TABLE public.admins 
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS password_reset_token_hash VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS password_reset_expires_at TIMESTAMP WITH TIME ZONE NULL;

-- 2. Índice único parcial estrito: no máximo 1 registro com role 'WEBMASTER'
-- Garante que o banco rejeite tentativas simultâneas via restrição física de unicidade.
CREATE UNIQUE INDEX IF NOT EXISTS uq_single_webmaster 
ON public.admins (role) 
WHERE role = 'WEBMASTER';