import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, finalize, forkJoin, map, of, switchMap, tap } from 'rxjs';

import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { ParClaveValor, aDatos, aPares, comoTexto } from '../../helpers/datos-libres';
import { mensajeDeError } from '../../helpers/errores-api';
import {
  COLOR_ESTADO_CASO,
  Caso,
  CasoDetalle,
  EVENTOS_MALOS,
  EstadoCaso,
  EstadoPaso,
  EventoCaso,
  NOMBRE_ESTADO_CASO,
  NOMBRE_ESTADO_PASO,
  NOMBRE_EVENTO_CASO,
  PasoDelCaso,
  TipoEventoCaso,
  pasoVivo,
} from '../../models/caso.model';
import { Diagrama, NOMBRE_DESTINO, NOMBRE_INTEGRACION } from '../../models/diagrama.model';
import {
  MensajesDelCaso,
  NOMBRE_ESTADO_SALIENTE,
  NOMBRE_ORIGEN,
  NOMBRE_RESULTADO,
} from '../../models/mensajeria.model';
import { AuthService } from '../../service/auth.service';
import { CasoService } from '../../service/caso.service';
import { MensajeriaService } from '../../service/mensajeria.service';
import { VersionService } from '../../service/version.service';
import { DiagramaBpmnComponent } from '../proceso-detalle/components/diagrama-bpmn/diagrama-bpmn.component';
import { Lienzo, dibujarDiagrama } from '../proceso-detalle/components/diagrama-bpmn/lienzo';

/** Lo que la pagina pide al abrir un caso: el caso con sus pasos, su linea de tiempo y sus mensajes. */
interface Carga {
  detalle: CasoDetalle;
  eventos: EventoCaso[];
  mensajes: MensajesDelCaso;
  /** El diagrama de la version sobre la que corre, que es el que hay que dibujar y no el borrador de hoy. */
  diagrama: Diagrama | null;
}

/**
 * Un caso, por dentro: por donde va sobre el diagrama de su version, que paso y en que orden, con que variables
 * deciden sus gateways y que mensajes se cruzaron. Es la pantalla que contesta "y por que esta parado".
 */
