import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, finalize, map, shareReplay, tap } from 'rxjs';

import { environment } from '../../environments/environment';
import { LoginRequest, LoginResponse } from '../models/login.model';
import { RolAcceso, Usuario } from '../models/usuario.model';
import { TokenService } from './token.service';

/** Inicia y cierra la sesion, y publica el usuario actual para toda la aplicacion. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly tokenService: TokenService = inject(TokenService);
  private readonly url: string = `${environment.apiUrl}/api/v1/auth`;

  // Guarda el usuario actual y se lo entrega de inmediato a cada componente que se suscribe
  private readonly usuarioSubject = new BehaviorSubject<Usuario | null>(this.usuarioGuardado());
  readonly usuario$: Observable<Usuario | null> = this.usuarioSubject.asObservable();

  // La renovacion en curso, mientras dura: varias peticiones que fallan a la vez esperan esta misma
  private renovacion$: Observable<string> | null = null;

  login(credenciales: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.url}/login`, credenciales)
      .pipe(tap((respuesta: LoginResponse) => this.guardarSesion(respuesta)));
  }

  /**
   * Cambia el token de refresco por un par nuevo y devuelve el token de acceso.
   *
   * Se hace una sola llamada aunque varias peticiones venzan al mismo tiempo: el token de refresco es de un solo
   * uso y mandarlo dos veces cierra la sesion, asi que todas comparten el observable en curso y se olvida al
   * terminar, para que la proxima vez se renueve de nuevo.
   */
  renovar(): Observable<string> {
    this.renovacion$ ??= this.http
      .post<LoginResponse>(`${this.url}/refresh`, { refreshToken: this.tokenService.obtenerRefresco() })
      .pipe(
        tap((respuesta: LoginResponse) => this.guardarSesion(respuesta)),
        map((respuesta: LoginResponse) => respuesta.accessToken),
        finalize(() => (this.renovacion$ = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    return this.renovacion$;
  }

  /**
   * Avisa a la API y borra la sesion del navegador, aunque la llamada falle (por ejemplo, con el token vencido).
   * El token de refresco va en el cuerpo para que la API cierre tambien su sesion y no quede uno usable.
   */
  logout(): Observable<void> {
    const refreshToken: string | null = this.tokenService.obtenerRefresco();
    return this.http
      .post<void>(`${this.url}/logout`, refreshToken === null ? null : { refreshToken })
      .pipe(finalize(() => this.cerrarSesionLocal()));
  }

  /** Borra la sesion del navegador. La usan el logout y el interceptor cuando la API responde 401. */
  cerrarSesionLocal(): void {
    this.tokenService.borrar();
    this.usuarioSubject.next(null);
  }

  estaAutenticado(): boolean {
    return this.tokenService.sesionAbierta();
  }

  tieneRol(...roles: RolAcceso[]): boolean {
    const usuario: Usuario | null = this.usuarioSubject.value;
    return usuario !== null && roles.includes(usuario.rolAcceso);
  }

  /** Administradores y editores crean, editan y publican; el rol de solo lectura solo consulta. */
  puedeEditar(): boolean {
    return this.tieneRol('ADMINISTRADOR', 'EDITOR');
  }

  /** Solo el administrador borra procesos y elementos del diagrama. */
  esAdministrador(): boolean {
    return this.tieneRol('ADMINISTRADOR');
  }

  private guardarSesion(respuesta: LoginResponse): void {
    this.tokenService.guardar(respuesta);
    this.usuarioSubject.next(respuesta.usuario);
  }

  private usuarioGuardado(): Usuario | null {
    return this.tokenService.sesionAbierta() ? this.tokenService.obtenerUsuario() : null;
  }
}
