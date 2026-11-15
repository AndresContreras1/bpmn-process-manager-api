import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnChanges, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription, finalize } from 'rxjs';

import { esConflictoDeVersion, marcarErroresDelServidor, mensajeDeError } from '../../helpers/errores-api';
import {
  CAMPOS_CARACTERIZACION,
  CampoCaracterizacion,
  Caracterizacion,
  LARGO_CAMPO_CARACTERIZACION,
  NOMBRE_CAMPO_CARACTERIZACION,
} from '../../models/documentacion.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { DocumentacionService } from '../../service/documentacion.service';

type Controles = Record<CampoCaracterizacion, FormControl<string>>;

function controles(): Controles {
  const resultado: Partial<Controles> = {};
  for (const campo of CAMPOS_CARACTERIZACION) {
    resultado[campo] = new FormControl<string>('', {
      nonNullable: true,
      validators: [Validators.maxLength(LARGO_CAMPO_CARACTERIZACION)],
    });
  }
  return resultado as Controles;
}

/**
 * PR F20: la ficha de caracterizacion de un proceso, sus nueve campos de texto libre. Se guarda entera con la version
 * leida: si otra persona la guardo antes, la API responde 409 y lo escrito se queda en pantalla hasta que se recargue.
 */
@Component({
  selector: 'app-ficha-caracterizacion',
  imports: [ReactiveFormsModule, FechaPipe],
  templateUrl: './ficha-caracterizacion.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class FichaCaracterizacionComponent implements OnChanges {
  readonly procesoId = input.required<number>();
  readonly soloLectura = input<boolean>(false);

  private readonly servicio = inject(DocumentacionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly campos: readonly CampoCaracterizacion[] = CAMPOS_CARACTERIZACION;
  readonly nombreCampo: Record<CampoCaracterizacion, string> = NOMBRE_CAMPO_CARACTERIZACION;
  readonly formulario = new FormGroup(controles());

  /** La que se leyo; su version es la que viaja al guardar. */
  ficha: Caracterizacion | null = null;
  cargando: boolean = true;
  guardando: boolean = false;
  conflicto: boolean = false;
  error: string | null = null;
  aviso: string | null = null;
  private carga: Subscription | null = null;

  /** Si nadie la escribio, no hay nada que leer: quien solo mira lo ve dicho asi. */
  get vacia(): boolean {
    return this.campos.every((campo: CampoCaracterizacion) => !this.ficha?.[campo]);
  }

  ngOnChanges(): void {
    this.cargar();
  }

  /** Lo del servidor, reemplazando lo escrito. */
  cargar(): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.conflicto = false;
    this.error = null;
    this.aviso = null;
    this.carga = this.servicio
      .caracterizacion(this.procesoId())
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (ficha: Caracterizacion) => this.mostrar(ficha),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  guardar(): void {
    if (this.formulario.invalid || this.guardando) {
      return;
    }
    this.guardando = true;
    this.error = null;
    this.aviso = null;
    this.servicio
      .guardarCaracterizacion(this.procesoId(), {
        ...this.formulario.getRawValue(),
        version: this.ficha?.version ?? null,
      })
      .pipe(
        finalize(() => (this.guardando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (ficha: Caracterizacion) => {
          this.mostrar(ficha);
          this.aviso = $localize`Characterization sheet saved.`;
        },
        error: (error: HttpErrorResponse) => {
          if (esConflictoDeVersion(error)) {
            this.conflicto = true;
            return;
          }
          marcarErroresDelServidor(this.formulario, error);
          this.error = mensajeDeError(error);
        },
      });
  }

  private mostrar(ficha: Caracterizacion): void {
    this.ficha = ficha;
    const valores: Partial<Record<CampoCaracterizacion, string>> = {};
    for (const campo of this.campos) {
      valores[campo] = ficha[campo] ?? '';
    }
    this.formulario.reset(valores);
  }
}
