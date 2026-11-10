import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, debounceTime, switchMap, tap } from 'rxjs';

import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { CargandoComponent } from '../../components/ui/cargando.component';
import { InsigniaComponent } from '../../components/ui/insignia.component';
import { VacioComponent } from '../../components/ui/vacio.component';
import { esConflictoDeVersion, mensajeDeError } from '../../helpers/errores-api';
import { ProcesoRecibido } from '../../models/compartir.model';
import { PageResponse } from '../../models/page-response.model';
import { CampoOrden, EstadoProceso, FiltrosProceso, NOMBRE_ESTADO, Proceso } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { CompartirService } from '../../service/compartir.service';
import { ProcesoService } from '../../service/proceso.service';
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
  };

  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly compartirService: CompartirService = inject(CompartirService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  // Cada cambio de filtro o de pagina emite aqui; switchMap cancela la busqueda anterior si sigue en curso
  private readonly busqueda$ = new Subject<void>();

  readonly nombreEstado: Record<EstadoProceso, string> = NOMBRE_ESTADO;
  readonly puedeEditar: boolean = this.authService.puedeEditar();
  readonly esAdministrador: boolean = this.authService.esAdministrador();

  // En pantallas pequenas se ocultan la categoria y la fecha para que quepan las acciones
  readonly columnas: ColumnaOrdenable[] = [
    { campo: 'nombre', titulo: $localize`Name`, clase: '' },
    { campo: 'categoria', titulo: $localize`Category`, clase: 'd-none d-sm-table-cell' },
    { campo: 'estado', titulo: $localize`State`, clase: '' },
    { campo: 'fechaModificacion', titulo: $localize`Last change`, clase: 'd-none d-md-table-cell' },
  ];

  filtros: FiltrosProceso = {
    nombre: '',
    estado: '',
    categoria: '',
    orden: 'fechaModificacion',
    direccion: 'desc',
    pagina: 0,
  };
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
      });
    this.compartirService
      .recibidos(0)
      .pipe(
        catchError(() => EMPTY),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina: PageResponse<ProcesoRecibido>) => (this.recibidos = pagina.content));
    // F11: la busqueda del encabezado llega como ?nombre=, tambien con la lista ya abierta
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros: ParamMap) => {
      this.filtros.nombre = parametros.get('nombre') ?? '';
      this.filtrar();
    });
  }

  buscar(): void {
    this.busqueda$.next();
  }

  filtrar(): void {
    this.filtros.pagina = 0;
    this.buscar();
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
      this.filtros.direccion = campo === 'fechaModificacion' ? 'desc' : 'asc';
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
    return this.filtros.nombre.trim() !== '' || this.filtros.estado !== '' || this.filtros.categoria !== '';
  }

  irAPagina(pagina: number): void {
    this.filtros.pagina = pagina;
    this.buscar();
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
            this.error = mensajeDeError(error);
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
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private limpiarMensajes(): void {
    this.aviso = null;
    this.error = null;
    this.desactualizado = null;
  }
}
