import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, debounceTime, of, switchMap, tap } from 'rxjs';

import { ParClaveValor, aDatos, comoTexto } from '../../helpers/datos-libres';
import { mensajeDeError } from '../../helpers/errores-api';
import { Datos, EstadoPaso, NOMBRE_ESTADO_PASO } from '../../models/caso.model';
import { PageResponse } from '../../models/page-response.model';
import { Proceso } from '../../models/proceso.model';
import { RolProceso } from '../../models/rol-proceso.model';
import { FiltrosTarea, Tarea } from '../../models/tarea.model';
import { AuthService } from '../../service/auth.service';
import { ProcesoService } from '../../service/proceso.service';
import { RolProcesoService } from '../../service/rol-proceso.service';
import { TareaService } from '../../service/tarea.service';

/**
 * La bandeja: lo que los casos abiertos estan esperando que haga alguien. Por defecto solo lo de los roles de
 * quien entro, que es lo que quiere ver; el resto de la tienda esta a un clic, porque quien no tiene roles no
 * tendria bandeja y no sabria por que.
 */
@Component({
  selector: 'app-tareas',
  imports: [FormsModule, RouterLink],
  templateUrl: './tareas.component.html',
})
export class TareasComponent implements OnInit {
  private readonly tareaService: TareaService = inject(TareaService);
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly rolProcesoService: RolProcesoService = inject(RolProcesoService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly authService: AuthService = inject(AuthService);

  readonly puedeEditar: boolean = this.authService.puedeEditar();
  private readonly yo: number | null = this.authService.usuarioActual()?.id ?? null;
  readonly nombreEstado: Record<EstadoPaso, string> = NOMBRE_ESTADO_PASO;
  readonly estados = Object.keys(NOMBRE_ESTADO_PASO) as EstadoPaso[];
  readonly comoTexto: (valor: unknown) => string = comoTexto;

  private readonly busqueda$ = new Subject<void>();

  procesos: Proceso[] = [];
  roles: RolProceso[] = [];
  // El estado viaja siempre: la API ensena las que esperan cuando no se dice, y decirlo evita un desplegable
  // con dos veces la misma opcion
  filtros: FiltrosTarea = { mias: true, rolProcesoId: '', procesoId: '', estado: 'EN_ESPERA', pagina: 0 };
  pagina: PageResponse<Tarea> | null = null;
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  /** La tarea cuyo formulario de completar esta abierto. */
  completando: Tarea | null = null;
  /** Los datos que se van a entregar con ella. */
  datos: ParClaveValor[] = [];

  ngOnInit(): void {
    const proceso: string | null = this.route.snapshot.queryParamMap.get('procesoId');
    if (proceso !== null && !Number.isNaN(Number(proceso))) {
      this.filtros.procesoId = Number(proceso);
    }
    this.procesoService
      .paraElegir()
      .pipe(
        catchError(() => of([] as Proceso[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((procesos: Proceso[]) => (this.procesos = procesos));
    this.rolProcesoService
      .listar()
      .pipe(
        catchError(() => of([] as RolProceso[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((roles: RolProceso[]) => (this.roles = roles));
    this.busqueda$
      .pipe(
        debounceTime(200),
        tap(() => {
          this.cargando = true;
          this.error = null;
        }),
        switchMap(() =>
          this.tareaService.bandeja(this.filtros).pipe(
            catchError((error: HttpErrorResponse) => {
              this.error = mensajeDeError(error);
              this.cargando = false;
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina: PageResponse<Tarea>) => {
        this.pagina = pagina;
        this.cargando = false;
      });
    this.buscar();
  }

  buscar(): void {
    this.busqueda$.next();
  }

  filtrar(): void {
    this.filtros.pagina = 0;
    this.completando = null;
    this.buscar();
  }

  irAPagina(pagina: number): void {
    this.filtros.pagina = pagina;
    this.buscar();
  }

  /** Solo se pueden completar las que esperan a alguien; las demas se miran. */
  enEspera(tarea: Tarea): boolean {
    return tarea.estado === 'EN_ESPERA';
  }

  esMia(tarea: Tarea): boolean {
    return this.yo !== null && tarea.asignadoA === this.yo;
  }

  /** Tomarla es una nota para el equipo: el rol entero la sigue viendo y cualquiera la puede completar. */
  tomar(tarea: Tarea): void {
    this.mandar(this.tareaService.asignar(tarea.id, { usuarioId: this.yo }), `You took "${tarea.nodoNombre}".`);
  }

  soltar(tarea: Tarea): void {
    this.mandar(
      this.tareaService.asignar(tarea.id, { usuarioId: null }),
      `"${tarea.nodoNombre}" is free again.`,
    );
  }

  /** Abre el formulario de completar, con los datos que la tarea ya tuviera. */
  alternarCompletar(tarea: Tarea): void {
    this.completando = this.completando?.id === tarea.id ? null : tarea;
    this.datos = [];
    this.aviso = null;
    this.error = null;
  }

  agregarDato(): void {
    this.datos = [...this.datos, { clave: '', valor: '' }];
  }

  quitarDato(indice: number): void {
    this.datos = this.datos.filter((_, posicion: number) => posicion !== indice);
  }

  /**
   * Completar una tarea es lo que mas veces mueve un caso: el motor sigue desde ahi, asi que despues la bandeja se
   * vuelve a pedir y la tarea siguiente ya esta en ella.
   */
  completar(): void {
    const tarea: Tarea | null = this.completando;
    if (!tarea) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.tareaService
      .completar(tarea.id, { datos: aDatos(this.datos) })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.enviando = false;
          this.completando = null;
          this.datos = [];
          this.aviso = `"${tarea.nodoNombre}" is done. The case moved on.`;
          this.buscar();
        },
        error: (error: HttpErrorResponse) => {
          this.enviando = false;
          this.error = mensajeDeError(error);
        },
      });
  }

  /** Lo que la persona entrego, para poder leerlo en las que ya estan hechas. */
  entregado(tarea: Tarea): { clave: string; valor: string }[] {
    const datos: Datos = tarea.datosSalida ?? {};
    return Object.entries(datos).map(([clave, valor]) => ({ clave, valor: comoTexto(valor) }));
  }

  /** Lo mismo para tomar y para soltar: la API contesta, se dice lo que paso y la bandeja se vuelve a pedir. */
  private mandar(peticion$: Observable<Tarea>, dicho: string): void {
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    peticion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.enviando = false;
        this.aviso = dicho;
        this.buscar();
      },
      error: (error: HttpErrorResponse) => {
        this.enviando = false;
        this.error = mensajeDeError(error);
      },
    });
  }
}
