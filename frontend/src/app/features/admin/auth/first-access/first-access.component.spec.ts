import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FirstAccessComponent } from './first-access.component';
import { AuthService } from '../../../../core/services/auth.service';
import { AdminProfileService } from '../../../../core/services/admin-profile.service';

describe('FirstAccessComponent', () => {
  let component: FirstAccessComponent;
  let fixture: ComponentFixture<FirstAccessComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let profileServiceSpy: jasmine.SpyObj<AdminProfileService>;
  let router: Router;

  const createComponentWithRoute = (queryParams: Record<string, string>) => {
    authServiceSpy = jasmine.createSpyObj('AuthService', [
      'isAuthenticated',
      'currentUser',
      'activateAccount',
      'setMustChangePassword'
    ]);
    profileServiceSpy = jasmine.createSpyObj('AdminProfileService', ['changePassword']);

    TestBed.configureTestingModule({
      imports: [FirstAccessComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: AdminProfileService, useValue: profileServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (key: string) => queryParams[key] || null
              }
            }
          }
        }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(FirstAccessComponent);
    component = fixture.componentInstance;
  };

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  describe('Cenário 1: Via Link do E-mail', () => {
    beforeEach(() => {
      createComponentWithRoute({ token: 'test-activation-token', email: 'novo@wbagency.com.br' });
      fixture.detectChanges();
    });

    it('deve inicializar no modo email-token e capturar token e email', () => {
      expect(component.mode).toBe('email-token');
      expect(component.token).toBe('test-activation-token');
      expect(component.email).toBe('novo@wbagency.com.br');
      expect(component.form.get('newPassword')).toBeTruthy();
      expect(component.form.get('confirmPassword')).toBeTruthy();
      expect(component.form.get('currentPassword')).toBeNull();
    });

    it('deve validar requisitos de complexidade da nova senha', () => {
      component.form.patchValue({ newPassword: 'fraca' });
      expect(component.hasMinLength).toBeFalse();
      expect(component.isComplexityMet).toBeFalse();

      component.form.patchValue({ newPassword: 'SenhaForte@2026' });
      expect(component.hasMinLength).toBeTrue();
      expect(component.hasUpper).toBeTrue();
      expect(component.hasLower).toBeTrue();
      expect(component.hasNumber).toBeTrue();
      expect(component.hasSpecial).toBeTrue();
      expect(component.isComplexityMet).toBeTrue();
    });

    it('deve chamar activateAccount e marcar isCompleted no sucesso', () => {
      authServiceSpy.activateAccount.and.returnValue(of({ message: 'Conta ativada com sucesso.' }));

      component.form.patchValue({
        newPassword: 'SenhaForte@2026',
        confirmPassword: 'SenhaForte@2026'
      });

      component.onSubmit();

      expect(authServiceSpy.activateAccount).toHaveBeenCalledWith({
        email: 'novo@wbagency.com.br',
        token: 'test-activation-token',
        newPassword: 'SenhaForte@2026',
        confirmPassword: 'SenhaForte@2026'
      });
      expect(component.isCompleted()).toBeTrue();
      expect(component.errorMessage()).toBeNull();
    });

    it('deve exibir mensagem de erro quando ativação falhar', () => {
      authServiceSpy.activateAccount.and.returnValue(
        throwError(() => ({ error: { message: 'Token expirado' } }))
      );

      component.form.patchValue({
        newPassword: 'SenhaForte@2026',
        confirmPassword: 'SenhaForte@2026'
      });

      component.onSubmit();

      expect(component.isCompleted()).toBeFalse();
      expect(component.errorMessage()).toBe('Token expirado');
    });
  });

  describe('Cenário 2: Via Login Forçado', () => {
    beforeEach(() => {
      createComponentWithRoute({});
      authServiceSpy.isAuthenticated.and.returnValue(true);
      authServiceSpy.currentUser.and.returnValue({
        id: 'uuid-1',
        name: 'Admin Provisório',
        email: 'provisorio@wbagency.com.br',
        role: 'ADMIN',
        mustChangePassword: true
      });
      fixture.detectChanges();
    });

    it('deve inicializar no modo forced-login para usuário logado', () => {
      expect(component.mode).toBe('forced-login');
      expect(component.email).toBe('provisorio@wbagency.com.br');
      expect(component.form.get('currentPassword')).toBeTruthy();
      expect(component.form.get('newPassword')).toBeTruthy();
      expect(component.form.get('confirmPassword')).toBeTruthy();
    });

    it('deve chamar profileService.changePassword e setMustChangePassword(false) no sucesso', () => {
      profileServiceSpy.changePassword.and.returnValue(of({ message: 'Senha alterada com sucesso.' }));

      component.form.patchValue({
        currentPassword: 'SenhaProvisoria123',
        newPassword: 'NovaSenhaForte@2026',
        confirmPassword: 'NovaSenhaForte@2026'
      });

      component.onSubmit();

      expect(profileServiceSpy.changePassword).toHaveBeenCalledWith({
        currentPassword: 'SenhaProvisoria123',
        newPassword: 'NovaSenhaForte@2026',
        confirmPassword: 'NovaSenhaForte@2026'
      });
      expect(authServiceSpy.setMustChangePassword).toHaveBeenCalledWith(false);
      expect(component.isCompleted()).toBeTrue();
    });
  });
});
