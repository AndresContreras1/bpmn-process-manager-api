import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, debounceTime, forkJoin, of, startWith, switchMap } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import {
  Diagrama,
  Lane,
  NOMBRE_ACTIVIDAD,
  NOMBRE_EVENTO,
  NOMBRE_GATEWAY,
  Pool,
  TipoActividad,
  TipoEvento,
  TipoGateway,
} from '../../models/diagrama.model';
import { Diagnostico, Hallazgo, TipoElemento } from '../../models/diagnostico.model';
import { Proceso } from '../../models/proceso.model';
import { RolProceso } from '../../models/rol-proceso.model';
import { AuthService } from '../../service/auth.service';
import { ActividadService } from '../../service/actividad.service';
import { ArcoService } from '../../service/arco.service';
import { DiagnosticoService } from '../../service/diagnostico.service';
import { EventoService } from '../../service/evento.service';
import { GatewayService } from '../../service/gateway.service';
import { LaneService } from '../../service/lane.service';
import { MensajeService } from '../../service/mensaje.service';
import { PoolService } from '../../service/pool.service';
import { DiagramaService } from '../../service/diagrama.service';
import { ProcesoService } from '../../service/proceso.service';
import { RolProcesoService } from '../../service/rol-proceso.service';
import { DiagramaBpmnComponent } from '../proceso-detalle/components/diagrama-bpmn/diagrama-bpmn.component';
import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { PanelElementoComponent } from './components/panel-elemento/panel-elemento.component';
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
  imports: [FormsModule, RouterLink, DiagramaBpmnComponent, ModalConfirmarComponent, PanelElementoComponent],
  templateUrl: './proceso-editor.component.html',
  styleUrl: './proceso-editor.component.scss',
})
export class ProcesoEditorComponent implements OnInit {
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly diagramaService: DiagramaService = inject(DiagramaService);
  private readonly diagnosticoService: DiagnosticoService = inject(DiagnosticoService);
  private readonly rolProcesoService: RolProcesoService = inject(RolProcesoService);
  private readonly poolService: PoolService = inject(PoolService);
  private readonly laneService: LaneService = inject(LaneService);
  private readonly actividadService: ActividadService = inject(ActividadService);
  private readonly arcoService: ArcoService = inject(ArcoService);
  private readonly gatewayService: GatewayService = inject(GatewayService);
  private readonly eventoService: EventoService = inject(EventoService);
  private readonly mensajeService: MensajeService = inject(MensajeService);
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
  roles: RolProceso[] = [];
  seleccion: Seleccion | null = null;
  nombreElegido: string = '';
  cargando: boolean = true;
  noEncontrado: boolean = false;
  error: string | null = null;

  // Lo que la paleta va a crear cuando se pulse su boton
  readonly nombreActividad = NOMBRE_ACTIVIDAD;
  readonly nombreGateway = NOMBRE_GATEWAY;
  readonly nombreEvento = NOMBRE_EVENTO;
  readonly tiposActividad = Object.keys(NOMBRE_ACTIVIDAD) as TipoActividad[];
  readonly tiposGateway = Object.keys(NOMBRE_GATEWAY) as TipoGateway[];
  readonly tiposEvento = Object.keys(NOMBRE_EVENTO) as TipoEvento[];
  nuevaActividad: TipoActividad = 'USUARIO';
  nuevoGateway: TipoGateway = 'EXCLUSIVO';
  nuevoEvento: TipoEvento = 'INICIO';
  mensajeOrigen: number | null = null;
  mensajeDestino: number | null = null;

  /** Lo que el modal de borrado dice que pasaria; se llena cuando responde el diagnostico simulado. */
  impacto: string = '';

  // Cada cambio confirmado por la API emite aqui; el debounce junta los que llegan seguidos
  private readonly cambios$ = new Subject<void>();

