import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, finalize, forkJoin, map, of, switchMap, tap, throwError } from 'rxjs';

import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { esConflictoDeVersion, mensajeDeError } from '../../helpers/errores-api';
import { Diagrama } from '../../models/diagrama.model';
import { EstadoProceso, NOMBRE_ESTADO, Proceso, ProcesoDetalle, cambioEnIngles } from '../../models/proceso.model';
import { Version } from '../../models/version.model';
import { AuthService } from '../../service/auth.service';
import { DiagramaService } from '../../service/diagrama.service';
import { ProcesoService } from '../../service/proceso.service';
import { VersionService } from '../../service/version.service';
import { DetalleNodo, detallarNodo } from './components/diagrama-bpmn/detalle-nodo';
import { DiagramaBpmnComponent } from './components/diagrama-bpmn/diagrama-bpmn.component';
import { Lienzo, dibujarDiagrama } from './components/diagrama-bpmn/lienzo';

/** Lo que la pagina pide al abrir un proceso: sus datos con el historial y su diagrama. */
interface Carga {
  detalle: ProcesoDetalle;
  diagrama: Diagrama | null;
  versiones: Version[];
}

/** Lo que contestan las tres peticiones antes de decidir si hay pagina que mostrar. */
interface Respuesta {
  detalle: ProcesoDetalle | null;
  diagrama: Diagrama | null;
  versiones: Version[];
}

/** Un proceso con sus datos, su diagrama y su historial de cambios, y las acciones que permite el rol del usuario. */
@Component({
  selector: 'app-proceso-detalle',
  imports: [DatePipe, FormsModule, RouterLink, ModalConfirmarComponent, DiagramaBpmnComponent],
  templateUrl: './proceso-detalle.component.html',
  styleUrl: './proceso-detalle.component.scss',
})
export class ProcesoDetalleComponent implements OnInit {
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly diagramaService: DiagramaService = inject(DiagramaService);
  private readonly versionService: VersionService = inject(VersionService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly nombreEstado: Record<EstadoProceso, string> = NOMBRE_ESTADO;
  readonly cambioEnIngles: (descripcion: string) => string = cambioEnIngles;
  readonly puedeEditar: boolean = this.authService.puedeEditar();
  readonly esAdministrador: boolean = this.authService.esAdministrador();

  /** Un proceso de otra tienda no se toca, por mucho rol que se tenga en la propia. */
  get soloLectura(): boolean {
    return this.diagrama?.compartido === true;
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
  lienzo: Lienzo | null = null;
  nodoElegido: DetalleNodo | null = null;
  cargando: boolean = true;
  noEncontrado: boolean = false;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  ngOnInit(): void {
    const consulta: ParamMap = this.route.snapshot.queryParamMap;
    if (consulta.has('creado')) {
      this.aviso = 'Process created as a draft.';
    } else if (consulta.has('editado')) {
      this.aviso = 'Changes saved.';
    }
    // Si el id de la ruta cambia, switchMap cancela la peticion anterior y carga el proceso nuevo
    this.route.paramMap
      .pipe(
        map((parametros: ParamMap) => Number(parametros.get('id'))),
        tap(() => {
          this.cargando = true;
          this.noEncontrado = false;
          this.error = null;
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
    diagrama$
      .pipe(
        finalize(() => (this.cambiandoVersion = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (diagrama: Diagrama) =>
          this.mostrar({ detalle: this.detalle as ProcesoDetalle, diagrama, versiones: this.versiones }),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** Muestra el detalle de la tarea, el gateway o el evento elegido; elegirlo otra vez lo cierra. */
  elegirNodo(id: number): void {
    this.nodoElegido =
      this.diagrama && this.lienzo && this.nodoElegido?.id !== id
        ? detallarNodo(this.diagrama, id, this.lienzo.mensajes)
        : null;
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
          this.aviso = 'The process is now published.';
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
          this.aviso = `Version ${numero} was retired. New cases will start on whatever is in force now.`;
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
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
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /**
   * Un conflicto de version dice que alguien guardo entre la carga de la pagina y el boton. Aqui no hay nada
   * escrito que perder, asi que la pagina se recarga sola y el aviso explica por que el estado no cambio.
   */
  private noSePudoPublicar(id: number, error: HttpErrorResponse): void {
    if (!esConflictoDeVersion(error)) {
      this.error = mensajeDeError(error);
      return;
    }
    this.error = 'Someone saved a change before you, so the process was not published. This page now shows the '
      + 'latest version: check it and publish again.';
    this.cargar(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((carga: Carga) => this.mostrar(carga));
  }

  /** Deja en pantalla lo que llego: el proceso, su diagrama ya calculado y ningun nodo abierto. */
  private mostrar(carga: Carga): void {
    this.detalle = carga.detalle;
    this.diagrama = carga.diagrama;
    this.versiones = carga.versiones;
    this.lienzo = carga.diagrama ? dibujarDiagrama(carga.diagrama) : null;
    this.nodoElegido = null;
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
      // Una invitada no puede listar las versiones del proceso de otra tienda: sin lista, no hay selector
      versiones: this.versionService.listar(id).pipe(catchError(() => of([] as Version[]))),
    }).pipe(
      switchMap((respuesta: Respuesta) => {
        // A una invitada la API solo le abre el diagrama: el detalle y el historial le responden 404. El
        // encabezado sale entonces del proceso que viene dentro del propio diagrama, que es el que ella puede ver.
        const detalle: ProcesoDetalle | null =
          respuesta.detalle ?? (respuesta.diagrama ? { proceso: respuesta.diagrama.proceso, historial: [] } : null);
        return detalle === null
          ? throwError(() => falloDelDetalle ?? new HttpErrorResponse({ status: 404 }))
          : of({ detalle, diagrama: respuesta.diagrama, versiones: respuesta.versiones });
      }),
    );
  }
}
