import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, output } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, finalize } from 'rxjs';

import { ErrorCampoComponent } from '../../../../components/error-campo/error-campo.component';
import { esConflictoDeVersion, marcarErroresDelServidor, mensajeDeError } from '../../../../helpers/errores-api';
import {
  Actividad,
  Arco,
  CampoDeMensaje,
  Correlacion,
  Diagrama,
  Evento,
  Gateway,
  Integracion,
  Lane,
  Mensaje,
  NOMBRE_ACTIVIDAD,
  NOMBRE_EVENTO,
  NOMBRE_GATEWAY,
  NOMBRE_INTEGRACION,
  NOMBRE_PARTICIPANTE,
  Pool,
  TipoActividad,
  TipoDeDato,
  TipoEvento,
  TipoGateway,
  TipoParticipante,
} from '../../../../models/diagrama.model';
import { RolProceso } from '../../../../models/rol-proceso.model';
import { ActividadService } from '../../../../service/actividad.service';
import { ArcoService } from '../../../../service/arco.service';
import { CorrelacionService } from '../../../../service/correlacion.service';
import { EventoService } from '../../../../service/evento.service';
import { GatewayService } from '../../../../service/gateway.service';
import { LaneService } from '../../../../service/lane.service';
import { MensajeService } from '../../../../service/mensaje.service';
import { PoolService } from '../../../../service/pool.service';
import { Seleccion } from '../../seleccion';

/** Un nodo cualquiera del flujo, para los desplegables que eligen uno. */
interface NodoElegible {
  id: number;
  nombre: string;
  laneId: number;
}

/**
 * El formulario del elemento elegido. Hay uno por tipo, con los campos exactos de su DTO y la version escondida:
 * la version no se enseña ni se edita, se manda tal como llego, que es lo que la API compara.
 */
@Component({
  selector: 'app-panel-elemento',
  imports: [ReactiveFormsModule, ErrorCampoComponent],
  templateUrl: './panel-elemento.component.html',
})
export class PanelElementoComponent {
  private readonly poolService: PoolService = inject(PoolService);
  private readonly laneService: LaneService = inject(LaneService);
  private readonly actividadService: ActividadService = inject(ActividadService);
  private readonly gatewayService: GatewayService = inject(GatewayService);
  private readonly eventoService: EventoService = inject(EventoService);
  private readonly arcoService: ArcoService = inject(ArcoService);
  private readonly mensajeService: MensajeService = inject(MensajeService);
  private readonly correlacionService: CorrelacionService = inject(CorrelacionService);

  readonly seleccion = input.required<Seleccion>();
  readonly diagrama = input.required<Diagrama>();
  readonly roles = input<RolProceso[]>([]);
  readonly puedeEditar = input<boolean>(true);
  /** La API confirmo un cambio: la pagina vuelve a pedir el diagrama y el diagnostico. */
  readonly guardado = output<void>();

  readonly nombreParticipante = NOMBRE_PARTICIPANTE;
  readonly nombreIntegracion = NOMBRE_INTEGRACION;
  readonly nombreActividad = NOMBRE_ACTIVIDAD;
  readonly nombreGateway = NOMBRE_GATEWAY;
  readonly nombreEvento = NOMBRE_EVENTO;
  // Las listas salen de los mismos mapas de nombres, tipadas: asi la plantilla indexa sin castings
  readonly tiposParticipante = Object.keys(NOMBRE_PARTICIPANTE) as TipoParticipante[];
  readonly integraciones = Object.keys(NOMBRE_INTEGRACION) as Integracion[];
  readonly tiposActividad = Object.keys(NOMBRE_ACTIVIDAD) as TipoActividad[];
  readonly tiposGateway = Object.keys(NOMBRE_GATEWAY) as TipoGateway[];
  readonly tiposEvento = Object.keys(NOMBRE_EVENTO) as TipoEvento[];
  readonly tiposDato: TipoDeDato[] = ['TEXTO', 'NUMERO', 'FECHA', 'BOOLEANO'];

