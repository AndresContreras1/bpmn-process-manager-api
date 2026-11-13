import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, ViewChild, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, finalize, forkJoin, map, of, switchMap, tap, throwError } from 'rxjs';

import { ElementoComentado, PanelComentariosComponent } from '../../components/comentarios/panel-comentarios.component';
import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { MoverProcesoComponent } from '../../components/mover-proceso/mover-proceso.component';
import { PedirRevisionComponent } from '../../components/pedir-revision/pedir-revision.component';
import { FichaRepositorioComponent } from './components/ficha-repositorio/ficha-repositorio.component';
import { esConflictoDeVersion, mensajeDeError, mensajeDeLaApi } from '../../helpers/errores-api';
import { Comentario } from '../../models/comentario.model';
import { Accion, BotonesDelProceso, botonesDelProceso } from '../../models/concesion.model';
import { Diagrama } from '../../models/diagrama.model';
import {
  AvisosAlAbrir,
  EstadoProceso,
  NOMBRE_ESTADO,
  Proceso,
  ProcesoDetalle,
  avisosAlAbrir,
  cambioEnIngles,
} from '../../models/proceso.model';
import { Solicitud } from '../../models/solicitud.model';
import { Version } from '../../models/version.model';
import { AuthService } from '../../service/auth.service';
import { ConcesionService } from '../../service/concesion.service';
import { DiagramaService } from '../../service/diagrama.service';
import { ProcesoService } from '../../service/proceso.service';
import { SolicitudService } from '../../service/solicitud.service';
import { VersionService } from '../../service/version.service';
import { DetalleNodo, MensajeDibujo, bpmnIdsDeNodos, detallarNodo, numerarMensajes } from '../../bpmn/detalle-nodo';
import { ElementoElegido, LienzoBpmnComponent } from '../../bpmn/lienzo-bpmn.component';
import { ModeloService } from '../../service/modelo.service';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { environment } from '../../../environments/environment';

/** Lo que la pagina pide al abrir un proceso: sus datos con el historial y su diagrama. */
interface Carga {
  detalle: ProcesoDetalle;
  diagrama: Diagrama | null;
  xml: string | null;
  versiones: Version[];
  acciones: Accion[] | null;
}

/** Lo que contestan las tres peticiones antes de decidir si hay pagina que mostrar. */
interface Respuesta {
  detalle: ProcesoDetalle | null;
  diagrama: Diagrama | null;
  xml: string | null;
  versiones: Version[];
  acciones: Accion[] | null;
}

