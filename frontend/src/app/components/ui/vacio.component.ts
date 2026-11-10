import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Una lista sin nada: un icono, que no hay y por que, y lo que se puede hacer, que llega como contenido. */
@Component({
  selector: 'app-vacio',
  template: `
    <div class="text-center py-5">
      <i class="fa-2x text-body-tertiary mb-3" [class]="icono()" aria-hidden="true"></i>
      <p class="fw-semibold mb-1">{{ titulo() }}</p>
      @if (texto()) {
        <p class="text-body-secondary mb-3">{{ texto() }}</p>
      }
      <ng-content />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class VacioComponent {
  readonly icono = input<string>('fa-regular fa-folder-open');
  readonly titulo = input.required<string>();
  readonly texto = input<string>('');
}
