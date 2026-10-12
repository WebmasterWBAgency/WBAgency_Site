import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { AdminProfileService } from '../../../../core/services/admin-profile.service';
import { strongPasswordValidator } from '../../../../shared/validators/password-match.validator';

export const firstAccessPasswordMatchValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const newPassword = control.get('newPassword');
  const confirmPassword = control.get('confirmPassword');
  if (!newPassword || !confirmPassword || !confirmPassword.value) {
    return null;
  }
  return newPassword.value === confirmPassword.value ? null : { mismatch: true };
};

@Component({
  selector: 'app-first-access',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './first-access.component.html',
  styleUrls: ['./first-access.component.scss']
})
export class FirstAccessComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly profileService = inject(AdminProfileService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  mode: 'email-token' | 'forced-login' = 'email-token';
  email: string | null = null;
  token: string | null = null;

  isLoading = signal<boolean>(false);
  isCompleted = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  showCurrentPassword = signal<boolean>(false);
  showNewPassword = signal<boolean>(false);
  showConfirmPassword = signal<boolean>(false);

  form!: FormGroup;

  ngOnInit(): void {
    const rawToken = this.route.snapshot.queryParamMap.get('token');
    const rawEmail = this.route.snapshot.queryParamMap.get('email');

    if (rawToken && rawToken.trim() && rawEmail && rawEmail.trim()) {
      this.mode = 'email-token';
      this.token = rawToken.trim();
      this.email = rawEmail.trim();

      this.form = this.fb.group(
        {
          newPassword: ['', [Validators.required, Validators.minLength(8), strongPasswordValidator]],
          confirmPassword: ['', [Validators.required]]
        },
        { validators: firstAccessPasswordMatchValidator }
      );
    } else if (this.authService.isAuthenticated()) {
      this.mode = 'forced-login';
      const currentUser = this.authService.currentUser();
      this.email = currentUser?.email || null;

      this.form = this.fb.group(
        {
          currentPassword: ['', [Validators.required]],
          newPassword: ['', [Validators.required, Validators.minLength(8), strongPasswordValidator]],
          confirmPassword: ['', [Validators.required]]
        },
        { validators: firstAccessPasswordMatchValidator }
      );
    } else {
      // Nenhum token válido informado e usuário não autenticado
      this.router.navigate(['/admin/login']);
    }
  }

  get newPasswordVal(): string {
    return this.form?.get('newPassword')?.value || '';
  }

  get hasMinLength(): boolean {
    return this.newPasswordVal.length >= 8;
  }

  get hasUpper(): boolean {
    return /[A-Z]/.test(this.newPasswordVal);
  }

  get hasLower(): boolean {
    return /[a-z]/.test(this.newPasswordVal);
  }

  get hasNumber(): boolean {
    return /[0-9]/.test(this.newPasswordVal);
  }

  get hasSpecial(): boolean {
    return /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?`~]/.test(this.newPasswordVal);
  }

  get isComplexityMet(): boolean {
    return this.hasMinLength && this.hasUpper && this.hasLower && this.hasNumber && this.hasSpecial;
  }

  onSubmit(): void {
    if (!this.form || this.form.invalid || this.isLoading()) {
      this.form?.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { currentPassword, newPassword, confirmPassword } = this.form.value;

    if (this.mode === 'email-token') {
      this.authService
        .activateAccount({
          email: this.email!,
          token: this.token!,
          newPassword,
          confirmPassword
        })
        .subscribe({
          next: () => {
            this.isLoading.set(false);
            this.isCompleted.set(true);
            this.successMessage.set(
              'Conta ativada e senha cadastrada com sucesso! Redirecionando para o acesso ao painel...'
            );
            setTimeout(() => {
              this.router.navigate(['/admin/login']);
            }, 2500);
          },
          error: (err: any) => {
            this.isLoading.set(false);
            const msg =
              err?.error?.message ||
              'O link de ativação é inválido ou expirou. Solicite um novo acesso ao administrador.';
            this.errorMessage.set(msg);
          }
        });
    } else {
      this.profileService
        .changePassword({
          currentPassword,
          newPassword,
          confirmPassword
        })
        .subscribe({
          next: () => {
            this.authService.setMustChangePassword(false);
            this.isLoading.set(false);
            this.isCompleted.set(true);
            this.successMessage.set(
              'Senha definitiva cadastrada com sucesso! Redirecionando para o painel de controle...'
            );
            setTimeout(() => {
              this.router.navigate(['/admin/dashboard']);
            }, 2000);
          },
          error: (err: any) => {
            this.isLoading.set(false);
            const msg =
              err?.error?.message ||
              'Senha provisória incorreta ou requisitos de segurança não preenchidos.';
            this.errorMessage.set(msg);
          }
        });
    }
  }

  goToNext(): void {
    if (this.mode === 'forced-login') {
      this.router.navigate(['/admin/dashboard']);
    } else {
      this.router.navigate(['/admin/login']);
    }
  }
}
