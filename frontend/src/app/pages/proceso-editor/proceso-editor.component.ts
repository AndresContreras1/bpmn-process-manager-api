import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, debounceTime, forkJoin, of, startWith, switchMap } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import { Diagrama, Lane, Pool } from '../../models/diagrama.model';
import { Diagnostico, Hallazgo, TipoElemento } from '../../models/diagnostico.model';
import { Proceso } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { DiagnosticoService } from '../../service/diagnostico.service';
import { DiagramaService } from '../../service/diagrama.service';
import { ProcesoService } from '../../service/proceso.service';
import { DiagramaBpmnComponent } from '../proceso-detalle/components/diagrama-bpmn/diagrama-bpmn.component';
import { Lienzo, dibujarDiagrama } from '../proceso-detalle/components/diagrama-bpmn/lienzo';
import {
  NOMBRE_ELEMENTO,
  Seleccion,
  elementoEnIngles,
  nombreDeSeleccion,
  seleccionDelHallazgo,
  tipoDelNodo,
} from './seleccion';

/** Un pool con sus carriles, para el esquema de la izquierda. */
interface RamaDelEsquema {
  pool: Pool;
  lanes: Lane[];
}

/**
 * El editor del diagrama. Lo que se ve es el mismo lienzo del visor, porque el dibujo tiene que ser el mismo; lo
 * que cambia es que aqui se elige un elemento y se trabaja sobre el.
 *
 * Despues de cada respuesta de la API se vuelven a pedir el diagrama y el diagnostico juntos: el diagnostico es
 * determinista y barato, y asi la lista de errores nunca habla de un diagrama que ya no existe.
 */
@Component({
  selector: 'app-proceso-editor',
  imports: [RouterLink, DiagramaBpmnComponent],
  templateUrl: './proceso-editor.component.html',
  styleUrl: './proceso-editor.component.scss',
})
export class ProcesoEditorComponent implements OnInit {
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly diagramaService: DiagramaService = inject(DiagramaService);
  private readonly diagnosticoService: DiagnosticoService = inject(DiagnosticoService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly puedeEditar: boolean = inject(AuthService).puedeEditar();
  readonly nombreElemento: Record<TipoElemento, string> = NOMBRE_ELEMENTO;
  readonly elementoEnIngles: (elemento: string | null) => string | null = elementoEnIngles;

  procesoId: number = 0;
  proceso: Proceso | null = null;
  diagrama: Diagrama | null = null;
  lienzo: Lienzo | null = null;
  diagnostico: Diagnostico | null = null;
  esquema: RamaDelEsquema[] = [];
  seleccion: Seleccion | null = null;
  nombreElegido: string = '';
  cargando: boolean = true;
  noEncontrado: boolean = false;
  error: string | null = null;

  // Cada cambio confirmado por la API emite aqui; el debounce junta los que llegan seguidos
  private readonly cambios$ = new Subject<void>();

  ngOnInit(): void {
    this.procesoId = Number(this.route.snapshot.paramMap.get('id'));
    this.cambios$
      .pipe(
        startWith(undefined),
        debounceTime(400),
        switchMap(() => this.cargar()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((carga: [Proceso, Diagrama, Diagnostico | null]) => {
        const [proceso, diagrama, diagnostico] = carga;
        this.proceso = proceso;
        this.diagrama = diagrama;
        this.lienzo = dibujarDiagrama(diagrama);
        this.diagnostico = diagnostico;
        this.esquema = diagrama.pools.map((pool: Pool) => ({
          pool,
          lanes: diagrama.lanes.filter((lane: Lane) => lane.poolId === pool.id),
        }));
        // Lo elegido puede haber desaparecido con el ultimo cambio
        this.refrescarSeleccion();
        this.cargando = false;
      });
  }

  /** Vuelve a pedirlo todo. La llaman los paneles cuando la API confirma un cambio. */
  refrescar(): void {
    this.cambios$.next();
  }

  elegir(seleccion: Seleccion | null): void {
    this.seleccion = seleccion;
    this.refrescarSeleccion();
  }

  /** El lienzo solo sabe el id del nodo; el tipo sale de en que lista esta. */
  elegirNodo(id: number): void {
    if (!this.diagrama) {
      return;
    }
    const tipo = tipoDelNodo(this.diagrama, id);
    this.elegir(this.seleccion?.id === id ? null : { tipo, id });
  }

  /** Salta al elemento del que habla un hallazgo. Los que son del proceso entero no llevan a ninguna parte. */
  irAlHallazgo(hallazgo: Hallazgo): void {
    const destino: Seleccion | null = seleccionDelHallazgo(hallazgo.elemento, hallazgo.elementoId);
    if (destino) {
      this.elegir(destino);
    }
  }

  /** El id del nodo elegido, o null si lo elegido no es un nodo: es lo que el lienzo sabe resaltar. */
  get nodoElegidoId(): number | null {
    return this.seleccion && ['ACTIVIDAD', 'GATEWAY', 'EVENTO'].includes(this.seleccion.tipo)
      ? this.seleccion.id
      : null;
  }

  private refrescarSeleccion(): void {
    if (!this.diagrama || !this.seleccion) {
      this.nombreElegido = '';
      return;
    }
    const nombre: string = nombreDeSeleccion(this.diagrama, this.seleccion);
    if (nombre === '') {
      // Se borro, o lo borro otra persona: se suelta en vez de dejar un panel de algo que ya no esta
      this.seleccion = null;
    }
    this.nombreElegido = nombre;
  }

  /**
   * El proceso, su diagrama y su diagnostico. El diagnostico se deja en null si falla, porque un diagrama que no
   * se puede diagnosticar se sigue pudiendo editar, y es justo cuando mas falta hace.
   */
  private cargar(): Observable<[Proceso, Diagrama, Diagnostico | null]> {
    return forkJoin([
      this.procesoService.obtener(this.procesoId),
      this.diagramaService.obtener(this.procesoId),
      this.diagnosticoService.obtener(this.procesoId).pipe(catchError(() => of(null))),
    ]).pipe(
      switchMap((respuesta) => of([respuesta[0].proceso, respuesta[1], respuesta[2]] as [Proceso, Diagrama, Diagnostico | null])),
      catchError((error: HttpErrorResponse) => {
        this.cargando = false;
        if (error.status === 404 || error.status === 400) {
          this.noEncontrado = true;
        } else {
          this.error = mensajeDeError(error);
        }
        return EMPTY;
      }),
    );
  }
}