  formulario: FormGroup = new FormGroup({});
  enviando: boolean = false;
  // Tipo e id del ultimo elemento que se armo, para saber si el formulario cambia de elemento o solo se refresca
  private ultimo: string = '';
  error: string | null = null;
  aviso: string | null = null;

  /** Los lanes a los que se puede mover un nodo: los del mismo pool, porque un nodo no cambia de participante. */
  readonly lanesDelPool = computed<Lane[]>(() => {
    const diagrama: Diagrama = this.diagrama();
    const lane: Lane | undefined = diagrama.lanes.find((candidata) => candidata.id === this.laneDelNodo());
    return lane ? diagrama.lanes.filter((candidata) => candidata.poolId === lane.poolId) : [];
  });

  /** Todos los nodos del diagrama, para anclar los extremos de un mensaje. */
  readonly nodos = computed<NodoElegible[]>(() => {
    const diagrama: Diagrama = this.diagrama();
    return [...diagrama.actividades, ...diagrama.gateways, ...diagrama.eventos].map((nodo) => ({
      id: nodo.id,
      nombre: nodo.nombre,
      laneId: nodo.laneId,
    }));
  });

  constructor() {
    // Cada vez que cambia lo elegido, o lo que hay en el diagrama, el formulario se rehace con sus valores
    effect(() => this.rehacer(this.seleccion(), this.diagrama()));
  }

  get campos(): FormArray {
    return this.formulario.get('campos') as FormArray;
  }

  agregarCampo(): void {
    this.campos.push(
      new FormGroup({
        nombre: new FormControl('', [Validators.required, Validators.maxLength(60)]),
        tipo: new FormControl<TipoDeDato>('TEXTO', Validators.required),
      }),
    );
  }

  quitarCampo(indice: number): void {
    this.campos.removeAt(indice);
  }

