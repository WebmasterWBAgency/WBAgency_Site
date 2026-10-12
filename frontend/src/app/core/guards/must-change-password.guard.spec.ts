import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { mustChangePasswordGuard } from './must-change-password.guard';
import { AuthService } from '../services/auth.service';

describe('mustChangePasswordGuard', () => {
  let authServiceSpy: { mustChangePassword: jasmine.Spy };
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = {
      mustChangePassword: jasmine.createSpy('mustChangePassword')
    };
    routerSpy = jasmine.createSpyObj('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  it('should allow access if mustChangePassword is false', () => {
    authServiceSpy.mustChangePassword.and.returnValue(false);

    const route = {} as ActivatedRouteSnapshot;
    const state = { url: '/admin/dashboard' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => mustChangePasswordGuard(route, state));

    expect(result).toBeTrue();
  });

  it('should redirect to /admin/primeiro-acesso if mustChangePassword is true', () => {
    authServiceSpy.mustChangePassword.and.returnValue(true);
    const mockUrlTree = {} as UrlTree;
    routerSpy.createUrlTree.and.returnValue(mockUrlTree);

    const route = {} as ActivatedRouteSnapshot;
    const state = { url: '/admin/dashboard' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => mustChangePasswordGuard(route, state));

    expect(result).toBe(mockUrlTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/admin/primeiro-acesso']);
  });
});
