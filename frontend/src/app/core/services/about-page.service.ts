import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { ApiService } from './api.service';
import { AboutPage } from '../../shared/models/about-page.interface';

@Injectable({
  providedIn: 'root'
})
export class AboutPageService {
  private readonly api = inject(ApiService);

  public readonly defaultAboutPage: AboutPage = {
    title: '',
    subtitle: '',
    description: '',
    heroQuote: '',
    manifestoTitle: '',
    manifestoText: '',
    pillarsTitle: '',
    pillars: [],
    seo: {
      metaTitle: '',
      metaDescription: ''
    }
  };

  getPublicAboutPage(): Observable<AboutPage> {
    return this.api.get<AboutPage>('/public/institutional/about');
  }

  getAboutContent(lang?: string): Observable<any> {
    const isEn = lang?.toLowerCase().startsWith('en');
    const param = isEn ? 'en' : 'pt';
    return this.api.get<any>(`/public/institutional/about?lang=${param}`).pipe(
      catchError(() => this.api.get<any>(`/public/content/ABOUT_MANIFESTO?lang=${param}`)),
      map(res => {
        if (!res) return null;
        const inner = res.content || res.payload || res.data || res;
        return {
          headline: inner.headline ?? inner.title ?? inner.pageTitle ?? res.headline ?? '',
          quote: inner.quote ?? inner.heroQuote ?? res.quote ?? '',
          sectionTitle: inner.sectionTitle ?? inner.manifestoTitle ?? res.sectionTitle ?? '',
          body: inner.body ?? inner.manifestoText ?? inner.bodyText ?? inner.description ?? res.body ?? '',
          pillarsTitle: inner.pillarsTitle ?? res.pillarsTitle ?? '',
          pillars: inner.pillars ?? res.pillars ?? [],
          seo: inner.seo ?? res.seo ?? {}
        };
      }),
      catchError(() => of(null))
    );
  }

  getAdminAboutPage(): Observable<AboutPage> {
    return this.api.get<AboutPage>('/admin/institutional/about');
  }

  updateAboutPage(dto: AboutPage): Observable<AboutPage> {
    return this.api.put<AboutPage>('/admin/institutional/about', dto);
  }
}
