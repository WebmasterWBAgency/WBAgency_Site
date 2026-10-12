import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import {
  passwordMatchValidator,
  strongPasswordValidator
} from '../../../shared/validators/password-match.validator';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.scss']
})
export class ResetPasswordComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  token: string | null = null;
  email: string | null = null;
  tokenInvalid = false;
  isCompleted = false;
  isLoading = false;
  successMessage = '';

  readonly resetForm: FormGroup = this.fb.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(8), strongPasswordValidator]],
      confirmPassword: ['', [Validators.required]]
    },
    { validators: passwordMatchValidator }
  );

  ngOnInit(): void {
    const rawToken = this.route.snapshot.queryParamMap.get('token');
    const rawEmail = this.route.snapshot.queryParamMap.get('email');

    if (!rawToken || rawToken.trim() === '') {
      this.tokenInvalid = true;
      return;
    }

    this.token = rawToken.trim();
    this.email = rawEmail ? rawEmail.trim() : null;
  }

  get passwordVal(): string {
    return this.resetForm.get('newPassword')?.value || '';
  }

  get hasMinLength(): boolean {
    return this.passwordVal.length >= 8;
  }

  get hasUpperCase(): boolean {
    return /[A-Z]/.test(this.passwordVal);
  }

  get hasLowerCase(): boolean {
    return /[a-z]/.test(this.passwordVal);
  }

  get hasNumber(): boolean {
    return /[0-9]/.test(this.passwordVal);
  }

  get hasSpecialChar(): boolean {
    return /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?`~]/.test(this.passwordVal);
  }

  get isPasswordStrong(): boolean {
    return this.hasMinLength && this.hasUpperCase && this.hasLowerCase && this.hasNumber && this.hasSpecialChar;
  }

  onSubmit(): void {
    if (this.resetForm.invalid || !this.token || this.isLoading) {
      this.resetForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    const { newPassword, confirmPassword } = this.resetForm.value;

    this.authService
      .resetPassword({
        email: this.email || undefined,
        token: this.token,
        newPassword: newPassword,
        confirmPassword: confirmPassword
      })
      .subscribe({
        next: () => {
          this.isLoading = false;
          this.isCompleted = true;
          this.successMessage = 'Senha alterada com sucesso! Faça login com a nova senha.';
          setTimeout(() => {
            this.router.navigate(['/admin/login']);
          }, 2500);
        },
        error: () => {
          this.isLoading = false;
          this.tokenInvalid = true;
        }
      });
  }

  navigateToLogin(): void {
    this.router.navigate(['/admin/login']);
  }
}
