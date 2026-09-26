import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  debounceTime,
  finalize,
  forkJoin,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';

import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
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
import { Diagnostico, Hallazgo, Revision, TipoElemento } from '../../models/diagnostico.model';
import { Proceso } from '../../models/proceso.model';
import { RolProceso } from '../../models/rol-proceso.model';
import { ActividadService } from '../../service/actividad.service';
import { ArcoService } from '../../service/arco.service';
import { AuthService } from '../../service/auth.service';
import { DiagnosticoService } from '../../service/diagnostico.service';
import { DiagramaService } from '../../service/diagrama.service';
import { EventoService } from '../../service/evento.service';
import { GatewayService } from '../../service/gateway.service';
import { LaneService } from '../../service/lane.service';
import { MensajeService } from '../../service/mensaje.service';
import { PoolService } from '../../service/pool.service';
import { ProcesoService } from '../../service/proceso.service';
import { RevisionService } from '../../service/revision.service';
import { RolProcesoService } from '../../service/rol-proceso.service';
import {
  DiagramaBpmnComponent,
  MovimientoDeNodo,
} from '../proceso-detalle/components/diagrama-bpmn/diagrama-bpmn.component';
import { Lienzo, dibujarDiagrama } from '../proceso-detalle/components/diagrama-bpmn/lienzo';
import { PanelElementoComponent } from './components/panel-elemento/panel-elemento.component';
import {
  NOMBRE_ELEMENTO,
  Seleccion,
  elementoEnIngles,
  esNodo,
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
  private readonly revisionService: RevisionService = inject(RevisionService);
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

  /** Mientras se conecta, un clic en un nodo no lo elige: marca el origen y despues el destino. */
  conectando: boolean = false;
  origenConexion: number | null = null;

  publicando: boolean = false;
  publicado: string | null = null;
  revisando: boolean = false;
  revision: Revision | null = null;
  errorRevision: string | null = null;

  /** Un diagrama con errores no se publica; las advertencias no lo impiden. */
  get sePuedePublicar(): boolean {
    return this.diagnostico !== null && this.diagnostico.errores === 0;
  }

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
    if (this.conectando) {
      this.conectar(id);
      return;
    }
    const tipo = tipoDelNodo(this.diagrama, id);
    this.elegir(this.seleccion?.id === id ? null : { tipo, id });
  }

  /** Entra y sale del modo de conectar. Al salir se olvida el origen a medias. */
  alternarConexion(): void {
    this.conectando = !this.conectando;
    this.origenConexion = null;
    this.error = null;
  }

  /**
   * Dos nodos del mismo participante se unen con un flujo de secuencia; dos de participantes distintos, con un
   * flujo de mensaje, porque un arco nunca cruza de un pool a otro.
   */
  private conectar(id: number): void {
    if (this.origenConexion === null) {
      this.origenConexion = id;
      return;
    }
    const origen: number = this.origenConexion;
    this.origenConexion = null;
    this.conectando = false;
    if (origen === id) {
      this.error = 'A flow needs two different nodes.';
      return;
    }
    const poolOrigen: number | null = this.poolDelNodo(origen);
    const poolDestino: number | null = this.poolDelNodo(id);
    if (poolOrigen === null || poolDestino === null) {
      return;
    }
    if (poolOrigen === poolDestino) {
      this.crear(
        this.arcoService.crear({
          origenId: origen,
          destinoId: id,
          etiqueta: null,
          condicion: null,
          porDefecto: false,
          orden: 0,
        }),
        'ARCO',
      );
      return;
    }
    this.crear(
      this.mensajeService.crear(this.procesoId, {
        nombre: this.nombreLibre('New message'),
        contenido: '',
        poolOrigenId: poolOrigen,
        poolDestinoId: poolDestino,
        nodoOrigenId: origen,
        nodoDestinoId: id,
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

  /**
   * Guarda donde se solto un nodo. Va con la version que tiene ahora, asi que si alguien lo movio entre medias la
   * API responde 409 y el diagrama se recarga con lo que hay de verdad.
   */
  moverNodo(movimiento: MovimientoDeNodo): void {
    const diagrama: Diagrama | null = this.diagrama;
    if (!diagrama || !this.puedeEditar) {
      return;
    }
    const actividad = diagrama.actividades.find((candidata) => candidata.id === movimiento.id);
    const gateway = diagrama.gateways.find((candidato) => candidato.id === movimiento.id);
    const evento = diagrama.eventos.find((candidato) => candidato.id === movimiento.id);
    const comun = {
      laneId: movimiento.laneId,
      posicionX: movimiento.posicionX,
      posicionY: movimiento.posicionY,
    };
    let peticion$: Observable<unknown> | null = null;
    if (actividad) {
      peticion$ = this.actividadService.editar(actividad.id, {
        ...comun,
        nombre: actividad.nombre,
        descripcion: actividad.descripcion,
        tipoActividad: actividad.tipoActividad,
        version: actividad.version,
      });
    } else if (gateway) {
      peticion$ = this.gatewayService.editar(gateway.id, {
        ...comun,
        nombre: gateway.nombre,
        tipoGateway: gateway.tipoGateway,
        version: gateway.version,
      });
    } else if (evento) {
      peticion$ = this.eventoService.editar(evento.id, {
        ...comun,
        nombre: evento.nombre,
        tipoEvento: evento.tipoEvento,
        version: evento.version,
      });
    }
    peticion$?.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.refrescar(),
      error: (error: HttpErrorResponse) => {
        this.error = mensajeDeError(error);
        // Se recarga igual: el nodo esta dibujado donde se solto y en la API sigue donde estaba
        this.refrescar();
      },
    });
  }

  /**
   * Publica el diagrama como una version nueva. El diagnostico ya dice si se puede: los errores lo impiden y las
   * advertencias no, asi que el boton no esta para averiguarlo, sino para hacerlo.
   */
  publicar(): void {
    const proceso: Proceso | null = this.proceso;
    if (!proceso) {
      return;
    }
    this.publicando = true;
    this.publicado = null;
    this.error = null;
    this.procesoService
      .publicar(proceso.id, proceso.version)
      .pipe(
        finalize(() => (this.publicando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (publicado: Proceso) => {
          this.publicado =
            publicado.versionPublicada === null
              ? 'The process is published.'
              : `Published as version ${publicado.versionPublicada}.`;
          this.refrescar();
        },
        error: (error: HttpErrorResponse) => {
          this.error = mensajeDeError(error);
          this.refrescar();
        },
      });
  }

  /**
   * La segunda opinion. Es consejo y no cambia nada, cuesta una llamada al modelo y la API la limita por tienda,
   * asi que cada respuesta que no sea un 200 dice algo distinto y hay que contarlo, no esconderlo.
   */
  revisar(): void {
    this.revisando = true;
    this.errorRevision = null;
    this.revision = null;
    this.revisionService
      .revisar(this.procesoId)
      .pipe(
        finalize(() => (this.revisando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (revision: Revision) => (this.revision = revision),
        error: (error: HttpErrorResponse) => {
          this.errorRevision =
            error.status === 503
              ? 'The AI review is not set up in this installation.'
              : error.status === 429
                ? 'Your store has asked for too many reviews. Try again later.'
                : mensajeDeError(error);
        },
      });
  }

  /** Sube o baja una lane dentro de su pool y manda el orden entero, que es lo que la API espera. */
  moverLane(lane: Lane, direccion: number): void {
    const diagrama: Diagrama | null = this.diagrama;
    if (!diagrama) {
      return;
    }
    const hermanas: Lane[] = diagrama.lanes
      .filter((candidata) => candidata.poolId === lane.poolId)
      .sort((a, b) => a.orden - b.orden);
    const desde: number = hermanas.findIndex((candidata) => candidata.id === lane.id);
    const hasta: number = desde + direccion;
    if (hasta < 0 || hasta >= hermanas.length) {
      return;
    }
    const ids: number[] = hermanas.map((candidata) => candidata.id);
    [ids[desde], ids[hasta]] = [ids[hasta], ids[desde]];
    this.laneService
      .ordenar(lane.poolId, { ids })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.refrescar(),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private poolDelNodo(id: number): number | null {
    const diagrama: Diagrama = this.diagrama as Diagrama;
    const nodo = [...diagrama.actividades, ...diagrama.gateways, ...diagrama.eventos].find(
      (candidato) => candidato.id === id,
    );
    return diagrama.lanes.find((lane) => lane.id === nodo?.laneId)?.poolId ?? null;
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
    return esNodo(seleccion.tipo) ? (nodo?.laneId ?? null) : null;
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
    return this.seleccion && esNodo(this.seleccion.tipo) ? this.seleccion.id : null;
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
      map(
        (respuesta): [Proceso, Diagrama, Diagnostico | null] => [respuesta[0].proceso, respuesta[1], respuesta[2]],
      ),
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
