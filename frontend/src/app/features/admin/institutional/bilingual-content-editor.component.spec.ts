import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { BilingualContentEditorComponent, SectionTranslationsResponse } from './bilingual-content-editor.component';
import { environment } from '../../../../environments/environment';

describe('BilingualContentEditorComponent', () => {
  let component: BilingualContentEditorComponent;
  let fixture: ComponentFixture<BilingualContentEditorComponent>;
  let httpMock: HttpTestingController;

  const mockResponse: SectionTranslationsResponse = {
    sectionKey: 'ABOUT_MANIFESTO',
    title: 'Manifesto Institucional',
    translations: {
      pt: {
        headline: 'Título Editorial PT',
        quote: 'A beleza contemporânea nasce da singularidade e precisão.',
        sectionTitle: 'Seção Editorial PT',
        body: 'Texto completo em português.'
      },
      en: {
        headline: 'Editorial Title EN',
        quote: 'Contemporary beauty stems from uniqueness and precision.',
        sectionTitle: 'Editorial Section EN',
        body: 'Full text in English.'
      }
    }
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        BilingualContentEditorComponent,
        HttpClientTestingModule,
        FormsModule,
        RouterTestingModule
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BilingualContentEditorComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('deve instanciar o componente e carregar dados da seção padrão', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(component).toBeTruthy();
    expect(component.currentSectionTitle).toBe('Sobre Nós');
    expect(component.currentContent.pt.headline).toBe('Título Editorial PT');
    expect(component.currentContent.en.headline).toBe('Editorial Title EN');
    expect(component.editingBlock).toBeNull();
  });

  it('deve iniciar e cancelar o modo de edição de um bloco específico', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.startEditBlock('headline');
    expect(component.editingBlock).toBe('headline');
    expect(component.tempPt.headline).toBe('Título Editorial PT');
    expect(component.tempEn.headline).toBe('Editorial Title EN');

    component.cancelEditBlock();
    expect(component.editingBlock).toBeNull();
  });

  it('deve alternar para a seção SCOUTING e buscar os dados de FAQ', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.loadSection('SCOUTING');

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/apply-faq`);
    expect(req.request.method).toBe('GET');
    req.flush({
      items: [
        {
          question: 'Critérios PT',
          answer: 'Resposta PT',
          questionEn: 'Guidelines EN',
          answerEn: 'Answer EN'
        }
      ]
    });

    expect(component.activeSection).toBe('SCOUTING');
    expect(component.currentSectionTitle).toBe('Configurações do Form. de Quero ser Modelo');
    expect(component.faqList.length).toBe(1);
    expect(component.faqList[0].question).toBe('Critérios PT');
    expect(component.faqList[0].questionEn).toBe('Guidelines EN');
  });

  it('deve carregar automaticamente as 7 perguntas canônicas bilíngues caso os endpoints de FAQ estejam vazios', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.loadSection('SCOUTING');

    // 1. GET /admin/apply-faq retorna vazio
    const req1 = httpMock.expectOne(`${environment.apiUrl}/admin/apply-faq`);
    req1.flush([]);

    // 2. Fallback GET /public/apply-faq retorna vazio
    const req2 = httpMock.expectOne(`${environment.apiUrl}/public/apply-faq`);
    req2.flush([]);

    // 3. Fallback GET /admin/institutional/translations/SCOUTING_FAQ retorna vazio
    const req3 = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/SCOUTING_FAQ`);
    req3.flush({ items: [] });

    expect(component.activeSection).toBe('SCOUTING');
    expect(component.faqList.length).toBe(7);
    expect(component.faqList[0].question).toBe('Quais são as medidas ideais para o mercado da moda e publicidade?');
    expect(component.faqList[0].questionEn).toBe('What are the ideal measurements for fashion and advertising?');
    expect(component.faqList[6].question).toBe('Preciso morar em SP?');
    expect(component.faqList[6].questionEn).toBe('Do I need to live in São Paulo?');
    expect(component.faqList.every(f => f.question && f.answer && f.questionEn && f.answerEn)).toBeTrue();
  });

  it('deve gerenciar itens de FAQ: adicionar até 10, mover e remover com trava de segurança', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.activeSection = 'SCOUTING';
    component.faqList = [
      { question: 'Q1', answer: 'A1', questionEn: 'QE1', answerEn: 'AE1' },
      { question: 'Q2', answer: 'A2', questionEn: 'QE2', answerEn: 'AE2' }
    ];

    // Reordenar descer
    component.moveFaqDown(0);
    expect(component.faqList[0].question).toBe('Q2');
    expect(component.faqList[1].question).toBe('Q1');

    // Reordenar subir
    component.moveFaqUp(1);
    expect(component.faqList[0].question).toBe('Q1');
    expect(component.faqList[1].question).toBe('Q2');

    // Adicionar item
    component.addFaqItem();
    expect(component.faqList.length).toBe(3);

    // Remover item
    component.removeFaqItem(2);
    expect(component.faqList.length).toBe(2);

    // Trava de segurança para não deixar lista vazia
    component.removeFaqItem(1);
    expect(component.faqList.length).toBe(1);
    component.removeFaqItem(0); // Não deve remover o último
    expect(component.faqList.length).toBe(1);
  });

  it('deve salvar lista de FAQ via saveFaqList e PUT /admin/apply-faq', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    spyOn(window, 'alert');
    component.activeSection = 'SCOUTING';
    component.faqList = [
      { question: 'Pergunta Teste', answer: 'Resposta Teste', questionEn: 'Test Question', answerEn: 'Test Answer' }
    ];

    component.saveFaqList();

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/apply-faq`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.items.length).toBe(1);
    expect(req.request.body.items[0].question).toBe('Pergunta Teste');
    req.flush({ items: component.faqList });

    const faqReq = httpMock.expectOne(`${environment.apiUrl}/admin/apply-faq`);
    expect(faqReq.request.method).toBe('GET');
    faqReq.flush({ items: component.faqList });

    expect(component.isSaving).toBeFalse();
    expect(component.successBlock).toBe('faq');
    tick(3000);
    expect(component.successBlock).toBeNull();
  }));

  it('deve alternar para a seção PRIVACY e buscar dados de PRIVACY', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.loadSection('PRIVACY');

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/PRIVACY`);
    expect(req.request.method).toBe('GET');
    req.flush({
      sectionKey: 'PRIVACY',
      title: 'Privacidade & LGPD',
      translations: {
        pt: { content: 'Texto LGPD em PT', body: 'Texto LGPD em PT' },
        en: { content: 'GDPR Text in EN', body: 'GDPR Text in EN' }
      }
    });

    expect(component.activeSection).toBe('PRIVACY');
    expect(component.currentSectionTitle).toBe('Privacidade & LGPD');
    expect(component.tempPt.content).toBe('Texto LGPD em PT');
    expect(component.tempEn.content).toBe('GDPR Text in EN');
  });

  it('deve alternar para a seção TERMS e buscar dados de TERMS', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.loadSection('TERMS');

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/TERMS`);
    expect(req.request.method).toBe('GET');
    req.flush({
      sectionKey: 'TERMS',
      title: 'Termos de Uso',
      translations: {
        pt: { content: 'Texto Termos em PT', body: 'Texto Termos em PT' },
        en: { content: 'Terms Text in EN', body: 'Terms Text in EN' }
      }
    });

    expect(component.activeSection).toBe('TERMS');
    expect(component.currentSectionTitle).toBe('Termos de Uso');
    expect(component.tempPt.content).toBe('Texto Termos em PT');
    expect(component.tempEn.content).toBe('Terms Text in EN');
  });

  it('deve salvar seção simplificada via saveCurrentSimpleSection', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.activeSection = 'TERMS';
    component.tempPt.content = 'Novos Termos em Português';
    component.tempEn.content = 'New Terms in English';

    component.saveCurrentSimpleSection();

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/TERMS`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.pt.content).toBe('Novos Termos em Português');
    expect(req.request.body.en.content).toBe('New Terms in English');
    req.flush({ success: true });

    expect(component.isSaving).toBeFalse();
    expect(component.successBlock).toBe('all');
    tick(3000);
    expect(component.successBlock).toBeNull();
  }));

  it('deve efetuar fallback para TERMS_OF_USE ao receber 404 ao salvar TERMS via saveCurrentSimpleSection', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.activeSection = 'TERMS';
    component.tempPt.content = 'Termos Atualizados';
    component.tempEn.content = 'Updated Terms';

    component.saveCurrentSimpleSection();

    // Primeira tentativa em TERMS retorna 404
    const req1 = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/TERMS`);
    expect(req1.request.method).toBe('PUT');
    req1.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });

    // Segunda tentativa automática em TERMS_OF_USE
    const req2 = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/TERMS_OF_USE`);
    expect(req2.request.method).toBe('PUT');
    expect(req2.request.body.pt.content).toBe('Termos Atualizados');
    expect(req2.request.body.en.content).toBe('Updated Terms');
    req2.flush({ success: true });

    expect(component.isSaving).toBeFalse();
    expect(component.successBlock).toBe('all');
    tick(3000);
    expect(component.successBlock).toBeNull();
  }));

  it('deve efetuar fallback para PRIVACY_POLICY ao receber 404 ao salvar PRIVACY via saveCurrentSimpleSection', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.activeSection = 'PRIVACY';
    component.tempPt.content = 'Privacidade Atualizada';
    component.tempEn.content = 'Updated Privacy';

    component.saveCurrentSimpleSection();

    // Primeira tentativa em PRIVACY retorna 404
    const req1 = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/PRIVACY`);
    expect(req1.request.method).toBe('PUT');
    req1.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });

    // Segunda tentativa automática em PRIVACY_POLICY
    const req2 = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/PRIVACY_POLICY`);
    expect(req2.request.method).toBe('PUT');
    expect(req2.request.body.pt.content).toBe('Privacidade Atualizada');
    expect(req2.request.body.en.content).toBe('Updated Privacy');
    req2.flush({ success: true });

    expect(component.isSaving).toBeFalse();
    expect(component.successBlock).toBe('all');
    tick(3000);
    expect(component.successBlock).toBeNull();
  }));

  it('deve salvar bloco individual via PUT com sucesso e exibir feedback', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.startEditBlock('headline');
    component.tempPt.headline = 'Novo Título PT';
    component.tempEn.headline = 'New Title EN';

    component.saveBlock('headline');

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.pt.headline).toBe('Novo Título PT');
    expect(req.request.body.en.headline).toBe('New Title EN');
    req.flush({ success: true });

    expect(component.currentContent.pt.headline).toBe('Novo Título PT');
    expect(component.currentContent.en.headline).toBe('New Title EN');
    expect(component.editingBlock).toBeNull();
    expect(component.successBlock).toBe('headline');

    tick(3000);
    expect(component.successBlock).toBeNull();
  }));

  it('deve manter campos vazios se a API retornar erro', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`);
    req.flush('Error', { status: 500, statusText: 'Server Error' });

    expect(component.currentSectionTitle).toBe('Sobre Nós');
    expect(component.currentContent.pt.headline).toBe('');
  });

  it('deve salvar campos vazios com sucesso via saveContent', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.currentContent.pt.headline = '';
    component.currentContent.pt.quote = '';
    component.currentContent.pt.sectionTitle = '';
    component.currentContent.pt.body = '';

    component.saveContent();

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.pt.headline).toBe('');
    expect(req.request.body.pt.quote).toBe('');
    req.flush({ success: true });

    expect(component.isSaving).toBeFalse();
    expect(component.successBlock).toBe('all');
    tick(3000);
    expect(component.successBlock).toBeNull();
  }));

  it('deve salvar todo o conteúdo via saveContent e enviar payload completo', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.currentContent.pt.headline = 'Título Global Atualizado';
    component.currentContent.en.headline = 'Updated Global Title';

    component.saveContent();

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.pt.headline).toBe('Título Global Atualizado');
    expect(req.request.body.en.headline).toBe('Updated Global Title');
    req.flush({ success: true });

    expect(component.isSaving).toBeFalse();
    expect(component.successBlock).toBe('all');
    tick(3000);
    expect(component.successBlock).toBeNull();
  }));

  it('deve alertar se tentar traduzir texto em português vazio ou somente espaços', () => {
    spyOn(window, 'alert');
    spyOn(window, 'open');

    component.openGoogleTranslator('');
    expect(window.alert).toHaveBeenCalledWith('O campo em português está vazio para tradução.');
    expect(window.open).not.toHaveBeenCalled();

    component.openGoogleTranslator('   ');
    expect(window.alert).toHaveBeenCalledWith('O campo em português está vazio para tradução.');
    expect(window.open).not.toHaveBeenCalled();
  });

  it('deve abrir o Google Tradutor com URL codificada e copiar para clipboard ao traduzir texto válido', () => {
    spyOn(window, 'open');
    if (!navigator.clipboard) {
      (navigator as any).clipboard = {
        writeText: () => Promise.resolve(),
        readText: () => Promise.resolve('')
      };
    }
    const writeSpy = spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());

    const textoPt = 'Termos e Condições de Uso';
    component.openGoogleTranslator(textoPt);

    expect(writeSpy).toHaveBeenCalledWith(textoPt);
    const expectedUrl = `https://translate.google.com/?sl=pt&tl=en&text=${encodeURIComponent(textoPt)}&op=translate`;
    expect(window.open).toHaveBeenCalledWith(expectedUrl, '_blank', 'noopener,noreferrer');
  });

  it('deve colar da área de transferência chamando o targetFieldSetter', async () => {
    if (!navigator.clipboard) {
      (navigator as any).clipboard = {
        writeText: () => Promise.resolve(),
        readText: () => Promise.resolve('')
      };
    }
    spyOn(navigator.clipboard, 'readText').and.returnValue(Promise.resolve('Translated English Text'));

    let capturedText = '';
    await component.pasteFromClipboard(text => {
      capturedText = text;
    });

    expect(capturedText).toBe('Translated English Text');
  });

  it('deve colar para tempEn e para item do FAQ via pasteToTempEn e pasteToFaq', async () => {
    if (!navigator.clipboard) {
      (navigator as any).clipboard = {
        writeText: () => Promise.resolve(),
        readText: () => Promise.resolve('')
      };
    }
    spyOn(navigator.clipboard, 'readText').and.returnValue(Promise.resolve('Terms of Use in English'));

    await component.pasteToTempEn('content');
    expect(component.tempEn.content).toBe('Terms of Use in English');

    const faqItem = { question: 'P', answer: 'R', questionEn: '', answerEn: '' };
    await component.pasteToFaq(faqItem, 'questionEn');
    expect(faqItem.questionEn).toBe('Terms of Use in English');
  });

  it('deve unificar o salvamento de toda a página via saveAllChanges enviando todos os campos editados', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    component.aboutForm.patchValue({
      headline: 'Novo Headline Unificado',
      quote: 'Nova Citação Unificada',
      sectionTitle: 'Nova Seção Unificada',
      body: 'Novo Corpo Unificado',
      headlineEn: 'New Unified Headline',
      quoteEn: 'New Unified Quote',
      sectionTitleEn: 'New Unified Section',
      bodyEn: 'New Unified Body'
    });
    component.aboutForm.markAsDirty();

    component.saveAllChanges();

    expect(component.isSaving).toBeTrue();

    const req = httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.translations.pt.headline).toBe('Novo Headline Unificado');
    expect(req.request.body.translations.pt.quote).toBe('Nova Citação Unificada');
    expect(req.request.body.translations.pt.sectionTitle).toBe('Nova Seção Unificada');
    expect(req.request.body.translations.pt.body).toBe('Novo Corpo Unificado');
    expect(req.request.body.translations.en.headline).toBe('New Unified Headline');
    expect(req.request.body.translations.en.quote).toBe('New Unified Quote');
    expect(req.request.body.translations.en.sectionTitle).toBe('New Unified Section');
    expect(req.request.body.translations.en.body).toBe('New Unified Body');

    req.flush({ success: true });

    expect(component.isSaving).toBeFalse();
    expect(component.saveSuccessMessage).toBe('Todas as alterações da página foram salvas com sucesso!');

    tick(3000);
    expect(component.saveSuccessMessage).toBe('');
  }));

  it('deve disparar saveAllChanges ao pressionar o atalho de teclado Ctrl + S ou Cmd + S prevenindo o comportamento padrão', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiUrl}/admin/institutional/translations/ABOUT_MANIFESTO`).flush(mockResponse);

    const saveSpy = spyOn(component, 'saveAllChanges');

    const event = new KeyboardEvent('keydown', {
      key: 's',
      ctrlKey: true,
      cancelable: true
    });
    spyOn(event, 'preventDefault');

    component.handleKeyboardEvent(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(saveSpy).toHaveBeenCalled();
  });
});