/** Un proceso con sus datos, su diagrama y su historial de cambios, y las acciones que permite el rol del usuario. */
@Component({
  selector: 'app-proceso-detalle',
  imports: [
    FechaPipe,
    FormsModule,
    RouterLink,
    ModalConfirmarComponent,
    MoverProcesoComponent,
    LienzoBpmnComponent,
    FichaRepositorioComponent,
    PanelComentariosComponent,
    PedirRevisionComponent,
  ],
  templateUrl: './proceso-detalle.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './proceso-detalle.component.scss',
})
export class ProcesoDetalleComponent implements OnInit {
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly diagramaService: DiagramaService = inject(DiagramaService);
  private readonly versionService: VersionService = inject(VersionService);
  private readonly modelos: ModeloService = inject(ModeloService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly concesionService: ConcesionService = inject(ConcesionService);
  private readonly solicitudService: SolicitudService = inject(SolicitudService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly nombreEstado: Record<EstadoProceso, string> = NOMBRE_ESTADO;
  readonly cambioEnIngles: (descripcion: string) => string = cambioEnIngles;
  readonly esAdministrador: boolean = this.authService.esAdministrador();
  private readonly rol = this.authService.usuarioActual()?.rolAcceso ?? null;

  /**
   * F19: los botones que van a funcionar, de lo que la API dice que esta persona puede hacer con este proceso
   * (GET /procesos/{id}/permisos). La API sigue mandando: si algo cambio entretanto, responde 403 y se dice.
   */
  botones: BotonesDelProceso = botonesDelProceso(null, this.rol);
  /** Abierto el dialogo de mover de carpeta. */
  moviendo: boolean = false;

  @ViewChild(LienzoBpmnComponent) private lienzo?: LienzoBpmnComponent;
  /** F21: la organizacion publica con aprobacion, asi que en vez de publicar se pide la revision. */
  conAprobacion: boolean = false;
  /** F21: la revision abierta del proceso, si hay una: no se puede pedir otra. */
  solicitudAbierta: Solicitud | null = null;
  pidiendoRevision: boolean = false;
  /** F21: el elemento cuyos comentarios se ven, cualquiera del dibujo; null es el proceso entero. */
  elementoComentado: ElementoComentado | null = null;
  /** F21: el comentario al que lleva la direccion (?comentario=), como el enlace de un aviso o de un correo. */
  comentarioPedido: number | null = null;
  contadoresDeComentarios: Record<string, number> = {};
  /** El elemento que hay que elegir en el lienzo en cuanto termine de dibujar. */
  private elementoAlDibujar: string | null = null;
  private dibujado: boolean = false;
  /** El bpmnId que trae la direccion, hasta que el diagrama llega y se elige. */
  private elementoPedido: string | null = null;

  /** El archivo BPMN como descarga: la API lo manda como adjunto con el nombre del proceso, y pide EXPORTAR. */
  get enlaceBpmn(): string {
    return `${environment.apiUrl}/api/v1/procesos/${this.detalle?.proceso.id}/modelo`;
  }

  /** Un proceso de otra tienda no se toca, por mucho rol que se tenga en la propia. */
  get soloLectura(): boolean {
    return this.diagrama?.compartido === true;
  }

  /** F21: un borrador sin publicar, o uno con cambios sobre lo publicado: lo que una revision puede llevar. */
  get hayQuePublicar(): boolean {
    const proceso = this.detalle?.proceso;
    return proceso !== undefined && (proceso.estado === 'BORRADOR' || proceso.borradorPendiente === true);
  }

  /** La version que se esta mirando, cuando se mira una publicada: de ahi sale si todavia se puede retirar. */
  get versionVista(): Version | null {
    return this.versiones.find((version: Version) => version.numero === this.verVersion) ?? null;
  }

  detalle: ProcesoDetalle | null = null;
  diagrama: Diagrama | null = null;
  versiones: Version[] = [];
  /** Que se esta mirando: null es el modelo de hoy, y un numero, la version publicada con ese numero. */
  verVersion: number | null = null;
  cambiandoVersion: boolean = false;
  xml: string | null = null;
  mensajes: MensajeDibujo[] = [];
  nodoElegido: DetalleNodo | null = null;
  cargando: boolean = true;
  noEncontrado: boolean = false;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  ngOnInit(): void {
    // Lo que trae la direccion se dice con la primera carga: antes, la carga limpia los mensajes y se lo llevaria
    const consulta: ParamMap = this.route.snapshot.queryParamMap;
    let alAbrir: AvisosAlAbrir | null = avisosAlAbrir(consulta);
    // F19: la busqueda global abre un proceso con el elemento que encontro (?elemento=), tambien desde otro proceso
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros: ParamMap) => {
      // F21: el enlace de un correo sobre una solicitud (?solicitud=) va a su pagina
      const solicitud: number = Number(parametros.get('solicitud'));
      if (solicitud > 0) {
        void this.router.navigate(['/solicitudes', solicitud], { replaceUrl: true });
        return;
      }
      const comentario: number = Number(parametros.get('comentario'));
      this.comentarioPedido = comentario > 0 ? comentario : null;
      this.elementoPedido = parametros.get('elemento');
      this.elegirElPedido();
    });
    // Si el id de la ruta cambia, switchMap cancela la peticion anterior y carga el proceso nuevo
    this.route.paramMap
      .pipe(
        map((parametros: ParamMap) => Number(parametros.get('id'))),
        tap(() => {
          this.cargando = true;
          this.noEncontrado = false;
          this.error = null;
          this.aviso = null;
        }),
        switchMap((id: number) =>
          this.cargar(id).pipe(
            catchError((error: HttpErrorResponse) => {
              this.cargando = false;
              // 404: el proceso es de otra tienda o ya se borro. 400: el id de la ruta no es un numero
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
        this.cargarRevision(carga.detalle.proceso.id);
        if (alAbrir) {
          this.error = alAbrir.error;
          this.aviso = alAbrir.aviso;
          alAbrir = null;
        }
        this.elegirElPedido();
      });
  }

  /**
   * Cambia lo que se dibuja. El modelo de hoy se vuelve a pedir entero; una version publicada se pide a su propio
   * endpoint, que devuelve el diagrama congelado con la misma forma.
   */
  mostrarVersion(numero: number | null): void {
    const id: number | undefined = this.detalle?.proceso.id;
    if (id === undefined || numero === this.verVersion) {
      return;
    }
    this.cambiandoVersion = true;
    this.verVersion = numero;
    const diagrama$: Observable<Diagrama> =
      numero === null ? this.diagramaService.obtener(id) : this.versionService.diagrama(id, numero);
    const xml$: Observable<string> =
      numero === null
        ? this.modelos.leer(id).pipe(map((modelo) => modelo.xml))
        : this.versionService.modelo(id, numero);
    forkJoin({ diagrama: diagrama$, xml: xml$ })
      .pipe(
        finalize(() => (this.cambiandoVersion = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ diagrama, xml }) =>
          this.mostrar({
            detalle: this.detalle as ProcesoDetalle,
            diagrama,
            xml,
            versiones: this.versiones,
            acciones: null,
          }),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** Muestra el detalle de la tarea, el gateway o el evento elegido; elegirlo otra vez lo cierra. */
  elegirNodo(elegido: ElementoElegido | null): void {
    this.elementoComentado = elegido ? { id: elegido.id, nombre: elegido.nombre } : null;
    const diagrama = this.diagrama;
    if (!elegido || !diagrama) {
      this.nodoElegido = null;
      return;
    }
    const nodo = [...bpmnIdsDeNodos(diagrama)].find(([, bpmnId]) => bpmnId === elegido.id);
    this.nodoElegido = nodo ? detallarNodo(diagrama, nodo[0], this.mensajes) : null;
  }

  /**
   * El elemento que pidio la direccion, elegido una vez, cuando llega un diagrama que lo tiene; despues lo elige la
   * persona. Si la direccion cambia antes que el proceso, el diagrama de ahora no lo tiene y se espera al siguiente.
   */
  private elegirElPedido(): void {
    const pedido = this.elementoPedido;
    if (pedido && this.diagrama && !this.cargando) {
      this.elegirNodo({ id: pedido, tipo: '', nombre: '' });
      if (this.nodoElegido) {
        this.elementoPedido = null;
      }
    }
  }

  /** F21: el lienzo termino de dibujar; si un enlace pedia un elemento, se elige ahora. */
  alDibujar(): void {
    this.dibujado = true;
    const pendiente = this.elementoAlDibujar;
    this.elementoAlDibujar = null;
    if (pendiente) {
      this.lienzo?.mostrar(pendiente);
    }
  }

  /**
   * F21: el hilo al que lleva un enlace. Si es de otra version se muestra esa version; despues se elige su elemento en
   * el lienzo, que es lo que hace que el panel muestre sus hilos.
   */
  irAlHilo(comentario: Comentario): void {
    if (comentario.versionDelProceso !== this.verVersion) {
      this.elementoAlDibujar = comentario.bpmnId;
      this.mostrarVersion(comentario.versionDelProceso);
      return;
    }
    if (!comentario.bpmnId) {
      this.elegirNodo(null);
    } else if (this.dibujado) {
      this.lienzo?.mostrar(comentario.bpmnId);
    } else {
      this.elementoAlDibujar = comentario.bpmnId;
    }
  }

  alPedirRevision(solicitud: Solicitud): void {
    this.pidiendoRevision = false;
    void this.router.navigate(['/solicitudes', solicitud.id]);
  }

  alGuardarFicha(proceso: Proceso): void {
    if (this.detalle) {
      this.detalle = { ...this.detalle, proceso };
    }
  }

  alMover(proceso: Proceso): void {
    this.moviendo = false;
    this.alGuardarFicha(proceso);
    this.aviso = $localize`Moved to its new folder.`;
  }

  cerrarNodo(): void {
    this.nodoElegido = null;
  }

  publicar(): void {
    const proceso: Proceso | undefined = this.detalle?.proceso;
    if (!proceso) {
      return;
    }
    this.enviando = true;
    this.aviso = null;
    this.error = null;
    // Publicar y volver a pedirlo, encadenados: el historial suma el cambio y las versiones, la nueva
    this.procesoService
      .publicar(proceso.id, proceso.version)
      .pipe(
        switchMap(() =>
          forkJoin({
            detalle: this.procesoService.obtener(proceso.id),
            // Si la lista falla se queda la que hay: el proceso se publico igual, y eso es lo que se cuenta
            versiones: this.versionService.listar(proceso.id).pipe(catchError(() => of(this.versiones))),
          }),
        ),
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (publicado: { detalle: ProcesoDetalle; versiones: Version[] }) => {
          this.detalle = publicado.detalle;
          this.versiones = publicado.versiones;
          this.aviso = $localize`The process is now published.`;
        },
        error: (error: HttpErrorResponse) => this.noSePudoPublicar(proceso.id, error),
      });
  }

  /**
   * Retirar la version que se esta mirando. Despues se vuelve a pedir todo: el proceso pasa a la version anterior
   * que siga en pie -o se queda sin ninguna- y el historial suma la linea, asi que recargar es lo honesto.
   */
  retirar(): void {
    const proceso: Proceso | undefined = this.detalle?.proceso;
    const numero: number | null = this.verVersion;
    if (!proceso || numero === null) {
      return;
    }
    this.enviando = true;
    this.aviso = null;
    this.error = null;
    this.versionService
      .retirar(proceso.id, numero)
      .pipe(
        switchMap(() => this.cargar(proceso.id)),
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (carga: Carga) => {
          this.mostrar(carga);
          this.aviso = $localize`Version ${numero} was retired. New cases will start on whatever is in force now.`;
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeLaApi(error)),
      });
  }

  eliminar(): void {
    const proceso: Proceso | undefined = this.detalle?.proceso;
    if (!proceso) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.procesoService
      .eliminar(proceso.id)
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.router.navigate(['/procesos'], { queryParams: { eliminado: 1 } }),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeLaApi(error)),
      });
  }

