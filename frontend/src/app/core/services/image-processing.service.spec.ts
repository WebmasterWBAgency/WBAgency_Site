import { TestBed } from '@angular/core/testing';
import { ImageProcessingService, processAndOptimizeImage } from './image-processing.service';

describe('ImageProcessingService', () => {
  let service: ImageProcessingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ImageProcessingService]
    });
    service = TestBed.inject(ImageProcessingService);
  });

  it('deve ser instanciado corretamente', () => {
    expect(service).toBeTruthy();
  });

  describe('processAndOptimizeImage (rotina UHD 4K unificada)', () => {
    it('deve rejeitar arquivo se exceder o teto de 8 MB', async () => {
      const oversizedFile = new File([new ArrayBuffer(9 * 1024 * 1024)], 'giant.jpg', {
        type: 'image/jpeg'
      });

      const result = await processAndOptimizeImage(oversizedFile);

      expect(result.valid).toBeFalse();
      expect(result.error).toContain('excede o tamanho máximo de 8 MB');
    });

    it('deve rejeitar formatos de arquivo não suportados', async () => {
      const invalidFile = new File([new ArrayBuffer(100)], 'doc.pdf', {
        type: 'application/pdf'
      });

      const result = await processAndOptimizeImage(invalidFile);

      expect(result.valid).toBeFalse();
      expect(result.error).toContain('Formato de imagem não suportado');
    });
  });

  describe('validateAndProcessPhoto (/apply e /become-model)', () => {
    it('deve rejeitar arquivo se exceder 8 MB', async () => {
      const oversizedFile = new File([new ArrayBuffer(9 * 1024 * 1024)], 'giant.jpg', {
        type: 'image/jpeg'
      });

      const result = await service.validateAndProcessPhoto(oversizedFile);

      expect(result.valid).toBeFalse();
      expect(result.error).toContain('excede o tamanho máximo de 8 MB');
    });

    it('deve rejeitar formatos de arquivo não suportados', async () => {
      const invalidFile = new File([new ArrayBuffer(100)], 'doc.pdf', {
        type: 'application/pdf'
      });

      const result = await service.validateAndProcessPhoto(invalidFile);

      expect(result.valid).toBeFalse();
      expect(result.error).toContain('Formato de imagem não suportado');
    });
  });

  describe('processAdminImage (painel admin /admin/models)', () => {
    it('deve rejeitar fotos acima de 8 MB', async () => {
      const oversizedFile = new File([new ArrayBuffer(9 * 1024 * 1024)], 'editorial-oversized.jpg', {
        type: 'image/jpeg'
      });

      const result = await service.processAdminImage(oversizedFile);

      expect(result.valid).toBeFalse();
      expect(result.error).toContain('excede o tamanho máximo de 8 MB');
    });

    it('deve aceitar arquivo com tamanho <= 8 MB e validar formato', async () => {
      const invalidDoc = new File([new ArrayBuffer(100)], 'document.docx', {
        type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      });

      const result = await service.processAdminImage(invalidDoc);

      expect(result.valid).toBeFalse();
      expect(result.error).toContain('Formato de imagem não suportado');
    });
  });

  describe('processAdminComposite', () => {
    it('deve rejeitar composite com mais de 25 MB', async () => {
      const oversizedComposite = new File([new ArrayBuffer(26 * 1024 * 1024)], 'sedcard-oversized.pdf', {
        type: 'application/pdf'
      });

      const result = await service.processAdminComposite(oversizedComposite);

      expect(result.valid).toBeFalse();
      expect(result.error).toContain('ultrapassa o limite máximo permitido de 25 MB');
    });

    it('deve validar PDF válido mantendo o binário original', async () => {
      const pdfBlob = new Blob(['%PDF-1.7 sample content'], { type: 'application/pdf' });
      const validPdf = new File([pdfBlob], 'sedcard.pdf', { type: 'application/pdf' });

      const result = await service.processAdminComposite(validPdf);

      expect(result.valid).toBeTrue();
      expect(result.file).toBe(validPdf);
    });
  });
});
