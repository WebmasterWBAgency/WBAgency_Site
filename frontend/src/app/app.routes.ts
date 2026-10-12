import { Routes } from '@angular/router';
import { adminAuthGuard } from './core/guards/auth.guard';
import { mustChangePasswordGuard } from './core/guards/must-change-password.guard';
import { roleGuard, webmasterOnlyGuard } from './core/guards/role.guard';

export const routes: Routes = [
  // Rotas Públicas Institucionais
  {
    path: '',
    loadComponent: () => import('./features/public/home/home.component').then(m => m.HomeComponent),
    title: 'WB Agency'
  },
  {
    path: 'sobre',
    loadComponent: () => import('./features/public/about/about.component').then(m => m.AboutComponent),
    title: 'Sobre Nós | WB Agency'
  },
  {
    path: 'about',
    redirectTo: 'sobre',
    pathMatch: 'full'
  },
  {
    path: 'models',
    loadComponent: () => import('./features/public/models/model-list.component').then(m => m.ModelListComponent),
    title: 'Modelos | WB Agency'
  },
  {
    path: 'modelos',
    redirectTo: 'models',
    pathMatch: 'full'
  },
  {
    path: 'models/female',
    redirectTo: 'models',
    pathMatch: 'full'
  },
  {
    path: 'models/male',
    redirectTo: 'models',
    pathMatch: 'full'
  },
  {
    path: 'models/stars',
    redirectTo: 'models',
    pathMatch: 'full'
  },
  {
    path: 'models/star',
    redirectTo: 'models',
    pathMatch: 'full'
  },
  {
    path: 'models/:id',
    loadComponent: () => import('./features/public/models/pages/model-detail/model-detail.component').then(m => m.ModelDetailComponent),
    title: 'Perfil do Modelo | WB Scouting'
  },
  {
    path: 'modelos/:id',
    redirectTo: 'models/:id'
  },
  {
    path: 'apply',
    loadComponent: () => import('./features/public/become-model/become-model.component').then(m => m.BecomeModelComponent),
    title: 'Quero Ser Modelo | Inscrição de Novos Talentos'
  },
  {
    path: 'become-model',
    redirectTo: 'apply',
    pathMatch: 'full'
  },
  {
    path: 'seja-modelo',
    redirectTo: 'apply',
    pathMatch: 'full'
  },
  {
    path: 'contact',
    loadComponent: () => import('./features/public/contact/contact.component').then(m => m.ContactComponent),
    title: 'Contato & Casting Comercial | WB Agency'
  },
  {
    path: 'contato',
    loadComponent: () => import('./features/public/contact/contact.component').then(m => m.ContactComponent),
    title: 'Contato & Casting Comercial | WB Agency'
  },
  {
    path: 'destaques-home',
    redirectTo: 'admin/models',
    pathMatch: 'full'
  },

  // Rota de Login Administrativo (Pública)
  {
    path: 'login',
    redirectTo: 'admin/login',
    pathMatch: 'full'
  },
  {
    path: 'admin/login',
    loadComponent: () => import('./features/admin/login/admin-login.component').then(m => m.AdminLoginComponent),
    title: 'Acesso Administrativo | WB Agency'
  },
  {
    path: 'admin/esqueci-senha',
    loadComponent: () => import('./features/admin/auth/forgot-password.component').then(m => m.ForgotPasswordComponent),
    title: 'Recuperação de Acesso | WB Agency'
  },
  {
    path: 'admin/forgot-password',
    redirectTo: 'admin/esqueci-senha',
    pathMatch: 'full'
  },
  {
    path: 'admin/redefinir-senha',
    loadComponent: () => import('./features/admin/auth/reset-password.component').then(m => m.ResetPasswordComponent),
    title: 'Redefinição de Senha | WB Agency'
  },
  {
    path: 'admin/reset-password',
    redirectTo: 'admin/redefinir-senha',
    pathMatch: 'full'
  },
  {
    path: 'admin/primeiro-acesso',
    loadComponent: () => import('./features/admin/auth/first-access/first-access.component').then(m => m.FirstAccessComponent),
    title: 'Primeiro Acesso & Definição de Senha | WB Agency'
  },

  // --- REDIRECIONAMENTOS LEGADOS (Defensivos para bookmarks) ---
  {
    path: 'admin/candidates',
    pathMatch: 'full',
    redirectTo: 'admin/candidaturas'
  },
  {
    path: 'admin/candidates/:id',
    redirectTo: 'admin/candidaturas/:id'
  },
  {
    path: 'admin/candidatos',
    pathMatch: 'full',
    redirectTo: 'admin/candidaturas'
  },
  {
    path: 'admin/candidatos/:id',
    redirectTo: 'admin/candidaturas/:id'
  },

  // Rotas Administrativas Protegidas (Backoffice / CMS)
  {
    path: 'admin',
    canActivate: [adminAuthGuard, mustChangePasswordGuard],
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/admin/dashboard/dashboard.component').then(m => m.DashboardComponent),
        title: 'Painel de Controle — Dashboard | WB Agency'
      },
      // --- ROTA OFICIAL EXCLUSIVA: SCOUTING DESK ---
      {
        path: 'candidaturas',
        loadComponent: () => import('./features/admin/applications/candidate-table.component').then(m => m.CandidateTableComponent),
        title: 'Scouting Desk — Candidaturas | WB Agency'
      },
      {
        path: 'candidaturas/:id',
        loadComponent: () => import('./features/admin/applications/candidate-detail.component').then(m => m.CandidateDetailComponent),
        title: 'Avaliação de Candidatura | WB Agency'
      },
      // --- REDIRECIONAMENTOS LEGADOS INTERNOS ---
      {
        path: 'candidatos',
        redirectTo: 'candidaturas',
        pathMatch: 'full'
      },
      {
        path: 'candidatos/:id',
        redirectTo: 'candidaturas/:id'
      },
      {
        path: 'candidates',
        redirectTo: 'candidaturas',
        pathMatch: 'full'
      },
      {
        path: 'candidates/:id',
        redirectTo: 'candidaturas/:id'
      },
      {
        path: 'applications',
        redirectTo: 'candidaturas',
        pathMatch: 'full'
      },
      {
        path: 'applications/:id',
        redirectTo: 'candidaturas/:id',
      },
      {
        path: 'submissions',
        redirectTo: 'candidaturas',
        pathMatch: 'full'
      },
      {
        path: 'submissions/:id',
        redirectTo: 'candidaturas/:id'
      },
      {
        path: 'models',
        loadComponent: () => import('./features/admin/models-mgmt/models-mgmt.component').then(m => m.ModelsMgmtComponent),
        title: 'Gestão de Modelos | WB Agency'
      },
      {
        path: 'modelos',
        redirectTo: 'models',
        pathMatch: 'full'
      },
      {
        path: 'models/new',
        loadComponent: () => import('./features/admin/models-mgmt/model-form/model-form.component').then(m => m.ModelFormComponent),
        title: 'Novo Modelo | WB Agency'
      },
      {
        path: 'modelos/novo',
        redirectTo: 'models/new',
        pathMatch: 'full'
      },
      {
        path: 'models/:id/edit',
        loadComponent: () => import('./features/admin/models-mgmt/model-form/model-form.component').then(m => m.ModelFormComponent),
        title: 'Editar Modelo | WB Agency'
      },
      {
        path: 'models/:id/editar',
        loadComponent: () => import('./features/admin/models-mgmt/model-form/model-form.component').then(m => m.ModelFormComponent),
        title: 'Editar Modelo | WB Agency'
      },
      {
        path: 'modelos/:id/editar',
        loadComponent: () => import('./features/admin/models-mgmt/model-form/model-form.component').then(m => m.ModelFormComponent),
        title: 'Editar Modelo | WB Agency'
      },
      {
        path: 'modelos/:id/edit',
        loadComponent: () => import('./features/admin/models-mgmt/model-form/model-form.component').then(m => m.ModelFormComponent),
        title: 'Editar Modelo | WB Agency'
      },
      {
        path: 'content',
        loadComponent: () => import('./features/admin/content-mgmt/content-mgmt.component').then(m => m.ContentMgmtComponent),
        title: 'Gestão de Conteúdo & Vídeo | WB Agency'
      },
      {
        path: 'conteudo',
        redirectTo: 'institucional/home',
        pathMatch: 'full'
      },
      {
        path: 'site-contents',
        redirectTo: 'institucional/home',
        pathMatch: 'full'
      },
      {
        path: 'destaques-home',
        redirectTo: 'models',
        pathMatch: 'full'
      },
      {
        path: 'pagina-principal',
        redirectTo: 'institucional/home',
        pathMatch: 'full'
      },
      {
        path: 'institucional/home',
        loadComponent: () => import('./features/admin/institutional/home-content-manager.component').then(m => m.HomeContentManagerComponent),
        title: 'Gestão da Home | WB Agency'
      },
      {
        path: 'institutional/home',
        redirectTo: 'institucional/home',
        pathMatch: 'full'
      },
      {
        path: 'home-content',
        redirectTo: 'institucional/home',
        pathMatch: 'full'
      },
      {
        path: 'canais-contato',
        redirectTo: 'institucional/contatos',
        pathMatch: 'full'
      },
      {
        path: 'institucional/contatos',
        loadComponent: () => import('./features/admin/institutional/contact-settings.component').then(m => m.ContactSettingsComponent),
        title: 'Canais de Contato | WB Agency'
      },
      {
        path: 'institutional/contact',
        redirectTo: 'institucional/contatos',
        pathMatch: 'full'
      },
      {
        path: 'contact-settings',
        redirectTo: 'institucional/contatos',
        pathMatch: 'full'
      },
      {
        path: 'institucional/sobre',
        loadComponent: () => import('./features/admin/institutional/about-page-manager.component').then(m => m.AboutPageManagerComponent),
        title: 'Gestão Sobre Nós & Manifesto | WB Agency'
      },
      {
        path: 'institucional/sobre-nos',
        redirectTo: 'institucional/sobre',
        pathMatch: 'full'
      },
      {
        path: 'institutional/about',
        redirectTo: 'institucional/sobre',
        pathMatch: 'full'
      },
      {
        path: 'institucional/quero-ser-modelo',
        loadComponent: () => import('./features/admin/institutional/apply-faq-manager.component').then(m => m.ApplyFaqManagerComponent),
        title: 'Gestão Quero Ser Modelo & FAQ | WB Agency'
      },
      {
        path: 'institucional/faq',
        redirectTo: 'institucional/quero-ser-modelo',
        pathMatch: 'full'
      },
      {
        path: 'conteudo/faq',
        redirectTo: 'institucional/quero-ser-modelo',
        pathMatch: 'full'
      },
      {
        path: 'idiomas',
        redirectTo: 'institucional/idiomas',
        pathMatch: 'full'
      },
      {
        path: 'institucional/idiomas',
        loadComponent: () => import('./features/admin/institutional/bilingual-content-editor.component').then(m => m.BilingualContentEditorComponent),
        title: 'Conteúdos Bilíngues (PT/EN) | WB Agency'
      },
      {
        path: 'institutional/translations',
        redirectTo: 'institucional/idiomas',
        pathMatch: 'full'
      },
      {
        path: 'translations',
        redirectTo: 'institucional/idiomas',
        pathMatch: 'full'
      },
      {
        path: 'bilingual-editor',
        redirectTo: 'institucional/idiomas',
        pathMatch: 'full'
      },
      {
        path: 'destaques',
        redirectTo: 'models',
        pathMatch: 'full'
      },
      {
        path: 'featured',
        redirectTo: 'models',
        pathMatch: 'full'
      },
      {
        path: 'featured-models',
        redirectTo: 'models',
        pathMatch: 'full'
      },
      {
        path: 'usuarios',
        canActivate: [roleGuard],
        data: { roles: ['WEBMASTER', 'SUPER_ADMIN'] },
        loadComponent: () => import('./features/admin/users/admin-users.component').then(m => m.AdminUsersComponent),
        title: 'Gestão de Usuários & Acessos | WB Agency'
      },
      {
        path: 'users',
        redirectTo: 'usuarios',
        pathMatch: 'full'
      },
      {
        path: 'auditoria',
        canActivate: [webmasterOnlyGuard],
        loadComponent: () => import('./features/admin/audit-logs/audit-logs.component').then(m => m.AuditLogsComponent),
        title: 'Trilha de Auditoria & Logs | WB Agency'
      },
      {
        path: 'logs',
        redirectTo: 'auditoria',
        pathMatch: 'full'
      },
      {
        path: 'perfil',
        loadComponent: () => import('./features/admin/profile/admin-profile.component').then(m => m.AdminProfileComponent),
        title: 'Meu Perfil & Segurança | WB Agency'
      },
      {
        path: 'profile',
        redirectTo: 'perfil',
        pathMatch: 'full'
      }
    ]
  },

  // Fallback para rota inicial
  {
    path: '**',
    redirectTo: ''
  }
];
