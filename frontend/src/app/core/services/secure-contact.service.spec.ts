import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SecureContactService } from './secure-contact.service';
import { PublicContentService } from './public-content.service';

describe('SecureContactService', () => {
  let service: SecureContactService;
  let publicContentServiceMock: jasmine.SpyObj<PublicContentService>;

  beforeEach(() => {
    publicContentServiceMock = jasmine.createSpyObj('PublicContentService', ['getContactChannels']);
    publicContentServiceMock.getContactChannels.and.returnValue(of({
      email: 'info@wbagency.com.br',
      whatsappNumber: '5511970656003',
      whatsappUrl: 'https://wa.me/5511970656003?text=Ola',
      instagramHandle: 'https://instagram.com/wbagency',
      address: 'São Paulo - SP',
      officeHours: 'Segunda a Sexta, das 09h às 18h'
    }));

    TestBed.configureTestingModule({
      providers: [
        SecureContactService,
        { provide: PublicContentService, useValue: publicContentServiceMock }
      ]
    });
    service = TestBed.inject(SecureContactService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should dynamically load email display from admin configuration', () => {
    expect(service.emailDisplay).toBe('info@wbagency.com.br');
  });

  it('should dynamically format phone display according to Brazilian international pattern', () => {
    expect(service.phoneDisplay).toBe('+55 (11) 97065-6003');
  });

  it('should normalize and return instagram display handle', () => {
    expect(service.instagramDisplay).toBe('@wbagency');
  });

  it('should open WhatsApp with dynamic link and noopener/noreferrer', () => {
    const windowSpy = spyOn(window, 'open').and.stub();

    service.openWhatsApp();

    expect(windowSpy).toHaveBeenCalledWith(
      'https://wa.me/5511970656003?text=Ola',
      '_blank',
      'noopener,noreferrer'
    );
  });

  it('should open Instagram with official URL and noopener/noreferrer', () => {
    const windowSpy = spyOn(window, 'open').and.stub();

    service.openInstagram();

    expect(windowSpy).toHaveBeenCalledWith(
      'https://instagram.com/wbagency/',
      '_blank',
      'noopener,noreferrer'
    );
  });

  it('should deduplicate social channels and exclude redundant Instagram when official handle exists', () => {
    const rawChannels = [
      { name: 'Instagram', url: 'https://www.instagram.com/infowbagency/' },
      { name: 'LinkedIn', url: 'https://www.linkedin.com/company/wbagency/' },
      { name: 'LinkedIn Official', url: 'https://linkedin.com/company/wbagency' },
      { name: 'TikTok', url: 'https://www.tiktok.com/@infowbagency' }
    ];

    const deduplicated = service.deduplicateSocialChannels(rawChannels);

    // O Instagram oficial já existe como canal dedicado (@wbagency), então não deve duplicar em socialMediaList
    expect(deduplicated.find(s => s.normalizedKey === 'instagram')).toBeUndefined();

    // LinkedIn duplicado deve ser consolidado para 1 único registro
    const linkedinList = deduplicated.filter(s => s.normalizedKey === 'linkedin');
    expect(linkedinList.length).toBe(1);

    // TikTok deve ter handle limpo e URL completa
    const tiktok = deduplicated.find(s => s.normalizedKey === 'tiktok');
    expect(tiktok).toBeDefined();
    expect(tiktok?.displayValue).toBe('@infowbagency');
  });
});


