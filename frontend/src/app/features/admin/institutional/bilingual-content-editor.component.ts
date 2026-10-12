import { Component, OnInit, inject, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

export interface TranslationBlock {
  headline?: string;
  quote?: string;
  sectionTitle?: string;
  body?: string;
  content?: string;
}

export interface BilingualContentState {
  pt: TranslationBlock;
  en: TranslationBlock;
}

export interface SectionTranslationsResponse {
  sectionKey: string;
  title: string;
  translations: BilingualContentState;
}

@Component({
  selector: 'app-bilingual-content-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule],
  templateUrl: './bilingual-content-editor.component.html',
  styleUrls: ['./bilingual-content-editor.component.scss']
})
export class BilingualContentEditorComponent implements OnInit {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);

  private readonly SECTION_MAP: Record<string, string> = {
    'MANIFESTO': 'ABOUT_MANIFESTO',
    'ABOUT_MANIFESTO': 'ABOUT_MANIFESTO',
    'SCOUTING': 'SCOUTING_GUIDELINES',
    'SCOUTING_GUIDELINES': 'SCOUTING_GUIDELINES',
    'APPLY_FORM': 'SCOUTING_GUIDELINES',
    'APPLY': 'APPLY_HOW_IT_WORKS',
    'APPLY_HOW_IT_WORKS': 'APPLY_HOW_IT_WORKS',
    'TERMS': 'TERMS',
    'TERMS_OF_USE': 'TERMS',
    'PRIVACY': 'PRIVACY',
    'PRIVACY_POLICY': 'PRIVACY'
  };

  activeSection = 'ABOUT_MANIFESTO';
  currentSectionTitle = 'Sobre Nós';
  isSaving = false;
  saveSuccessMessage = '';
  successBlock: string | null = null;
  errorMessage: string | null = null;

  // Formulário unificado retendo todos os valores editados na página
  aboutForm: FormGroup = this.fb.group({
    headline: [''],
    quote: [''],
    sectionTitle: [''],
    body: [''],
    headlineEn: [''],
    quoteEn: [''],
    sectionTitleEn: [''],
    bodyEn: ['']
  });

  // Listener para Ctrl + S / Cmd + S
  @HostListener('window:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
      event.preventDefault();
      this.saveAllChanges();
    }
  }

  get selectedSectionKey(): string {
    return this.activeSection;
  }
  set selectedSectionKey(val: string) {
    this.activeSection = val;
  }

  loadSectionData(key: string): void {
    this.loadSection(key);
  }

  onSectionChange(key: string): void {
    this.loadSection(key);
  }

  currentContent: BilingualContentState = {
    pt: {
      headline: '',
      quote: '',
      sectionTitle: '',
      body: '',
      content: ''
    },
    en: {
      headline: '',
      quote: '',
      sectionTitle: '',
      body: '',
      content: ''
    }
  };

  editingBlock: 'headline' | 'quote' | 'sectionTitle' | 'body' | null = null;
  tempPt: TranslationBlock = { headline: '', quote: '', sectionTitle: '', body: '', content: '' };
  tempEn: TranslationBlock = { headline: '', quote: '', sectionTitle: '', body: '', content: '' };
  faqList: Array<{
    id?: string | null;
    displayOrder?: number;
    question: string;
    answer: string;
    questionEn: string;
    answerEn: string;
    isActive?: boolean;
  }> = [];

  ngOnInit(): void {
    this.aboutForm.valueChanges.subscribe(val => {
      if (val) {
        this.currentContent.pt.headline = val.headline || '';
        this.currentContent.pt.quote = val.quote || '';
        this.currentContent.pt.sectionTitle = val.sectionTitle || '';
        this.currentContent.pt.body = val.body || '';

        this.currentContent.en.headline = val.headlineEn || '';
        this.currentContent.en.quote = val.quoteEn || '';
        this.currentContent.en.sectionTitle = val.sectionTitleEn || '';
        this.currentContent.en.body = val.bodyEn || '';

        this.tempPt.headline = val.headline || '';
        this.tempPt.quote = val.quote || '';
        this.tempPt.sectionTitle = val.sectionTitle || '';
        this.tempPt.body = val.body || '';

        this.tempEn.headline = val.headlineEn || '';
        this.tempEn.quote = val.quoteEn || '';
        this.tempEn.sectionTitle = val.sectionTitleEn || '';
        this.tempEn.body = val.bodyEn || '';
      }
    });

    this.loadSection(this.activeSection);
  }

  getCanonicalKey(key: string): string {
    return this.SECTION_MAP[key] || key;
  }

  loadSection(sectionKey: string): void {
    this.activeSection = sectionKey;
    this.cancelEditBlock();
    this.currentSectionTitle = this.getSectionTitleByKey(sectionKey);

    if (sectionKey === 'SCOUTING') {
      this.loadFaqList();
      return;
    }

    if (sectionKey === 'TERMS' || sectionKey === 'PRIVACY') {
      const canonicalKey = this.getCanonicalKey(sectionKey);
      this.http.get<any>(`${environment.apiUrl}/admin/institutional/translations/${canonicalKey}`).subscribe({
        next: (res) => {
          // Extrai o texto de PT
          const ptText = 
            res?.translations?.pt?.content || 
            res?.translations?.pt?.body || 
            res?.payloadPt?.content || 
            res?.payloadPt?.body || 
            res?.content || 
            res?.body || 
            '';

          // Extrai o texto de EN
          const enText = 
            res?.translations?.en?.content || 
            res?.translations?.en?.body || 
            res?.payloadEn?.content || 
            res?.payloadEn?.body || 
            res?.contentEn || 
            '';

          this.tempPt = { ...this.tempPt, content: ptText };
          this.tempEn = { ...this.tempEn, content: enText };
          this.currentContent.pt.content = ptText;
          this.currentContent.pt.body = ptText;
          this.currentContent.en.content = enText;
          this.currentContent.en.body = enText;

          // Se um dos campos vier vazio, busca fallback pelo endpoint público
          if (!ptText || !ptText.trim()) {
            this.http.get<any>(`${environment.apiUrl}/public/content/${sectionKey}?lang=pt`).subscribe({
              next: (r) => {
                const val = r?.content || r?.body || (typeof r === 'string' ? r : '');
                if (val) {
                  this.tempPt.content = val;
                  this.currentContent.pt.content = val;
                  this.currentContent.pt.body = val;
                }
              }
            });
          }
          if (!enText || !enText.trim()) {
            this.http.get<any>(`${environment.apiUrl}/public/content/${sectionKey}?lang=en`).subscribe({
              next: (r) => {
                const val = r?.content || r?.body || (typeof r === 'string' ? r : '');
                if (val) {
                  this.tempEn.content = val;
                  this.currentContent.en.content = val;
                  this.currentContent.en.body = val;
                }
              }
            });
          }
        },
        error: (err) => {
          console.warn(`[I18N] Erro ao carregar traduções admin para ${sectionKey}. Aplicando fallback público:`, err);
          this.http.get<any>(`${environment.apiUrl}/public/content/${sectionKey}?lang=pt`).subscribe({
            next: (r) => {
              const val = r?.content || r?.body || (typeof r === 'string' ? r : '');
              this.tempPt = { ...this.tempPt, content: val };
              this.currentContent.pt.content = val;
              this.currentContent.pt.body = val;
            }
          });
          this.http.get<any>(`${environment.apiUrl}/public/content/${sectionKey}?lang=en`).subscribe({
            next: (r) => {
              const val = r?.content || r?.body || (typeof r === 'string' ? r : '');
              this.tempEn = { ...this.tempEn, content: val };
              this.currentContent.en.content = val;
              this.currentContent.en.body = val;
            }
          });
        }
      });
      return;
    }

    const canonicalKey = this.getCanonicalKey(sectionKey);

    this.http.get<SectionTranslationsResponse>(`${environment.apiUrl}/admin/institutional/translations/${canonicalKey}`).subscribe({
      next: (res) => {
        if (res && res.translations) {
          const isAbout = sectionKey.includes('ABOUT') || sectionKey.includes('MANIFESTO');
          this.currentSectionTitle = this.getSectionTitleByKey(sectionKey) || res.title;
          const ptContent = res.translations.pt?.content || res.translations.pt?.body || '';
          const enContent = res.translations.en?.content || res.translations.en?.body || '';

          this.currentContent = {
            pt: {
              headline: res.translations.pt?.headline || '',
              quote: res.translations.pt?.quote || '',
              sectionTitle: res.translations.pt?.sectionTitle || '',
              body: res.translations.pt?.body || '',
              content: ptContent
            },
            en: {
              headline: res.translations.en?.headline || '',
              quote: res.translations.en?.quote || '',
              sectionTitle: res.translations.en?.sectionTitle || '',
              body: res.translations.en?.body || '',
              content: enContent
            }
          };

          this.tempPt = { ...this.currentContent.pt };
          this.tempEn = { ...this.currentContent.en };

          this.aboutForm.patchValue({
            headline: this.currentContent.pt.headline,
            quote: this.currentContent.pt.quote,
            sectionTitle: this.currentContent.pt.sectionTitle,
            body: this.currentContent.pt.body,
            headlineEn: this.currentContent.en.headline,
            quoteEn: this.currentContent.en.quote,
            sectionTitleEn: this.currentContent.en.sectionTitle,
            bodyEn: this.currentContent.en.body
          }, { emitEvent: false });

          if (canonicalKey === 'TERMS' || canonicalKey === 'PRIVACY') {
            this.tempPt.content = ptContent;
            this.tempEn.content = enContent;
          }
        } else {
          this.errorMessage = `Nenhuma tradução encontrada para a seção ${sectionKey} no banco de dados.`;
        }
      },
      error: (err) => {
        console.warn(`[I18N] Carregando estrutura padrão para ${sectionKey}:`, err);
        if (canonicalKey === 'TERMS' || canonicalKey === 'PRIVACY') {
          this.http.get<any>(`${environment.apiUrl}/public/content/${canonicalKey}?lang=pt`).subscribe({
            next: (r) => {
              const val = r?.content || r?.payload?.content || '';
              this.tempPt.content = val;
              this.currentContent.pt.content = val;
            }
          });
          this.http.get<any>(`${environment.apiUrl}/public/content/${canonicalKey}?lang=en`).subscribe({
            next: (r) => {
              const val = r?.content || r?.payload?.content || '';
              this.tempEn.content = val;
              this.currentContent.en.content = val;
            }
          });
        } else {
          this.resetOrSetDefaultSection(sectionKey);
        }
      }
    });
  }

  startEditBlock(block: 'headline' | 'quote' | 'sectionTitle' | 'body'): void {
    // Preserva o que já foi digitado no bloco anterior
    if (this.editingBlock) {
      this.currentContent.pt[this.editingBlock] = this.tempPt[this.editingBlock];
      this.currentContent.en[this.editingBlock] = this.tempEn[this.editingBlock];
    }
    this.editingBlock = block;
    this.tempPt[block] = this.currentContent.pt[block] || '';
    this.tempEn[block] = this.currentContent.en[block] || '';
    this.errorMessage = null;
    setTimeout(() => {
      const el = document.getElementById('field-pt-' + block);
      if (el) el.focus();
    }, 50);
  }

  cancelEditBlock(): void {
    this.editingBlock = null;
    const ptContent = this.tempPt.content;
    const enContent = this.tempEn.content;
    this.tempPt = { headline: '', quote: '', sectionTitle: '', body: '', content: ptContent };
    this.tempEn = { headline: '', quote: '', sectionTitle: '', body: '', content: enContent };
    this.errorMessage = null;
  }

  isBlockEmpty(block: 'headline' | 'quote' | 'sectionTitle' | 'body'): string | boolean {
    const pt = this.currentContent.pt[block];
    const en = this.currentContent.en[block];
    return (!pt || pt.trim() === '') && (!en || en.trim() === '');
  }

  getBlockStatusClass(block: 'headline' | 'quote' | 'sectionTitle' | 'body'): string {
    if (this.editingBlock === block) {
      return 'status-editing';
    }
    if (this.isBlockEmpty(block)) {
      return 'status-empty';
    }
    return 'status-ready';
  }

  getBlockStatusLabel(block: 'headline' | 'quote' | 'sectionTitle' | 'body'): string {
    if (this.editingBlock === block) {
      return 'Editando';
    }
    if (this.isBlockEmpty(block)) {
      return 'Vazio';
    }
    return 'Salvo';
  }

  saveBlock(block: 'headline' | 'quote' | 'sectionTitle' | 'body'): void {
    const ptVal = this.tempPt[block];
    const enVal = this.tempEn[block];
    const enKey = block + 'En';

    if (ptVal !== undefined) {
      this.currentContent.pt[block] = ptVal;
    }
    if (enVal !== undefined) {
      this.currentContent.en[block] = enVal;
    }

    this.aboutForm.patchValue({
      [block]: ptVal,
      [enKey]: enVal
    }, { emitEvent: false });

    this.aboutForm.get(block)?.markAsDirty();
    this.aboutForm.get(enKey)?.markAsDirty();

    this.editingBlock = null;
    this.saveAllChanges(block);
  }

  saveCurrentSimpleSection(): void {
    this.saveSimpleSectionAll();
  }

  saveCurrentSection(): void {
    this.saveAllChanges();
  }

  saveSimpleSection(): void {
    this.saveSimpleSectionAll();
  }

  saveContent(): void {
    this.saveAllChanges('all');
  }

  saveSimpleSectionAll(): void {
    this.isSaving = true;
    this.errorMessage = null;

    const canonicalKey = this.getCanonicalKey(this.activeSection);
    const contentPt = this.tempPt.content || this.currentContent.pt.content || '';
    const contentEn = this.tempEn.content || this.currentContent.en.content || '';

    this.currentContent.pt.content = contentPt;
    this.currentContent.pt.body = contentPt;
    this.currentContent.en.content = contentEn;
    this.currentContent.en.body = contentEn;
    this.tempPt.content = contentPt;
    this.tempEn.content = contentEn;

    const payload = {
      translations: {
        pt: { content: contentPt, body: contentPt },
        en: { content: contentEn, body: contentEn }
      },
      pt: {
        content: contentPt,
        body: contentPt
      },
      en: {
        content: contentEn,
        body: contentEn
      }
    };

    const fallbackKeys: Record<string, string> = {
      'TERMS': 'TERMS_OF_USE',
      'PRIVACY': 'PRIVACY_POLICY'
    };
    const fallbackKey = fallbackKeys[canonicalKey] || fallbackKeys[this.activeSection];

    const handleSuccess = () => {
      this.isSaving = false;
      this.saveSuccessMessage = 'Todas as alterações da página foram salvas com sucesso!';
      this.showSuccessFeedback('all');
      try { alert(`Conteúdo de ${this.currentSectionTitle} salvo com sucesso!`); } catch {}
      setTimeout(() => this.saveSuccessMessage = '', 2500);
    };

    const handleError = (err: any) => {
      this.isSaving = false;
      const msg = err?.error?.message || err?.message || 'Falha ao sincronizar com o banco de dados.';
      this.errorMessage = `Erro ao salvar conteúdo: ${msg}`;
      console.error('Falha ao persistir traduções:', err);
      try { alert(`Erro ao salvar conteúdo: ${msg}`); } catch {}
    };

    this.http.put(`${environment.apiUrl}/admin/institutional/translations/${canonicalKey}`, payload).subscribe({
      next: () => handleSuccess(),
      error: (err) => {
        if (err?.status === 404 && fallbackKey) {
          console.warn(`[I18N] Erro 404 ao salvar em /admin/institutional/translations/${canonicalKey}. Tentando fallback defensivo para /admin/institutional/translations/${fallbackKey}...`);
          this.http.put(`${environment.apiUrl}/admin/institutional/translations/${fallbackKey}`, payload).subscribe({
            next: () => handleSuccess(),
            error: (fallbackErr) => handleError(fallbackErr)
          });
          return;
        }
        handleError(err);
      }
    });
  }

  saveAllChanges(feedbackBlock: string = 'all'): void {
    this.isSaving = true;
    this.saveSuccessMessage = '';
    this.errorMessage = null;

    if (this.activeSection === 'SCOUTING') {
      this.saveFaqList();
      return;
    }

    if (this.activeSection === 'TERMS' || this.activeSection === 'PRIVACY') {
      this.saveSimpleSectionAll();
      return;
    }

    const val = this.aboutForm.value;
    const isDirty = this.aboutForm.dirty;

    const ptHeadline = isDirty ? (val.headline ?? '') : (this.currentContent.pt.headline !== undefined ? this.currentContent.pt.headline : (val.headline ?? ''));
    const ptQuote = isDirty ? (val.quote ?? '') : (this.currentContent.pt.quote !== undefined ? this.currentContent.pt.quote : (val.quote ?? ''));
    const ptSectionTitle = isDirty ? (val.sectionTitle ?? '') : (this.currentContent.pt.sectionTitle !== undefined ? this.currentContent.pt.sectionTitle : (val.sectionTitle ?? ''));
    const ptBody = isDirty ? (val.body ?? '') : (this.currentContent.pt.body !== undefined ? this.currentContent.pt.body : (val.body ?? ''));

    const enHeadline = isDirty ? (val.headlineEn ?? '') : (this.currentContent.en.headline !== undefined ? this.currentContent.en.headline : (val.headlineEn ?? ''));
    const enQuote = isDirty ? (val.quoteEn ?? '') : (this.currentContent.en.quote !== undefined ? this.currentContent.en.quote : (val.quoteEn ?? ''));
    const enSectionTitle = isDirty ? (val.sectionTitleEn ?? '') : (this.currentContent.en.sectionTitle !== undefined ? this.currentContent.en.sectionTitle : (val.sectionTitleEn ?? ''));
    const enBody = isDirty ? (val.bodyEn ?? '') : (this.currentContent.en.body !== undefined ? this.currentContent.en.body : (val.bodyEn ?? ''));

    const canonicalKey = this.getCanonicalKey(this.activeSection);
    const payload = {
      translations: {
        pt: {
          headline: ptHeadline,
          quote: ptQuote,
          sectionTitle: ptSectionTitle,
          body: ptBody,
          content: ptBody
        },
        en: {
          headline: enHeadline,
          quote: enQuote,
          sectionTitle: enSectionTitle,
          body: enBody,
          content: enBody
        }
      },
      pt: {
        headline: ptHeadline,
        quote: ptQuote,
        sectionTitle: ptSectionTitle,
        body: ptBody,
        content: ptBody
      },
      en: {
        headline: enHeadline,
        quote: enQuote,
        sectionTitle: enSectionTitle,
        body: enBody,
        content: enBody
      }
    };

    this.http.put(`${environment.apiUrl}/admin/institutional/translations/${canonicalKey}`, payload).subscribe({
      next: () => {
        this.isSaving = false;
        this.currentContent.pt.headline = ptHeadline;
        this.currentContent.pt.quote = ptQuote;
        this.currentContent.pt.sectionTitle = ptSectionTitle;
        this.currentContent.pt.body = ptBody;
        this.currentContent.pt.content = ptBody;

        this.currentContent.en.headline = enHeadline;
        this.currentContent.en.quote = enQuote;
        this.currentContent.en.sectionTitle = enSectionTitle;
        this.currentContent.en.body = enBody;
        this.currentContent.en.content = enBody;

        this.saveSuccessMessage = 'Todas as alterações da página foram salvas com sucesso!';
        this.showSuccessFeedback(feedbackBlock);
        this.aboutForm.markAsPristine();
        try { alert(`Conteúdo de ${this.currentSectionTitle} salvo com sucesso!`); } catch {}
        setTimeout(() => this.saveSuccessMessage = '', 2500);
      },
      error: (err) => {
        this.isSaving = false;
        console.error('[Admin I18N] Erro ao salvar página completa:', err);
        const msg = err?.error?.message || err?.message || 'Falha ao sincronizar com o banco de dados.';
        this.errorMessage = `Erro ao salvar as alterações da página: ${msg}`;
        try { alert('Erro ao salvar as alterações da página. Verifique o console.'); } catch {}
      }
    });
  }

  pasteToFormField(controlName: string): void {
    this.pasteFromClipboard((text: string) => {
      this.aboutForm.patchValue({ [controlName]: text });
    });
  }

  private showSuccessFeedback(block: string): void {
    this.successBlock = block;
    setTimeout(() => {
      if (this.successBlock === block) {
        this.successBlock = null;
      }
    }, 2500);
  }

  resetOrSetDefaultSection(sectionKey: string): void {
    const canonicalKey = this.getCanonicalKey(sectionKey);
    this.currentSectionTitle = this.getSectionTitleByKey(sectionKey);

    if (canonicalKey === 'PRIVACY' || canonicalKey === 'PRIVACY_POLICY') {
      const ptText = 'Política de Privacidade & Diretrizes LGPD (Lei nº 13.709/2018)...\n\nA WB Agency preza pela segurança, transparência e proteção rigorosa dos dados pessoais de seus modelos e visitantes.';
      const enText = 'Privacy Policy & GDPR/LGPD Compliance...\n\nWB Agency values security, transparency, and strict protection of personal data belonging to our talent and visitors.';
      this.currentContent = {
        pt: {
          headline: 'Privacidade e Proteção de Dados Pessoais',
          quote: 'Conformidade rigorosa com a LGPD e o Regulamento Geral de Proteção de Dados (GDPR).',
          sectionTitle: 'Proteção de Dados',
          body: ptText,
          content: ptText
        },
        en: {
          headline: 'Privacy Policy & Personal Data Protection',
          quote: 'Strict compliance with LGPD and General Data Protection Regulation (GDPR).',
          sectionTitle: 'Data Protection',
          body: enText,
          content: enText
        }
      };
      this.tempPt.content = ptText;
      this.tempEn.content = enText;
    } else if (canonicalKey === 'TERMS' || canonicalKey === 'TERMS_OF_USE') {
      const ptText = 'Termos e Condições de Uso da WB Agency...\n\nAo acessar e utilizar este website, você concorda com os termos aqui dispostos.';
      const enText = 'WB Agency Terms of Use...\n\nBy accessing and using this website, you agree to comply with the terms set forth herein.';
      this.currentContent = {
        pt: {
          headline: 'Termos e Condições de Uso da Plataforma',
          quote: 'Proteção patrimonial, segurança jurídica e transparência no agenciamento.',
          sectionTitle: 'Termos Gerais',
          body: ptText,
          content: ptText
        },
        en: {
          headline: 'Terms and Conditions of Platform Use',
          quote: 'Asset protection, legal compliance, and agency transparency.',
          sectionTitle: 'General Terms',
          body: enText,
          content: enText
        }
      };
      this.tempPt.content = ptText;
      this.tempEn.content = enText;
    } else if (canonicalKey === 'SCOUTING_GUIDELINES') {
      this.currentContent = {
        pt: {
          headline: 'Diretrizes de Scouting e Aplicação',
          quote: 'Critérios técnicos para submissão digital de novos talentos.',
          sectionTitle: 'Requisitos Fotográficos',
          body: 'Fotos polaroids com iluminação natural, rosto limpo sem maquiagem e roupas neutras. Não aceitamos fotos com filtros ou edições digitais.'
        },
        en: {
          headline: 'Scouting Guidelines & Application',
          quote: 'Technical criteria for digital submission of new talents.',
          sectionTitle: 'Polaroid Requirements',
          body: 'Natural light snapshots, clean face without makeup, and neutral wardrobe. We do not accept digitally filtered or altered photos.'
        }
      };
    } else {
      this.currentContent = {
        pt: {
          headline: '',
          quote: '',
          sectionTitle: '',
          body: '',
          content: ''
        },
        en: {
          headline: '',
          quote: '',
          sectionTitle: '',
          body: '',
          content: ''
        }
      };
    }
  }

  getSectionTitleByKey(key: string): string {
    switch (key) {
      case 'MANIFESTO':
      case 'ABOUT_MANIFESTO':
        return 'Sobre Nós';
      case 'SCOUTING':
      case 'SCOUTING_GUIDELINES':
      case 'APPLY_FORM':
        return 'Configurações do Form. de Quero ser Modelo';
      case 'APPLY':
      case 'APPLY_HOW_IT_WORKS':
        return 'Próximos Passos Apply (Como Funciona)';
      case 'TERMS':
      case 'TERMS_OF_USE':
        return 'Termos de Uso';
      case 'PRIVACY':
      case 'PRIVACY_POLICY':
        return 'Privacidade & LGPD';
      default:
        return 'Conteúdos Bilíngues';
    }
  }

  addFaqItem(): void {
    if (this.faqList.length < 10) {
      this.faqList.push({
        id: null,
        displayOrder: this.faqList.length + 1,
        question: '',
        answer: '',
        questionEn: '',
        answerEn: '',
        isActive: true
      });
    }
  }

  removeFaqItem(index: number): void {
    if (this.faqList.length <= 1) {
      alert('A lista deve conter ao menos uma pergunta e resposta.');
      return;
    }
    this.faqList.splice(index, 1);
  }

  moveFaqUp(index: number): void {
    if (index > 0) {
      const item = this.faqList.splice(index, 1)[0];
      this.faqList.splice(index - 1, 0, item);
    }
  }

  moveFaqDown(index: number): void {
    if (index < this.faqList.length - 1) {
      const item = this.faqList.splice(index, 1)[0];
      this.faqList.splice(index + 1, 0, item);
    }
  }

  saveFaqList(): void {
    this.isSaving = true;
    this.errorMessage = null;

    const payload = {
      items: this.faqList.map((item, index) => ({
        id: item.id || null,
        displayOrder: index + 1,
        question: item.question?.trim() || '',
        answer: item.answer?.trim() || '',
        questionEn: item.questionEn?.trim() || '',
        answerEn: item.answerEn?.trim() || '',
        isActive: item.isActive !== undefined ? item.isActive : true
      }))
    };

    this.http.put<any>(`${environment.apiUrl}/admin/apply-faq`, payload).subscribe({
      next: (res) => {
        this.isSaving = false;
        this.saveSuccessMessage = 'Todas as alterações da página foram salvas com sucesso!';
        this.showSuccessFeedback('faq');
        try { alert('Perguntas e respostas atualizadas com sucesso!'); } catch {}
        setTimeout(() => this.saveSuccessMessage = '', 2500);
        this.loadFaqList();
      },
      error: (err) => {
        this.isSaving = false;
        const msg = err?.error?.message || err?.message || 'Falha ao sincronizar FAQ com o servidor.';
        this.errorMessage = `Erro ao salvar FAQ: ${msg}`;
        console.error('Erro ao salvar FAQ:', err);
        alert('Erro ao salvar FAQ. Verifique os dados.');
      }
    });
  }

  loadFaqList(): void {
    this.http.get<any>(`${environment.apiUrl}/admin/apply-faq`).subscribe({
      next: (res) => {
        const items = this.extractFaqItems(res);
        if (items && items.length > 0) {
          this.faqList = this.mapFaqItems(items);
        } else {
          this.loadFaqFallback();
        }
      },
      error: (err) => {
        console.warn('Erro ao carregar FAQ de /admin/apply-faq, tentando fallbacks:', err);
        this.loadFaqFallback();
      }
    });
  }

  private extractFaqItems(res: any): any[] | null {
    if (!res) return null;
    if (Array.isArray(res)) return res;
    if (Array.isArray(res.items)) return res.items;
    if (Array.isArray(res.data)) return res.data;
    if (Array.isArray(res.content)) return res.content;
    if (res.payloadPt?.items && Array.isArray(res.payloadPt.items)) return res.payloadPt.items;
    if (res.translations?.pt?.items && Array.isArray(res.translations.pt.items)) return res.translations.pt.items;
    return null;
  }

  private mapFaqItems(list: any[]): any[] {
    const defaults = this.getDefaultFaqList();
    return list.map((item: any, index: number) => {
      const defMatch = defaults.find((d, dIdx) => 
        (item.question && d.question && d.question.trim().toLowerCase() === item.question.trim().toLowerCase()) ||
        (item.id && d.id && d.id === item.id) ||
        (dIdx === index)
      );

      const question = (item.question && item.question.trim()) || defMatch?.question || '';
      const answer = (item.answer && item.answer.trim()) || defMatch?.answer || '';
      const questionEn = (item.questionEn && item.questionEn.trim()) || 
                         (item.question_en && item.question_en.trim()) || 
                         defMatch?.questionEn || '';
      const answerEn = (item.answerEn && item.answerEn.trim()) || 
                       (item.answer_en && item.answer_en.trim()) || 
                       defMatch?.answerEn || '';

      return {
        id: item.id !== undefined ? item.id : (defMatch?.id || null),
        displayOrder: item.displayOrder || item.order || (index + 1),
        question,
        answer,
        questionEn,
        answerEn,
        isActive: item.isActive !== undefined ? item.isActive : true
      };
    });
  }

  private loadFaqFallback(): void {
    // 1º Fallback: GET /api/v1/public/apply-faq
    this.http.get<any>(`${environment.apiUrl}/public/apply-faq`).subscribe({
      next: (pubRes) => {
        const pubItems = this.extractFaqItems(pubRes);
        if (pubItems && pubItems.length > 0) {
          this.faqList = this.mapFaqItems(pubItems);
        } else {
          this.loadFaqInstitutionalFallback();
        }
      },
      error: () => {
        this.loadFaqInstitutionalFallback();
      }
    });
  }

  private loadFaqInstitutionalFallback(): void {
    // 2º Fallback: GET /api/v1/admin/institutional/translations/SCOUTING_FAQ
    this.http.get<any>(`${environment.apiUrl}/admin/institutional/translations/SCOUTING_FAQ`).subscribe({
      next: (instRes) => {
        const instItems = this.extractFaqItems(instRes);
        if (instItems && instItems.length > 0) {
          this.faqList = this.mapFaqItems(instItems);
        } else {
          this.setDefaultFaqList();
        }
      },
      error: () => {
        this.setDefaultFaqList();
      }
    });
  }

  getDefaultFaqList(): any[] {
    return [
      {
        id: 'e92ead7e-858b-4988-b921-230c32b9d9fb',
        displayOrder: 1,
        question: 'Quais são as medidas ideais para o mercado da moda e publicidade?',
        answer: 'Não existem medidas certas ou específicas. O importante é ter personalidade marcante, atitude e querer muito ser modelo.',
        questionEn: 'What are the ideal measurements for fashion and advertising?',
        answerEn: 'There are no strict specific measurements. What matters most is strong personality, attitude, and dedication.',
        isActive: true
      },
      {
        id: '8e12a91c-30f0-468d-ad27-576fce70bdfe',
        displayOrder: 2,
        question: 'É exigida altura mínima?',
        answer: 'Dentro do mercado de moda e publicidade isso varia muito. Nós avaliamos todos os perfis, independente da altura.',
        questionEn: 'Is there a minimum height requirement?',
        answerEn: 'In the fashion and advertising industry this varies greatly. We evaluate all profiles, regardless of height.',
        isActive: true
      },
      {
        id: '6c2cc246-b516-4102-9dd4-ab072e7125a8',
        displayOrder: 3,
        question: 'Quero ser modelo! Como faço para realizar uma avaliação digital? Quais devem ser os meus primeiros passos?',
        answer: 'Primeiramente, preencher corretamente todos os dados e medidas na ficha abaixo, deixar o seu Instagram e TikTok abertos para visualização de todos e incluir todas as fotos. Independente da resposta, todos receberão um e-mail devolutivo. Se o feedback for positivo, iremos orientá-lo(a) para os próximos passos.',
        questionEn: 'I want to be a model! How do I get a digital evaluation? What should be my first steps?',
        answerEn: 'First, fill out all information and measurements in the application form, keep your Instagram and TikTok profiles public, and upload all required photos. All applicants receive a feedback email. If positive, we will guide you on next steps.',
        isActive: true
      },
      {
        id: 'cc646a84-85b4-45d7-9ed9-233bb88e8c39',
        displayOrder: 4,
        question: 'Como devem ser essas fotos? Preciso ter book profissional?',
        answer: 'Não é necessário pagar por um book profissional. Nós analisamos, de preferência, fotos caseiras feitas em celular. Evitar: selfies, de costas, fotos com óculos de sol ou outros acessórios.\n\nPara fazer boas fotos, encontre uma parede de cor neutra, de dia com luz natural, sem maquiagem, sem edição e sem filtros – queremos ver a sua beleza natural. Use camiseta branca ou preta básica/sem estampas e calça jeans (meninas de salto).\n\nCertifique-se de fazer fotos:\n• Do corpo inteiro e meio corpo;\n• De perfil;\n• E close do rosto – sorrindo e sem sorrir.',
        questionEn: 'How should the polaroids and submitted photos look? Do I need a book?',
        answerEn: 'A professional portfolio is not required. We prefer natural photos taken on a smartphone. Avoid: selfies, back shots, sunglasses, or accessories.\n\nFor good photos, use a neutral plain wall in daylight, no makeup, no filters—we want to see your natural look. Wear a plain black or white t-shirt and jeans (heels for female applicants).\n\nInclude:\n• Full length and half body;\n• Profile shot;\n• Close-up—smiling and serious.',
        isActive: true
      },
      {
        id: '27bd1b92-0927-4a7b-b353-db4ec825d29e',
        displayOrder: 5,
        question: 'A partir de qual idade posso trabalhar na profissão de modelo?',
        answer: 'Antigamente era comum ver modelos que começavam a carreira aos 13, 14 anos, mas o mercado a cada ano se profissionaliza mais, por isso não agenciamos crianças.\n\nO mercado de moda nacional, de acordo com a legislação trabalhista, exige que os modelos profissionais tenham no mínimo 16 anos para trabalhar em qualquer tipo de campanha publicitária, fotos, filmes, desfiles… No Brasil, exige-se uma emancipação para quem tem entre 16 e 17 anos de acordo com a legislação da Justiça do Trabalho.',
        questionEn: 'From what age can I work as a professional model?',
        answerEn: 'WB Agency works with talent development starting from age 13 for training. Under labor regulations, models must be at least 16 to work professionally in campaigns, runway, and photoshoots. For applicants under 18, parental or legal guardian consent is strictly mandatory.',
        isActive: true
      },
      {
        id: '1ebaf156-0203-4edb-930c-07628496e092',
        displayOrder: 6,
        question: 'Preciso pagar alguma taxa para fazer avaliação na agência?',
        answer: 'Não cobramos nenhuma taxa para envio ou avaliação de material de novos talentos. A WB Agency nunca cobra nenhuma taxa para inscrição, avaliação de perfil, teste de vídeo ou agenciamento inicial. O processo de scouting é 100% gratuito e desconfie de qualquer abordagem cobrando taxas em nosso nome.',
        questionEn: 'Do I need to pay any fee to be evaluated by the agency?',
        answerEn: 'We do not charge any fee for submitting or evaluating materials from new talent. WB Agency never charges any fees for registration, profile evaluation, video tests, or initial signing. The scouting process is 100% free; beware of anyone charging fees in our name.',
        isActive: true
      },
      {
        id: '51a45938-7450-4f6d-bf0c-6cda87f9f0b0',
        displayOrder: 7,
        question: 'Preciso morar em SP?',
        answer: 'Os principais clientes do mercado estão localizados em SP – além de a maior semana de moda da América Latina ser realizada na cidade. Por isso, estar mais próxima desse mercado é um fator que facilita.',
        questionEn: 'Do I need to live in São Paulo?',
        answerEn: 'Major fashion clients and São Paulo Fashion Week are located in SP, so proximity to this market is an advantage. However, we scout talents nationwide across Brazil.',
        isActive: true
      }
    ];
  }

  setDefaultFaqList(): void {
    this.faqList = this.getDefaultFaqList();
  }

  // 1. Abre o Google Tradutor com o texto carregado e copia para a área de transferência
  openGoogleTranslator(sourceText: string): void {
    if (!sourceText || !sourceText.trim()) {
      alert('O campo em português está vazio para tradução.');
      return;
    }

    // Copia o texto para a área de transferência do usuário
    navigator.clipboard?.writeText(sourceText)?.catch?.(err => {
      console.warn('Não foi possível copiar automaticamente para o clipboard:', err);
    });

    // Monta a URL oficial do Google Tradutor configurando origem PT e destino EN
    const encodedText = encodeURIComponent(sourceText);
    const translatorUrl = `https://translate.google.com/?sl=pt&tl=en&text=${encodedText}&op=translate`;

    // Abre em uma nova aba do navegador
    window.open(translatorUrl, '_blank', 'noopener,noreferrer');
  }

  // 2. Cola o conteúdo da área de transferência diretamente no setter do campo alvo
  async pasteFromClipboard(targetFieldSetter?: (text: string) => void): Promise<void> {
    try {
      const text = await navigator.clipboard?.readText();
      if (text) {
        if (targetFieldSetter) {
          targetFieldSetter(text);
        } else {
          this.tempEn.content = text;
        }
      }
    } catch (err) {
      console.warn('Permissão de leitura do clipboard negada ou indisponível:', err);
      try {
        alert('Não foi possível acessar a área de transferência automaticamente. Por favor, cole usando Ctrl + V.');
      } catch {}
    }
  }

  // Setters compatíveis com templates Angular (evitando erro de sintaxe do parser com arrow functions inline)
  setEnContent = (text: string): void => {
    this.tempEn.content = text;
  };

  setEnBody = (text: string): void => {
    this.tempEn.body = text;
  };

  pasteToTempEn(field: keyof TranslationBlock = 'content'): void {
    this.pasteFromClipboard((text: string) => {
      this.tempEn[field] = text;
    });
  }

  pasteToFaq(item: any, field: 'questionEn' | 'answerEn'): void {
    this.pasteFromClipboard((text: string) => {
      item[field] = text;
    });
  }
}

