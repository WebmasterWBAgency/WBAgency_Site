import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Route guard que intercepta acessos ao painel administrativo quando o usuário
 * autenticado possuir a flag de troca obrigatória de senha ativa (mustChangePassword === true).
 * Força o redirecionamento imediato para a tela de primeiro acesso (/admin/primeiro-acesso).
 */
export const mustChangePasswordGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.mustChangePassword()) {
    return router.createUrlTree(['/admin/primeiro-acesso']);
  }

  return true;
};
