import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, Subject, catchError, debounceTime, finalize, switchMap, tap } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { marcarErroresDelServidor, mensajeDeError } from '../../helpers/errores-api';
import { PageResponse } from '../../models/page-response.model';
import { RolProceso } from '../../models/rol-proceso.model';
import { NOMBRE_ROL, RolAcceso, Usuario } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';
import { RolProcesoService } from '../../service/rol-proceso.service';
import { FiltrosUsuario, UsuarioService } from '../../service/usuario.service';

/**
 * Los usuarios de la tienda. Solo un administrador entra aqui; la API responde 403 a los demas, asi que la
 * pantalla no promete nada que la API no vaya a cumplir.
 */
@Component({
  selector: 'app-usuarios',
  imports: [FormsModule, ReactiveFormsModule, ErrorCampoComponent, ModalConfirmarComponent],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.scss',
})
export class UsuariosComponent implements OnInit {
  private readonly usuarioService: UsuarioService = inject(UsuarioService);
  private readonly rolProcesoService: RolProcesoService = inject(RolProcesoService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  private readonly busqueda$ = new Subject<void>();

  readonly nombreRol: Record<RolAcceso, string> = NOMBRE_ROL;
  readonly roles: RolAcceso[] = ['ADMINISTRADOR', 'EDITOR', 'SOLO_LECTURA'];
  readonly esAdministrador: boolean = this.authService.esAdministrador();

  readonly altaForm = new FormGroup({
    nombre: new FormControl('', [Validators.required, Validators.maxLength(120)]),
    email: new FormControl('', [Validators.required, Validators.email]),
    rolAcceso: new FormControl<RolAcceso>('EDITOR', Validators.required),
  });

  filtros: FiltrosUsuario = { nombre: '', pagina: 0, orden: 'nombre', direccion: 'asc' };
  pagina: PageResponse<Usuario> | null = null;
  rolesDeProceso: RolProceso[] = [];
  seleccionado: Usuario | null = null;
  rolesElegidos: number[] = [];
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;
  /** La clave temporal recien creada. Se ensena una vez y no se vuelve a poder leer. */
  claveTemporal: { usuario: string; clave: string } | null = null;

  ngOnInit(): void {
    this.busqueda$
      .pipe(
        debounceTime(250),
        tap(() => {
          this.cargando = true;
          this.error = null;
        }),
        switchMap(() =>
          this.usuarioService.listar(this.filtros).pipe(
            catchError((error: HttpErrorResponse) => {
              this.error = mensajeDeError(error);
              this.cargando = false;
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina: PageResponse<Usuario>) => {
        this.pagina = pagina;
        this.cargando = false;
      });
    this.rolProcesoService
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((roles: RolProceso[]) => (this.rolesDeProceso = roles));
    this.buscar();
  }

  buscar(): void {
    this.busqueda$.next();
  }

  filtrar(): void {
    this.filtros.pagina = 0;
    this.buscar();
  }

  ordenarPor(campo: string): void {
    if (this.filtros.orden === campo) {
      this.filtros.direccion = this.filtros.direccion === 'asc' ? 'desc' : 'asc';
    } else {
      this.filtros.orden = campo;
      this.filtros.direccion = 'asc';
    }
    this.filtrar();
  }

  irAPagina(pagina: number): void {
    this.filtros.pagina = pagina;
    this.buscar();
  }

  /** Sin contrasena: la API inventa una temporal y la devuelve, y esta pantalla es el unico sitio donde se lee. */
  crear(): void {
    if (this.altaForm.invalid) {
      this.altaForm.markAllAsTouched();
      return;
    }
    this.enviando = true;
    this.limpiarMensajes();
    const valores = this.altaForm.getRawValue();
    this.usuarioService
      .crear({
        nombre: valores.nombre ?? '',
        email: valores.email ?? '',
        password: null,
        rolAcceso: valores.rolAcceso ?? 'EDITOR',
      })
      .pipe(finalize(() => (this.enviando = false)))
      .subscribe({
        next: (creado: Usuario) => {
          this.claveTemporal = creado.claveTemporal
            ? { usuario: creado.email, clave: creado.claveTemporal }
            : null;
          this.altaForm.reset({ rolAcceso: 'EDITOR' });
          this.buscar();
        },
        error: (error: HttpErrorResponse) => this.mostrarError(error, this.altaForm),
      });
  }

  cambiarRol(usuario: Usuario, rolAcceso: RolAcceso): void {
    this.limpiarMensajes();
    this.usuarioService
      .actualizar(usuario.id, { nombre: null, rolAcceso, activo: null, version: usuario.version })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.aviso = `${usuario.nombre} is now ${this.nombreRol[rolAcceso].toLowerCase()}.`;
          this.buscar();
        },
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  confirmarBaja(): void {
    const usuario: Usuario | null = this.seleccionado;
    if (!usuario) {
      return;
    }
    this.limpiarMensajes();
    this.usuarioService
      .actualizar(usuario.id, { nombre: null, rolAcceso: null, activo: false, version: usuario.version })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.aviso = `${usuario.nombre} can no longer sign in.`;
          this.buscar();
        },
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  reactivar(usuario: Usuario): void {
    this.limpiarMensajes();
    this.usuarioService
      .actualizar(usuario.id, { nombre: null, rolAcceso: null, activo: true, version: usuario.version })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.aviso = `${usuario.nombre} can sign in again.`;
          this.buscar();
        },
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  restablecerClave(usuario: Usuario): void {
    this.limpiarMensajes();
    this.usuarioService
      .restablecerClave(usuario.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (respuesta: Usuario) => {
          this.claveTemporal = respuesta.claveTemporal
            ? { usuario: respuesta.email, clave: respuesta.claveTemporal }
            : null;
          this.buscar();
        },
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  /** Abre el panel de los roles de proceso de un usuario: de ahi sale su bandeja de tareas. */
  abrirRoles(usuario: Usuario): void {
    this.seleccionado = this.seleccionado?.id === usuario.id ? null : usuario;
    this.rolesElegidos = [];
    this.limpiarMensajes();
  }

  alternarRol(id: number): void {
    this.rolesElegidos = this.rolesElegidos.includes(id)
      ? this.rolesElegidos.filter((elegido) => elegido !== id)
      : [...this.rolesElegidos, id];
  }

  guardarRoles(): void {
    const usuario: Usuario | null = this.seleccionado;
    if (!usuario) {
      return;
    }
    this.enviando = true;
    this.limpiarMensajes();
    this.usuarioService
      .rolesDeProceso(usuario.id, this.rolesElegidos)
      .pipe(finalize(() => (this.enviando = false)))
      .subscribe({
        next: () => {
          this.aviso = `The process roles of ${usuario.nombre} were saved.`;
          this.seleccionado = null;
        },
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  private mostrarError(error: HttpErrorResponse, formulario?: FormGroup): void {
    if (error.status === 400 && formulario) {
      marcarErroresDelServidor(formulario, error);
      this.error = 'Check the highlighted fields.';
    } else {
      this.error = mensajeDeError(error);
    }
  }

  private limpiarMensajes(): void {
    this.error = null;
    this.aviso = null;
    this.claveTemporal = null;
  }
}
