import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-admin-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './admin-login.component.html',
  styleUrls: ['./admin-login.component.scss']
})
export class AdminLoginComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal<boolean>(false);

  // 2FA Challenge Flow
  readonly is2faStep = signal<boolean>(false);
  readonly tempToken = signal<string>('');
  readonly useBackupCode = signal<boolean>(false);

  readonly loginForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
    rememberMe: [false]
  });

  readonly challengeForm: FormGroup = this.fb.group({
    code: ['', [Validators.required]]
  });

  ngOnInit(): void {
    // Se o usuário já estiver com sessão válida ativa, redireciona diretamente ao dashboard
    if (typeof this.authService.isAuthenticated === 'function' && this.authService.isAuthenticated()) {
      this.router.navigate(['/admin/dashboard'], { replaceUrl: true });
      return;
    }

    // Recupera e-mail salvo previamente caso o usuário tenha marcado 'Lembrar senha'
    if (typeof localStorage !== 'undefined') {
      try {
        const savedEmail = localStorage.getItem('wb_remember_email');
        if (savedEmail) {
          this.loginForm.patchValue({
            email: savedEmail,
            rememberMe: true
          });
        }
      } catch {}
    }
  }

  togglePasswordVisibility(): void {
    this.showPassword.update((val) => !val);
  }

  toggleBackupCode(): void {
    this.useBackupCode.update((val) => !val);
    this.challengeForm.reset();
  }

  backToLogin(): void {
    this.is2faStep.set(false);
    this.tempToken.set('');
    this.challengeForm.reset();
    this.errorMessage.set(null);
  }

  onSubmit(): void {
    console.log('[LOGIN] Clique detectado no formulário!');
    console.log('[LOGIN] Status do form:', this.loginForm.status, this.loginForm.value);

    if (this.loginForm.invalid) {
      console.warn('[LOGIN] Formulário inválido. Detalhes dos erros:', {
        emailErrors: this.loginForm.get('email')?.errors,
        passwordErrors: this.loginForm.get('password')?.errors
      });
      this.errorMessage.set('Preencha o e-mail e a senha corretamente.');
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { email, password, rememberMe } = this.loginForm.value;
    console.log('[LOGIN] Disparando requisição para:', `${environment.apiUrl}/auth/login`);

    // Gerencia a preferência do usuário de salvar ou esquecer o e-mail
    if (typeof localStorage !== 'undefined') {
      try {
        if (rememberMe) {
          localStorage.setItem('wb_remember_email', email);
          localStorage.setItem('wb_remember_me', 'true');
        } else {
          localStorage.removeItem('wb_remember_email');
          localStorage.removeItem('wb_remember_me');
        }
      } catch {}
    }

    const credentials = { email: email ? email.trim() : '', password };

    this.authService.login(credentials).subscribe({
      next: (res) => {
        console.log('[LOGIN] Sucesso! Redirecionando para /admin/models...', res);
        this.isLoading.set(false);
        if (res.requires2fa && res.tempToken) {
          this.tempToken.set(res.tempToken);
          this.is2faStep.set(true);
          this.errorMessage.set(null);
          return;
        }
        this.redirectToTarget();
      },
      error: (err) => {
        console.error('[LOGIN] Erro retornado pela API:', err);
        this.isLoading.set(false);
        const detail =
          err?.error?.message ||
          err?.error?.detail ||
          'Credenciais inválidas ou erro no servidor.';
        this.errorMessage.set(detail);
      }
    });
  }

  onSubmitChallenge(): void {
    console.log('[LOGIN 2FA] Validando código do desafio...');
    if (this.challengeForm.invalid) {
      this.errorMessage.set('Informe o código de autenticação.');
      this.challengeForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const code = this.challengeForm.value.code?.trim() || '';

    this.authService.challenge2fa(this.tempToken(), code).subscribe({
      next: () => {
        console.log('[LOGIN 2FA] Sucesso! Redirecionando para /admin/models...');
        this.isLoading.set(false);
        this.redirectToTarget();
      },
      error: (err) => {
        console.error('[LOGIN 2FA] Erro retornado pela API:', err);
        this.isLoading.set(false);
        const detail =
          err?.error?.message ||
          err?.error?.detail ||
          'Código de autenticação inválido ou expirado. Tente novamente.';
        this.errorMessage.set(detail);
      }
    });
  }

  private redirectToTarget(): void {
    let returnUrl = this.route.snapshot.queryParams['returnUrl'];
    if (!returnUrl || returnUrl === '/' || returnUrl === '' || returnUrl.includes('/login')) {
      returnUrl = '/admin/dashboard';
    }
    console.log('[LOGIN] Navegando para rota de destino:', returnUrl);
    this.router.navigateByUrl(returnUrl);
  }
}
