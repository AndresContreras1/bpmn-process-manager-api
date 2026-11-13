import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, finalize, forkJoin, map, of, switchMap, tap, throwError } from 'rxjs';

import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { mensajeDeLaApi } from '../../helpers/errores-api';
import { Carpeta } from '../../models/carpeta.model';
import {
  ACCIONES,
  AYUDA_ACCION,
  Accion,
  Concesion,
  FaltaEnConcesion,
  NOMBRE_ACCION,
  NOMBRE_SUJETO,
  SUJETOS,
  SujetoConcesion,
  cadenaDeCarpetas,
  faltaEnConcesion,
  solicitudDeConcesion,
} from '../../models/concesion.model';
import { RolProceso } from '../../models/rol-proceso.model';
import { Usuario } from '../../models/usuario.model';
import { CarpetaService } from '../../service/carpeta.service';
import { ConcesionService, TipoDeRecurso } from '../../service/concesion.service';
import { ProcesoService } from '../../service/proceso.service';
import { RolProcesoService } from '../../service/rol-proceso.service';
import { UsuarioService } from '../../service/usuario.service';

/** Las concesiones que una carpeta de arriba le pasa al recurso; null si quien mira no puede leerlas. */
interface Heredadas {
  carpeta: Carpeta;
  concesiones: Concesion[] | null;
}

/** Lo que dice el formulario cuando le falta algo, junto al campo que falta. */
const AVISO_DE_FALTA: Record<FaltaEnConcesion, string> = {
  accion: $localize`Choose what they can do.`,
  sujeto: $localize`Choose who gets it.`,
  usuario: $localize`Choose the person.`,
  rol: $localize`Choose the process role.`,
};

/**
 * PR 60 en la web: quien puede hacer que con una carpeta o un proceso. Lista lo concedido aqui, que se puede retirar,
 * y lo que llega de las carpetas de arriba, marcado como heredado y de solo lectura: se cambia en su carpeta. Los
 * mensajes de la API, como la separacion de funciones, se muestran como llegan.
 */
