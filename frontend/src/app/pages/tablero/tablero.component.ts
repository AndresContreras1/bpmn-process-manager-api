import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, of, switchMap, tap } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import { COLOR_ESTADO_CASO, EstadoCaso, NOMBRE_ESTADO_CASO } from '../../models/caso.model';
import {
  EstadoMensajeSaliente,
  NOMBRE_ESTADO_SALIENTE,
  NOMBRE_RESULTADO,
  ResultadoCorrelacion,
} from '../../models/mensajeria.model';
import { Proceso } from '../../models/proceso.model';
import { Tablero } from '../../models/tablero.model';
import { ProcesoService } from '../../service/proceso.service';
import { TableroService } from '../../service/tablero.service';

/**
 * Como va la operacion: los mismos numeros para la tienda entera o para un proceso, que es lo que la API contesta
 * en los dos casos. Se elige de que se habla y la pantalla no cambia de forma, solo de datos.
 */
@Component({
  selector: 'app-tablero',
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './tablero.component.html',
})
export class TableroComponent implements OnInit {
  private readonly tableroService: TableroService = inject(TableroService);
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly nombreEstado: Record<EstadoCaso, string> = NOMBRE_ESTADO_CASO;
  readonly colorEstado: Record<EstadoCaso, string> = COLOR_ESTADO_CASO;
  readonly nombreEstadoSaliente: Record<EstadoMensajeSaliente, string> = NOMBRE_ESTADO_SALIENTE;
  readonly nombreResultado: Record<ResultadoCorrelacion, string> = NOMBRE_RESULTADO;

  private readonly consulta$ = new Subject<void>();

  procesos: Proceso[] = [];
  /** De que se habla: la tienda entera, o el proceso elegido. */
  procesoId: number | '' = '';
  tablero: Tablero | null = null;
  cargando: boolean = true;
  error: string | null = null;

  ngOnInit(): void {
    const proceso: string | null = this.route.snapshot.queryParamMap.get('procesoId');
    if (proceso !== null && !Number.isNaN(Number(proceso))) {
      this.procesoId = Number(proceso);
    }
    this.procesoService
      .paraElegir()
      .pipe(
        catchError(() => of([] as Proceso[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((procesos: Proceso[]) => (this.procesos = procesos));
    this.consulta$
      .pipe(
        tap(() => {
          this.cargando = true;
          this.error = null;
        }),
        switchMap(() =>
          this.tableroService.obtener(this.procesoId === '' ? null : this.procesoId).pipe(
            catchError((error: HttpErrorResponse) => {
              this.error = mensajeDeError(error);
              this.cargando = false;
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((tablero: Tablero) => {
        this.tablero = tablero;
        this.cargando = false;
      });
    this.pedir();
  }

  pedir(): void {
    this.consulta$.next();
  }

  /** El nombre de lo que se esta mirando, para el encabezado. */
  get mirando(): string {
    const proceso: Proceso | undefined = this.procesos.find((candidato) => candidato.id === this.procesoId);
    return proceso?.nombre ?? 'the whole store';
  }

  /** Los casos de lo que se esta mirando, para el enlace que los lista. */
  get filtroDeCasos(): Record<string, number> {
    return this.procesoId === '' ? {} : { procesoId: this.procesoId };
  }

  get algoSalioMal(): boolean {
    const mal = this.tablero?.loQueSalioMal;
    return mal !== undefined && mal.enviosFallidos + mal.sinCamino + mal.variablesAusentes > 0;
  }
}
