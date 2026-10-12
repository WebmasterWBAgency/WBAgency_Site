import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ModelsMgmtComponent } from './models-mgmt.component';
import { AdminModelService } from '../../../core/services/admin-model.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ModelAdminItem } from '../../../shared/models/admin-model.interface';

describe('ModelsMgmtComponent', () => {
  let component: ModelsMgmtComponent;
  let fixture: ComponentFixture<ModelsMgmtComponent>;
  let adminModelService: AdminModelService;

  const mockModel: ModelAdminItem = {
    id: 'test-model-1',
    stageName: 'Isabella Fontana',
    gender: 'FEMALE',
    isStar: true,
    isFeaturedHome: true,
    isActive: true,
    heightCm: 179
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModelsMgmtComponent],
      providers: [
        AdminModelService,
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: () => null
              }
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ModelsMgmtComponent);
    component = fixture.componentInstance;
    adminModelService = TestBed.inject(AdminModelService);
  });

  it('deve instanciar o componente e carregar modelos', () => {
    spyOn(adminModelService, 'getModels').and.returnValue(
      of({
        content: [mockModel],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0
      })
    );

    fixture.detectChanges();

    expect(component).toBeTruthy();
    expect(component.models().length).toBe(1);
    expect(component.models()[0].stageName).toBe('Isabella Fontana');
  });

  it('deve abrir o formulário para criação ao chamar openCreateForm()', () => {
    component.openCreateForm();
    expect(component.isFormOpen()).toBeTrue();
    expect(component.editingModelId()).toBeNull();
  });

  it('deve abrir o formulário para edição ao chamar openEditForm()', () => {
    component.openEditForm(mockModel);
    expect(component.isFormOpen()).toBeTrue();
    expect(component.editingModelId()).toBe('test-model-1');
  });

  it('deve fechar o formulário ao chamar closeForm()', () => {
    component.openCreateForm();
    component.closeForm();
    expect(component.isFormOpen()).toBeFalse();
    expect(component.editingModelId()).toBeNull();
  });

  it('deve alternar status do modelo', () => {
    spyOn(adminModelService, 'updateStatus').and.returnValue(of({ ...mockModel, isActive: false }));
    component.models.set([mockModel]);

    component.toggleStatus(mockModel);

    expect(adminModelService.updateStatus).toHaveBeenCalledWith('test-model-1', false);
    expect(component.models()[0].isActive).toBeFalse();
  });

  it('deve alternar Stars do modelo', () => {
    spyOn(adminModelService, 'updateStar').and.returnValue(of({ ...mockModel, isStar: false }));
    component.models.set([mockModel]);

    component.toggleStar(mockModel);

    expect(adminModelService.updateStar).toHaveBeenCalledWith('test-model-1', false);
    expect(component.models()[0].isStar).toBeFalse();
  });

  it('deve aplicar filtro de gênero', () => {
    spyOn(component, 'loadModels');
    component.setGenderFilter('MALE');
    expect(component.filterGender()).toBe('MALE');
    expect(component.loadModels).toHaveBeenCalled();
  });

  it('deve aplicar filtro de Stars', () => {
    spyOn(component, 'loadModels');
    component.setStarFilter(true);
    expect(component.filterStar()).toBeTrue();
    expect(component.loadModels).toHaveBeenCalled();
  });

  it('deve alternar abas de ciclo de vida (ALL, MAIN, STARS, INACTIVE)', () => {
    spyOn(component, 'loadModels');
    component.setTab('STARS');
    expect(component.activeTab()).toBe('STARS');
    expect(component.page()).toBe(0);
    expect(component.loadModels).toHaveBeenCalled();

    component.setTab('INACTIVE');
    expect(component.activeTab()).toBe('INACTIVE');
    expect(component.page()).toBe(0);

    component.setTab('ALL');
    expect(component.activeTab()).toBe('ALL');
  });

  it('deve aprovar aplicante e transicionar para CAST_PUBLIC', () => {
    spyOn(adminModelService, 'updateTalentStatus').and.returnValue(of({ ...mockModel, status: 'CAST_PUBLIC' }));
    spyOn(component, 'loadModels');

    component.approveApplicant(mockModel);

    expect(adminModelService.updateTalentStatus).toHaveBeenCalledWith('test-model-1', 'CAST_PUBLIC');
    expect(component.loadModels).toHaveBeenCalled();
  });

  it('deve recusar aplicante e transicionar para APPLICANT_DECLINED', () => {
    spyOn(adminModelService, 'updateTalentStatus').and.returnValue(of({ ...mockModel, status: 'APPLICANT_DECLINED' }));
    spyOn(component, 'loadModels');

    component.declineApplicant(mockModel);

    expect(adminModelService.updateTalentStatus).toHaveBeenCalledWith('test-model-1', 'APPLICANT_DECLINED');
    expect(component.loadModels).toHaveBeenCalled();
  });

  it('deve arquivar modelo e transicionar para ARCHIVED', () => {
    spyOn(adminModelService, 'updateTalentStatus').and.returnValue(of({ ...mockModel, status: 'ARCHIVED' }));
    spyOn(component, 'loadModels');

    component.archiveModel(mockModel);

    expect(adminModelService.updateTalentStatus).toHaveBeenCalledWith('test-model-1', 'ARCHIVED');
    expect(component.loadModels).toHaveBeenCalled();
  });

  it('deve reativar modelo arquivado e transicionar para CAST_PUBLIC', () => {
    spyOn(adminModelService, 'updateTalentStatus').and.returnValue(of({ ...mockModel, status: 'CAST_PUBLIC' }));
    spyOn(component, 'loadModels');

    component.reactivateModel(mockModel);

    expect(adminModelService.updateTalentStatus).toHaveBeenCalledWith('test-model-1', 'CAST_PUBLIC');
    expect(component.loadModels).toHaveBeenCalled();
  });

  it('deve aplicar filtro de Status (Todos, Ativos, Inativos)', () => {
    spyOn(component, 'loadModels');

    // Filtro Ativos
    component.setStatusFilter(true);
    expect(component.filterStatus()).toBeTrue();
    expect(component.page()).toBe(0);
    expect(component.loadModels).toHaveBeenCalled();

    // Filtro Inativos
    component.setStatusFilter(false);
    expect(component.filterStatus()).toBeFalse();
    expect(component.page()).toBe(0);

    // Filtro Todos (sem filtro de isActive)
    component.setStatusFilter(null);
    expect(component.filterStatus()).toBeNull();
    expect(component.page()).toBe(0);
  });

  it('deve exibir badge "Inativo" quando isActive = false', () => {
    const inactiveModel: ModelAdminItem = {
      ...mockModel,
      id: 'inactive-model-1',
      stageName: 'Modelo Inativo',
      isActive: false
    };

    spyOn(adminModelService, 'getModels').and.returnValue(
      of({
        content: [inactiveModel],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0
      })
    );

    component.setViewMode('TABLE');
    component.loadModels();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const badge = compiled.querySelector('tbody tr td span.inline-flex');
    expect(badge).toBeTruthy();
    expect(badge?.textContent?.trim()).toContain('Inativo');
    expect(badge?.classList.contains('bg-neutral-100')).toBeTrue();
    expect(badge?.classList.contains('text-neutral-600')).toBeTrue();
  });

  it('deve exibir badge "Ativo" quando isActive = true', () => {
    spyOn(adminModelService, 'getModels').and.returnValue(
      of({
        content: [mockModel],
        totalElements: 1,
        totalPages: 1,
        size: 20,
        number: 0
      })
    );

    component.setViewMode('TABLE');
    component.loadModels();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const badge = compiled.querySelector('tbody tr td span.inline-flex');
    expect(badge).toBeTruthy();
    expect(badge?.textContent?.trim()).toContain('Ativo');
    expect(badge?.classList.contains('bg-emerald-50')).toBeTrue();
    expect(badge?.classList.contains('text-emerald-700')).toBeTrue();
  });

  it('deve alternar para aba INACTIVE e enviar isActive: false ao carregar modelos', () => {
    spyOn(adminModelService, 'getModels').and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 })
    );

    component.setTab('INACTIVE');

    expect(component.activeTab()).toBe('INACTIVE');
    expect(adminModelService.getModels).toHaveBeenCalledWith(
      jasmine.objectContaining({
        isActive: false
      })
    );
  });

  it('deve abrir modal de arquivamento (soft delete) e chamar deleteModel com permanent: false', () => {
    spyOn(adminModelService, 'deleteModel').and.returnValue(of(void 0));
    component.models.set([mockModel]);

    component.confirmArchive(mockModel);

    expect(component.isConfirmDeleteOpen()).toBeTrue();
    expect(component.deleteMode()).toBe('ARCHIVE');
    expect(component.pendingDeleteModel()?.id).toBe('test-model-1');

    component.executeDelete();

    expect(adminModelService.deleteModel).toHaveBeenCalledWith('test-model-1', false);
    expect(component.isConfirmDeleteOpen()).toBeFalse();
  });

  it('deve abrir modal de exclusão definitiva (hard delete) e chamar deleteModel com permanent: true', () => {
    spyOn(adminModelService, 'deleteModel').and.returnValue(of(void 0));
    component.models.set([mockModel]);

    component.confirmPermanentDelete(mockModel);

    expect(component.isConfirmDeleteOpen()).toBeTrue();
    expect(component.deleteMode()).toBe('PERMANENT');
    expect(component.pendingDeleteModel()?.id).toBe('test-model-1');

    component.executeDelete();

    expect(adminModelService.deleteModel).toHaveBeenCalledWith('test-model-1', true);
    expect(component.isConfirmDeleteOpen()).toBeFalse();
  });

  it('deve restaurar modelo do arquivo morto chamando restoreModel', () => {
    spyOn(adminModelService, 'restoreModel').and.returnValue(of({ ...mockModel, archivedAt: null, isActive: false }));
    component.models.set([mockModel]);

    component.restoreModel(mockModel);

    expect(adminModelService.restoreModel).toHaveBeenCalledWith('test-model-1');
  });
});

