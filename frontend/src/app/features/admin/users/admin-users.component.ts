import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdminUserService, AdminUserItem, CreateAdminUserPayload } from '../../../core/services/admin-user.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './admin-users.component.html',
  styleUrls: ['./admin-users.component.scss']
})
export class AdminUsersComponent implements OnInit {
  private readonly adminUserService = inject(AdminUserService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly users = signal<AdminUserItem[]>([]);
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  // Modais
  readonly isCreateModalOpen = signal<boolean>(false);
  readonly isRoleModalOpen = signal<boolean>(false);
  readonly isSuccessPasswordModalOpen = signal<boolean>(false);
  readonly isDeleteModalOpen = signal<boolean>(false);
  readonly isReset2FaModalOpen = signal<boolean>(false);
  readonly isResetPasswordModalOpen = signal<boolean>(false);

  // Estados temporários
  readonly createdTempPassword = signal<string>('');
  readonly createdUserEmail = signal<string>('');
  readonly selectedUserForRole = signal<AdminUserItem | null>(null);
  readonly userToDelete = signal<AdminUserItem | null>(null);
  readonly userFor2FaReset = signal<AdminUserItem | null>(null);
  readonly userForPasswordReset = signal<AdminUserItem | null>(null);
  readonly resetPasswordCustomInput = signal<string>('');
  readonly copiedPassword = signal<boolean>(false);

  // Formulário de Criação
  readonly createUserForm: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    role: ['ADMIN', [Validators.required]]
  });

  // Formulário de Alteração de Papel
  readonly changeRoleForm: FormGroup = this.fb.group({
    role: ['ADMIN', [Validators.required]]
  });

  get currentUser() {
    return this.authService.currentUser();
  }

  get selectedUser(): AdminUserItem | null {
    return this.selectedUserForRole();
  }

  get hasActiveWebmaster(): boolean {
    return this.users().some(u => String(u.role).toUpperCase() === 'WEBMASTER');
  }

  get isSelfWebmaster(): boolean {
    const cur = this.currentUser;
    const sel = this.selectedUser;
    if (!cur || !sel) return false;
    const sameId = (cur.id && sel.id && cur.id === sel.id) || (cur.email && sel.email && cur.email.toLowerCase() === sel.email.toLowerCase());
    return !!sameId && String(sel.role).toUpperCase() === 'WEBMASTER';
  }

  get currentAdminEmail(): string {
    return this.authService.currentUser()?.email || '';
  }

  get isCurrentWebmaster(): boolean {
    return String(this.authService.currentUser()?.role || '').toUpperCase() === 'WEBMASTER';
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.adminUserService.listUsers().subscribe({
      next: (response) => {
        this.users.set(response.content || []);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || 'Erro ao carregar lista de administradores.');
      }
    });
  }

  openCreateModal(): void {
    this.createUserForm.reset({ role: 'ADMIN' });
    this.isCreateModalOpen.set(true);
  }

  closeCreateModal(): void {
    this.isCreateModalOpen.set(false);
  }

  submitCreateUser(): void {
    if (this.createUserForm.invalid) {
      this.createUserForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const payload: CreateAdminUserPayload = this.createUserForm.value;

    this.adminUserService.createUser(payload).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        this.closeCreateModal();
        this.createdTempPassword.set(res.temporaryPassword);
        this.createdUserEmail.set(res.user.email);
        this.copiedPassword.set(false);
        this.isSuccessPasswordModalOpen.set(true);
        this.loadUsers();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || 'Erro ao cadastrar novo administrador.');
      }
    });
  }

  closeSuccessPasswordModal(): void {
    this.isSuccessPasswordModalOpen.set(false);
    this.createdTempPassword.set('');
  }

  copyTemporaryPassword(): void {
    const pwd = this.createdTempPassword();
    if (pwd && typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(pwd).then(() => {
        this.copiedPassword.set(true);
        setTimeout(() => this.copiedPassword.set(false), 3000);
      });
    }
  }

  // ============================================================
  // GESTÃO DE PAPEL (ROLE) COM TRAVA DE WEBMASTER ÚNICO
  // ============================================================
  hasActiveWebmasterOtherThan(targetId?: string): boolean {
    return this.users().some(u =>
      String(u.role).toUpperCase() === 'WEBMASTER' &&
      u.isActive &&
      u.id !== targetId
    );
  }

  openRoleModal(user: AdminUserItem): void {
    this.selectedUserForRole.set(user);
    const cur = this.currentUser;
    const isSelfWebmaster = ((cur?.id && user.id && cur.id === user.id) || this.isCurrentUser(user)) && String(user.role).toUpperCase() === 'WEBMASTER';
    this.changeRoleForm.reset({
      role: { value: user.role, disabled: isSelfWebmaster }
    });
    this.isRoleModalOpen.set(true);
  }

  openRoleModalSafe(user: AdminUserItem): void {
    this.openRoleModal(user);
  }

  closeRoleModal(): void {
    this.isRoleModalOpen.set(false);
    this.selectedUserForRole.set(null);
  }

  submitChangeRole(): void {
    const user = this.selectedUserForRole();
    if (!user) return;

    const newRole = this.changeRoleForm.getRawValue().role;

    if (this.isSelfWebmaster) {
      this.errorMessage.set('Você não pode alterar seu próprio cargo de Webmaster.');
      setTimeout(() => this.errorMessage.set(null), 4000);
      return;
    }

    if (newRole === 'WEBMASTER' && this.hasActiveWebmaster && user.role !== 'WEBMASTER') {
      this.errorMessage.set('Limite de 1 Webmaster ativo atingido. Rebaixe o atual antes de promover este usuário.');
      setTimeout(() => this.errorMessage.set(null), 5000);
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.adminUserService.updateUserRole(user.id, newRole).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.closeRoleModal();
        this.successMessage.set(`Papel de ${user.name} atualizado para ${newRole}.`);
        setTimeout(() => this.successMessage.set(null), 4000);
        this.loadUsers();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || err?.error?.message || 'Erro ao atualizar nível de acesso.');
      }
    });
  }

  submitChangeRoleSafe(): void {
    this.submitChangeRole();
  }

  // ============================================================
  // RESET ADMINISTRATIVO DE 2FA
  // ============================================================
  openReset2FaModal(user: AdminUserItem): void {
    this.userFor2FaReset.set(user);
    this.isReset2FaModalOpen.set(true);
  }

  closeReset2FaModal(): void {
    this.isReset2FaModalOpen.set(false);
    this.userFor2FaReset.set(null);
  }

  confirmReset2Fa(): void {
    const user = this.userFor2FaReset();
    if (!user) return;

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.adminUserService.resetUser2Fa(user.id).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.closeReset2FaModal();
        this.successMessage.set(`2FA de ${user.name} foi resetado com sucesso. Sessões ativas foram invalidadas.`);
        setTimeout(() => this.successMessage.set(null), 5000);
        this.loadUsers();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || err?.error?.message || 'Erro ao resetar 2FA do usuário.');
      }
    });
  }

  // ============================================================
  // RESET ADMINISTRATIVO DE SENHA
  // ============================================================
  openResetPasswordModal(user: AdminUserItem): void {
    this.userForPasswordReset.set(user);
    this.resetPasswordCustomInput.set('');
    this.isResetPasswordModalOpen.set(true);
  }

  closeResetPasswordModal(): void {
    this.isResetPasswordModalOpen.set(false);
    this.userForPasswordReset.set(null);
    this.resetPasswordCustomInput.set('');
  }

  onResetPasswordInputChange(event: Event): void {
    const target = event.target as HTMLInputElement;
    this.resetPasswordCustomInput.set(target.value);
  }

  generateRandomTempPassword(): void {
    const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
    const lower = 'abcdefghijkmnopqrstuvwxyz';
    const digits = '23456789';
    const special = '@#$%&*!';

    let pwd = 'Wb@';
    for (let i = 0; i < 3; i++) {
      pwd += upper.charAt(Math.floor(Math.random() * upper.length));
      pwd += lower.charAt(Math.floor(Math.random() * lower.length));
    }
    pwd += digits.charAt(Math.floor(Math.random() * digits.length));
    pwd += special.charAt(Math.floor(Math.random() * special.length));

    this.resetPasswordCustomInput.set(pwd);
  }

  confirmResetPassword(): void {
    const user = this.userForPasswordReset();
    if (!user) return;

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const tempPassword = this.resetPasswordCustomInput().trim() || undefined;

    this.adminUserService.resetUserPassword(user.id, tempPassword).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        const effectivePwd = res?.temporaryPassword || tempPassword || '';
        this.closeResetPasswordModal();
        this.createdTempPassword.set(effectivePwd);
        this.createdUserEmail.set(user.email);
        this.copiedPassword.set(false);
        this.isSuccessPasswordModalOpen.set(true);
        this.successMessage.set(`Senha do administrador ${user.name} resetada com sucesso.`);
        setTimeout(() => this.successMessage.set(null), 5000);
        this.loadUsers();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || err?.error?.message || 'Erro ao resetar senha do administrador.');
      }
    });
  }

  // ============================================================
  // EXCLUSÃO E STATUS
  // ============================================================
  openDeleteModal(user: AdminUserItem): void {
    if (this.isCurrentUser(user)) {
      this.errorMessage.set('Por segurança, você não pode excluir seu próprio usuário logado.');
      setTimeout(() => this.errorMessage.set(null), 4000);
      return;
    }
    this.userToDelete.set(user);
    this.isDeleteModalOpen.set(true);
  }

  closeDeleteModal(): void {
    this.isDeleteModalOpen.set(false);
    this.userToDelete.set(null);
  }

  confirmDeleteUser(): void {
    const user = this.userToDelete();
    if (!user) return;

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.adminUserService.deleteUser(user.id).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.closeDeleteModal();
        this.successMessage.set(`Administrador ${user.name} excluído com sucesso.`);
        setTimeout(() => this.successMessage.set(null), 4000);
        this.loadUsers();
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || 'Erro ao excluir administrador.');
      }
    });
  }

  toggleStatus(user: AdminUserItem): void {
    if (this.isCurrentUser(user)) {
      this.errorMessage.set('Por segurança, você não pode desativar seu próprio usuário logado.');
      setTimeout(() => this.errorMessage.set(null), 4000);
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.adminUserService.toggleStatus(user.id).subscribe({
      next: (updatedUser: AdminUserItem) => {
        this.isLoading.set(false);
        const action = updatedUser.isActive ? 'ativada' : 'desativada';
        this.successMessage.set(`Conta de ${updatedUser.name} foi ${action} com sucesso.`);
        setTimeout(() => this.successMessage.set(null), 4000);
        this.loadUsers();
      },
      error: (err: any) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || 'Erro ao alterar status do usuário.');
      }
    });
  }

  getInitials(name: string): string {
    if (!name) return 'WB';
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  isCurrentUser(user: AdminUserItem): boolean {
    return user.email.toLowerCase() === this.currentAdminEmail.toLowerCase();
  }

  isWebmasterRole(role: string | null | undefined): boolean {
    return String(role || '').toUpperCase() === 'WEBMASTER';
  }

  openDeleteModalSafe(user: AdminUserItem): void {
    if (this.isCurrentUser(user)) {
      this.errorMessage.set('Por segurança, você não pode excluir seu próprio usuário logado.');
      setTimeout(() => this.errorMessage.set(null), 4000);
      return;
    }
    if (this.isWebmasterRole(user.role)) {
      this.errorMessage.set('🔒 Operação bloqueada: Contas de WEBMASTER são de provisionamento raiz e não podem ser excluídas pelo painel (RBAC-018).');
      setTimeout(() => this.errorMessage.set(null), 6000);
      return;
    }
    this.openDeleteModal(user);
  }

  submitCreateUserSafe(): void {
    if (this.createUserForm.invalid) {
      this.createUserForm.markAllAsTouched();
      return;
    }
    const formRole = String(this.createUserForm.value?.role || '').toUpperCase();
    if (this.isWebmasterRole(formRole)) {
      this.successMessage.set('🔒 Aviso: Não é permitido criar contas de WEBMASTER pelo painel. O novo administrador foi definido como SUPER_ADMIN.');
      setTimeout(() => this.successMessage.set(null), 7000);
      this.createUserForm.patchValue({ role: 'SUPER_ADMIN' }, { emitEvent: false });
    }
    this.submitCreateUser();
  }
}
