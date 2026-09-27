import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, catchError, finalize, forkJoin, of } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { ParClaveValor, aDatos } from '../../helpers/datos-libres';
import { mensajeDeError } from '../../helpers/errores-api';
import { ConfiguracionTienda, ModoSimulacion } from '../../models/configuracion.model';
import { Integracion, NOMBRE_INTEGRACION } from '../../models/diagrama.model';
import { Proceso } from '../../models/proceso.model';
import { PanelDeSimulacion, PedidosSimulados } from '../../models/simulacion.model';
import { AuthService } from '../../service/auth.service';
import { ConfiguracionService } from '../../service/configuracion.service';
import { ProcesoService } from '../../service/proceso.service';
import { SimulacionService } from '../../service/simulacion.service';

/** Lo que la pantalla necesita para dibujarse: donde va la simulacion y como estan puestos los socios. */
interface Estado {
  panel: PanelDeSimulacion;
  configuracion: ConfiguracionTienda | null;
}

/**
 * El panel de simulacion: el reloj de la tienda, el lote de pedidos del cliente simulado y como se portan los
 * socios. Solo un administrador entra: mover el reloj mueve todos los casos de la tienda a la vez.
 */
@Component({
  selector: 'app-simulacion',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, ErrorCampoComponent],
  templateUrl: './simulacion.component.html',
})
export class SimulacionComponent implements OnInit {
  private readonly simulacionService: SimulacionService = inject(SimulacionService);
  private readonly configuracionService: ConfiguracionService = inject(ConfiguracionService);
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly esAdministrador: boolean = inject(AuthService).esAdministrador();
  readonly nombreIntegracion: Record<Integracion, string> = NOMBRE_INTEGRACION;

  panel: PanelDeSimulacion | null = null;
  configuracion: ConfiguracionTienda | null = null;
  procesos: Proceso[] = [];
  /** El ultimo lote que se pidio, para contar lo que hizo el proceso con el. */
  pedidos: PedidosSimulados | null = null;
  cargando: boolean = true;
  moviendo: boolean = false;
  pidiendo: boolean = false;
  guardando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  readonly relojForm = new FormGroup({
    ticks: new FormControl<number>(1, [Validators.required, Validators.min(1), Validators.max(100)]),
  });

  readonly pedidosForm = new FormGroup({
    procesoId: new FormControl<number | null>(null, Validators.required),
    cantidad: new FormControl<number>(5, [Validators.required, Validators.min(1), Validators.max(200)]),
  });
  /** Lo que todos los pedidos del lote llevan igual. */
  plantilla: ParClaveValor[] = [];

  readonly sociosForm = new FormGroup({
    modoSimulacion: new FormControl<ModoSimulacion>('MANUAL', Validators.required),
    semilla: new FormControl<number>(42, [Validators.required, Validators.min(0)]),
    tasaRechazoPagos: new FormControl<number>(10, [Validators.required, Validators.min(0), Validators.max(100)]),
    ticksRespuestaPagos: new FormControl<number>(1, [Validators.required, Validators.min(1)]),
    reglaRechazoPagos: new FormControl('', Validators.maxLength(500)),
    ticksRespuestaTransporte: new FormControl<number>(1, [Validators.required, Validators.min(1)]),
    ticksEntrega: new FormControl<number>(3, [Validators.required, Validators.min(1)]),
    tasaPerdidaEnvios: new FormControl<number>(5, [Validators.required, Validators.min(0), Validators.max(100)]),
    tasaFalloNotificaciones: new FormControl<number>(2, [Validators.required, Validators.min(0), Validators.max(100)]),
  });

