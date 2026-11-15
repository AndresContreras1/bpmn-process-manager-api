import { HttpErrorResponse } from '@angular/common/http';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { mensajeDeLaApi } from '../../helpers/errores-api';
import { retenerFoco } from '../../helpers/teclado';
import { LARGO_MAXIMO_COMENTARIO } from '../../models/comentario.model';
import { Solicitud } from '../../models/solicitud.model';
import { SolicitudService } from '../../service/solicitud.service';

/**
 * PR F21: pedir la revision del borrador cuando la organizacion publica con aprobacion. La API congela el borrador tal
 * como esta guardado; si no se puede pedir (no hay cambios, el diagnostico tiene errores, ya hay una abierta o faltan
 * aprobadores) responde 409 con el motivo, que se dice tal cual. Escape cierra y el foco vuelve al boton.
 */
@Component({
  selector: 'app-pedir-revision',
  imports: [FormsModule],
  templateUrl: './pedir-revision.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class PedirRevisionComponent implements AfterViewInit {
  readonly procesoId = input.required<number>();
  readonly nombre = input<string>('');
  readonly pedida = output<Solicitud>();
  readonly cerrar = output<void>();

  private readonly solicitudes = inject(SolicitudService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogo = viewChild.required<ElementRef<HTMLElement>>('dialogo');
  private readonly anterior: HTMLElement | null = document.activeElement as HTMLElement | null;

  readonly largoMaximo: number = LARGO_MAXIMO_COMENTARIO;
  texto: string = '';
  enviando: boolean = false;
  error: string | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.anterior?.focus());
  }

  ngAfterViewInit(): void {
    this.dialogo().nativeElement.querySelector<HTMLElement>('textarea')?.focus();
  }

  pedir(): void {
    if (this.enviando) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.solicitudes
      .pedir(this.procesoId(), this.texto)
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (solicitud) => this.pedida.emit(solicitud),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeLaApi(error)),
      });
  }

  teclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.cerrar.emit();
    } else if (retenerFoco(evento, this.dialogo().nativeElement)) {
      evento.preventDefault();
    }
  }
}