  guardar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    const peticion$: Observable<unknown> | null = this.peticionDeGuardado();
    if (peticion$ === null) {
      this.enviando = false;
      return;
    }
    peticion$.pipe(finalize(() => (this.enviando = false))).subscribe({
      next: () => {
        this.aviso = 'Saved.';
        this.guardado.emit();
      },
      error: (error: HttpErrorResponse) => this.mostrarError(error),
    });
  }

  private peticionDeGuardado(): Observable<unknown> | null {
    const valores = this.formulario.getRawValue();
    const { tipo, id } = this.seleccion();
    switch (tipo) {
      case 'POOL':
        return this.poolService.editar(id, valores);
      case 'LANE':
        return this.laneService.editar(id, valores);
      case 'ACTIVIDAD':
        return this.actividadService.editar(id, valores);
      case 'GATEWAY':
        return this.gatewayService.editar(id, valores);
      case 'EVENTO':
        return this.eventoService.editar(id, valores);
      case 'ARCO':
        return this.arcoService.editar(id, valores);
      case 'MENSAJE':
        return this.mensajeService.editar(id, valores);
    }
  }

  /** La correlacion se guarda aparte del mensaje: es otro recurso, con su propio PUT y su propia version. */
  guardarCorrelacion(): void {
    const grupo = this.formulario.get('correlacion') as FormGroup;
    if (grupo.invalid) {
      grupo.markAllAsTouched();
      return;
    }
    this.enviando = true;
    this.error = null;
    this.correlacionService
      .definir(this.seleccion().id, grupo.getRawValue())
      .pipe(finalize(() => (this.enviando = false)))
      .subscribe({
        next: () => {
          this.aviso = 'Correlation saved.';
          this.guardado.emit();
        },
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  private mostrarError(error: HttpErrorResponse): void {
    if (esConflictoDeVersion(error)) {
      // La pagina se recarga sola al guardar cualquier cosa, asi que basta con decir que hay que volver a intentar
      this.error = 'Someone changed this element before you. The diagram was reloaded: check it and try again.';
      this.guardado.emit();
    } else if (error.status === 400) {
      marcarErroresDelServidor(this.formulario, error);
      this.error = 'Check the highlighted fields.';
    } else {
      this.error = mensajeDeError(error);
    }
  }

  /** El lane del nodo elegido, para saber a cuales se puede mover. */
  private laneDelNodo(): number | null {
    const { tipo, id } = this.seleccion();
    const diagrama: Diagrama = this.diagrama();
    const nodo = [...diagrama.actividades, ...diagrama.gateways, ...diagrama.eventos].find(
      (candidato) => candidato.id === id,
    );
    return tipo === 'ACTIVIDAD' || tipo === 'GATEWAY' || tipo === 'EVENTO' ? (nodo?.laneId ?? null) : null;
  }

  /**
   * Rehace el formulario con los campos del tipo elegido. La posicion no se pregunta —se arrastra— pero viaja en
   * el cuerpo, porque el DTO la exige; se manda la que tiene hoy el elemento.
   */
  private rehacer(seleccion: Seleccion, diagrama: Diagrama): void {
    const { tipo, id } = seleccion;
    // Guardar rehace el formulario con el diagrama que vuelve de la API. Borrar los mensajes aqui se llevaria por
    // delante el "Saved." recien puesto, asi que solo se limpian cuando de verdad se cambia de elemento.
    const otroElemento: boolean = this.ultimo !== `${tipo}:${id}`;
    if (otroElemento) {
      this.error = null;
      this.aviso = null;
    }
    this.ultimo = `${tipo}:${id}`;
    switch (tipo) {
      case 'POOL': {
        const pool: Pool | undefined = diagrama.pools.find((candidato) => candidato.id === id);
        this.formulario = new FormGroup({
          nombre: new FormControl(pool?.nombre ?? '', [Validators.required, Validators.maxLength(120)]),
          tipoParticipante: new FormControl(pool?.tipoParticipante ?? 'EMPRESA', Validators.required),
          cajaNegra: new FormControl(pool?.cajaNegra ?? false),
          integracion: new FormControl(pool?.integracion ?? 'NINGUNA'),
          version: new FormControl(pool?.version ?? 0),
        });
        return;
      }
      case 'LANE': {
        const lane: Lane | undefined = diagrama.lanes.find((candidata) => candidata.id === id);
        this.formulario = new FormGroup({
          nombre: new FormControl(lane?.nombre ?? '', [Validators.required, Validators.maxLength(120)]),
          rolProcesoId: new FormControl(lane?.rolProcesoId ?? null, Validators.required),
          version: new FormControl(lane?.version ?? 0),
        });
        return;
      }
      case 'ACTIVIDAD': {
        const actividad: Actividad | undefined = diagrama.actividades.find((candidata) => candidata.id === id);
        this.formulario = new FormGroup({
          nombre: new FormControl(actividad?.nombre ?? '', [Validators.required, Validators.maxLength(120)]),
          descripcion: new FormControl(actividad?.descripcion ?? '', Validators.maxLength(1000)),
          tipoActividad: new FormControl<TipoActividad>(actividad?.tipoActividad ?? 'USUARIO', Validators.required),
          laneId: new FormControl(actividad?.laneId ?? null, Validators.required),
          posicionX: new FormControl(actividad?.posicionX ?? 0),
          posicionY: new FormControl(actividad?.posicionY ?? 0),
          version: new FormControl(actividad?.version ?? 0),
        });
        return;
      }
      case 'GATEWAY': {
        const gateway: Gateway | undefined = diagrama.gateways.find((candidato) => candidato.id === id);
        this.formulario = new FormGroup({
          nombre: new FormControl(gateway?.nombre ?? '', [Validators.required, Validators.maxLength(120)]),
          tipoGateway: new FormControl<TipoGateway>(gateway?.tipoGateway ?? 'EXCLUSIVO', Validators.required),
          laneId: new FormControl(gateway?.laneId ?? null, Validators.required),
          posicionX: new FormControl(gateway?.posicionX ?? 0),
          posicionY: new FormControl(gateway?.posicionY ?? 0),
          version: new FormControl(gateway?.version ?? 0),
        });
        return;
      }
      case 'EVENTO': {
        const evento: Evento | undefined = diagrama.eventos.find((candidato) => candidato.id === id);
        this.formulario = new FormGroup({
          nombre: new FormControl(evento?.nombre ?? '', [Validators.required, Validators.maxLength(120)]),
          tipoEvento: new FormControl<TipoEvento>(evento?.tipoEvento ?? 'INICIO', Validators.required),
          laneId: new FormControl(evento?.laneId ?? null, Validators.required),
          posicionX: new FormControl(evento?.posicionX ?? 0),
          posicionY: new FormControl(evento?.posicionY ?? 0),
          version: new FormControl(evento?.version ?? 0),
        });
        return;
      }
      case 'ARCO': {
        const arco: Arco | undefined = diagrama.arcos.find((candidato) => candidato.id === id);
        this.formulario = new FormGroup({
          origenId: new FormControl(arco?.origenId ?? null, Validators.required),
          destinoId: new FormControl(arco?.destinoId ?? null, Validators.required),
          etiqueta: new FormControl(arco?.etiqueta ?? '', Validators.maxLength(60)),
          condicion: new FormControl(arco?.condicion ?? '', Validators.maxLength(200)),
          porDefecto: new FormControl(arco?.porDefecto ?? false),
          orden: new FormControl(arco?.orden ?? 0),
          version: new FormControl(arco?.version ?? 0),
        });
        return;
      }
      case 'MENSAJE': {
        const mensaje: Mensaje | undefined = diagrama.mensajes.find((candidato) => candidato.id === id);
        const correlacion: Correlacion | undefined = diagrama.correlaciones.find(
          (candidata) => candidata.mensajeId === id,
        );
        this.formulario = new FormGroup({
          nombre: new FormControl(mensaje?.nombre ?? '', [Validators.required, Validators.maxLength(120)]),
          contenido: new FormControl(mensaje?.contenido ?? '', Validators.maxLength(1000)),
          nodoOrigenId: new FormControl(mensaje?.nodoOrigenId ?? null),
          nodoDestinoId: new FormControl(mensaje?.nodoDestinoId ?? null),
          tipoDestino: new FormControl(mensaje?.tipoDestino ?? null),
          siFalla: new FormControl(mensaje?.siFalla ?? null),
          nodoManejoErrorId: new FormControl(mensaje?.nodoManejoErrorId ?? null),
          origenExterno: new FormControl(mensaje?.origenExterno ?? false),
          campos: new FormArray(
            (mensaje?.campos ?? []).map(
              (campo: CampoDeMensaje) =>
                new FormGroup({
                  nombre: new FormControl(campo.nombre, [Validators.required, Validators.maxLength(60)]),
                  tipo: new FormControl<TipoDeDato>(campo.tipo, Validators.required),
                }),
            ),
          ),
          usoDeLosDatos: new FormControl(mensaje?.usoDeLosDatos ?? '', Validators.maxLength(1000)),
          variable: new FormControl(mensaje?.variable ?? '', Validators.maxLength(60)),
          respuestaEsperadaId: new FormControl(mensaje?.respuestaEsperadaId ?? null),
          version: new FormControl(mensaje?.version ?? 0),
          // La correlacion es otro recurso: va en su propio grupo y se guarda con su propio boton
          correlacion: new FormGroup({
            criterio: new FormControl(correlacion?.criterio ?? '', Validators.maxLength(60)),
            campo: new FormControl(correlacion?.campo ?? '', Validators.maxLength(60)),
            sinCaso: new FormControl(correlacion?.sinCaso ?? 'DESCARTAR'),
            version: new FormControl(correlacion?.version ?? null),
          }),
        });
        return;
      }
    }
  }
}
