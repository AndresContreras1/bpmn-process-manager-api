import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  LOCALE_ID,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { InsigniaComponent } from '../../../components/ui/insignia.component';
import { abrirDescarga, tamanoLegible } from '../../../helpers/archivos';
import { mensajeDeError } from '../../../helpers/errores-api';
import { Documentacion } from '../../../models/documentacion.model';
import {
  Documento,
  EnlaceDeDescarga,
  EstadoDocumento,
  NOMBRE_ESTADO_DOCUMENTO,
  TONO_ESTADO_DOCUMENTO,
} from '../../../models/documento.model';
import { documentacionDe, documentosDe, sePuedeBajar } from '../../../models/portal.model';
import { Tono } from '../../../models/tono.model';
import { DocumentoService } from '../../../service/documento.service';

/** El elemento del diagrama que se esta leyendo; null es el proceso entero. */
export interface ElementoLeido {
  id: string;
  nombre: string;
}

/**
 * F22: lo que se lee de un elemento del diagrama, o del proceso entero si no hay ninguno elegido: su documentacion,
 * tal como la API la convirtio y la limpio, y sus documentos, que se bajan con un enlace firmado pedido justo antes.
 * No hay nada que editar ni subir.
 */
@Component({
  selector: 'app-panel-elemento',
  imports: [InsigniaComponent],
  templateUrl: './panel-elemento.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelElementoComponent {
  readonly elemento = input<ElementoLeido | null>(null);
  readonly textos = input<Documentacion[]>([]);
  readonly documentos = input<Documento[]>([]);

  private readonly servicio = inject(DocumentoService);
  private readonly idioma = inject(LOCALE_ID);
  private readonly destroyRef = inject(DestroyRef);

  readonly nombreEstado: Record<EstadoDocumento, string> = NOMBRE_ESTADO_DOCUMENTO;
  readonly tonoEstado: Record<EstadoDocumento, Tono> = TONO_ESTADO_DOCUMENTO;
  readonly sePuedeBajar = sePuedeBajar;

  readonly bpmnId = computed((): string | null => this.elemento()?.id ?? null);
  readonly texto = computed((): Documentacion | null => documentacionDe(this.textos(), this.bpmnId()));
  readonly suyos = computed((): Documento[] => documentosDe(this.documentos(), this.bpmnId()));
  readonly error = signal<string | null>(null);

  tamano(bytes: number): string {
    return tamanoLegible(bytes, this.idioma);
  }

  /** El enlace vale cinco minutos y no necesita sesion: se pide al pulsar, no al abrir la pagina. */
  bajar(documento: Documento): void {
    this.error.set(null);
    this.servicio
      .enlace(documento.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (enlace: EnlaceDeDescarga) => abrirDescarga(enlace.url),
        error: (error: HttpErrorResponse) => this.error.set(mensajeDeError(error)),
      });
  }
}
