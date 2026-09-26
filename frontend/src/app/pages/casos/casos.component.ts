import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, debounceTime, of, switchMap, tap } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { ParClaveValor, aDatos } from '../../helpers/datos-libres';
import { mensajeDeError } from '../../helpers/errores-api';
import {
  COLOR_ESTADO_CASO,
  Caso,
  EstadoCaso,
  FiltrosCaso,
  NOMBRE_ESTADO_CASO,
} from '../../models/caso.model';
import { PageResponse } from '../../models/page-response.model';
import { Proceso } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { CasoService } from '../../service/caso.service';
import { ProcesoService } from '../../service/proceso.service';

/** Una fila del editor de variables: la clave y el valor tal como se escriben. */
type FilaDeVariable = FormGroup<{ clave: FormControl<string | null>; valor: FormControl<string | null> }>;

/** Una columna de la tabla que se puede ordenar, con el nombre que la API entiende. */
interface Columna {
  campo: 'id' | 'referencia' | 'estado';
  titulo: string;
  clase: string;
}

/**
 * Los casos de la tienda: cada uno es un pedido corriendo sobre una version publicada. Desde aqui se abre uno a
 * mano, que es como se arranca un proceso que empieza con un evento de inicio normal; los que empiezan con un
 * mensaje los abre el mensaje, y para eso esta el panel de simulacion.
 */
@Component({
  selector: 'app-casos',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, ErrorCampoComponent],
  templateUrl: './casos.component.html',
})
export class CasosComponent implements OnInit {
  private readonly casoService: CasoService = inject(CasoService);
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly puedeEditar: boolean = inject(AuthService).puedeEditar();
  readonly nombreEstado: Record<EstadoCaso, string> = NOMBRE_ESTADO_CASO;
  readonly colorEstado: Record<EstadoCaso, string> = COLOR_ESTADO_CASO;
  readonly estados = Object.keys(NOMBRE_ESTADO_CASO) as EstadoCaso[];
  readonly columnas: Columna[] = [
    { campo: 'referencia', titulo: 'Reference', clase: '' },
    { campo: 'estado', titulo: 'State', clase: '' },
    { campo: 'id', titulo: 'Opened', clase: 'd-none d-sm-table-cell' },
  ];

  private readonly busqueda$ = new Subject<void>();

  procesos: Proceso[] = [];
  filtros: FiltrosCaso = { procesoId: '', estado: '', referencia: '', orden: 'id', direccion: 'desc', pagina: 0 };
  pagina: PageResponse<Caso> | null = null;
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  /** El panel de abrir un caso, cerrado hasta que alguien lo pide. */
  abriendo: boolean = false;
  readonly abrirForm = new FormGroup({
    procesoId: new FormControl<number | null>(null, Validators.required),
    referencia: new FormControl('', [Validators.required, Validators.maxLength(120)]),
    variables: new FormArray<FilaDeVariable>([]),
  });

  ngOnInit(): void {
    // Se llega aqui desde un proceso: entonces la lista empieza filtrada por el
    const desdeUnProceso: string | null = this.route.snapshot.queryParamMap.get('procesoId');
    if (desdeUnProceso !== null && !Number.isNaN(Number(desdeUnProceso))) {
      this.filtros.procesoId = Number(desdeUnProceso);
      this.abrirForm.patchValue({ procesoId: Number(desdeUnProceso) });
    }
    this.procesoService
      .paraElegir()
      .pipe(
        catchError(() => of([] as Proceso[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((procesos: Proceso[]) => (this.procesos = procesos));
    this.busqueda$
      .pipe(
        debounceTime(250),
        tap(() => {
          this.cargando = true;
          this.error = null;
        }),
        switchMap(() =>
          this.casoService.listar(this.filtros).pipe(
            catchError((error: HttpErrorResponse) => {
              this.error = mensajeDeError(error);
              this.cargando = false;
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina: PageResponse<Caso>) => {
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
    this.buscar();
  }

  irAPagina(pagina: number): void {
    this.filtros.pagina = pagina;
    this.buscar();
  }

  ordenarPor(campo: Columna['campo']): void {
    if (this.filtros.orden === campo) {
      this.filtros.direccion = this.filtros.direccion === 'asc' ? 'desc' : 'asc';
    } else {
      this.filtros.orden = campo;
      this.filtros.direccion = campo === 'id' ? 'desc' : 'asc';
    }
    this.filtrar();
  }

  iconoOrden(campo: Columna['campo']): string {
    if (this.filtros.orden !== campo) {
      return 'fa-sort text-body-tertiary';
    }
    return this.filtros.direccion === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  ariaOrden(campo: Columna['campo']): string {
    return this.filtros.orden === campo ? (this.filtros.direccion === 'asc' ? 'ascending' : 'descending') : 'none';
  }

  hayFiltros(): boolean {
    return this.filtros.procesoId !== '' || this.filtros.estado !== '' || this.filtros.referencia.trim() !== '';
  }

  // ------------------------------------------------------------------ abrir un caso

  get variables(): FormArray<FilaDeVariable> {
    return this.abrirForm.controls.variables;
  }

  alternarPanel(): void {
    this.abriendo = !this.abriendo;
    this.error = null;
    this.aviso = null;
  }

  agregarVariable(): void {
    this.variables.push(
      new FormGroup({
        clave: new FormControl('', [Validators.required, Validators.maxLength(60)]),
        valor: new FormControl(''),
      }),
    );
  }

  quitarVariable(indice: number): void {
    this.variables.removeAt(indice);
  }

  /** Las filas del formulario como pares de verdad: un control vacio es una cadena vacia, no un nulo. */
  private paresDeVariables(): ParClaveValor[] {
    return this.variables
      .getRawValue()
      .map((par) => ({ clave: par.clave ?? '', valor: par.valor ?? '' }));
  }

  /** Abre el caso y se va a su detalle: lo que se quiere ver despues de abrirlo es por donde se quedo. */
  abrir(): void {
    if (this.abrirForm.invalid) {
      this.abrirForm.markAllAsTouched();
      return;
    }
    const valores = this.abrirForm.getRawValue();
    const procesoId: number | null = valores.procesoId;
    if (procesoId === null) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    this.casoService
      .abrir(procesoId, {
        referencia: valores.referencia ?? '',
        variables: aDatos(this.paresDeVariables()),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (caso: Caso) => {
          this.enviando = false;
          void this.router.navigate(['/casos', caso.id], { queryParams: { abierto: 1 } });
        },
        error: (error: HttpErrorResponse) => {
          this.enviando = false;
          this.error = mensajeDeError(error);
        },
      });
  }
}
