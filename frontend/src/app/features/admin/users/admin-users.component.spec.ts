import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminUsersComponent } from './admin-users.component';
import { AdminUserService, AdminUserItem, PaginatedAdminUsers } from '../../../core/services/admin-user.service';
import { AuthService } from '../../../core/services/auth.service';

describe('AdminUsersComponent', () => {
  let component: AdminUsersComponent;
  let fixture: ComponentFixture<AdminUsersComponent>;
  let adminUserServiceSpy: jasmine.SpyObj<AdminUserService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockUsers: AdminUserItem[] = [
    {
      id: 'uuid-1',
      name: 'Webmaster Principal',
      email: 'webmaster@wbagency.com.br',
      role: 'WEBMASTER',
      isActive: true,
      is2faEnabled: true,
      createdAt: '2026-10-01T10:00:00Z'
    },
    {
      id: 'uuid-2',
      name: 'Operador Admin',
      email: 'admin.geral@wbagency.com.br',
      role: 'ADMIN',
      isActive: true,
      is2faEnabled: false,
      createdAt: '2026-10-01T11:00:00Z'
    }
  ];

  const mockPage: PaginatedAdminUsers = {
    content: mockUsers,
    totalElements: 2,
    totalPages: 1,
    size: 20,
    number: 0
  };

  beforeEach(async () => {
    adminUserServiceSpy = jasmine.createSpyObj<AdminUserService>('AdminUserService', [
      'listUsers',
      'createUser',
      'toggleStatus',
      'updateRole',
      'updateUserRole',
      'resetUser2Fa',
      'resetUserPassword',
      'deleteUser'
    ]);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['currentUser']);

    authServiceSpy.currentUser.and.returnValue({
      id: 'uuid-1',
      name: 'Webmaster Principal',
      email: 'webmaster@wbagency.com.br',
      role: 'WEBMASTER'
    });

    adminUserServiceSpy.listUsers.and.returnValue(of(mockPage));

    await TestBed.configureTestingModule({
      imports: [AdminUsersComponent],
      providers: [
        { provide: AdminUserService, useValue: adminUserServiceSpy },
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUsersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve criar o componente e carregar a lista de administradores', () => {
    expect(component).toBeTruthy();
    expect(adminUserServiceSpy.listUsers).toHaveBeenCalled();
    expect(component.users().length).toBe(2);
  });

  it('deve impedir a auto-desativação do usuário logado', () => {
    const loggedUser = mockUsers[0]; // webmaster@wbagency.com.br
    component.toggleStatus(loggedUser);

    expect(adminUserServiceSpy.toggleStatus).not.toHaveBeenCalled();
    expect(component.errorMessage()).toContain('você não pode desativar seu próprio usuário');
  });

  it('deve alternar status de outro administrador com sucesso', () => {
    const targetUser = mockUsers[1];
    adminUserServiceSpy.toggleStatus.and.returnValue(
      of({ ...targetUser, isActive: false })
    );

    component.toggleStatus(targetUser);

    expect(adminUserServiceSpy.toggleStatus).toHaveBeenCalledWith(targetUser.id);
  });

  it('deve abrir modal de criação e resetar formulário', () => {
    component.openCreateModal();
    expect(component.isCreateModalOpen()).toBeTrue();
    expect(component.createUserForm.get('role')?.value).toBe('ADMIN');
  });

  it('deve cadastrar novo administrador e exibir modal com senha temporária', () => {
    component.openCreateModal();
    component.createUserForm.patchValue({
      name: 'Novo Booker',
      email: 'booker.novo@wbagency.com.br',
      role: 'ADMIN'
    });

    adminUserServiceSpy.createUser.and.returnValue(
      of({
        user: {
          id: 'uuid-new',
          name: 'Novo Booker',
          email: 'booker.novo@wbagency.com.br',
          role: 'ADMIN',
          isActive: true,
          is2faEnabled: false
        },
        temporaryPassword: 'TempPassword@123'
      })
    );

    component.submitCreateUser();

    expect(adminUserServiceSpy.createUser).toHaveBeenCalled();
    expect(component.isCreateModalOpen()).toBeFalse();
    expect(component.isSuccessPasswordModalOpen()).toBeTrue();
    expect(component.createdTempPassword()).toBe('TempPassword@123');
  });

  it('deve impedir o auto-rebaixamento do Webmaster logado e desabilitar o campo', () => {
    const loggedUser = mockUsers[0]; // webmaster@wbagency.com.br
    component.openRoleModal(loggedUser);

    expect(component.isRoleModalOpen()).toBeTrue();
    expect(component.isSelfWebmaster).toBeTrue();
    expect(component.changeRoleForm.get('role')?.disabled).toBeTrue();

    component.submitChangeRole();
    expect(component.errorMessage()).toContain('Você não pode alterar seu próprio cargo de Webmaster.');
    expect(adminUserServiceSpy.updateUserRole).not.toHaveBeenCalled();
  });

  it('deve identificar se já existe Webmaster ativo no sistema', () => {
    expect(component.hasActiveWebmaster).toBeTrue();
    expect(component.hasActiveWebmasterOtherThan('uuid-2')).toBeTrue();
    expect(component.hasActiveWebmasterOtherThan('uuid-1')).toBeFalse();
  });

  it('deve abrir modal e resetar 2FA de outro usuário com sucesso', () => {
    const targetUser = mockUsers[0];
    component.openReset2FaModal(targetUser);

    expect(component.isReset2FaModalOpen()).toBeTrue();
    expect(component.userFor2FaReset()).toBe(targetUser);

    adminUserServiceSpy.resetUser2Fa.and.returnValue(of({ message: '2FA resetado' }));

    component.confirmReset2Fa();

    expect(adminUserServiceSpy.resetUser2Fa).toHaveBeenCalledWith(targetUser.id);
    expect(component.isReset2FaModalOpen()).toBeFalse();
    expect(component.successMessage()).toContain('resetado com sucesso');
  });

  it('deve abrir modal e resetar senha administrativamente com sucesso', () => {
    const targetUser = mockUsers[1];
    component.openResetPasswordModal(targetUser);

    expect(component.isResetPasswordModalOpen()).toBeTrue();
    expect(component.userForPasswordReset()).toBe(targetUser);

    component.generateRandomTempPassword();
    expect(component.resetPasswordCustomInput().length).toBeGreaterThanOrEqual(8);

    adminUserServiceSpy.resetUserPassword.and.returnValue(
      of({ message: 'Senha resetada', temporaryPassword: 'GeneratedPwd@99' })
    );

    component.confirmResetPassword();

    expect(adminUserServiceSpy.resetUserPassword).toHaveBeenCalled();
    expect(component.isResetPasswordModalOpen()).toBeFalse();
    expect(component.isSuccessPasswordModalOpen()).toBeTrue();
    expect(component.createdTempPassword()).toBe('GeneratedPwd@99');
  });

  it('deve impedir a auto-exclusão do usuário logado', () => {
    const loggedUser = mockUsers[0]; // webmaster@wbagency.com.br
    component.openDeleteModal(loggedUser);

    expect(component.isDeleteModalOpen()).toBeFalse();
    expect(component.errorMessage()).toContain('você não pode excluir seu próprio usuário');
  });

  it('deve permitir abrir modal de exclusão e excluir outro administrador', () => {
    const targetUser = mockUsers[1];
    component.openDeleteModal(targetUser);

    expect(component.isDeleteModalOpen()).toBeTrue();
    expect(component.userToDelete()).toBe(targetUser);

    adminUserServiceSpy.deleteUser.and.returnValue(of(void 0));

    component.confirmDeleteUser();

    expect(adminUserServiceSpy.deleteUser).toHaveBeenCalledWith(targetUser.id);
    expect(component.isDeleteModalOpen()).toBeFalse();
    expect(component.successMessage()).toContain('excluído com sucesso');
  });
});
