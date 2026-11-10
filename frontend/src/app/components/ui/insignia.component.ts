import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Tono } from '../../models/tono.model';

const CLASE: Record<Tono, string> = {
  exito: 'text-bg-success',
  aviso: 'text-bg-warning',
  peligro: 'text-bg-danger',
  info: 'text-bg-info',
  primario: 'text-bg-primary',
  neutro: 'text-bg-secondary',
};

/** El icono de cada tono: el estado no se distingue solo por el color. */
const ICONO: Record<Tono, string> = {
  exito: 'fa-circle-check',
  aviso: 'fa-hourglass-half',
  peligro: 'fa-circle-xmark',
  info: 'fa-circle-info',
  primario: 'fa-circle-play',
  neutro: 'fa-circle',
};

/** El estado de algo (un proceso, un caso, un paso) como insignia, con su icono y su texto. */
@Component({
  selector: 'app-insignia',
  template: `<span class="badge" [class]="clase()"
    ><i class="fa-solid me-1" [class]="icono()" aria-hidden="true"></i>{{ texto() }}</span
  >`,
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class InsigniaComponent {
  readonly tono = input<Tono>('neutro');
  readonly texto = input.required<string>();

  readonly clase = computed((): string => CLASE[this.tono()]);
  readonly icono = computed((): string => ICONO[this.tono()]);
}
