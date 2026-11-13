import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, debounceTime, finalize, switchMap, tap } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { marcarErroresDelServidor, mensajeDeError } from '../../helpers/errores-api';
import { PageResponse } from '../../models/page-response.model';
import { ProcesoDelRol, RolProceso } from '../../models/rol-proceso.model';
import { AuthService } from '../../service/auth.service';
import { RolProcesoService } from '../../service/rol-proceso.service';

/**
 * Los roles de proceso de la tienda: quien atiende cada trabajo. Una lane va ligada a uno, asi que un rol en uso
 * no se puede eliminar y la API lo dice con un 409; la lista lo avisa antes de que nadie lo intente.
 */
@Component({
  selector: 'app-roles',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, ErrorCampoComponent, ModalConfirmarComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './roles.component.html',
})
export class RolesComponent implements OnInit {
  private readonly rolProcesoService: RolProcesoService = inject(RolProcesoService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  private readonly busqueda$ = new Subject<void>();

  readonly esAdministrador: boolean = inject(AuthService).esAdministrador();

  readonly rolForm = new FormGroup({
    nombre: new FormControl('', [Validators.required, Validators.maxLength(80)]),
    descripcion: new FormControl('', Validators.maxLength(300)),
  });

  nombre: string = '';
  paginaActual: number = 0;
  pagina: PageResponse<RolProceso> | null = null;
  /** El rol que se esta editando; sin ninguno, el formulario crea uno nuevo. */
  editando: RolProceso | null = null;
  seleccionado: RolProceso | null = null;
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;
  /** HU-20.4: el rol cuya lista de procesos esta abierta, y esa lista cuando llega. */
  abierto: number | null = null;
  procesosDelRol: ProcesoDelRol[] | null = null;
  errorDeProcesos: string | null = null;

  ngOnInit(): void {
    this.busqueda$
      .pipe(
        debounceTime(250),
        tap(() => {
          this.cargando = true;
          this.error = null;
        }),
        switchMap(() =>
          this.rolProcesoService.pagina(this.paginaActual, this.nombre).pipe(
            catchError((error: HttpErrorResponse) => {
              this.error = mensajeDeError(error);
              this.cargando = false;
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina: PageResponse<RolProceso>) => {
        this.pagina = pagina;
        this.cargando = false;
      });
    this.buscar();
  }

  buscar(): void {
    this.busqueda$.next();
  }

  filtrar(): void {
    this.paginaActual = 0;
    this.buscar();
  }

  irAPagina(pagina: number): void {
    this.paginaActual = pagina;
    this.buscar();
  }

  /** Abre o cierra la lista de procesos que usan el rol: la pide al abrirla, porque la lista de roles solo cuenta. */
  verProcesos(rol: RolProceso): void {
    if (this.abierto === rol.id) {
      this.abierto = null;
      return;
    }
    this.abierto = rol.id;
    this.procesosDelRol = null;
    this.errorDeProcesos = null;
    this.rolProcesoService
      .obtener(rol.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (completo: RolProceso) => {
          if (this.abierto === rol.id) {
            this.procesosDelRol = completo.procesos ?? [];
          }
        },
        error: (error: HttpErrorResponse) => (this.errorDeProcesos = mensajeDeError(error)),
      });
  }

  editar(rol: RolProceso): void {
    this.editando = rol;
    this.rolForm.setValue({ nombre: rol.nombre, descripcion: rol.descripcion ?? '' });
    this.limpiarMensajes();
  }

  cancelar(): void {
    this.vaciarFormulario();
    this.limpiarMensajes();
  }

  /** Vacia el formulario sin tocar los mensajes: guardar deja uno puesto y no puede borrarselo a si mismo. */
  private vaciarFormulario(): void {
    this.editando = null;
    this.rolForm.reset({ nombre: '', descripcion: '' });
  }

  guardar(): void {
    if (this.rolForm.invalid) {
      this.rolForm.markAllAsTouched();
      return;
    }
    this.enviando = true;
    this.limpiarMensajes();
    const valores = this.rolForm.getRawValue();
    const datos = { nombre: valores.nombre ?? '', descripcion: valores.descripcion ?? '' };
    const guardado$: Observable<RolProceso> = this.editando
      ? this.rolProcesoService.editar(this.editando.id, { ...datos, version: this.editando.version })
      : this.rolProcesoService.crear(datos);
    guardado$
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (rol: RolProceso) => {
          this.aviso = this.editando ? $localize`"${rol.nombre}" was saved.` : $localize`"${rol.nombre}" was created.`;
          this.vaciarFormulario();
          this.buscar();
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 400) {
            marcarErroresDelServidor(this.rolForm, error);
            this.error = $localize`Check the highlighted fields.`;
          } else {
            this.error = mensajeDeError(error);
          }
        },
      });
  }

  confirmarBorrado(): void {
    const rol: RolProceso | null = this.seleccionado;
    if (!rol) {
      return;
    }
    this.limpiarMensajes();
    this.rolProcesoService
      .eliminar(rol.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.aviso = $localize`"${rol.nombre}" was deleted.`;
          this.buscar();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private limpiarMensajes(): void {
    this.error = null;
    this.aviso = null;
  }
}