@Component({
  selector: 'app-permisos',
  imports: [ReactiveFormsModule, RouterLink, ModalConfirmarComponent],
  templateUrl: './permisos.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class PermisosComponent implements OnInit {
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly concesiones: ConcesionService = inject(ConcesionService);
  private readonly carpetaService: CarpetaService = inject(CarpetaService);
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly usuarioService: UsuarioService = inject(UsuarioService);
  private readonly rolService: RolProcesoService = inject(RolProcesoService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly recargas$ = new Subject<void>();

  readonly acciones: Accion[] = ACCIONES;
  readonly sujetos: SujetoConcesion[] = SUJETOS;
  readonly nombreAccion: Record<Accion, string> = NOMBRE_ACCION;
  readonly ayudaAccion: Record<Accion, string> = AYUDA_ACCION;
  readonly nombreSujeto: Record<SujetoConcesion, string> = NOMBRE_SUJETO;

  readonly formulario = new FormGroup({
    accion: new FormControl<Accion | null>('VER'),
    sujeto: new FormControl<SujetoConcesion | null>('PERSONA'),
    usuarioId: new FormControl<number | null>(null),
    rolProcesoId: new FormControl<number | null>(null),
  });

  tipo: TipoDeRecurso = 'procesos';
  id: number = 0;
  nombre: string = '';
  directas: Concesion[] = [];
  heredadas: Heredadas[] = [];
  usuarios: Usuario[] = [];
  /** Sin la lista de personas, que es del administrador, no se le puede conceder a una persona desde aqui. */
  sinPersonas: boolean = false;
  roles: RolProceso[] = [];
  carpetas: Carpeta[] = [];
  falta: FaltaEnConcesion | null = null;
  seleccionada: Concesion | null = null;
  cargando: boolean = true;
  noEncontrado: boolean = false;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  get esProceso(): boolean {
    return this.tipo === 'procesos';
  }

  get rutaDelRecurso(): (string | number)[] {
    return this.esProceso ? ['/procesos', this.id] : ['/procesos'];
  }

  get consultaDelRecurso(): Record<string, number> {
    return this.esProceso ? {} : { carpeta: this.id };
  }

  ngOnInit(): void {
    this.tipo = this.route.snapshot.data['recurso'] === 'carpetas' ? 'carpetas' : 'procesos';
    // Cada recarga cancela la anterior: el enlace a una carpeta de arriba reusa esta pantalla con otro id
    this.recargas$
      .pipe(
        tap(() => (this.cargando = true)),
        switchMap(() =>
          this.leer().pipe(
            catchError((error: HttpErrorResponse) => {
              this.cargando = false;
              if (error.status === 404 || error.status === 400) {
                this.noEncontrado = true;
              } else {
                this.error = mensajeDeLaApi(error);
              }
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((heredadas: Heredadas[]) => {
        this.heredadas = heredadas;
        this.cargando = false;
      });
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros: ParamMap) => {
      this.id = Number(parametros.get('id'));
      this.noEncontrado = false;
      this.error = null;
      this.aviso = null;
      this.directas = [];
      this.heredadas = [];
      this.cargar();
    });
    this.usuarioService
      .paraElegir()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (usuarios: Usuario[]) => (this.usuarios = usuarios),
        error: () => {
          this.sinPersonas = true;
          if (this.formulario.controls.sujeto.value === 'PERSONA') {
            this.formulario.controls.sujeto.setValue('ROL');
          }
        },
      });
    this.rolService
      .listar()
      .pipe(
        catchError(() => of([] as RolProceso[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((roles: RolProceso[]) => (this.roles = roles));
  }

  /** De quien es una concesion, dicho con su nombre cuando se sabe. */
  sujetoDe(concesion: Concesion): string {
    if (concesion.sujeto === 'ORGANIZACION') {
      return this.nombreSujeto.ORGANIZACION;
    }
    if (concesion.sujeto === 'ROL') {
      const rol = this.roles.find((uno) => uno.id === concesion.rolProcesoId);
      return $localize`Role ${rol?.nombre ?? '#' + concesion.rolProcesoId}`;
    }
    const usuario = this.usuarios.find((uno) => uno.id === concesion.usuarioId);
    return usuario?.nombre ?? $localize`Person #${concesion.usuarioId}`;
  }

  conceder(): void {
    const valores = this.formulario.getRawValue();
    this.falta = faltaEnConcesion(valores);
    const solicitud = solicitudDeConcesion(valores);
    if (!solicitud) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    this.concesiones
      .conceder(this.tipo, this.id, solicitud)
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (concesion: Concesion) => {
          this.aviso = $localize`Granted: ${this.nombreAccion[concesion.accion]}:accion: to ${this.sujetoDe(concesion)}:sujeto:.`;
          this.cargar();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeLaApi(error)),
      });
  }

  avisoDeFalta(falta: FaltaEnConcesion): string {
    return AVISO_DE_FALTA[falta];
  }

  confirmarRetirada(): void {
    const concesion = this.seleccionada;
    if (!concesion) {
      return;
    }
    this.error = null;
    this.aviso = null;
    this.concesiones
      .retirar(concesion.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.aviso = $localize`Taken back from ${this.sujetoDe(concesion)}.`;
          this.cargar();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeLaApi(error)),
      });
  }

  private cargar(): void {
    this.recargas$.next();
  }

  /**
   * Lee el recurso, sus concesiones y las de cada carpeta de arriba. Lo de las carpetas puede no dejarse leer, porque
   * pide administrarlas a ellas: entonces se dice que no se pudo, en vez de callar que hay algo heredado.
   */
  private leer(): Observable<Heredadas[]> {
    return this.carpetaService.listar().pipe(
      switchMap((carpetas: Carpeta[]) => {
        this.carpetas = carpetas;
        return forkJoin({
          padre: this.padreDelRecurso(carpetas),
          directas: this.concesiones.listar(this.tipo, this.id),
        });
      }),
      switchMap(({ padre, directas }) => {
        this.directas = directas;
        const cadena = cadenaDeCarpetas(padre, this.carpetas);
        if (cadena.length === 0) {
          return of([] as Heredadas[]);
        }
        return forkJoin(
          cadena.map((carpeta: Carpeta) =>
            this.concesiones.listar('carpetas', carpeta.id).pipe(
              map((concesiones): Heredadas => ({ carpeta, concesiones })),
              catchError(() => of<Heredadas>({ carpeta, concesiones: null })),
            ),
          ),
        );
      }),
    );
  }

  /** La carpeta de la que hereda: la del proceso, o la madre de la carpeta. Tambien deja puesto el nombre. */
  private padreDelRecurso(carpetas: Carpeta[]): Observable<number | null> {
    if (!this.esProceso) {
      const carpeta = carpetas.find((una) => una.id === this.id);
      if (!carpeta) {
        return throwError(() => new HttpErrorResponse({ status: 404 }));
      }
      this.nombre = carpeta.nombre;
      return of(carpeta.padreId);
    }
    return this.procesoService.obtener(this.id).pipe(
      map((detalle) => {
        this.nombre = detalle.proceso.nombre;
        return detalle.proceso.carpetaId ?? null;
      }),
    );
  }
}
