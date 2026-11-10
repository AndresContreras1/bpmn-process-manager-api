import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/** Lo que fallo al cargar, como alerta, y el boton para volver a intentarlo. */
@Component({
  selector: 'app-error-carga',
  template: `
    <div class="alert alert-danger d-flex align-items-center justify-content-between gap-3" role="alert">
      <span><i class="fa-solid fa-triangle-exclamation me-2" aria-hidden="true"></i>{{ mensaje() }}</span>
      <button type="button" class="btn btn-sm btn-outline-danger" (click)="reintentar.emit()">Try again</button>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ErrorCargaComponent {
  readonly mensaje = input.required<string>();
  readonly reintentar = output<void>();
}
