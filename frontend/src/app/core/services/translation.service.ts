import { Injectable, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of } from 'rxjs';

import ptTranslations from '../../../assets/i18n/pt.json';
import enTranslations from '../../../assets/i18n/en.json';

export type SupportedLanguage = 'pt' | 'en';

const STORAGE_KEY = 'wb_scouting_lang';

const DEFAULT_DICTIONARIES: Record<SupportedLanguage, Record<string, any>> = {
  pt: ptTranslations as Record<string, any>,
  en: enTranslations as Record<string, any>
};

@Injectable({
  providedIn: 'root'
})
export class TranslationService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  // In-memory cache for loaded translation dictionaries
  private readonly cache = new Map<SupportedLanguage, Record<string, any>>();

  readonly currentLang = signal<SupportedLanguage>(this.getInitialLanguage());
  readonly translations = signal<Record<string, any>>(
    DEFAULT_DICTIONARIES[this.getInitialLanguage()] || ptTranslations
  );

  constructor() {}

  /**
   * Initializes translations for the currently selected language.
   * Can be hooked into APP_INITIALIZER.
   */
  init(): Observable<Record<string, any>> {
    const lang = this.currentLang();
    return this.loadTranslations(lang);
  }

  /**
   * Switches language, persists choice, and loads the corresponding dictionary.
   */
  setLanguage(lang: SupportedLanguage): void {
    if (this.currentLang() === lang && Object.keys(this.translations()).length > 0 && this.cache.has(lang)) {
      return;
    }

    this.currentLang.set(lang);

    if (this.isBrowser) {
      try {
        localStorage.setItem(STORAGE_KEY, lang);
      } catch (e) {
        console.warn('Unable to persist language preference in localStorage', e);
      }
    }

    // Instant synchronous switch to cached or bundled translations (zero delay / flicker)
    if (this.cache.has(lang)) {
      this.translations.set(this.cache.get(lang)!);
      return;
    }

    this.translations.set(DEFAULT_DICTIONARIES[lang] || ptTranslations);
    this.loadTranslations(lang).subscribe();
  }

  /**
   * Resolves nested dot-separated keys (e.g. 'nav.models') with variable interpolation.
   * Implements fallback to 'pt' default language if key is not found in the current language.
   */
  translate(key: string, params?: Record<string, string | number>): string {
    if (!key) return '';

    const dict = this.translations();

    // 1. Check direct flat key or traverse nested segments
    let current: any = dict ? dict[key] : undefined;
    if (typeof current !== 'string' && dict) {
      const segments = key.split('.');
      current = dict;
      for (const segment of segments) {
        if (current && typeof current === 'object' && segment in current) {
          current = current[segment];
        } else {
          current = undefined;
          break;
        }
      }
    }

    if (typeof current !== 'string') {
      // 2. Key not found in active dictionary. Fallback to default 'pt' language.
      const ptDict = this.cache.get('pt') || DEFAULT_DICTIONARIES['pt'] || ptTranslations;
      let fallbackCurrent: any = ptDict ? ptDict[key] : undefined;

      if (typeof fallbackCurrent !== 'string' && ptDict) {
        const segments = key.split('.');
        fallbackCurrent = ptDict;
        for (const segment of segments) {
          if (fallbackCurrent && typeof fallbackCurrent === 'object' && segment in fallbackCurrent) {
            fallbackCurrent = fallbackCurrent[segment];
          } else {
            fallbackCurrent = undefined;
            break;
          }
        }
      }

      if (typeof fallbackCurrent === 'string') {
        let fallbackResult = fallbackCurrent;
        if (params) {
          Object.entries(params).forEach(([placeholder, value]) => {
            fallbackResult = fallbackResult.replace(new RegExp(`\\{${placeholder}\\}`, 'g'), String(value));
          });
        }
        return fallbackResult;
      }

      return key;
    }

    let result = current;
    if (params) {
      Object.entries(params).forEach(([placeholder, value]) => {
        result = result.replace(new RegExp(`\\{${placeholder}\\}`, 'g'), String(value));
      });
    }

    return result;
  }

  /**
   * Synchronous translation method (alias for instant lookups matching ngx-translate interface).
   */
  instant(key: string, params?: Record<string, string | number>): string {
    return this.translate(key, params);
  }

  /**
   * Helper to unpack dynamic backend content payloads based on active language signal.
   */
  selectContent<T>(payloadPt: T, payloadEn: T): T {
    return this.currentLang() === 'pt' ? payloadPt : (payloadEn ?? payloadPt);
  }

  /**
   * Salva alterações de textos no cache em memória.
   */
  saveTranslations(locale: string, translations: Record<string, string>): Observable<any> {
    if (locale === 'pt' || locale === 'en') {
      const current = { ...(this.cache.get(locale) || DEFAULT_DICTIONARIES[locale] || {}) };

      Object.entries(translations).forEach(([k, val]) => {
        current[k] = val;
        const parts = k.split('.');
        let obj = current;
        for (let i = 0; i < parts.length - 1; i++) {
          if (!obj[parts[i]] || typeof obj[parts[i]] !== 'object') {
            obj[parts[i]] = {};
          }
          obj = obj[parts[i]];
        }
        obj[parts[parts.length - 1]] = val;
      });

      this.cache.set(locale, current);
      if (this.currentLang() === locale) {
        this.translations.set(current);
      }
    }
    return of({ success: true });
  }

  /**
   * Loads translation dictionary file via HttpClient and caches it in memory.
   * Exclusively consumes local static assets (assets/i18n/${lang}.json).
   */
  loadTranslations(lang: SupportedLanguage): Observable<Record<string, any>> {
    if (this.cache.has(lang)) {
      const cached = this.cache.get(lang)!;
      this.translations.set(cached);
      return of(cached);
    }

    return this.http.get<Record<string, any>>(`assets/i18n/${lang}.json`).pipe(
      tap((localData) => {
        const merged = {
          ...DEFAULT_DICTIONARIES[lang],
          ...(localData || {})
        };
        this.cache.set(lang, merged);
        this.translations.set(merged);
      }),
      catchError((error) => {
        console.warn(`Failed to load local translations for '${lang}', using bundled fallback:`, error);
        const fallback = DEFAULT_DICTIONARIES[lang] || ptTranslations;
        this.cache.set(lang, fallback);
        this.translations.set(fallback);
        return of(fallback);
      })
    );
  }

  /**
   * Resolves initial language checking localStorage first, then navigator.language, fallback to 'pt'.
   */
  private getInitialLanguage(): SupportedLanguage {
    if (!this.isBrowser) {
      return 'pt';
    }

    try {
      const stored = localStorage.getItem(STORAGE_KEY) as SupportedLanguage | null;
      if (stored === 'pt' || stored === 'en') {
        return stored;
      }
    } catch (e) {
      console.warn('Failed to access localStorage for language preference', e);
    }

    try {
      const browserLang = navigator.language?.toLowerCase() || '';
      if (browserLang.startsWith('en')) {
        return 'en';
      }
    } catch (e) {
      console.warn('Failed to access navigator.language', e);
    }

    return 'pt';
  }
}
