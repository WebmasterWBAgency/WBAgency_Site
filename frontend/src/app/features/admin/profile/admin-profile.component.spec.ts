import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AdminProfileComponent } from './admin-profile.component';
import { AdminProfileService } from '../../../core/services/admin-profile.service';
import { AuthService } from '../../../core/services/auth.service';

describe('AdminProfileComponent', () => {
  let component: AdminProfileComponent;
  let fixture: ComponentFixture<AdminProfileComponent>;
  let profileServiceSpy: jasmine.SpyObj<AdminProfileService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    profileServiceSpy = jasmine.createSpyObj<AdminProfileService>('AdminProfileService', [
      'changePassword',
      'setup2fa',
      'confirm2fa',
      'disable2fa',
      'get2faStatus'
    ]);
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', [
      'currentUser',
      'setup2Fa',
      'confirm2Fa',
      'disable2Fa'
    ]);

    authServiceSpy.currentUser.and.returnValue({
      id: 'uuid-admin-01',
      name: 'Operador Teste',
      email: 'operador@wbagency.com.br',
      role: 'ADMIN',
      is2faEnabled: false
    });

    profileServiceSpy.get2faStatus.and.returnValue(of({ is2faEnabled: false }));

    await TestBed.configureTestingModule({
      imports: [AdminProfileComponent],
      providers: [
        { provide: AdminProfileService, useValue: profileServiceSpy },
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve inicializar com 2FA desativado', () => {
    expect(component).toBeTruthy();
    expect(component.is2faEnabled()).toBeFalse();
  });

  it('deve validar requisitos de complexidade da nova senha', () => {
    component.passwordForm.get('currentPassword')?.setValue('SenhaAtual@123');
    component.passwordForm.get('newPassword')?.setValue('short');
    component.passwordForm.get('confirmPassword')?.setValue('short');

    expect(component.hasMinLength()).toBeFalse();

    component.passwordForm.get('newPassword')?.setValue('ValidPassword123#');
    component.passwordForm.get('confirmPassword')?.setValue('ValidPassword123#');

    expect(component.hasMinLength()).toBeTrue();
    expect(component.hasUppercase()).toBeTrue();
    expect(component.hasLowercase()).toBeTrue();
    expect(component.hasNumber()).toBeTrue();
    expect(component.hasSpecialChar()).toBeTrue();
    expect(component.passwordsMatch()).toBeTrue();
  });

  it('deve alterar a senha com sucesso', () => {
    component.passwordForm.get('currentPassword')?.setValue('SenhaAtual@123');
    component.passwordForm.get('newPassword')?.setValue('NovaSenhaForte@456');
    component.passwordForm.get('confirmPassword')?.setValue('NovaSenhaForte@456');

    profileServiceSpy.changePassword.and.returnValue(of({ message: 'Senha alterada com sucesso.' }));

    component.submitPasswordChange();

    expect(profileServiceSpy.changePassword).toHaveBeenCalledWith({
      currentPassword: 'SenhaAtual@123',
      newPassword: 'NovaSenhaForte@456',
      confirmPassword: 'NovaSenhaForte@456'
    });
    expect(component.successMessage()).toBe('Senha alterada com sucesso.');
  });

  it('deve abrir modal de setup 2FA com QR Code a partir de qrCodeUri dinâmico', async () => {
    const dynamicQrCodeUri = 'otpauth://totp/WBAgency:operador%40wbagency.com.br?secret=BASE32SECRETKEY&issuer=WBAgency';
    authServiceSpy.setup2Fa.and.returnValue(
      of({
        secret: 'BASE32SECRETKEY',
        qrCodeUri: dynamicQrCodeUri,
        qrCodeDataUrl: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA'
      })
    );

    component.start2faSetup();
    await fixture.whenStable();

    expect(authServiceSpy.setup2Fa).toHaveBeenCalled();
    expect(component.isSetup2faModalOpen()).toBeTrue();
    expect(component.secret).toBe('BASE32SECRETKEY');
    expect(component.qrCodeDataUrl).toContain('data:image/png;base64');
  });

  it('deve confirmar 2FA e exibir códigos de backup descartáveis', () => {
    component.confirm2faForm.patchValue({ code: '123456' });
    authServiceSpy.confirm2Fa.and.returnValue(
      of({
        message: '2FA ativado com sucesso.',
        is2faEnabled: true,
        backupCodes: ['CODE-0001', 'CODE-0002', 'CODE-0003']
      })
    );

    component.submitConfirm2fa();

    expect(authServiceSpy.confirm2Fa).toHaveBeenCalledWith('123456');
    expect(component.is2faEnabled()).toBeTrue();
    expect(component.isBackupCodesModalOpen()).toBeTrue();
    expect(component.backupCodes().length).toBe(3);
    expect(component.toast()?.message).toBe('2FA ativado com sucesso!');
  });

  it('deve desativar 2FA com sucesso ao informar a senha correta', () => {
    component.is2faEnabled.set(true);
    component.openDisable2faModal();
    expect(component.isDisable2faModalOpen()).toBeTrue();

    component.disable2faForm.patchValue({ password: 'MinhaSenha@123' });
    authServiceSpy.disable2Fa.and.returnValue(of({ message: 'Autenticação em duas etapas desativada com sucesso.' }));

    component.submitDisable2fa();

    expect(authServiceSpy.disable2Fa).toHaveBeenCalledWith('MinhaSenha@123');
    expect(component.is2faEnabled()).toBeFalse();
    expect(component.isDisable2faModalOpen()).toBeFalse();
  });
});
