import { Component, inject, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss']
})
export class ForgotPasswordComponent implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  submittedSuccessfully = false;
  isLoading = false;
  cooldown = 0;
  private cooldownTimer: ReturnType<typeof setInterval> | null = null;

  readonly forgotForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  get isCooldownActive(): boolean {
    return this.cooldown > 0;
  }

  onSubmit(): void {
    if (this.forgotForm.invalid || this.isLoading || this.isCooldownActive) {
      this.forgotForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    const email = this.forgotForm.get('email')?.value?.trim();

    this.authService.forgotPassword(email).subscribe({
      next: () => {
        this.isLoading = false;
        this.submittedSuccessfully = true;
        this.startCooldown();
      },
      error: () => {
        // Política de Segurança Anti-Enumeração (OWASP):
        // Mesmo em falha transitória ou e-mail inexistente, a UI apresenta
        // resposta de sucesso genérica para evitar que invasores descubram cadastros.
        this.isLoading = false;
        this.submittedSuccessfully = true;
        this.startCooldown();
      }
    });
  }

  private startCooldown(): void {
    this.clearCooldown();
    this.cooldown = 60;
    this.cooldownTimer = setInterval(() => {
      this.cooldown--;
      if (this.cooldown <= 0) {
        this.clearCooldown();
      }
    }, 1000);
  }

  private clearCooldown(): void {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
      this.cooldownTimer = null;
    }
  }

  ngOnDestroy(): void {
    this.clearCooldown();
  }
}
