import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminProfileService, TwoFactorSetupData } from '../../../core/services/admin-profile.service';
import { AuthService } from '../../../core/services/auth.service';
import QRCode from 'qrcode';

@Component({
  selector: 'app-admin-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-profile.component.html',
  styleUrls: ['./admin-profile.component.scss']
})
export class AdminProfileComponent implements OnInit {
  private readonly profileService = inject(AdminProfileService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  // Estados reativos
  readonly isLoading = signal<boolean>(false);
  readonly is2faEnabled = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly toast = signal<{ message: string; type: 'success' | 'error' } | null>(null);

  // Visibilidade de senhas
  readonly showCurrentPassword = signal<boolean>(false);
  readonly showNewPassword = signal<boolean>(false);
  readonly showConfirmPassword = signal<boolean>(false);

  // Modais de 2FA
  readonly isSetup2faModalOpen = signal<boolean>(false);
  readonly isDisable2faModalOpen = signal<boolean>(false);
  readonly isBackupCodesModalOpen = signal<boolean>(false);
  readonly isSetupActive = signal<boolean>(false);

  // Variáveis diretas de 2FA para bind de template e inserção manual
  qrCodeDataUrl: string = '';
  secret: string = '';

  // Dados de Setup 2FA
  readonly setupData = signal<TwoFactorSetupData | null>(null);
  readonly backupCodes = signal<string[]>([]);
  readonly copiedSecret = signal<boolean>(false);
  readonly copiedBackupCodes = signal<boolean>(false);

  // Formulário de Alteração de Senha
  readonly passwordForm: FormGroup = this.fb.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required]]
  });

  // Formulário de Confirmação 2FA
  readonly confirm2faForm: FormGroup = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^[0-9]{6}$/)]]
  });

  // Formulário de Desativação 2FA
  readonly disable2faForm: FormGroup = this.fb.group({
    password: ['', [Validators.required]]
  });

  // Requisitos de complexidade da nova senha
  readonly newPasswordValue = signal<string>('');
  readonly confirmPasswordValue = signal<string>('');
  readonly hasMinLength = computed(() => this.newPasswordValue().length >= 8);
  readonly hasUppercase = computed(() => /[A-Z]/.test(this.newPasswordValue()));
  readonly hasLowercase = computed(() => /[a-z]/.test(this.newPasswordValue()));
  readonly hasNumber = computed(() => /[0-9]/.test(this.newPasswordValue()));
  readonly hasSpecialChar = computed(() => /[@$!%*?&#^+=._\-]/.test(this.newPasswordValue()));
  readonly passwordsMatch = computed(() => {
    const p1 = this.newPasswordValue();
    const p2 = this.confirmPasswordValue();
    return !!p1 && p1 === p2;
  });

  get currentUser() {
    return this.authService.currentUser();
  }

  showToast(message: string, type: 'success' | 'error' = 'success'): void {
    this.toast.set({ message, type });
    setTimeout(() => {
      const current = this.toast();
      if (current && current.message === message) {
        this.toast.set(null);
      }
    }, 4500);
  }

  ngOnInit(): void {
    this.check2faStatus();

    this.passwordForm.get('newPassword')?.valueChanges.subscribe(val => {
      this.newPasswordValue.set(val || '');
    });
    this.passwordForm.get('confirmPassword')?.valueChanges.subscribe(val => {
      this.confirmPasswordValue.set(val || '');
    });
  }

  check2faStatus(): void {
    this.profileService.get2faStatus().subscribe({
      next: (res) => {
        this.is2faEnabled.set(res.is2faEnabled);
      },
      error: () => {
        // Fallback para estado do usuário armazenado
        this.is2faEnabled.set(!!this.currentUser?.is2faEnabled);
      }
    });
  }

  submitPasswordChange(): void {
    if (this.passwordForm.invalid || !this.passwordsMatch()) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    if (!this.hasMinLength() || !this.hasUppercase() || !this.hasLowercase() || !this.hasNumber() || !this.hasSpecialChar()) {
      this.errorMessage.set('A nova senha não atende a todos os critérios de segurança.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const { currentPassword, newPassword, confirmPassword } = this.passwordForm.value;

    this.profileService.changePassword({ currentPassword, newPassword, confirmPassword }).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        this.successMessage.set(res.message || 'Senha alterada com sucesso.');
        this.passwordForm.reset();
        setTimeout(() => this.successMessage.set(null), 5000);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || err?.error?.message || 'Erro ao alterar senha. Verifique sua senha atual.');
      }
    });
  }

  start2faSetup(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.setup2Fa().subscribe({
      next: async (res: any) => {
        try {
          const payload = res?.data || res;
          this.secret = payload.secret || '';
          const qrCodeUri = payload.qrCodeUri || payload.otpauthUrl || '';

          if (qrCodeUri) {
            // Renderize a imagem do QR Code a partir de qrCodeUri convertendo a string otpauth://... em Data URL base64 usando QRCode.toDataURL(qrCodeUri)
            this.qrCodeDataUrl = await QRCode.toDataURL(qrCodeUri, {
              width: 256,
              margin: 1,
              color: {
                dark: '#222624',
                light: '#FFFFFF'
              }
            });
          } else if (payload.qrCodeDataUrl) {
            this.qrCodeDataUrl = payload.qrCodeDataUrl;
          }

          this.setupData.set({
            secret: this.secret,
            otpauthUrl: qrCodeUri,
            qrCodeDataUrl: this.qrCodeDataUrl,
            qrCodeUri
          });

          this.confirm2faForm.reset();
          this.copiedSecret.set(false);
          this.isSetupActive.set(true);
          this.isSetup2faModalOpen.set(true);
        } catch (err) {
          console.error('Erro ao renderizar QR Code:', err);
          this.errorMessage.set('Erro ao gerar imagem do QR Code para autenticação.');
        } finally {
          this.isLoading.set(false);
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || err?.error?.message || 'Erro ao iniciar configuração do 2FA.');
      }
    });
  }

  closeSetup2faModal(): void {
    this.isSetup2faModalOpen.set(false);
    this.isSetupActive.set(false);
    this.setupData.set(null);
    this.qrCodeDataUrl = '';
    this.secret = '';
    this.confirm2faForm.reset();
  }

  copySecret(): void {
    const sec = this.secret || this.setupData()?.secret;
    if (sec && typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(sec).then(() => {
        this.copiedSecret.set(true);
        this.showToast('Chave copiada para a área de transferência.', 'success');
        setTimeout(() => this.copiedSecret.set(false), 3000);
      });
    }
  }

  submitConfirm2fa(): void {
    if (this.confirm2faForm.invalid) {
      this.confirm2faForm.markAllAsTouched();
      return;
    }

    const rawCode = this.confirm2faForm.value.code?.toString().trim();
    if (!rawCode || rawCode.length !== 6) {
      this.errorMessage.set('Insira o código numérico de 6 dígitos.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.confirm2Fa(rawCode).subscribe({
      next: (res: any) => {
        this.isLoading.set(false);
        this.is2faEnabled.set(true);

        // Alerte com toast: "2FA ativado com sucesso!"
        this.showToast('2FA ativado com sucesso!', 'success');
        this.successMessage.set('2FA ativado com sucesso!');
        setTimeout(() => this.successMessage.set(null), 5000);

        // Limpe os campos e oculte a visualização do QR Code
        this.confirm2faForm.reset();
        this.qrCodeDataUrl = '';
        this.secret = '';
        this.isSetupActive.set(false);
        this.closeSetup2faModal();

        const backupCodes = res?.backupCodes || res?.data?.backupCodes || [];
        if (backupCodes.length > 0) {
          this.backupCodes.set(backupCodes);
          this.copiedBackupCodes.set(false);
          this.isBackupCodesModalOpen.set(true);
        }

        const user = this.authService.currentUser();
        if (user) {
          user.is2faEnabled = true;
          try {
            localStorage.setItem('wb_auth_user', JSON.stringify(user));
            localStorage.setItem('wb_scouting_user', JSON.stringify(user));
          } catch {}
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        // Exiba mensagem de erro: "Código inválido ou expirado. Verifique o relógio do aparelho."
        const customError = 'Código inválido ou expirado. Verifique o relógio do aparelho.';
        this.errorMessage.set(customError);
        this.showToast(customError, 'error');
      }
    });
  }

  closeBackupCodesModal(): void {
    this.isBackupCodesModalOpen.set(false);
    this.backupCodes.set([]);
  }

  copyBackupCodes(): void {
    const codes = this.backupCodes().join('\n');
    if (codes && typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(codes).then(() => {
        this.copiedBackupCodes.set(true);
        this.showToast('Códigos de contingência copiados.', 'success');
        setTimeout(() => this.copiedBackupCodes.set(false), 3000);
      });
    }
  }

  openDisable2faModal(): void {
    this.disable2faForm.reset();
    this.isDisable2faModalOpen.set(true);
  }

  closeDisable2faModal(): void {
    this.isDisable2faModalOpen.set(false);
  }

  submitDisable2fa(): void {
    if (this.disable2faForm.invalid) {
      this.disable2faForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const pwd = this.disable2faForm.value.password;

    this.authService.disable2Fa(pwd).subscribe({
      next: (res: any) => {
        this.isLoading.set(false);
        this.is2faEnabled.set(false);
        this.closeDisable2faModal();
        const msg = res?.message || 'Autenticação em duas etapas desativada com sucesso.';
        this.showToast(msg, 'success');
        this.successMessage.set(msg);
        setTimeout(() => this.successMessage.set(null), 5000);

        const user = this.authService.currentUser();
        if (user) {
          user.is2faEnabled = false;
          try {
            localStorage.setItem('wb_auth_user', JSON.stringify(user));
            localStorage.setItem('wb_scouting_user', JSON.stringify(user));
          } catch {}
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        const errDetail = err?.error?.detail || err?.error?.message || 'Senha incorreta. Não foi possível desativar o 2FA.';
        this.errorMessage.set(errDetail);
        this.showToast(errDetail, 'error');
      }
    });
  }
}
