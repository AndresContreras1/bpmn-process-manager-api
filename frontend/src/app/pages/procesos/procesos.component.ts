import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, ChangeDetectionStrategy, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, debounceTime, forkJoin, map, of, switchMap, tap } from 'rxjs';

import { CarpetasComponent } from '../../components/carpetas/carpetas.component';
import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { MoverProcesoComponent } from '../../components/mover-proceso/mover-proceso.component';
import { CargandoComponent } from '../../components/ui/cargando.component';
import { InsigniaComponent } from '../../components/ui/insignia.component';
import { VacioComponent } from '../../components/ui/vacio.component';
import { esConflictoDeVersion, mensajeDeError, mensajeDeLaApi } from '../../helpers/errores-api';
import { direccionPorDefecto, filtrosDesdeUrl, filtrosEnUrl } from '../../helpers/filtros-en-url';
import { ProcesoRecibido } from '../../models/compartir.model';
import { BotonesDelProceso, botonesDelProceso } from '../../models/concesion.model';
import { PageResponse } from '../../models/page-response.model';
import { Usuario } from '../../models/usuario.model';
import {
  CLASIFICACIONES,
  CampoOrden,
  Clasificacion,
  EstadoProceso,
  FiltrosProceso,
  NOMBRE_CLASIFICACION,
  NOMBRE_ESTADO,
  Proceso,
} from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { CompartirService } from '../../service/compartir.service';
import { ConcesionService } from '../../service/concesion.service';
import { ProcesoService } from '../../service/proceso.service';
import { UsuarioService } from '../../service/usuario.service';
import { FechaPipe } from '../../pipes/fecha.pipe';

/** Columna de la tabla que ordena la lista al hacer clic en su titulo. */
interface ColumnaOrdenable {
  campo: CampoOrden;
  titulo: string;
  clase: string;
}

