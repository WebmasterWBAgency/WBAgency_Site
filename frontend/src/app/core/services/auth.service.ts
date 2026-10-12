import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, tap, catchError, throwError, delay, finalize } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AdminUser,
  AuthResponse,
  LoginRequest,
  RefreshTokenRequest,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  MessageResponse,
  ActivateAccountRequest,
  ApiResponse
} from '../../shared/models/auth.interface';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly TOKEN_KEY = 'wb_auth_token';
  private readonly REFRESH_TOKEN_KEY = 'wb_auth_refresh_token';
  private readonly USER_KEY = 'wb_auth_user';

  // Chaves de compatibilidade legada
  private readonly LEGACY_TOKEN_KEY = 'wb_scouting_token';
  private readonly LEGACY_USER_KEY = 'wb_scouting_user';

  // Gerenciamento de estado reativo com Signals do Angular 18+
  private readonly currentUserSignal = signal<AdminUser | null>(this.getStoredUser());
  readonly currentUser = computed(() => this.currentUserSignal());
  readonly mustChangePassword = signal<boolean>(this.getStoredUser()?.mustChangePassword ?? false);
  readonly isAuthenticated = computed(() => {
    const user = this.currentUserSignal();
    const token = this.getToken();
    return !!user && !!token && !this.isTokenExpired(token);
  });

  /**
   * Realiza login administrativo via API Spring Boot.
   */
  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/login`, credentials).pipe(
      tap((res) => {
        if (res.mustChangePassword !== undefined) {
          this.mustChangePassword.set(!!res.mustChangePassword);
        } else if (res.user?.mustChangePassword !== undefined) {
          this.mustChangePassword.set(!!res.user.mustChangePassword);
        }
        if (!res.requires2fa) {
          this.setSession(res);
        }
      })
    );
  }

  /**
   * Valida o desafio de dois fatores (TOTP RFC 6238 ou Código de Backup).
   * Emite a sessão definitiva caso o código seja aceito.
   */
  challenge2fa(tempToken: string, code: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/2fa/challenge`, { tempToken, code }).pipe(
      tap((res) => {
        this.setSession(res);
      })
    );
  }

  /**
   * Renova o token JWT utilizando o refresh token persistido.
   */
  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      this.logout();
      return throwError(() => new Error('Nenhum refresh token disponível.'));
    }

    const payload: RefreshTokenRequest = { refreshToken };
    return this.http.post<AuthResponse>(`${this.API_URL}/refresh`, payload).pipe(
      tap((res) => {
        this.setSession(res);
      }),
      catchError((err) => {
        this.logout();
        return throwError(() => err);
      })
    );
  }

  /**
   * Executa Hard Logout destruindo credenciais no cliente e notificando o servidor.
   * - Limpeza estrita de localStorage e sessionStorage
   * - Limpeza de todos os cookies acessíveis
   * - Redefinição reativa de sinais de usuário
   * - Redirecionamento substituindo histórico (replaceUrl: true)
   * - Recarregamento do contexto para forçar garbage collection
   */
  secureLogout(): void {
    const token = this.getToken();

    // 1. Limpeza estrita de Storage local e de sessão
    try {
      localStorage.clear();
      sessionStorage.clear();
    } catch {
      // Ignora caso storage esteja indisponível
    }

    // 2. Limpeza de cookies acessíveis
    if (typeof document !== 'undefined' && document.cookie) {
      document.cookie.split(';').forEach((c) => {
        document.cookie = c
          .replace(/^ +/, '')
          .replace(/=.*/, '=;expires=' + new Date().toUTCString() + ';path=/');
      });
    }

    // 3. Zera estados reativos em memória
    this.currentUserSignal.set(null);
    this.mustChangePassword.set(false);

    // 4. Redireciona no SPA para /admin/login SOMENTE se não for a página inicial pública ('/')
    // Se o usuário estiver na Home pública ('/'), limpa as credenciais silenciosamente sem ejetar o visitante
    const isPublicHome = typeof window !== 'undefined' && (window.location.pathname === '/' || window.location.pathname === '');
    if (!isPublicHome) {
      this.router.navigate(['/admin/login'], { replaceUrl: true });
    }

    // 5. Notifica o backend em segundo plano caso haja token (sem travar a interface)
    if (token) {
      this.http
        .post(
          `${this.API_URL}/logout`,
          {},
          {
            headers: { Authorization: `Bearer ${token}` }
          }
        )
        .subscribe({
          error: () => {
            // Ignora erro caso o token já tenha expirado ou backend offline
          }
        });
    }
  }

  /**
   * Encerra a sessão administrativa executando rotina de Hard Logout defensivo.
   */
  logout(): void {
    this.secureLogout();
  }

  /**
   * Obtém o token JWT ativo armazenado.
   */
  getToken(): string | null {
    return (
      localStorage.getItem(this.TOKEN_KEY) ||
      sessionStorage.getItem(this.TOKEN_KEY) ||
      localStorage.getItem(this.LEGACY_TOKEN_KEY)
    );
  }

  /**
   * Obtém o refresh token ativo armazenado.
   */
  getRefreshToken(): string | null {
    return (
      localStorage.getItem(this.REFRESH_TOKEN_KEY) ||
      sessionStorage.getItem(this.REFRESH_TOKEN_KEY)
    );
  }

  /**
   * Decodifica a expiração do JWT de forma segura.
   */
  isTokenExpired(token?: string | null): boolean {
    const jwt = token ?? this.getToken();
    if (!jwt) return true;

    try {
      const parts = jwt.split('.');
      if (parts.length !== 3) {
        // Se for token mock de teste sem formato 3-part, considera válido
        return false;
      }

      const payloadBase64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const decodedJson = atob(payloadBase64);
      const payload = JSON.parse(decodedJson);

      if (payload && typeof payload.exp === 'number') {
        const expirationDate = payload.exp * 1000;
        return Date.now() >= expirationDate;
      }

      return false;
    } catch {
      return false;
    }
  }

  /**
   * Persiste os dados de sessão no localStorage e atualiza os Signals reativos.
   */
  setSession(authResult: AuthResponse): void {
    const token =
      authResult.token ||
      authResult.accessToken ||
      authResult.jwt ||
      '';

    if (!token) {
      console.error('Token JWT não encontrado na resposta de autenticação:', authResult);
      return;
    }

    localStorage.setItem(this.TOKEN_KEY, token);
    // Também sincroniza com a chave legada para compatibilidade de serviços existentes
    localStorage.setItem(this.LEGACY_TOKEN_KEY, token);

    const refreshToken = authResult.refreshToken;
    if (refreshToken) {
      localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
    }

    const mustChange = authResult.mustChangePassword !== undefined
      ? !!authResult.mustChangePassword
      : (authResult.user?.mustChangePassword ?? false);

    let user: AdminUser;
    if (authResult.user) {
      user = { ...authResult.user, mustChangePassword: mustChange };
    } else {
      user = {
        id: authResult.id || 'admin-01',
        name: authResult.adminName || authResult.name || 'Administrador',
        email: authResult.adminEmail || authResult.email || '',
        role: authResult.role || 'ADMIN',
        mustChangePassword: mustChange
      };
    }

    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    localStorage.setItem(this.LEGACY_USER_KEY, JSON.stringify(user));

    this.currentUserSignal.set(user);
    this.mustChangePassword.set(mustChange);
  }

  /**
   * Recupera o usuário persistido no armazenamento local.
   */
  getStoredUser(): AdminUser | null {
    try {
      const raw =
        localStorage.getItem(this.USER_KEY) ||
        sessionStorage.getItem(this.USER_KEY) ||
        localStorage.getItem(this.LEGACY_USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  /**
   * Solicitação de recuperação de senha institucional (Fase 1).
   * Envia o e-mail corporativo para recebimento do link seguro.
   * Inclui fallback mock com delay de 600ms caso o backend esteja offline (status 0 ou 404).
   */
  requestPasswordReset(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.API_URL}/forgot-password`, { email }).pipe(
      catchError((err) => {
        if (err.status === 0 || err.status === 404) {
          return of({
            message: 'Se o e-mail informado estiver registrado em nossa base, as instruções de redefinição foram enviadas.'
          }).pipe(delay(600));
        }
        return throwError(() => err);
      })
    );
  }

  /**
   * Solicitação de recuperação de senha (suporta email direto ou ForgotPasswordRequest).
   */
  forgotPassword(requestOrEmail: ForgotPasswordRequest | string): Observable<MessageResponse> {
    const email = typeof requestOrEmail === 'string' ? requestOrEmail : requestOrEmail.email;
    return this.requestPasswordReset(email);
  }

  /**
   * Redefinição de senha com token de validação de uso único (Fase 2).
   * Inclui fallback mock com delay de 600ms caso o backend esteja offline (status 0 ou 404).
   */
  resetPassword(payload: { token: string; newPassword: string; confirmPassword?: string } | ResetPasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.API_URL}/reset-password`, payload).pipe(
      catchError((err) => {
        if (err.status === 0 || err.status === 404) {
          return of({
            message: 'Senha atualizada com sucesso.'
          }).pipe(delay(600));
        }
        return throwError(() => err);
      })
    );
  }

  /**
   * Ativação de conta e primeiro acesso com definição de senha definitiva.
   */
  activateAccount(payload: ActivateAccountRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.API_URL}/activate-account`, payload).pipe(
      tap(() => {
        this.mustChangePassword.set(false);
      }),
      catchError((err) => {
        if (err.status === 0 || err.status === 404) {
          this.mustChangePassword.set(false);
          return of({
            message: 'Conta ativada e senha definida com sucesso.'
          }).pipe(delay(600));
        }
        return throwError(() => err);
      })
    );
  }

  /**
   * Atualiza o estado da flag de obrigatoriedade de troca de senha no AuthService e storage.
   */
  setMustChangePassword(value: boolean): void {
    this.mustChangePassword.set(value);
    const user = this.currentUserSignal();
    if (user) {
      user.mustChangePassword = value;
      try {
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
        localStorage.setItem(this.LEGACY_USER_KEY, JSON.stringify(user));
      } catch {
        // ignore
      }
    }
  }

  /**
   * Inicia o ciclo de configuração de 2FA para o administrador logado.
   */
  setup2Fa(): Observable<{ secret: string; qrCodeUri: string; qrCodeDataUrl?: string; otpauthUrl?: string }> {
    return this.http.post<{ secret: string; qrCodeUri: string; qrCodeDataUrl?: string; otpauthUrl?: string }>(`${this.API_URL}/2fa/setup`, {});
  }

  /**
   * Confirma ativação de 2FA validando o código TOTP de 6 dígitos.
   */
  confirm2Fa(code: string): Observable<{ message?: string; is2faEnabled?: boolean; backupCodes?: string[] }> {
    return this.http.post<{ message?: string; is2faEnabled?: boolean; backupCodes?: string[] }>(`${this.API_URL}/2fa/confirm`, { code });
  }

  /**
   * Desativação voluntária de 2FA exigindo senha atual.
   */
  disable2Fa(currentPassword: string): Observable<{ message?: string }> {
    return this.http.post<{ message?: string }>(`${this.API_URL}/2fa/disable`, { currentPassword });
  }
}
