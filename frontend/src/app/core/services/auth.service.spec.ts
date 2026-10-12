import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;
  let mockRouter: jasmine.SpyObj<Router>;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    mockRouter = jasmine.createSpyObj('Router', ['navigate', 'navigateByUrl']);
    mockRouter.navigate.and.returnValue(Promise.resolve(true));

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: mockRouter }
      ]
    });

    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should be created and start unauthenticated if storage is empty', () => {
    expect(service).toBeTruthy();
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.currentUser()).toBeNull();
    expect(service.getToken()).toBeNull();
  });

  it('should store token and user on successful login', () => {
    const mockExp = Math.floor(Date.now() / 1000) + 3600;
    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify({ exp: mockExp }));
    const testJwt = `${header}.${payload}.sig`;

    const credentials = { email: 'webmaster@wbagency.com.br', password: 'secretpassword' };
    const mockResponse = {
      token: testJwt,
      refreshToken: 'refresh-123',
      user: {
        id: 'admin-1',
        name: 'Carlos Booker',
        email: 'webmaster@wbagency.com.br',
        role: 'WEBMASTER'
      }
    };

    service.login(credentials).subscribe((res) => {
      expect(res.token).toBe(testJwt);
      expect(service.getToken()).toBe(testJwt);
      expect(service.getRefreshToken()).toBe('refresh-123');
      expect(service.currentUser()?.name).toBe('Carlos Booker');
      expect(service.isAuthenticated()).toBeTrue();
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });

  it('should handle mock fallback for local testing with webmaster credentials when backend is offline', () => {
    const credentials = { email: 'webmaster@wbagency.com.br', password: 'admin123' };

    service.login(credentials).subscribe((res) => {
      expect(res).toBeTruthy();
      expect(service.isAuthenticated()).toBeTrue();
      expect(service.currentUser()?.email).toBe('webmaster@wbagency.com.br');
      expect(service.currentUser()?.role).toBe('WEBMASTER');
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush('Network Error', { status: 0, statusText: 'Unknown Error' });
  });

  it('should reject legacy admin@wbscouting.com in offline mock fallback', () => {
    const credentials = { email: 'admin@wbscouting.com', password: 'admin123' };
    let errorThrown = false;

    service.login(credentials).subscribe({
      next: () => fail('Should not authenticate legacy email'),
      error: (err) => {
        errorThrown = true;
        expect(err.status).toBe(0);
      }
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush('Network Error', { status: 0, statusText: 'Unknown Error' });
    expect(errorThrown).toBeTrue();
  });

  it('should execute secureLogout by notifying backend, purging storage, and replacing url', () => {
    localStorage.setItem('wb_auth_token', 'sample-token');
    localStorage.setItem('wb_auth_user', JSON.stringify({ name: 'Admin', email: 'admin@wb.com', role: 'ADMIN' }));
    sessionStorage.setItem('wb_session_key', 'session-data');

    service.secureLogout();

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/logout`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(service.getToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(localStorage.getItem('wb_auth_token')).toBeNull();
    expect(sessionStorage.getItem('wb_session_key')).toBeNull();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/admin/login'], { replaceUrl: true });
  });

  it('should execute secureLogout immediately when no token is present', () => {
    localStorage.setItem('some_stale_key', 'val');
    service.secureLogout();

    httpTesting.expectNone(`${environment.apiUrl}/auth/logout`);
    expect(service.getToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(localStorage.getItem('some_stale_key')).toBeNull();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/admin/login'], { replaceUrl: true });
  });

  it('should correctly detect expired JWT tokens', () => {
    const expiredExp = Math.floor(Date.now() / 1000) - 100;
    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify({ exp: expiredExp }));
    const expiredJwt = `${header}.${payload}.sig`;

    expect(service.isTokenExpired(expiredJwt)).toBeTrue();
  });

  it('should initialize mustChangePassword and update it upon login and activateAccount', () => {
    expect(service.mustChangePassword()).toBeFalse();

    const credentials = { email: 'novo@wbagency.com.br', password: 'temp-password' };
    const mockResponse = {
      token: 'jwt-123',
      mustChangePassword: true,
      user: {
        id: 'admin-2',
        name: 'Novo Admin',
        email: 'novo@wbagency.com.br',
        role: 'ADMIN',
        mustChangePassword: true
      }
    };

    service.login(credentials).subscribe();

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/login`);
    req.flush(mockResponse);

    expect(service.mustChangePassword()).toBeTrue();

    // Testa activateAccount resetando mustChangePassword para false
    service.activateAccount({
      email: 'novo@wbagency.com.br',
      token: 'token-abc',
      newPassword: 'NovaSenha@2026'
    }).subscribe();

    const activateReq = httpTesting.expectOne(`${environment.apiUrl}/auth/activate-account`);
    expect(activateReq.request.method).toBe('POST');
    activateReq.flush({ message: 'Conta ativada com sucesso.' });

    expect(service.mustChangePassword()).toBeFalse();
  });

  it('should call 2fa setup, confirm, and disable endpoints', () => {
    service.setup2Fa().subscribe(res => {
      expect(res.secret).toBe('TESTSECRET');
    });
    const setupReq = httpTesting.expectOne(`${environment.apiUrl}/auth/2fa/setup`);
    expect(setupReq.request.method).toBe('POST');
    setupReq.flush({ secret: 'TESTSECRET', qrCodeUri: 'data:image/png;base64,abc' });

    service.confirm2Fa('123456').subscribe();
    const confirmReq = httpTesting.expectOne(`${environment.apiUrl}/auth/2fa/confirm`);
    expect(confirmReq.request.method).toBe('POST');
    expect(confirmReq.request.body).toEqual({ code: '123456' });
    confirmReq.flush({ message: '2FA ativado' });

    service.disable2Fa('Senha@123').subscribe();
    const disableReq = httpTesting.expectOne(`${environment.apiUrl}/auth/2fa/disable`);
    expect(disableReq.request.method).toBe('POST');
    expect(disableReq.request.body).toEqual({ currentPassword: 'Senha@123' });
    disableReq.flush({ message: '2FA desativado' });
  });
});
