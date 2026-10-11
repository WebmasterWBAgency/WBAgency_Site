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

  it('deve alternar abas de ciclo de vida (Aplicantes, Elenco e Arquivados)', () => {
    spyOn(component, 'loadModels');
    component.setTab('APPLICANTS');
    expect(component.activeTab()).toBe('APPLICANTS');
    expect(component.page()).toBe(0);
    expect(component.loadModels).toHaveBeenCalled();

    component.setTab('ARCHIVED');
    expect(component.activeTab()).toBe('ARCHIVED');
    expect(component.page()).toBe(0);
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
});
