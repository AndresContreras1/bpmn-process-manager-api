import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnChanges, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, finalize, switchMap, timer } from 'rxjs';

import { abrirDescarga } from '../../helpers/archivos';
import { mensajeDeError } from '../../helpers/errores-api';
import { EnlaceDeDescarga } from '../../models/documento.model';
import { EstadoManual, FormatoManual, Manual, NOMBRE_ESTADO_MANUAL } from '../../models/documentacion.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { ManualService } from '../../service/manual.service';

/** Cada cuanto se vuelve a preguntar por un manual que la cola todavia arma. */
export const INTERVALO_DEL_MANUAL: number = 2000;

/**
 * PR F20: el manual de la version vigente. Se pide y la cola lo arma como HTML y PDF; mientras alguno este pendiente,
 * la lista se vuelve a pedir cada dos segundos, y lo que termina se anuncia en una region aria-live. Pedirlo es
 * exportar el proceso; bajarlo, solo verlo.
 */
@Component({
  selector: 'app-manual-proceso',
  imports: [FechaPipe],
  templateUrl: './manual-proceso.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ManualProcesoComponent implements OnChanges {
  readonly procesoId = input.required<number>();
  /** La version vigente; sin ella no hay manual que pedir. */
  readonly versionPublicada = input<number | null>(null);
  readonly puedePedir = input<boolean>(false);

  private readonly servicio = inject(ManualService);
  private readonly destroyRef = inject(DestroyRef);

  readonly nombreEstado: Record<EstadoManual, string> = NOMBRE_ESTADO_MANUAL;

  manuales: Manual[] = [];
  cargando: boolean = true;
  pidiendo: boolean = false;
  error: string | null = null;
  anuncio: string = '';
  private carga: Subscription | null = null;
  private sondeo: Subscription | null = null;

  ngOnChanges(): void {
    this.cargar();
  }

  cargar(): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.error = null;
    this.carga = this.servicio
      .listar(this.procesoId())
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (manuales: Manual[]) => {
          this.manuales = manuales;
          this.programarRevision();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** El de la version vigente, en el idioma de quien lo pide. */
  pedir(): void {
    if (this.pidiendo) {
      return;
    }
    this.pidiendo = true;
    this.error = null;
    this.servicio
      .pedir(this.procesoId())
      .pipe(
        finalize(() => (this.pidiendo = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (manual: Manual) => {
          this.manuales = [manual, ...this.manuales.filter((uno: Manual) => uno.id !== manual.id)];
          this.anuncio = $localize`The manual of version ${manual.version} is being built.`;
          this.programarRevision();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** Pide el enlace justo antes de usarlo: vale cinco minutos. */
  bajar(manual: Manual, formato: FormatoManual): void {
    this.error = null;
    this.servicio
      .enlace(manual.id, formato)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (enlace: EnlaceDeDescarga) => abrirDescarga(enlace.url),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private programarRevision(): void {
    this.sondeo?.unsubscribe();
    this.sondeo = null;
    if (!this.manuales.some((manual: Manual) => manual.estado === 'PENDIENTE')) {
      return;
    }
    this.sondeo = timer(INTERVALO_DEL_MANUAL)
      .pipe(
        switchMap(() => this.servicio.listar(this.procesoId())),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (manuales: Manual[]) => this.alRevisar(manuales),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private alRevisar(manuales: Manual[]): void {
    const antes = new Map<number, EstadoManual>(this.manuales.map((manual: Manual) => [manual.id, manual.estado]));
    const terminados: Manual[] = manuales.filter(
      (manual: Manual) => antes.get(manual.id) === 'PENDIENTE' && manual.estado !== 'PENDIENTE',
    );
    this.manuales = manuales;
    if (terminados.length > 0) {
      this.anuncio = terminados.map((manual: Manual) => anunciar(manual)).join(' ');
    }
    this.programarRevision();
  }
}

function anunciar(manual: Manual): string {
  return manual.estado === 'DISPONIBLE'
    ? $localize`The manual of version ${manual.version} is ready to download.`
    : $localize`The manual of version ${manual.version} could not be built: ${manual.motivo ?? ''}`;
}
