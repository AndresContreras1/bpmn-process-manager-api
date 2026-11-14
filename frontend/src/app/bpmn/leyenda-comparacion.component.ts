import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { Comparacion } from '../models/modelo.model';
import { filasDeDiferencia, gruposDeDiferencia, resumenDeDiferencia } from './diferencia';

/**
 * La comparacion en palabras: el color del dibujo acompana a este texto, nunca lo reemplaza. La usan la pagina de
 * comparar, el modelador y la de una solicitud de revision, asi que las tres dicen la diferencia igual.
 */
@Component({
  selector: 'app-leyenda-comparacion',
  templateUrl: './leyenda-comparacion.component.html',
  styleUrl: './leyenda-comparacion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LeyendaComparacionComponent {
  readonly comparacion = input.required<Comparacion>();
  readonly mostrar = output<string>();

  private readonly filas = computed(() => filasDeDiferencia(this.comparacion()));
  readonly grupos = computed(() => gruposDeDiferencia(this.filas()));
  readonly resumen = computed(() => resumenDeDiferencia(this.filas()));
}