@Component({
  selector: 'app-caso-detalle',
  imports: [FormsModule, RouterLink, ModalConfirmarComponent, DiagramaBpmnComponent],
  templateUrl: './caso-detalle.component.html',
})
export class CasoDetalleComponent implements OnInit {
  private readonly casoService: CasoService = inject(CasoService);
  private readonly mensajeriaService: MensajeriaService = inject(MensajeriaService);
  private readonly versionService: VersionService = inject(VersionService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly authService: AuthService = inject(AuthService);

  readonly puedeEditar: boolean = this.authService.puedeEditar();
  readonly esAdministrador: boolean = this.authService.esAdministrador();
  readonly nombreEstado: Record<EstadoCaso, string> = NOMBRE_ESTADO_CASO;
  readonly colorEstado: Record<EstadoCaso, string> = COLOR_ESTADO_CASO;
  readonly nombreEstadoPaso: Record<EstadoPaso, string> = NOMBRE_ESTADO_PASO;
  readonly nombreEvento: Record<TipoEventoCaso, string> = NOMBRE_EVENTO_CASO;
  readonly nombreEstadoSaliente = NOMBRE_ESTADO_SALIENTE;
  readonly nombreResultado = NOMBRE_RESULTADO;
  readonly nombreOrigen = NOMBRE_ORIGEN;
  readonly nombreIntegracion = NOMBRE_INTEGRACION;
  readonly nombreDestino = NOMBRE_DESTINO;
  readonly comoTexto: (valor: unknown) => string = comoTexto;
  readonly pasoVivo: (paso: PasoDelCaso) => boolean = pasoVivo;

  casoId: number = 0;
  detalle: CasoDetalle | null = null;
  eventos: EventoCaso[] = [];
  mensajes: MensajesDelCaso = { salientes: [], entrantes: [] };
  lienzo: Lienzo | null = null;
  sinDiagrama: boolean = false;
  cargando: boolean = true;
  noEncontrado: boolean = false;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  /** El editor de variables, cerrado hasta que un administrador lo abre. */
  editandoVariables: boolean = false;
  variables: ParClaveValor[] = [];

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.has('abierto')) {
      this.aviso = 'The case is open. This is where it stopped.';
    }
    this.route.paramMap
      .pipe(
        map((parametros: ParamMap) => Number(parametros.get('id'))),
        tap((id: number) => {
          this.casoId = id;
          this.cargando = true;
          this.noEncontrado = false;
          this.error = null;
        }),
        switchMap((id: number) =>
          this.cargar(id).pipe(
            catchError((error: HttpErrorResponse) => {
              this.cargando = false;
              if (error.status === 404 || error.status === 400) {
                this.noEncontrado = true;
              } else {
                this.error = mensajeDeError(error);
              }
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((carga: Carga) => {
        this.mostrar(carga);
        this.cargando = false;
      });
  }

  /** Vuelve a pedirlo todo. Un caso cambia solo: lo mueve el reloj, o una tarea que alguien completa. */
  recargar(): void {
    this.cargando = true;
    this.error = null;
    this.cargar(this.casoId)
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (carga: Carga) => this.mostrar(carga),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** Donde tiene el token ahora mismo: es lo que el lienzo resalta. */
  get nodosActivos(): number[] {
    return (this.detalle?.pasos ?? []).filter(pasoVivo).map((paso: PasoDelCaso) => paso.nodoId);
  }

  /** Por donde ya paso, para que se lea el camino y no solo el punto. */
  get nodosRecorridos(): number[] {
    return (this.detalle?.pasos ?? [])
      .filter((paso: PasoDelCaso) => paso.estado === 'COMPLETADA')
      .map((paso: PasoDelCaso) => paso.nodoId);
  }

  /** Las variables del caso como pares, para leerlas sin abrir el editor. */
  get paresDeVariables(): ParClaveValor[] {
    return aPares(this.detalle?.variables ?? null);
  }

  esMalo(evento: EventoCaso): boolean {
    return EVENTOS_MALOS.includes(evento.tipo);
  }

  /** Un caso sin camino se arregla cambiandole las variables y volviendo a intentarlo. */
  get estaAtascado(): boolean {
    return this.detalle?.caso.estado === 'ERROR';
  }

  get sigueAbierto(): boolean {
    return this.detalle?.caso.estado === 'ABIERTO' || this.estaAtascado;
  }

  // ------------------------------------------------------------------ acciones

  cancelar(): void {
    this.hacer(this.casoService.cancelar(this.casoId), 'The case was cancelled. Nothing it did was deleted.');
  }

  reintentar(): void {
    this.hacer(this.casoService.reintentar(this.casoId), 'Tried again with the variables it has now.');
  }

  alternarVariables(): void {
    this.editandoVariables = !this.editandoVariables;
    this.variables = this.editandoVariables ? aPares(this.detalle?.variables ?? null) : [];
    this.error = null;
  }

  agregarVariable(): void {
    this.variables = [...this.variables, { clave: '', valor: '' }];
  }

  quitarVariable(indice: number): void {
    this.variables = this.variables.filter((_, posicion: number) => posicion !== indice);
  }

  /** Las variables se reemplazan enteras y sobre la version que se leyo: si alguien cambio algo, la API avisa. */
  guardarVariables(): void {
    const caso: Caso | undefined = this.detalle?.caso;
    if (!caso) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    this.casoService
      .variables(caso.id, { variables: aDatos(this.variables), version: caso.version })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.enviando = false;
          this.editandoVariables = false;
          this.aviso = 'The variables were replaced.';
          this.recargar();
        },
        error: (error: HttpErrorResponse) => {
          this.enviando = false;
          this.error = mensajeDeError(error);
        },
      });
  }

  private hacer(peticion$: Observable<Caso>, dicho: string): void {
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    peticion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.enviando = false;
        this.aviso = dicho;
        this.recargar();
      },
      error: (error: HttpErrorResponse) => {
        this.enviando = false;
        this.error = mensajeDeError(error);
      },
    });
  }

  private mostrar(carga: Carga): void {
    this.detalle = carga.detalle;
    this.eventos = carga.eventos;
    this.mensajes = carga.mensajes;
    this.sinDiagrama = carga.diagrama === null;
    this.lienzo = carga.diagrama ? dibujarDiagrama(carga.diagrama) : null;
  }

  /**
   * El caso, su linea de tiempo y sus mensajes en paralelo, y despues el diagrama de la version, que hace falta
   * saber cual es. Si algo de lo secundario falla, la pagina se ensena igual: lo que no se puede es no tener el
   * caso.
   */
  private cargar(id: number): Observable<Carga> {
    return forkJoin({
      detalle: this.casoService.obtener(id),
      eventos: this.casoService.eventos(id).pipe(catchError(() => of([] as EventoCaso[]))),
      mensajes: this.mensajeriaService
        .delCaso(id)
        .pipe(catchError(() => of({ salientes: [], entrantes: [] } as MensajesDelCaso))),
    }).pipe(
      switchMap((parcial: Omit<Carga, 'diagrama'>) =>
        this.versionService
          .diagrama(parcial.detalle.caso.procesoId, parcial.detalle.caso.versionNumero)
          .pipe(
            catchError(() => of(null)),
            map((diagrama: Diagrama | null) => ({ ...parcial, diagrama })),
          ),
      ),
    );
  }
}
