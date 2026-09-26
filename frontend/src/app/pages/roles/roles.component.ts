import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, Observable, Subject, catchError, debounceTime, finalize, switchMap, tap } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { marcarErroresDelServidor, mensajeDeError } from '../../helpers/errores-api';
import { PageResponse } from '../../models/page-response.model';
import { RolProceso } from '../../models/rol-proceso.model';
import { AuthService } from '../../service/auth.service';
import { RolProcesoService } from '../../service/rol-proceso.service';

/**
 * Los roles de proceso de la tienda: quien atiende cada trabajo. Una lane va ligada a uno, asi que un rol en uso
 * no se puede eliminar y la API lo dice con un 409; la lista lo avisa antes de que nadie lo intente.
 */
@Component({
  selector: 'app-roles',
  imports: [FormsModule, ReactiveFormsModule, ErrorCampoComponent, ModalConfirmarComponent],
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

  editar(rol: RolProceso): void {
    this.editando = rol;
    this.rolForm.setValue({ nombre: rol.nombre, descripcion: rol.descripcion ?? '' });
    this.limpiarMensajes();
  }

  cancelar(): void {
    this.editando = null;
    this.rolForm.reset({ nombre: '', descripcion: '' });
    this.limpiarMensajes();
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
          this.aviso = this.editando ? `"${rol.nombre}" was saved.` : `"${rol.nombre}" was created.`;
          this.cancelar();
          this.buscar();
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 400) {
            marcarErroresDelServidor(this.rolForm, error);
            this.error = 'Check the highlighted fields.';
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
          this.aviso = `"${rol.nombre}" was deleted.`;
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
