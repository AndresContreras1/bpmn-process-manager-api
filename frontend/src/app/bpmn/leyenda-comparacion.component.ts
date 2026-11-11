import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Comparacion } from '../models/modelo.model';

/** La comparacion en palabras: el color del dibujo acompana a este texto, nunca lo reemplaza. */
@Component({
  selector: 'app-leyenda-comparacion',
  templateUrl: './leyenda-comparacion.component.html',
  styleUrl: './leyenda-comparacion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LeyendaComparacionComponent {
  readonly comparacion = input.required<Comparacion>();
  readonly mostrar = output<string>();

  get sinCambios(): boolean {
    const { agregados, quitados, cambiados, movidos } = this.comparacion();
    return agregados.length + quitados.length + cambiados.length + movidos.length === 0;
  }
}
