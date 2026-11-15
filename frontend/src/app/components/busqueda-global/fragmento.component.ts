import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Segmento } from '../../models/busqueda.model';

/**
 * El fragmento de un resultado, tramo a tramo. Cada tramo es texto que interpola Angular, que lo escapa: lo que traiga
 * el documento o el comentario, etiquetas incluidas, se lee como texto y nunca se interpreta. Lo resaltado va en un
 * mark, que un lector de pantalla tambien anuncia.
 */
@Component({
  selector: 'app-fragmento',
  template: `@for (segmento of segmentos(); track $index) {
    @if (segmento.resaltado) {
      <mark>{{ segmento.texto }}</mark>
    } @else {
      <span>{{ segmento.texto }}</span>
    }
  }`,
  // El resaltado conserva el color del texto: el amarillo de mark baja el contraste en el tema oscuro
  styles: `
    mark {
      background-color: var(--bs-warning-bg-subtle);
      color: var(--bs-body-color);
      font-weight: 600;
      padding: 0;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FragmentoComponent {
  readonly segmentos = input.required<Segmento[]>();
}
