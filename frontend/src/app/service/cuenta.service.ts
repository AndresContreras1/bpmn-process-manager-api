import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../environments/environment';
import { LoginResponse } from '../models/login.model';
import { RolAcceso, Usuario } from '../models/usuario.model';
import { AuthService } from './auth.service';

/**
 * F14: lo que llega por correo y lo que se hace sin haber entrado: verificar el correo, recuperar la clave, aceptar una
 * invitacion y vincular un proveedor a una cuenta que ya existia. Cada enlace lleva su token despues de #.
 */
@Injectable({ providedIn: 'root' })
export class CuentaService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly authService: AuthService = inject(AuthService);
  private readonly url: string = `${environment.apiUrl}/api/v1/auth`;

  verificarCorreo(token: string): Observable<void> {
    return this.http.post<void>(`${this.url}/verificacion/confirmar`, { token });
  }

  /** Manda otra vez el enlace de verificacion a quien entro. */
  pedirVerificacion(): Observable<void> {
    return this.http.post<void>(`${this.url}/verificacion`, null);
  }

  /** Responde lo mismo exista o no el correo: no dice quien tiene cuenta. */
  pedirRecuperacion(email: string): Observable<void> {
    return this.http.post<void>(`${this.url}/recuperacion`, { email });
  }

  recuperarClave(token: string, nueva: string): Observable<void> {
    return this.http.post<void>(`${this.url}/recuperacion/confirmar`, { token, nueva });
  }

  aceptarInvitacion(token: string, nombre: string, password: string): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.url}/invitacion/aceptar`, { token, nombre, password });
  }

  /** Los proveedores con que se puede entrar en esta instalacion: google, microsoft, o ninguno. */
  proveedores(): Observable<string[]> {
    return this.http.get<string[]>(`${this.url}/proveedores`);
  }

  /** La direccion que manda el navegador al proveedor; vuelve a la web con la sesion abierta, o a medias. */
  entradaConProveedor(proveedor: string): string {
    return `${environment.apiUrl}/api/v1/auth/oidc/${encodeURIComponent(proveedor)}`;
  }

  /** Confirma que la cuenta a la que volvio un proveedor es suya, y abre la sesion. */
  vincular(token: string, password: string | null, codigo: string | null): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.url}/identidades/vinculo`, { token, password, codigo })
      .pipe(tap((respuesta: LoginResponse) => this.authService.abrir(respuesta)));
  }

  /** Solo el administrador: la persona recibe un enlace para entrar con el rol que se le da. */
  invitar(email: string, rolAcceso: RolAcceso): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/api/v1/usuarios/invitaciones`, { email, rolAcceso });
  }
}
