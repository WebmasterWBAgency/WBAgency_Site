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
    it('deve rejeitar formatos de arquivo não suportados', async () => {
      const invalidFile = new File([new ArrayBuffer(100)], 'doc.pdf', {
        type: 'application/pdf'
      });

      const result = await processAndOptimizeImage(invalidFile);

      expect(result.valid).toBeFalse();
      expect(result.error).toContain('Formato de imagem não suportado');
    });

    it('deve retornar erro amigável caso os dados da imagem não possam ser carregados', async () => {
      const corruptFile = new File([new ArrayBuffer(100)], 'corrupt.jpg', {
        type: 'image/jpeg'
      });

      const result = await processAndOptimizeImage(corruptFile);

      expect(result.valid).toBeFalse();
      expect(result.error).toContain('Não foi possível carregar os dados');
    });
  });

  describe('validateAndProcessPhoto (/apply e /become-model)', () => {
    it('deve rejeitar se nenhum arquivo for fornecido', async () => {
      const result = await service.validateAndProcessPhoto(null as any);
      expect(result.valid).toBeFalse();
      expect(result.error).toContain('Nenhum arquivo de imagem fornecido');
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
    it('deve rejeitar formatos de arquivo não suportados', async () => {
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
