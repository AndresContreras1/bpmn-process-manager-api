import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, finalize, tap } from 'rxjs';

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

  login(credenciales: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.url}/login`, credenciales).pipe(
      tap((respuesta: LoginResponse) => {
        this.tokenService.guardar(respuesta.accessToken, respuesta.usuario, respuesta.expiresIn);
        this.usuarioSubject.next(respuesta.usuario);
      }),
    );
  }

  /** Avisa a la API y borra la sesion del navegador, aunque la llamada falle (por ejemplo, con el token vencido). */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.url}/logout`, {}).pipe(finalize(() => this.cerrarSesionLocal()));
  }

  /** Borra la sesion del navegador. La usan el logout y el interceptor cuando la API responde 401. */
  cerrarSesionLocal(): void {
    this.tokenService.borrar();
    this.usuarioSubject.next(null);
  }

  estaAutenticado(): boolean {
    return this.tokenService.tokenVigente();
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

  private usuarioGuardado(): Usuario | null {
    return this.tokenService.tokenVigente() ? this.tokenService.obtenerUsuario() : null;
  }
}