  ngOnInit(): void {
    if (!this.esAdministrador) {
      this.cargando = false;
      return;
    }
    this.procesoService
      .paraElegir()
      .pipe(
        catchError(() => of([] as Proceso[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((procesos: Proceso[]) => (this.procesos = procesos));
    this.pedir()
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (estado: Estado) => this.mostrar(estado),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** Mueve el reloj y entrega lo que ya toca; la respuesta es el panel de despues. */
  avanzar(): void {
    if (this.relojForm.invalid) {
      this.relojForm.markAllAsTouched();
      return;
    }
    const ticks: number = this.relojForm.getRawValue().ticks ?? 1;
    this.moviendo = true;
    this.error = null;
    this.aviso = null;
    this.simulacionService
      .tick(ticks)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (panel: PanelDeSimulacion) => {
          this.moviendo = false;
          this.panel = panel;
          this.aviso = `The clock is on tick ${panel.reloj}.`;
          // El reloj vive en la misma fila que la configuracion, asi que moverlo le cambia la version: sin volver
          // a leerla, guardar los socios despues respondería 409
          this.recargarConfiguracion();
        },
        error: (error: HttpErrorResponse) => {
          this.moviendo = false;
          this.error = mensajeDeError(error);
        },
      });
  }

  agregarDato(): void {
    this.plantilla = [...this.plantilla, { clave: '', valor: '' }];
  }

  quitarDato(indice: number): void {
    this.plantilla = this.plantilla.filter((_, posicion: number) => posicion !== indice);
  }

  /** Le pide al cliente simulado un lote: entran por la misma puerta que cualquier otro mensaje. */
  pedirPedidos(): void {
    if (this.pedidosForm.invalid) {
      this.pedidosForm.markAllAsTouched();
      return;
    }
    const valores = this.pedidosForm.getRawValue();
    if (valores.procesoId === null) {
      return;
    }
    this.pidiendo = true;
    this.error = null;
    this.aviso = null;
    this.pedidos = null;
    this.simulacionService
      .pedidos({
        procesoId: valores.procesoId,
        cantidad: valores.cantidad ?? 1,
        plantilla: aDatos(this.plantilla),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pedidos: PedidosSimulados) => {
          this.pidiendo = false;
          this.pedidos = pedidos;
          this.refrescarPanel();
        },
        error: (error: HttpErrorResponse) => {
          this.pidiendo = false;
          this.error = mensajeDeError(error);
        },
      });
  }

  /**
   * Guarda los socios enteros, que es como la API los toma, y con la politica de estructura tal como estaba: esta
   * pantalla no decide quien puede tocar la estructura de un diagrama, pero manda el mismo cuerpo.
   */
  guardarSocios(): void {
    const configuracion: ConfiguracionTienda | null = this.configuracion;
    if (!configuracion || this.sociosForm.invalid) {
      this.sociosForm.markAllAsTouched();
      return;
    }
    const valores = this.sociosForm.getRawValue();
    this.guardando = true;
    this.error = null;
    this.aviso = null;
    this.configuracionService
      .guardar({
        politicaEstructura: configuracion.politicaEstructura,
        modoSimulacion: valores.modoSimulacion,
        simulacion: {
          semilla: valores.semilla ?? 0,
          tasaRechazoPagos: valores.tasaRechazoPagos ?? 0,
          ticksRespuestaPagos: valores.ticksRespuestaPagos ?? 1,
          reglaRechazoPagos: valores.reglaRechazoPagos?.trim() ? valores.reglaRechazoPagos.trim() : null,
          ticksRespuestaTransporte: valores.ticksRespuestaTransporte ?? 1,
          ticksEntrega: valores.ticksEntrega ?? 1,
          tasaPerdidaEnvios: valores.tasaPerdidaEnvios ?? 0,
          tasaFalloNotificaciones: valores.tasaFalloNotificaciones ?? 0,
        },
        version: configuracion.version,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (guardada: ConfiguracionTienda) => {
          this.guardando = false;
          this.configuracion = guardada;
          this.llenarSocios(guardada);
          this.aviso = 'The partners answer like this from now on.';
          this.refrescarPanel();
        },
        error: (error: HttpErrorResponse) => {
          this.guardando = false;
          this.error = mensajeDeError(error);
        },
      });
  }

  private refrescarPanel(): void {
    this.simulacionService
      .panel()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (panel: PanelDeSimulacion) => (this.panel = panel),
        error: () => undefined,
      });
  }

  private recargarConfiguracion(): void {
    this.configuracionService
      .obtener()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (configuracion: ConfiguracionTienda) => {
          this.configuracion = configuracion;
          this.llenarSocios(configuracion);
        },
        error: () => undefined,
      });
  }

  private pedir(): Observable<Estado> {
    return forkJoin({
      panel: this.simulacionService.panel(),
      // Si la configuracion no se puede leer, el reloj y los pedidos siguen funcionando: los socios no
      configuracion: this.configuracionService.obtener().pipe(catchError(() => of(null))),
    });
  }

  private mostrar(estado: Estado): void {
    this.panel = estado.panel;
    this.configuracion = estado.configuracion;
    if (estado.configuracion) {
      this.llenarSocios(estado.configuracion);
    }
  }

  private llenarSocios(configuracion: ConfiguracionTienda): void {
    this.sociosForm.setValue({
      modoSimulacion: configuracion.modoSimulacion,
      semilla: configuracion.simulacion.semilla,
      tasaRechazoPagos: configuracion.simulacion.tasaRechazoPagos,
      ticksRespuestaPagos: configuracion.simulacion.ticksRespuestaPagos,
      reglaRechazoPagos: configuracion.simulacion.reglaRechazoPagos ?? '',
      ticksRespuestaTransporte: configuracion.simulacion.ticksRespuestaTransporte,
      ticksEntrega: configuracion.simulacion.ticksEntrega,
      tasaPerdidaEnvios: configuracion.simulacion.tasaPerdidaEnvios,
      tasaFalloNotificaciones: configuracion.simulacion.tasaFalloNotificaciones,
    });
  }
}
