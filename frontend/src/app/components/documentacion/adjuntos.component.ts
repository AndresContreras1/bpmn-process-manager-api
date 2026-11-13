import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  LOCALE_ID,
  OnChanges,
  ViewChild,
  inject,
  input,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subscription, catchError, concatMap, finalize, from, switchMap, timer } from 'rxjs';

import { ModalConfirmarComponent } from '../modal-confirmar/modal-confirmar.component';
import { abrirDescarga, mensajeDeSubida, tamanoLegible } from '../../helpers/archivos';
import { mensajeDeError } from '../../helpers/errores-api';
import {
  Documento,
  DocumentoDetalle,
  EnlaceDeDescarga,
  EstadoDocumento,
  NOMBRE_ESTADO_DOCUMENTO,
  TAMANO_MAXIMO_DOCUMENTO,
} from '../../models/documento.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { DocumentoService } from '../../service/documento.service';

/** Cada cuanto se vuelve a preguntar por un documento que el antivirus todavia revisa. */
export const INTERVALO_DE_REVISION: number = 2000;

/**
 * PR F20: los documentos de un proceso, o de uno de sus elementos. Se adjuntan con el boton o soltandolos sobre la
 * zona; antes de mandar nada se descarta lo que pasa de 25 MB. Un documento llega PENDIENTE: mientras alguno lo este,
 * la lista se vuelve a pedir cada dos segundos, y cuando el antivirus termina se anuncia en una region aria-live.
 *
 * La subida no dice cuanto lleva: la web usa el backend fetch de HttpClient, que no informa el progreso de un envio.
 */