  /**
   * Un conflicto de version dice que alguien guardo entre la carga de la pagina y el boton. Aqui no hay nada
   * escrito que perder, asi que la pagina se recarga sola y el aviso explica por que el estado no cambio.
   */
  private noSePudoPublicar(id: number, error: HttpErrorResponse): void {
    if (!esConflictoDeVersion(error)) {
      // Un 403 por una concesion que falta trae su propio motivo, que no es cuestion del rol
      this.error = mensajeDeLaApi(error);
      return;
    }
    this.error = $localize`Someone saved a change before you, so the process was not published. This page now shows the latest version: check it and publish again.`;
    this.cargar(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((carga: Carga) => this.mostrar(carga));
  }

  /** Deja en pantalla lo que llego: el proceso, su diagrama ya calculado y ningun nodo abierto. */
  private mostrar(carga: Carga): void {
    if (carga.xml !== this.xml) {
      this.dibujado = false;
    }
    this.elementoComentado = null;
    this.detalle = carga.detalle;
    this.diagrama = carga.diagrama;
    this.versiones = carga.versiones;
    this.xml = carga.xml;
    this.mensajes = carga.diagrama ? numerarMensajes(carga.diagrama) : [];
    this.nodoElegido = null;
    if (carga.acciones !== null) {
      this.botones = botonesDelProceso(carga.acciones, this.rol);
    }
  }

  /**
   * F21: como publica la organizacion y si el proceso ya tiene una revision abierta. Si no llega, se ofrece publicar
   * como antes, y la API dice con un 409 que hay que pedir la revision. Una invitada no publica ni pide nada.
   */
  private cargarRevision(id: number): void {
    if (this.soloLectura) {
      this.conAprobacion = false;
      this.solicitudAbierta = null;
      return;
    }
    forkJoin({
      politica: this.solicitudService.politica().pipe(catchError(() => of(null))),
      abiertas: this.solicitudService.delProceso(id, 'ABIERTA').pipe(catchError(() => of(null))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ politica, abiertas }) => {
        this.conAprobacion = politica?.modo === 'CON_APROBACION';
        this.solicitudAbierta = abiertas?.content[0] ?? null;
      });
  }

  /**
   * Pide en paralelo los datos del proceso y su diagrama. Si solo falla el diagrama, la pagina se muestra igual y
   * avisa en su lugar; si falla el proceso, el error sigue hacia ngOnInit.
   *
   * <p>El fallo del detalle se guarda en vez de tragarse: si al final no hay nada que mostrar, lo que se lanza es
   * el error de verdad y no un 404 inventado. Con la API caida, la pantalla tiene que decir que no pudo cargar,
   * no que el proceso no existe.
   */
  private cargar(id: number): Observable<Carga> {
    this.verVersion = null;
    let falloDelDetalle: HttpErrorResponse | null = null;
    return forkJoin({
      detalle: this.procesoService.obtener(id).pipe(
        catchError((error: HttpErrorResponse) => {
          falloDelDetalle = error;
          return of(null);
        }),
      ),
      diagrama: this.diagramaService.obtener(id).pipe(catchError(() => of(null))),
      xml: this.modelos.leer(id).pipe(
        map((modelo) => modelo.xml),
        catchError(() => of(null)),
      ),
      // Una invitada no puede listar las versiones del proceso de otra tienda: sin lista, no hay selector
      versiones: this.versionService.listar(id).pipe(catchError(() => of([] as Version[]))),
      // Sin la respuesta, los botones siguen al rol, como antes; una invitada no la recibe y no ve botones igual
      acciones: this.concesionService.permisos(id).pipe(catchError(() => of(null))),
    }).pipe(
      switchMap((respuesta: Respuesta) => {
        // A una invitada la API solo le abre el diagrama: el detalle y el historial le responden 404. El
        // encabezado sale entonces del proceso que viene dentro del propio diagrama, que es el que ella puede ver.
        const detalle: ProcesoDetalle | null =
          respuesta.detalle ?? (respuesta.diagrama ? { proceso: respuesta.diagrama.proceso, historial: [] } : null);
        return detalle === null
          ? throwError(() => falloDelDetalle ?? new HttpErrorResponse({ status: 404 }))
          : of({
              detalle,
              diagrama: respuesta.diagrama,
              xml: respuesta.xml,
              versiones: respuesta.versiones,
              acciones: respuesta.acciones,
            });
      }),
    );
  }
}
