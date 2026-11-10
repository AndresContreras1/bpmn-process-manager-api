import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { Observable, catchError, finalize, of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { fueCancelada, soportaPasskeys } from '../../helpers/webauthn';
import { LoginResponse } from '../../models/login.model';
import { AuthService } from '../../service/auth.service';
import { CuentaService } from '../../service/cuenta.service';
import { SegundoFactorService } from '../../service/segundo-factor.service';

/** Como se llama cada proveedor en el boton. */
const NOMBRE_PROVEEDOR: Record<string, string> = { google: 'Google', microsoft: 'Microsoft' };

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, FormsModule, RouterLink, ErrorCampoComponent],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly authService: AuthService = inject(AuthService);
  private readonly cuentaService: CuentaService = inject(CuentaService);
  private readonly segundoFactor: SegundoFactorService = inject(SegundoFactorService);
  private readonly router: Router = inject(Router);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  /** La cuenta de demostracion solo existe cuando la API corre en dev. */
  readonly demo: boolean = environment.demo;
  readonly passkeysEnEsteNavegador: boolean = soportaPasskeys();

  readonly loginForm = new FormGroup({
    email: new FormControl('', [Validators.required, Validators.email, Validators.maxLength(254)]),
    password: new FormControl('', [Validators.required, Validators.maxLength(72)]),
  });

  /** F14: con segundo factor, la clave (o el proveedor) deja el login a medias y falta el codigo. */
  paso: 'clave' | 'segundo-factor' = 'clave';
  codigo: string = '';
  proveedores: string[] = [];
  enviando: boolean = false;
  error: string | null = null;
  sesionVencida: boolean = false;
  /** F13: se salio desde otra pestaña de la web. */
  sesionCerrada: boolean = false;
  private volver: string = '/procesos';

  ngOnInit(): void {
    const parametros: ParamMap = this.route.snapshot.queryParamMap;
    this.sesionVencida = parametros.get('sesion') === 'vencida';
    this.sesionCerrada = parametros.get('sesion') === 'cerrada';
    // Vuelta de un proveedor: con segundo factor, el login quedo a medias; si no entro, dice por que
    if (parametros.get('paso') === 'segundo-factor') {
      this.paso = 'segundo-factor';
    }
    const error: string | null = parametros.get('error');
    if (error) {
      this.error = errorDelProveedor(error);
    }
    const volver: string | null = parametros.get('volver');
    // Solo rutas internas: un enlace manipulado no puede mandar al usuario a otro sitio
    if (volver?.startsWith('/') && !volver.startsWith('//')) {
      this.volver = volver;
    }
    this.cuentaService
      .proveedores()
      .pipe(
        catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((proveedores: string[]) => (this.proveedores = proveedores));
  }

  nombreDe(proveedor: string): string {
    return NOMBRE_PROVEEDOR[proveedor] ?? proveedor;
  }

  entradaCon(proveedor: string): string {
    return this.cuentaService.entradaConProveedor(proveedor);
  }

  usarCuentaDemo(): void {
    this.loginForm.setValue({ email: 'admin@demo.com', password: 'every-order-on-time' });
  }

  iniciarSesion(): void {
    const credenciales = { email: this.loginForm.value.email ?? '', password: this.loginForm.value.password ?? '' };
    this.ejecutar(this.authService.login(credenciales), (error: HttpErrorResponse) =>
      error.status === 401 ? $localize`Wrong email or password.` : mensajeDeError(error),
    );
  }

  /** F14: el codigo de la aplicacion, o uno de recuperacion, termina el login a medias. */
  enviarCodigo(): void {
    this.ejecutar(this.segundoFactor.completarConCodigo(this.codigo.trim()), (error: HttpErrorResponse) =>
      error.status === 401 ? $localize`That code does not work. Check it and try again.` : mensajeDeError(error),
    );
  }

  segundoFactorConPasskey(): void {
    this.ejecutar(this.segundoFactor.completarConPasskey(), mensajeDePasskey);
  }

  /** F14: sin clave, la passkey dice quien es. */
  entrarConPasskey(): void {
    this.ejecutar(this.segundoFactor.entrarConPasskey(), mensajeDePasskey);
  }

  /** Empezar otra vez: el login a medias se descarta. */
  volverALaClave(): void {
    this.paso = 'clave';
    this.codigo = '';
    this.error = null;
  }

  private ejecutar(peticion: Observable<LoginResponse>, mensaje: (error: HttpErrorResponse) => string): void {
    this.enviando = true;
    this.error = null;
    peticion
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (respuesta: LoginResponse) => {
          if (respuesta.segundoFactorPendiente) {
            this.paso = 'segundo-factor';
            return;
          }
          this.router.navigateByUrl(this.volver);
        },
        error: (error: unknown) => {
          if (!fueCancelada(error)) {
            this.error = mensaje(error as HttpErrorResponse);
          }
        },
      });
  }
}

function mensajeDePasskey(error: HttpErrorResponse): string {
  return error.status === 401 ? $localize`That passkey could not sign you in.` : mensajeDeError(error);
}

/** Lo que dice la API cuando un proveedor no deja entrar (ResultadoEntradaExterna). */
function errorDelProveedor(motivo: string): string {
  switch (motivo) {
    case 'sin-cuenta':
      return $localize`No account uses the e-mail of that provider. Ask your administrator for an invitation.`;
    case 'correo-no-verificado':
      return $localize`The provider has not verified that e-mail, so it cannot sign you in.`;
    case 'cuenta-inactiva':
      return $localize`That account is deactivated.`;
    default:
      return $localize`Signing in with the provider did not work. Try again.`;
  }
}