@Component({
  selector: 'app-adjuntos',
  imports: [FechaPipe, ModalConfirmarComponent],
  templateUrl: './adjuntos.component.html',
  styleUrl: './adjuntos.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class AdjuntosComponent implements OnChanges {
  readonly procesoId = input.required<number>();
  /** El bpmnId del elemento; vacio para el proceso, que lista tambien los de sus elementos. */
  readonly elemento = input<string | null>(null);
  readonly soloLectura = input<boolean>(false);
  /** En el panel del modelador, que es angosto. */
  readonly compacto = input<boolean>(false);

  private readonly servicio = inject(DocumentoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly idioma: string = inject(LOCALE_ID);

  @ViewChild('archivoVersion') private entradaVersion?: ElementRef<HTMLInputElement>;

  readonly nombreEstado: Record<EstadoDocumento, string> = NOMBRE_ESTADO_DOCUMENTO;

  documentos: Documento[] = [];
  cargando: boolean = true;
  /** El nombre del archivo que se esta mandando. */
  subiendo: string | null = null;
  arrastrando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;
  /** Lo que se le dice a un lector de pantalla cuando el antivirus termina con un documento. */
  anuncio: string = '';
  /** El documento con sus versiones a la vista. */
  abierto: DocumentoDetalle | null = null;
  abriendo: number | null = null;
  porBorrar: Documento | null = null;
  /** A que documento va el archivo que se elija para una version nueva. */
  private versionDe: Documento | null = null;
  private carga: Subscription | null = null;
  private sondeo: Subscription | null = null;

  ngOnChanges(): void {
    this.abierto = null;
    this.cargar();
  }

  cargar(): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.error = null;
    this.carga = this.listar()
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (documentos: Documento[]) => {
          this.documentos = documentos;
          this.programarRevision();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  tamano(bytes: number): string {
    return tamanoLegible(bytes, this.idioma);
  }

  estaAbierto(documento: Documento): boolean {
    return this.abierto?.documento.id === documento.id;
  }

  // La zona de soltar: sin preventDefault el navegador abriria el archivo en vez de entregarlo
  alArrastrar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrando = true;
  }

  alSalir(evento: DragEvent): void {
    // Pasar sobre un hijo de la zona tambien dispara dragleave: solo cuenta salir de la zona entera
    const zona = evento.currentTarget as Node | null;
    if (zona?.contains(evento.relatedTarget as Node | null)) {
      return;
    }
    this.arrastrando = false;
  }

  alSoltar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrando = false;
    this.subir(Array.from(evento.dataTransfer?.files ?? []));
  }

  alElegir(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivos: File[] = Array.from(entrada.files ?? []);
    // Vacia la entrada, para que elegir otra vez el mismo archivo tambien avise
    entrada.value = '';
    this.subir(archivos);
  }

  /** Uno detras de otro, sin mandar lo que pasa del tope: la API y NGINX lo cortarian igual, despues de esperar. */
  subir(archivos: File[]): void {
    if (this.soloLectura() || archivos.length === 0) {
      return;
    }
    this.error = this.demasiadoGrandes(archivos);
    this.aviso = null;
    const aceptados: File[] = archivos.filter((archivo: File) => archivo.size <= TAMANO_MAXIMO_DOCUMENTO);
    from(aceptados)
      .pipe(
        concatMap((archivo: File) => {
          this.subiendo = archivo.name;
          return this.servicio.subir(this.procesoId(), archivo, this.elemento()).pipe(
            catchError((error: HttpErrorResponse) => {
              this.error = $localize`${archivo.name} was not uploaded: ${mensajeDeSubida(error)}`;
              return EMPTY;
            }),
          );
        }),
        finalize(() => (this.subiendo = null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((documento: Documento) => {
        this.documentos = ordenar([...this.documentos, documento]);
        this.aviso = $localize`“${documento.nombre}” was uploaded. The antivirus is checking it.`;
        this.programarRevision();
      });
  }

  /** Abre el selector de archivos para la version nueva de ese documento. */
  elegirVersion(documento: Documento): void {
    this.versionDe = documento;
    this.entradaVersion?.nativeElement.click();
  }

  alElegirVersion(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo: File | undefined = entrada.files?.[0];
    entrada.value = '';
    const documento: Documento | null = this.versionDe;
    this.versionDe = null;
    if (!archivo || !documento || this.soloLectura()) {
      return;
    }
    this.aviso = null;
    this.error = this.demasiadoGrandes([archivo]);
    if (this.error) {
      return;
    }
    this.subiendo = archivo.name;
    this.servicio
      .subirVersion(documento.id, archivo)
      .pipe(
        finalize(() => (this.subiendo = null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (actualizado: Documento) => {
          this.reemplazar(actualizado);
          this.aviso = $localize`Version ${actualizado.versionActual} of “${actualizado.nombre}” was uploaded. The antivirus is checking it.`;
          if (this.estaAbierto(actualizado)) {
            this.verVersiones(actualizado);
          }
          this.programarRevision();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeSubida(error)),
      });
  }

  /** Abre o cierra la lista de versiones de un documento. */
  alternarVersiones(documento: Documento): void {
    if (this.estaAbierto(documento)) {
      this.abierto = null;
      return;
    }
    this.verVersiones(documento);
  }

  /** Pide un enlace firmado justo antes de usarlo: vale cinco minutos. Sin version, la ultima. */
  bajar(documento: Documento, version?: number): void {
    this.error = null;
    this.servicio
      .enlace(documento.id, version)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (enlace: EnlaceDeDescarga) => abrirDescarga(enlace.url),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  pedirBorrado(documento: Documento): void {
    this.porBorrar = documento;
  }

  borrar(): void {
    const documento: Documento | null = this.porBorrar;
    if (!documento) {
      return;
    }
    this.error = null;
    this.aviso = null;
    this.servicio
      .eliminar(documento.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.documentos = this.documentos.filter((uno: Documento) => uno.id !== documento.id);
          if (this.estaAbierto(documento)) {
            this.abierto = null;
          }
          this.porBorrar = null;
          this.aviso = $localize`“${documento.nombre}” was deleted.`;
          this.programarRevision();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private listar(): Observable<Documento[]> {
    return this.servicio.listar(this.procesoId(), this.elemento());
  }

  private demasiadoGrandes(archivos: File[]): string | null {
    const nombres: string[] = archivos
      .filter((archivo: File) => archivo.size > TAMANO_MAXIMO_DOCUMENTO)
      .map((archivo: File) => archivo.name);
    return nombres.length === 0
      ? null
      : $localize`Larger than 25 MB, the most a document can be, so not sent: ${nombres.join(', ')}.`;
  }

  private verVersiones(documento: Documento): void {
    this.abriendo = documento.id;
    this.servicio
      .obtener(documento.id)
      .pipe(
        finalize(() => (this.abriendo = null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (detalle: DocumentoDetalle) => (this.abierto = detalle),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private reemplazar(actualizado: Documento): void {
    this.documentos = ordenar(
      this.documentos.map((documento: Documento) => (documento.id === actualizado.id ? actualizado : documento)),
    );
  }

  /** Mientras quede algo pendiente, se vuelve a pedir la lista; cuando no, se deja de preguntar. */
  private programarRevision(): void {
    this.sondeo?.unsubscribe();
    this.sondeo = null;
    if (!this.documentos.some((documento: Documento) => documento.estado === 'PENDIENTE')) {
      return;
    }
    this.sondeo = timer(INTERVALO_DE_REVISION)
      .pipe(
        switchMap(() => this.listar()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (documentos: Documento[]) => this.alRevisar(documentos),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private alRevisar(documentos: Documento[]): void {
    const antes = new Map<number, EstadoDocumento>(
      this.documentos.map((documento: Documento) => [documento.id, documento.estado]),
    );
    const revisados: Documento[] = documentos.filter(
      (documento: Documento) => antes.get(documento.id) === 'PENDIENTE' && documento.estado !== 'PENDIENTE',
    );
    this.documentos = documentos;
    if (revisados.length > 0) {
      this.anuncio = revisados.map((documento: Documento) => anunciar(documento)).join(' ');
      const abierto: Documento | undefined = revisados.find((documento: Documento) => this.estaAbierto(documento));
      if (abierto) {
        this.verVersiones(abierto);
      }
    }
    this.programarRevision();
  }
}

/** Por nombre, como los devuelve la API. */
function ordenar(documentos: Documento[]): Documento[] {
  return [...documentos].sort((a: Documento, b: Documento) => a.nombre.localeCompare(b.nombre) || a.id - b.id);
}

function anunciar(documento: Documento): string {
  return documento.estado === 'DISPONIBLE'
    ? $localize`“${documento.nombre}” is ready to download.`
    : $localize`“${documento.nombre}” was blocked: ${documento.motivo ?? ''}`;
}
