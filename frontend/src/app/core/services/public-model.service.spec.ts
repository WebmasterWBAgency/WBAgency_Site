import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PublicModelService } from './public-model.service';
import { environment } from '../../../environments/environment';

describe('PublicModelService', () => {
  let service: PublicModelService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PublicModelService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(PublicModelService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should get featured models from /featured-models', () => {
    service.getFeaturedModels(8).subscribe(response => {
      expect(response).toBeTruthy();
      expect(response.content.length).toBe(1);
      expect(response.content[0].stageName).toBe('Isabella Martins');
      expect(response.content[0].isStar).toBeTrue();
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/featured-models`);
    expect(req.request.method).toBe('GET');
    req.flush([{
      id: '123e4567-e89b-12d3-a456-426614174000',
      stageName: 'Isabella Martins',
      gender: 'FEMALE',
      coverImageUrl: 'https://images.unsplash.com/photo-1.jpg',
      heightCm: 179,
      city: 'São Paulo',
      isStar: true,
      displayOrder: 1
    }]);
  });

  it('should propagate HTTP error in getFeaturedModels', () => {
    service.getFeaturedModels(8).subscribe({
      next: () => fail('should have failed with HTTP error'),
      error: (err) => {
        expect(err).toBeTruthy();
      }
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/featured-models`);
    req.error(new ProgressEvent('error'));
  });

  it('should propagate HTTP error in getModels', () => {
    service.getModels({ gender: 'FEMALE' }).subscribe({
      next: () => fail('should have failed with HTTP error'),
      error: (err) => {
        expect(err).toBeTruthy();
      }
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/public/models?gender=FEMALE`);
    req.error(new ProgressEvent('error'));
  });

  it('should get models with query filters and pagination', () => {
    service.getModels({
      gender: 'FEMALE',
      isStar: true,
      search: 'Gisele',
      page: 1,
      size: 12,
      sort: 'stageName,asc'
    }).subscribe(response => {
      expect(response).toBeTruthy();
      expect(response.totalElements).toBe(1);
    });

    const req = httpTesting.expectOne(
      `${environment.apiUrl}/public/models?gender=FEMALE&isStar=true&search=Gisele&page=1&size=12&sort=stageName,asc`
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: [{
        id: '123',
        stageName: 'Gisele B',
        gender: 'FEMALE',
        coverImageUrl: 'https://example.com/cover.jpg',
        heightCm: 180,
        city: 'Horizontina',
        isStar: true
      }],
      pageNumber: 1,
      pageSize: 12,
      totalElements: 1,
      totalPages: 1,
      isLast: true
    });
  });

  it('should get model details by id', () => {
    service.getModelById('test-uuid-456').subscribe(model => {
      expect(model).toBeTruthy();
      expect(model.stageName).toBe('Valentina R');
      expect(model.heightCm).toBe(180);
      expect(model.bookPhotos?.length).toBe(1);
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/public/models/test-uuid-456`);
    expect(req.request.method).toBe('GET');
    req.flush({
      id: 'test-uuid-456',
      stageName: 'Valentina R',
      gender: 'FEMALE',
      isStar: true,
      heightCm: 180,
      bookPhotos: [{ id: 'bp-1', fileUrl: 'https://example.com/book1.jpg' }]
    });
  });

  it('should propagate HTTP error in getModelById', () => {
    service.getModelById('f1a2b3c4-1111-4000-8000-000000000001').subscribe({
      next: () => fail('should have failed with HTTP error'),
      error: (err) => {
        expect(err).toBeTruthy();
      }
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/public/models/f1a2b3c4-1111-4000-8000-000000000001`);
    req.error(new ProgressEvent('error'));
  });
});