  ngOnInit(): void {
    this.procesoId = Number(this.route.snapshot.paramMap.get('id'));
    // Los roles no cambian mientras se modela, asi que se piden una vez y no en cada recarga
    this.rolProcesoService
      .listar()
      .pipe(
        catchError(() => of([] as RolProceso[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((roles: RolProceso[]) => (this.roles = roles));
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

  // ------------------------------------------------------------------ crear

  /** Un participante nuevo nace como caja negra: lo normal es que sea alguien de fuera al que no se modela. */
  agregarPool(): void {
    this.crear(
      this.poolService.crear(this.procesoId, {
        nombre: this.nombreLibre('New participant'),
        tipoParticipante: 'CLIENTE',
        cajaNegra: true,
        integracion: 'NINGUNA',
      }),
      'POOL',
    );
  }

  /** Una lane cuelga del participante elegido, y necesita un rol: sin roles no hay a quien darle el trabajo. */
  agregarLane(): void {
    const poolId: number | null = this.poolDelContexto();
    if (poolId === null) {
      this.error = 'Pick a participant first: a lane belongs to one.';
      return;
    }
    if (this.roles.length === 0) {
      this.error = 'Your store has no process roles yet, and a lane needs one.';
      return;
    }
    this.crear(
      this.laneService.crear(poolId, { nombre: this.nombreLibre('New lane'), rolProcesoId: this.roles[0].id }),
      'LANE',
    );
  }

  agregarActividad(): void {
    const lane: number | null = this.laneDelContexto();
    if (lane === null) {
      return;
    }
    const sitio = this.sitioLibre(lane);
    this.crear(
      this.actividadService.crear(lane, {
        nombre: this.nombreLibre('New task'),
        descripcion: null,
        tipoActividad: this.nuevaActividad,
        posicionX: sitio.x,
        posicionY: sitio.y,
      }),
      'ACTIVIDAD',
    );
  }

  agregarGateway(): void {
    const lane: number | null = this.laneDelContexto();
    if (lane === null) {
      return;
    }
    const sitio = this.sitioLibre(lane);
    this.crear(
      this.gatewayService.crear(lane, {
        nombre: this.nombreLibre('New gateway'),
        tipoGateway: this.nuevoGateway,
        posicionX: sitio.x,
        posicionY: sitio.y,
      }),
      'GATEWAY',
    );
  }

  agregarEvento(): void {
    const lane: number | null = this.laneDelContexto();
    if (lane === null) {
      return;
    }
    const sitio = this.sitioLibre(lane);
    this.crear(
      this.eventoService.crear(lane, {
        nombre: this.nombreLibre('New event'),
        tipoEvento: this.nuevoEvento,
        posicionX: sitio.x,
        posicionY: sitio.y,
      }),
      'EVENTO',
    );
  }

  /** Un mensaje va de un participante a otro, asi que lo primero que hay que decir son esos dos. */
  agregarMensaje(): void {
    if (this.mensajeOrigen === null || this.mensajeDestino === null || this.mensajeOrigen === this.mensajeDestino) {
      this.error = 'A message flow goes between two different participants.';
      return;
    }
    this.crear(
      this.mensajeService.crear(this.procesoId, {
        nombre: this.nombreLibre('New message'),
        contenido: '',
        poolOrigenId: this.mensajeOrigen,
        poolDestinoId: this.mensajeDestino,
        nodoOrigenId: null,
        nodoDestinoId: null,
        tipoDestino: null,
        siFalla: null,
        nodoManejoErrorId: null,
        origenExterno: false,
        campos: [],
        usoDeLosDatos: null,
        variable: null,
        respuestaEsperadaId: null,
      }),
      'MENSAJE',
    );
  }

  // ------------------------------------------------------------------ borrar

  /**
   * Antes de confirmar un borrado se pide el diagnostico del diagrama que quedaria sin ese elemento. Borrar un
   * nodo se lleva sus flujos por delante, asi que la cuenta de errores puede subir mucho mas de lo que parece.
   */
  prepararBorrado(): void {
    const seleccion: Seleccion | null = this.seleccion;
    if (!seleccion) {
      return;
    }
    this.impacto = 'Working out what would be left…';
    const antes: number = this.diagnostico?.errores ?? 0;
    this.diagnosticoService
      .sinElemento(this.procesoId, seleccion.tipo, seleccion.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (despues: Diagnostico) => {
          const nombre: string = this.nombreElegido;
          this.impacto =
            despues.errores === antes
              ? `Deleting "${nombre}" leaves the diagram with the same ${antes} errors it has now.`
              : `Deleting "${nombre}" takes the diagram from ${antes} to ${despues.errores} errors.`;
        },
        error: () => (this.impacto = 'The impact could not be worked out. Deleting cannot be undone.'),
      });
  }

  confirmarBorrado(): void {
    const seleccion: Seleccion | null = this.seleccion;
    if (!seleccion) {
      return;
    }
    const borrado$: Observable<void> = this.borradoDe(seleccion);
    borrado$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.seleccion = null;
        this.refrescar();
      },
      error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
    });
  }

  private borradoDe(seleccion: Seleccion): Observable<void> {
    switch (seleccion.tipo) {
      case 'POOL':
        return this.poolService.eliminar(seleccion.id);
      case 'LANE':
        return this.laneService.eliminar(seleccion.id);
      case 'ACTIVIDAD':
        return this.actividadService.eliminar(seleccion.id);
      case 'GATEWAY':
        return this.gatewayService.eliminar(seleccion.id);
      case 'EVENTO':
        return this.eventoService.eliminar(seleccion.id);
      case 'MENSAJE':
        return this.mensajeService.eliminar(seleccion.id);
      case 'ARCO':
        return this.arcoService.eliminar(seleccion.id);
    }
  }

  // ------------------------------------------------------------------ ayudas

  /** Crea el elemento, lo deja elegido y recarga: asi el panel se abre sobre lo que se acaba de agregar. */
  private crear(peticion$: Observable<{ id: number }>, tipo: TipoElemento): void {
    this.error = null;
    peticion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (creado: { id: number }) => {
        this.seleccion = { tipo, id: creado.id };
        this.refrescar();
      },
      error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
    });
  }

  /** El pool sobre el que se esta trabajando: el elegido, o el del lane o el nodo elegido. */
  private poolDelContexto(): number | null {
    const lane: number | null = this.laneDelContextoSinQuejarse();
    if (this.seleccion?.tipo === 'POOL') {
      return this.seleccion.id;
    }
    return this.diagrama?.lanes.find((candidata) => candidata.id === lane)?.poolId ?? null;
  }

  /** El lane sobre el que se trabaja. Sin uno no se puede agregar un nodo, y se dice en vez de fallar callado. */
  private laneDelContexto(): number | null {
    const lane: number | null = this.laneDelContextoSinQuejarse();
    if (lane === null) {
      this.error = 'Pick a lane first: tasks, gateways and events live inside one.';
    }
    return lane;
  }

  private laneDelContextoSinQuejarse(): number | null {
    const seleccion: Seleccion | null = this.seleccion;
    const diagrama: Diagrama | null = this.diagrama;
    if (!seleccion || !diagrama) {
      return null;
    }
    if (seleccion.tipo === 'LANE') {
      return seleccion.id;
    }
    const nodo = [...diagrama.actividades, ...diagrama.gateways, ...diagrama.eventos].find(
      (candidato) => candidato.id === seleccion.id,
    );
    return ['ACTIVIDAD', 'GATEWAY', 'EVENTO'].includes(seleccion.tipo) ? (nodo?.laneId ?? null) : null;
  }

  /** A la derecha de lo que ya hay en ese lane, para que lo nuevo no caiga encima de nada. */
  private sitioLibre(laneId: number): { x: number; y: number } {
    const diagrama: Diagrama = this.diagrama as Diagrama;
    const enElLane = [...diagrama.actividades, ...diagrama.gateways, ...diagrama.eventos].filter(
      (nodo) => nodo.laneId === laneId,
    );
    const x: number = enElLane.length ? Math.max(...enElLane.map((nodo) => nodo.posicionX)) + 160 : 20;
    return { x, y: enElLane.length ? enElLane[0].posicionY : 80 };
  }

  /** Un nombre que no repita otro del proceso: la API no deja dos nodos con el mismo nombre. */
  private nombreLibre(base: string): string {
    const diagrama: Diagrama | null = this.diagrama;
    if (!diagrama) {
      return base;
    }
    const usados: string[] = [
      ...diagrama.pools,
      ...diagrama.lanes,
      ...diagrama.actividades,
      ...diagrama.gateways,
      ...diagrama.eventos,
      ...diagrama.mensajes,
    ].map((elemento) => elemento.nombre.toLowerCase());
    if (!usados.includes(base.toLowerCase())) {
      return base;
    }
    let numero = 2;
    while (usados.includes(`${base} ${numero}`.toLowerCase())) {
      numero += 1;
    }
    return `${base} ${numero}`;
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