@Component({
  selector: 'app-procesos',
  imports: [
    FechaPipe,
    FormsModule,
    RouterLink,
    ModalConfirmarComponent,
    MoverProcesoComponent,
    CarpetasComponent,
    CargandoComponent,
    VacioComponent,
    InsigniaComponent,
  ],
  templateUrl: './procesos.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './procesos.component.scss',
})
export class ProcesosComponent implements OnInit {
  /** Lo que un lector de pantalla dice de los controles que nombran algo de la fila: el texto va con el valor. */
  readonly etiqueta = {
    accionesDe: (valor: string | number): string => $localize`Actions for ${valor}`,
    editar: (valor: string | number): string => $localize`Edit ${valor}`,
    publicar: (valor: string | number): string => $localize`Publish ${valor}`,
    eliminar: (valor: string | number): string => $localize`Delete ${valor}`,
    mover: (valor: string | number): string => $localize`Move ${valor} to another folder`,
  };

  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly compartirService: CompartirService = inject(CompartirService);
  private readonly usuarioService: UsuarioService = inject(UsuarioService);
  private readonly concesionService: ConcesionService = inject(ConcesionService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  // Cada cambio de filtro o de pagina emite aqui; switchMap cancela la busqueda anterior si sigue en curso
  private readonly busqueda$ = new Subject<void>();
  // Cada pagina que llega pide lo que se puede hacer con sus filas; una pagina nueva cancela lo de la anterior
  private readonly filas$ = new Subject<Proceso[]>();
  private readonly arbol = viewChild(CarpetasComponent);
  private readonly rol = this.authService.usuarioActual()?.rolAcceso ?? null;
  /** Mientras no llega lo de una fila, sus botones siguen al rol, como antes de las concesiones. */
  private readonly porRol: BotonesDelProceso = botonesDelProceso(null, this.rol);
  private botonesPorFila: ReadonlyMap<number, BotonesDelProceso> = new Map();

  readonly nombreEstado: Record<EstadoProceso, string> = NOMBRE_ESTADO;
  readonly clasificaciones: Clasificacion[] = CLASIFICACIONES;
  readonly nombreClasificacion: Record<Clasificacion, string> = NOMBRE_CLASIFICACION;
  readonly puedeEditar: boolean = this.authService.puedeEditar();
  readonly esAdministrador: boolean = this.authService.esAdministrador();
  /** Quien mira: el filtro de dueno le ofrece lo suyo aunque no pueda listar a los demas. */
  readonly yo: number | null = this.authService.usuarioActual()?.id ?? null;

  // En pantallas pequenas se ocultan la categoria y la fecha para que quepan las acciones
  readonly columnas: ColumnaOrdenable[] = [
    { campo: 'nombre', titulo: $localize`Name`, clase: '' },
    { campo: 'categoria', titulo: $localize`Category`, clase: 'd-none d-sm-table-cell' },
    { campo: 'estado', titulo: $localize`State`, clase: '' },
    { campo: 'fechaModificacion', titulo: $localize`Last change`, clase: 'd-none d-md-table-cell' },
    { campo: 'proximaRevision', titulo: $localize`Next review`, clase: 'd-none d-lg-table-cell' },
    { campo: 'clasificacion', titulo: $localize`Classification`, clase: 'd-none d-xl-table-cell' },
  ];

  /** F19: los filtros viven en la direccion, que es lo que se comparte y lo que sobrevive a recargar. */
  filtros: FiltrosProceso = filtrosDesdeUrl(this.route.snapshot.queryParamMap);
  /** Los usuarios para el filtro de dueno: solo un administrador puede listarlos. */
  usuarios: Usuario[] = [];
  /** El proceso que se esta moviendo de carpeta, con su dialogo abierto. */
  moviendo: Proceso | null = null;
  pagina: PageResponse<Proceso> | null = null;
  cargando: boolean = true;
  error: string | null = null;
  aviso: string | null = null;
  // Vive aparte de error porque la busqueda lo limpia: este aviso tiene que sobrevivir a la recarga de la lista
  desactualizado: string | null = null;
  // Proceso sobre el que se abrio el modal de publicar o el de borrar
  seleccionado: Proceso | null = null;
  /** Los procesos que otras tiendas comparten con la tuya: se leen, no se tocan. */
  recibidos: ProcesoRecibido[] = [];

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.has('eliminado')) {
      this.aviso = $localize`The process was deleted.`;
    }
    this.busqueda$
      .pipe(
        // Espera a que el usuario deje de escribir para no pedir una pagina por cada tecla
        debounceTime(250),
        tap(() => {
          this.cargando = true;
          this.error = null;
        }),
        // El catchError va dentro del switchMap: un error no corta la busqueda, que sigue escuchando
        switchMap(() =>
          this.procesoService.listar(this.filtros).pipe(
            catchError((error: HttpErrorResponse) => {
              this.error = mensajeDeError(error);
              this.cargando = false;
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina: PageResponse<Proceso>) => {
        // Si se borro el unico proceso de la ultima pagina, esa pagina ya no existe y se pide la anterior
        if (pagina.content.length === 0 && pagina.page > 0) {
          this.irAPagina(Math.max(pagina.totalPages - 1, 0));
          return;
        }
        this.pagina = pagina;
        this.cargando = false;
        this.filas$.next(pagina.content);
      });
    this.filas$
      .pipe(
        switchMap((procesos: Proceso[]) => this.botonesDeLasFilas(procesos)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((botones) => (this.botonesPorFila = botones));
    this.compartirService
      .recibidos(0)
      .pipe(
        catchError(() => EMPTY),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina: PageResponse<ProcesoRecibido>) => (this.recibidos = pagina.content));
    if (this.esAdministrador) {
      this.usuarioService
        .paraElegir()
        .pipe(
          catchError(() => EMPTY),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((usuarios: Usuario[]) => (this.usuarios = usuarios));
    }
    // La direccion manda: un enlace, recargar, atras y adelante, y un ?nombre= escrito a mano llegan aqui
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros: ParamMap) => {
      this.filtros = filtrosDesdeUrl(parametros);
      this.buscar();
    });
  }

  buscar(): void {
    this.busqueda$.next();
  }

  /**
   * F19: los botones de una fila, de GET /procesos/{id}/permisos, como en el detalle: quien tiene una concesion que
   * le quita modelar no ve Editar ni Mover. La API sigue mandando y responde 403 a lo que no toca.
   */
  botonesDe(proceso: Proceso): BotonesDelProceso {
    return this.botonesPorFila.get(proceso.id) ?? this.porRol;
  }

  filtrar(): void {
    this.filtros.pagina = 0;
    this.llevarALaDireccion();
  }

  /**
   * Los filtros pasan a la direccion y de ahi vuelven a la lista, por un solo camino. replaceUrl evita que cada tecla
   * del buscador deje una entrada en el historial: atras vuelve a la pantalla anterior, no a la letra anterior.
   */
  private llevarALaDireccion(): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: filtrosEnUrl(this.filtros), replaceUrl: true });
  }

  filtrarPorCarpeta(carpetaId: number | null): void {
    this.filtros.carpetaId = carpetaId;
    this.filtrar();
  }

  filtrarPorCategoria(categoria: string): void {
    this.filtros.categoria = categoria;
    this.filtrar();
  }

  /** El primer clic ordena de la A a la Z (las fechas, de la mas reciente) y el segundo invierte el orden. */
  ordenarPor(campo: CampoOrden): void {
    if (this.filtros.orden === campo) {
      this.filtros.direccion = this.filtros.direccion === 'asc' ? 'desc' : 'asc';
    } else {
      this.filtros.orden = campo;
      this.filtros.direccion = direccionPorDefecto(campo);
    }
    this.filtrar();
  }

  iconoOrden(campo: CampoOrden): string {
    if (this.filtros.orden !== campo) {
      return 'fa-sort text-body-tertiary';
    }
    return this.filtros.direccion === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  ariaOrden(campo: CampoOrden): string {
    if (this.filtros.orden !== campo) {
      return 'none';
    }
    return this.filtros.direccion === 'asc' ? 'ascending' : 'descending';
  }

  hayFiltros(): boolean {
    return (
      this.filtros.nombre.trim() !== '' ||
      this.filtros.estado !== '' ||
      this.filtros.categoria !== '' ||
      !!this.filtros.carpetaId ||
      !!this.filtros.duenoId ||
      !!this.filtros.etiqueta?.trim() ||
      !!this.filtros.clasificacion ||
      !!this.filtros.revisionAntesDe
    );
  }

  irAPagina(pagina: number): void {
    this.filtros.pagina = pagina;
    this.llevarALaDireccion();
  }

  /** Lo que se le pasa a Nuevo proceso: si la lista esta en una carpeta, el proceso nace en ella. */
  get alCrear(): Record<string, number> {
    return this.filtros.carpetaId ? { carpeta: this.filtros.carpetaId } : {};
  }

  alMover(proceso: Proceso): void {
    this.moviendo = null;
    this.limpiarMensajes();
    this.aviso = $localize`"${proceso.nombre}" was moved.`;
    this.buscar();
    // Lo que cuenta cada carpeta cambio: una tiene un proceso menos y otra uno mas
    this.arbol()?.recargar();
  }

  confirmarPublicacion(): void {
    const proceso: Proceso | null = this.seleccionado;
    if (!proceso) {
      return;
    }
    this.limpiarMensajes();
    this.procesoService
      .publicar(proceso.id, proceso.version)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // La lista se vuelve a pedir con la respuesta de la API: el proceso cambia de estado y sube por su fecha
        next: (publicado: Proceso) => {
          this.aviso = $localize`"${publicado.nombre}" is now published.`;
          this.buscar();
        },
        error: (error: HttpErrorResponse) => {
          // Si la version de la fila ya no vale, la lista se recarga: con la version nueva el boton vuelve a servir
          if (esConflictoDeVersion(error)) {
            this.desactualizado =
              $localize`"${proceso.nombre}" changed while this list was open, so it was not published. ` +
              $localize`The list is up to date now: try again.`;
            this.buscar();
          } else {
            this.error = mensajeDeLaApi(error);
          }
        },
      });
  }

  confirmarEliminacion(): void {
    const proceso: Proceso | null = this.seleccionado;
    if (!proceso) {
      return;
    }
    this.limpiarMensajes();
    this.procesoService
      .eliminar(proceso.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.aviso = $localize`"${proceso.nombre}" was deleted.`;
          this.buscar();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeLaApi(error)),
      });
  }

  /**
   * Lo que se puede hacer con cada fila, preguntado en paralelo: una pagina trae como mucho cincuenta. Quien no edita
   * no tiene botones en la lista, asi que no pregunta. Una fila cuya pregunta falla se queda con lo del rol.
   */
  private botonesDeLasFilas(procesos: Proceso[]): Observable<ReadonlyMap<number, BotonesDelProceso>> {
    if (!this.puedeEditar || procesos.length === 0) {
      return of(new Map());
    }
    return forkJoin(
      procesos.map((proceso: Proceso) =>
        this.concesionService.permisos(proceso.id).pipe(
          map((acciones): [number, BotonesDelProceso] => [proceso.id, botonesDelProceso(acciones, this.rol)]),
          catchError(() => of<[number, BotonesDelProceso]>([proceso.id, this.porRol])),
        ),
      ),
    ).pipe(map((filas) => new Map(filas)));
  }

  private limpiarMensajes(): void {
    this.aviso = null;
    this.error = null;
    this.desactualizado = null;
  }
}
