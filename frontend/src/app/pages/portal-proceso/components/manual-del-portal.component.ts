import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  LOCALE_ID,
  OnChanges,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, finalize, switchMap, timer } from 'rxjs';

import { abrirDescarga } from '../../../helpers/archivos';
import { mensajeDeError } from '../../../helpers/errores-api';
import { FormatoManual, Manual } from '../../../models/documentacion.model';
import { EnlaceDeDescarga } from '../../../models/documento.model';
import { manualEnCurso, manualParaBajar, nombreDeLengua } from '../../../models/portal.model';
import { FechaPipe } from '../../../pipes/fecha.pipe';
import { ManualService } from '../../../service/manual.service';

/** Cada cuanto se vuelve a preguntar por un manual que la cola todavia arma. */
export const INTERVALO_DEL_MANUAL: number = 2000;

/**
 * Cuantas veces se pregunta antes de dejar de esperar: tres minutos. Un manual que tarda mas esta atascado o lo dejo
 * otra persona, y una pestana abierta no puede quedarse preguntando para siempre.
 */
export const REVISIONES_DEL_MANUAL: number = 90;

/**
 * F22: el manual de la version vigente, en el portal. Quien puede exportar lo pide y espera a que la cola lo arme;
 * quien no, baja el ultimo que haya listo de esa version, en su idioma si lo hay. Lo que termina se anuncia.
 */
@Component({
  selector: 'app-manual-del-portal',
  imports: [FechaPipe],
  templateUrl: './manual-del-portal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ManualDelPortalComponent implements OnChanges {
  readonly procesoId = input.required<number>();
  readonly version = input.required<number>();
  readonly manualesLeidos = input<Manual[]>([]);
  readonly puedePedir = input<boolean>(false);

  private readonly servicio = inject(ManualService);
  private readonly idioma = inject(LOCALE_ID);
  private readonly destroyRef = inject(DestroyRef);

  readonly manuales = signal<Manual[]>([]);
  readonly pidiendo = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly anuncio = signal<string>('');
  /** Se pregunto REVISIONES_DEL_MANUAL veces y sigue en la cola: se deja de preguntar y se dice que vuelva luego. */
  readonly demorado = signal<boolean>(false);
  readonly listo = computed((): Manual | null => manualParaBajar(this.manuales(), this.version(), this.idioma));
  readonly enCurso = computed((): Manual | null => manualEnCurso(this.manuales(), this.version()));
  private sondeo: Subscription | null = null;
  private revisiones: number = 0;

  ngOnChanges(): void {
    this.manuales.set(this.manualesLeidos());
    this.esperarDeNuevo();
  }

  /** La lengua del manual con su nombre, en la de quien lee. */
  lengua(manual: Manual): string {
    return nombreDeLengua(manual.idioma, this.idioma);
  }

  /** El de la version vigente, en el idioma de quien lo pide; la API lo arma en la cola. */
  pedir(): void {
    if (this.pidiendo() || !this.puedePedir()) {
      return;
    }
    this.pidiendo.set(true);
    this.error.set(null);
    this.servicio
      .pedir(this.procesoId())
      .pipe(
        finalize(() => this.pidiendo.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (manual: Manual) => {
          this.manuales.update((antes) => [manual, ...antes.filter((uno) => uno.id !== manual.id)]);
          this.anuncio.set($localize`The manual of version ${manual.version} is being built.`);
          this.esperarDeNuevo();
        },
        error: (error: HttpErrorResponse) => this.error.set(mensajeDeError(error)),
      });
  }

  /** Pide el enlace justo antes de usarlo: vale cinco minutos. */
  bajar(manual: Manual, formato: FormatoManual): void {
    this.error.set(null);
    this.servicio
      .enlace(manual.id, formato)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (enlace: EnlaceDeDescarga) => abrirDescarga(enlace.url),
        error: (error: HttpErrorResponse) => this.error.set(mensajeDeError(error)),
      });
  }

  /** Un manual recien pedido, o una lista nueva, vuelven a contar desde cero. */
  private esperarDeNuevo(): void {
    this.revisiones = 0;
    this.demorado.set(false);
    this.programarRevision();
  }

  /**
   * Mientras uno de la version vigente este en la cola, se vuelve a mirar la lista cada dos segundos, hasta
   * REVISIONES_DEL_MANUAL veces.
   */
  private programarRevision(): void {
    this.sondeo?.unsubscribe();
    this.sondeo = null;
    if (!this.enCurso()) {
      return;
    }
    if (this.revisiones >= REVISIONES_DEL_MANUAL) {
      this.demorado.set(true);
      return;
    }
    this.revisiones++;
    this.sondeo = timer(INTERVALO_DEL_MANUAL)
      .pipe(
        switchMap(() => this.servicio.listar(this.procesoId())),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (manuales: Manual[]) => this.alRevisar(manuales),
        error: (error: HttpErrorResponse) => this.error.set(mensajeDeError(error)),
      });
  }

  private alRevisar(manuales: Manual[]): void {
    const esperado = this.enCurso();
    this.manuales.set(manuales);
    const terminado = manuales.find((manual) => manual.id === esperado?.id && manual.estado !== 'PENDIENTE');
    if (terminado) {
      this.anuncio.set(
        terminado.estado === 'DISPONIBLE'
          ? $localize`The manual of version ${terminado.version} is ready to download.`
          : $localize`The manual of version ${terminado.version} could not be built: ${terminado.motivo ?? ''}`,
      );
    }
    this.programarRevision();
  }
}
