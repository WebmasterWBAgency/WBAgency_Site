import { Component, input, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ModelCardPublicDto } from '../../../../../core/services/public-model.service';
import { TranslatePipe } from '../../../../../shared/pipes/translate.pipe';

@Component({
  selector: 'app-model-card',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslatePipe],
  templateUrl: './model-card.component.html',
  styleUrls: ['./model-card.component.scss']
})
export class ModelCardComponent {
  private readonly router = inject(Router);

  readonly model = input.required<ModelCardPublicDto>();

  // Fallback de alta moda caso a imagem primária falhe ou esteja em branco
  readonly defaultCoverImage = 'assets/images/placeholder-polaroid.jpg';
  readonly isImageError = signal<boolean>(false);

  getImageSrc(): string {
    if (this.isImageError()) {
      return this.defaultCoverImage;
    }
    const m = this.model();
    const cover = m.coverPhotoUrl || m.coverImageUrl || m.primaryPhotoUrl || m.media?.[0]?.fileUrl;
    return cover && cover.trim() !== '' ? cover : this.defaultCoverImage;
  }

  onImageError(): void {
    this.isImageError.set(true);
  }

  onCardClick(event: Event): void {
    const id = this.model()?.id;
    if (!id) return;

    if (event instanceof MouseEvent) {
      // Permite abrir em nova aba com modificadores ou botão do meio
      if (event.ctrlKey || event.metaKey || event.shiftKey || event.button === 1) {
        return;
      }
    }

    event.preventDefault();
    this.router.navigate(['/models', id]);
  }
}
