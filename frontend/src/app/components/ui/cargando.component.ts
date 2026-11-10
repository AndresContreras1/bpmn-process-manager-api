import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Lo que se ve mientras llega algo de la API: un giro y que es lo que se carga, anunciado como estado. */
@Component({
  selector: 'app-cargando',
  template: `
    <div class="text-center text-body-secondary py-5" role="status">
      <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>{{ texto() }}
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class CargandoComponent {
  readonly texto = input<string>($localize`Loading…`);
}
