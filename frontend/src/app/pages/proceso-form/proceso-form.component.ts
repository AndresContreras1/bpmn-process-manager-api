import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, catchError, finalize, map, of, switchMap } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { esConflictoDeVersion, marcarErroresDelServidor, mensajeDeError } from '../../helpers/errores-api';
import { FilaDeCarpeta, aplanarCarpetas } from '../../models/carpeta.model';
import {
  CLASIFICACIONES,
  Clasificacion,
  FaltaEnEtiquetas,
  LARGO_MAXIMO_DE_ETIQUETA,
  MAXIMO_DE_ETIQUETAS,
  NOMBRE_CLASIFICACION,
  Proceso,
  ProcesoDetalle,
  ProcesoRequest,
  faltaEnEtiquetas,
  fichaInicial,
} from '../../models/proceso.model';
import { Usuario } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';
import { CarpetaService } from '../../service/carpeta.service';
import { ProcesoService } from '../../service/proceso.service';
import { UsuarioService } from '../../service/usuario.service';

/** Lo que vuelve de guardar: el proceso, y si su ficha no se pudo guardar despues de crearlo. */
interface Guardado {
  proceso: Proceso;
  sinArchivar: boolean;
}

/** Lo que se dice junto a las etiquetas cuando la API no las aceptaria. */
const AVISO_DE_ETIQUETAS: Record<FaltaEnEtiquetas, string> = {
  demasiadas: $localize`A process can have at most ${MAXIMO_DE_ETIQUETAS}:maximo: tags.`,
  larga: $localize`Each tag can have at most ${LARGO_MAXIMO_DE_ETIQUETA}:largo: characters.`,
};

/** Las etiquetas como las valida la API, para que la ficha del alta no responda 400 con el proceso ya creado. */
function etiquetasAceptables(control: AbstractControl<string>): ValidationErrors | null {
  const falta = faltaEnEtiquetas(control.value ?? '');
  return falta === null ? null : { etiquetas: falta };
}

/** Crear y editar un proceso con el mismo formulario: si la ruta trae un id, edita ese proceso. */
@Component({
  selector: 'app-proceso-form',
  imports: [ReactiveFormsModule, RouterLink, ErrorCampoComponent],
  templateUrl: './proceso-form.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './proceso-form.component.scss',
})
export class ProcesoFormComponent implements OnInit {
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly carpetaService: CarpetaService = inject(CarpetaService);
  private readonly usuarioService: UsuarioService = inject(UsuarioService);
  private readonly router: Router = inject(Router);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  // Los mismos limites que valida la API, para avisar antes de enviar
  readonly procesoForm = new FormGroup({
    nombre: new FormControl('', [Validators.required, Validators.maxLength(120)]),
    categoria: new FormControl('', [Validators.required, Validators.maxLength(80)]),
    descripcion: new FormControl('', [Validators.required, Validators.maxLength(4000)]),
  });

  /** F19: al crear, la ficha entera: carpeta, dueno, proxima revision, clasificacion y etiquetas. */
  readonly archivoForm = new FormGroup({
    carpetaId: new FormControl<number | null>(null),
    duenoId: new FormControl<number | null>(null),
    proximaRevision: new FormControl('', { nonNullable: true }),
    clasificacion: new FormControl<Clasificacion>('INTERNA', { nonNullable: true }),
    etiquetas: new FormControl('', { nonNullable: true, validators: etiquetasAceptables }),
  });
  readonly clasificaciones: Clasificacion[] = CLASIFICACIONES;
  readonly nombreClasificacion: Record<Clasificacion, string> = NOMBRE_CLASIFICACION;
  filas: FilaDeCarpeta[] = [];
  /** La lista de usuarios es del administrador: quien edita deja el dueno para la ficha del proceso. */
  usuarios: Usuario[] = [];
  /** La ficha se abre sola cuando la lista ya traia una carpeta; despues la abre y la cierra la persona. */
  archivoAbierto: boolean = false;

  private readonly authService: AuthService = inject(AuthService);
  readonly puedeEditar: boolean = this.authService.puedeEditar();
  readonly eligeDueno: boolean = this.authService.esAdministrador();
  isEdit: boolean = false;
  procesoId: number | null = null;
  cargando: boolean = false;
  enviando: boolean = false;
  noEncontrado: boolean = false;
  error: string | null = null;
  conflicto: boolean = false;

  // La version del ultimo GET; se reenvia al guardar para que la API sepa sobre que se edito
  private version: number | null = null;

  ngOnInit(): void {
    const id: string | null = this.route.snapshot.paramMap.get('id');
    if (id === null) {
      this.prepararArchivo();
      return;
    }
    this.isEdit = true;
    this.procesoId = Number(id);
    this.cargar();
  }

  /** Descarta lo escrito y deja el formulario en la version que hay ahora en la API. */
  recargar(): void {
    this.conflicto = false;
    this.error = null;
    this.cargar();
  }

