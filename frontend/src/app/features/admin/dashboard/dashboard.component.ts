import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent {
  readonly authService = inject(AuthService);

  readonly recentCandidates = signal([
    { name: 'Mariana Rios', age: 19, height: '179 cm', city: 'São Paulo - SP', date: 'Hoje às 14:20', status: 'NOVO' },
    { name: 'Lucas Santana', age: 22, height: '187 cm', city: 'Campinas - SP', date: 'Ontem às 18:45', status: 'AVALIADO' },
    { name: 'Beatriz Vasques', age: 17, height: '176 cm', city: 'Santos - SP', date: '16/09/2026', status: 'NOVO' }
  ]);

  get userName(): string {
    return this.authService.currentUser()?.name || 'Administrador';
  }

  get userEmail(): string {
    return this.authService.currentUser()?.email || 'webmaster@wbagency.com.br';
  }

  get userRole(): string {
    return (this.authService.currentUser()?.role || 'WEBMASTER').toUpperCase();
  }

  get isWebmasterOrSuperAdmin(): boolean {
    const role = this.userRole;
    return role === 'WEBMASTER' || role === 'SUPER_ADMIN';
  }

  logout(): void {
    this.authService.logout();
  }
}
