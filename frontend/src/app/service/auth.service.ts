import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';

import { environment } from '../../environments/environment';
import { LoginRequest, LoginResponse } from '../models/login.model';
import { RolAcceso, Usuario } from '../models/usuario.model';
import { SesionLocalService } from './sesion-local.service';

/**
 * Inicia y cierra la sesion, y publica el usuario actual para toda la aplicacion.
 *
 * D29: la sesion son dos cookies HttpOnly que pone y quita la API; este servicio nunca ve un token. Lo que cambia algo
 * lleva ademas el token CSRF, que HttpClient copia solo de la cookie XSRF-TOKEN a la cabecera X-XSRF-TOKEN.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly sesionLocal: SesionLocalService = inject(SesionLocalService);
  private readonly url: string = `${environment.apiUrl}/api/v1/auth`;

  // Guarda el usuario actual y se lo entrega de inmediato a cada componente que se suscribe
  private readonly usuarioSubject = new BehaviorSubject<Usuario | null>(this.sesionLocal.obtenerUsuario());
  readonly usuario$: Observable<Usuario | null> = this.usuarioSubject.asObservable();

  // La renovacion en curso, mientras dura: varias peticiones que fallan a la vez esperan esta misma
  private renovacion$: Observable<void> | null = null;

  /**
   * Pide a la API la cookie XSRF-TOKEN. La aplicacion lo hace al arrancar, porque el login ya la necesita; si la API
   * no contesta, la aplicacion arranca igual y el login mostrara el error.
   */
  prepararCsrf(): Observable<void> {
    return this.http.get<void>(`${this.url}/csrf`).pipe(catchError(() => of(undefined)));
  }

  login(credenciales: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.url}/login`, credenciales)
      .pipe(tap((respuesta: LoginResponse) => this.guardarSesion(respuesta)));
  }

  /**
   * Renueva la sesion con la cookie de refresco, que el navegador manda solo a /api/v1/auth.
   *
   * Se hace una sola llamada aunque varias peticiones venzan al mismo tiempo: el refresh token es de un solo uso y
   * mandarlo dos veces cierra la sesion, asi que todas comparten el observable en curso y se olvida al terminar, para
   * que la proxima vez se renueve de nuevo.
   */
  renovar(): Observable<void> {
    this.renovacion$ ??= this.http.post<LoginResponse>(`${this.url}/refresh`, null).pipe(
      tap((respuesta: LoginResponse) => this.guardarSesion(respuesta)),
      map((): void => undefined),
      finalize(() => (this.renovacion$ = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.renovacion$;
  }

  /**
   * Cambia la contrasena y abre una sesion nueva: la API cierra todas las de este usuario, esta incluida, y deja las
   * cookies de la nueva.
   */
  cambiarClave(actual: string, nueva: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.url}/password`, { actual, nueva })
      .pipe(tap((respuesta: LoginResponse) => this.guardarSesion(respuesta)));
  }

  /**
   * Avisa a la API, que cierra la sesion y borra sus cookies, y olvida al usuario aunque la llamada falle. La API
   * responde lo mismo con el acceso vencido, asi que siempre se puede salir.
   */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.url}/logout`, null).pipe(finalize(() => this.cerrarSesionLocal()));
  }

  /** Olvida al usuario. La usan el logout y el interceptor cuando la renovacion es rechazada. */
  cerrarSesionLocal(): void {
    this.sesionLocal.borrar();
    this.usuarioSubject.next(null);
  }

  estaAutenticado(): boolean {
    return this.sesionLocal.hayUsuario();
  }

  /** El usuario de la sesion, tal como lo devolvio la API. Null cuando no hay sesion abierta. */
  usuarioActual(): Usuario | null {
    return this.usuarioSubject.value;
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
    this.sesionLocal.guardar(respuesta.usuario);
    this.usuarioSubject.next(respuesta.usuario);
  }
}
