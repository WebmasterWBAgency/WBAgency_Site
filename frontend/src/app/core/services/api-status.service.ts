import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ApiStatusService {
  isServerDown = signal<boolean>(false);
  errorMessage = signal<string>('Servidor temporariamente indisponível. Algumas funções podem não responder.');

  setServerDown(down: boolean, msg?: string): void {
    this.isServerDown.set(down);
    if (msg) {
      this.errorMessage.set(msg);
    }
  }
}
