import { TestBed } from '@angular/core/testing';
import { ImageProcessingService } from './image-processing.service';

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

  it('deve rejeitar arquivo se exceder 8 MB', async () => {
    const oversizedFile = new File([new ArrayBuffer(9 * 1024 * 1024)], 'giant.jpg', {
      type: 'image/jpeg'
    });

    const result = await service.validateAndProcessPhoto(oversizedFile);

    expect(result.valid).toBeFalse();
    expect(result.error).toContain('excede o limite máximo permitido de 8 MB');
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