  /** La persona abre y cierra la ficha; la pagina solo la abre sola una vez, o cuando algo de dentro no vale. */
  alPlegar(evento: Event): void {
    this.archivoAbierto = (evento.target as HTMLDetailsElement).open;
  }

  /** El aviso de las etiquetas, o null mientras la API las vaya a aceptar. */
  get avisoDeEtiquetas(): string | null {
    const falta = this.archivoForm.controls.etiquetas.errors?.['etiquetas'] as FaltaEnEtiquetas | undefined;
    return falta ? AVISO_DE_ETIQUETAS[falta] : null;
  }

  guardar(): void {
    if (!this.isEdit && this.archivoForm.invalid) {
      this.archivoAbierto = true;
      return;
    }
    this.enviando = true;
    this.error = null;
    this.conflicto = false;
    const valores = this.procesoForm.value;
    const solicitud: ProcesoRequest = {
      nombre: valores.nombre ?? '',
      categoria: valores.categoria ?? '',
      descripcion: valores.descripcion ?? '',
    };
    if (this.procesoId !== null && this.version === null) {
      // El proceso no se llego a cargar. Sin version no se puede editar, y crear otro seria peor
      this.enviando = false;
      this.error = $localize`The process could not be loaded, so the change cannot be saved. Reload the page.`;
      return;
    }
    const guardado$: Observable<Guardado> =
      this.procesoId === null
        ? this.procesoService.crear(solicitud).pipe(switchMap((proceso: Proceso) => this.archivar(proceso)))
        : this.procesoService
            .editar(this.procesoId, { ...solicitud, version: this.version as number })
            .pipe(map((proceso: Proceso) => ({ proceso, sinArchivar: false })));
    guardado$
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ proceso, sinArchivar }: Guardado) =>
          this.router.navigate(['/procesos', proceso.id], {
            queryParams: this.isEdit ? { editado: 1 } : { creado: 1, ...(sinArchivar ? { sinArchivar: 1 } : {}) },
          }),
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  /** Un proceso nuevo nace en la carpeta que tenia la lista (?carpeta=), y llegan las carpetas para elegir otra. */
  private prepararArchivo(): void {
    const carpeta = Number(this.route.snapshot.queryParamMap.get('carpeta'));
    this.carpetaService
      .listar()
      .pipe(
        catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((carpetas) => {
        this.filas = aplanarCarpetas(carpetas);
        if (carpetas.some((una) => una.id === carpeta)) {
          this.archivoForm.controls.carpetaId.setValue(carpeta);
          this.archivoAbierto = true;
        }
      });
    if (this.eligeDueno) {
      this.usuarioService
        .paraElegir()
        .pipe(
          catchError(() => of([] as Usuario[])),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((usuarios: Usuario[]) => (this.usuarios = usuarios));
    }
  }

  /**
   * Archivar es una segunda peticion, la ficha, con la version que devolvio el alta. El proceso ya existe aunque esa
   * falle: se abre igual y la pagina dice que la ficha quedo por hacer, en vez de dar el alta por fallida.
   */
  private archivar(proceso: Proceso): Observable<Guardado> {
    const ficha = fichaInicial(this.archivoForm.getRawValue(), proceso);
    if (ficha === null) {
      return of({ proceso, sinArchivar: false });
    }
    return this.procesoService.editarFicha(proceso.id, ficha).pipe(
      map((archivado: Proceso) => ({ proceso: archivado, sinArchivar: false })),
      catchError(() => of({ proceso, sinArchivar: true })),
    );
  }

  /** Pide el proceso y deja el formulario con sus valores y su version. */
  private cargar(): void {
    if (this.procesoId === null) {
      return;
    }
    this.cargando = true;
    this.procesoService
      .obtener(this.procesoId)
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (detalle: ProcesoDetalle) => {
          const { nombre, categoria, descripcion, version } = detalle.proceso;
          this.procesoForm.patchValue({ nombre, categoria, descripcion });
          this.version = version;
        },
        error: (error: HttpErrorResponse) => {
          // 404: el proceso es de otra tienda o ya se borro. 400: el id de la ruta no es un numero
          if (error.status === 404 || error.status === 400) {
            this.noEncontrado = true;
          } else {
            this.error = mensajeDeError(error);
          }
        },
      });
  }

  private mostrarError(error: HttpErrorResponse): void {
    if (error.status === 404) {
      // Otro usuario lo borro mientras se editaba
      this.noEncontrado = true;
    } else if (esConflictoDeVersion(error)) {
      // Alguien guardo entre el GET y el PUT. Lo escrito sigue en pantalla: recargar es decision del usuario
      this.conflicto = true;
    } else if (error.status === 409) {
      // El nombre es unico entre los procesos activos de la tienda
      this.procesoForm
        .get('nombre')
        ?.setErrors({ servidor: $localize`Another active process of your organization already uses this name.` });
      this.error = $localize`Check the highlighted fields.`;
    } else if (error.status === 400) {
      marcarErroresDelServidor(this.procesoForm, error);
      this.error = $localize`Check the highlighted fields.`;
    } else {
      this.error = mensajeDeError(error);
    }
  }
}
