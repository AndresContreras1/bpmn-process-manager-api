import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { AvisosService, TipoDeAviso } from '../../service/avisos.service';

const CLASE: Record<TipoDeAviso, string> = { exito: 'text-bg-success', error: 'text-bg-danger', info: 'text-bg-info' };

/**
 * Los avisos pasajeros, en la esquina de la pantalla. La region es aria-live: un lector de pantalla lee cada aviso nuevo
 * sin mover el foco, y uno de error, que importa mas, interrumpe.
 */
@Component({
  selector: 'app-avisos',
  template: `
    <div class="toast-container position-fixed bottom-0 end-0 p-3" aria-live="polite" aria-atomic="false" id="avisos">
      @for (aviso of avisosService.avisos(); track aviso.id) {
        <div
          class="toast show align-items-center border-0"
          [class]="clase[aviso.tipo]"
          [attr.role]="aviso.tipo === 'error' ? 'alert' : 'status'"
          data-testid="aviso"
        >
          <div class="d-flex">
            <div class="toast-body">{{ aviso.texto }}</div>
            <button
              type="button"
              class="btn-close btn-close-white me-2 m-auto"
              aria-label="Close"
              (click)="avisosService.cerrar(aviso.id)"
            ></button>
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class AvisosComponent {
  readonly avisosService: AvisosService = inject(AvisosService);
  readonly clase: Record<TipoDeAviso, string> = CLASE;
}
