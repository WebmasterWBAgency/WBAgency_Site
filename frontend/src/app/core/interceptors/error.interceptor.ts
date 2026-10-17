import { inject } from '@angular/core';
import { HttpInterceptorFn, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { catchError, tap, throwError } from 'rxjs';
import { ApiStatusService } from '../services/api-status.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const apiStatusService = inject(ApiStatusService);

  return next(req).pipe(
    tap(event => {
      if (event instanceof HttpResponse) {
        if (apiStatusService.isServerDown()) {
          apiStatusService.setServerDown(false);
        }
      }
    }),
    catchError((error: HttpErrorResponse) => {
      if (error.status === 0 || error.status >= 500) {
        apiStatusService.setServerDown(true);
      }
      if (error.status === 403) {
        console.error('Acesso proibido para este recurso:', req.url);
      }
      return throwError(() => error);
    })
  );
};
