import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, forkJoin, of } from 'rxjs';
import { toDataURL } from 'qrcode';

import { mensajeDeError } from '../../helpers/errores-api';
import { fueCancelada, soportaPasskeys } from '../../helpers/webauthn';
import {
  CodigosDeRecuperacion,
  EstadoSegundoFactor,
  IdentidadExterna,
  InscripcionTotp,
  Passkey,
  PasskeyRegistrada,
  SesionAbierta,
} from '../../models/seguridad.model';
import { Usuario } from '../../models/usuario.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { AuthService } from '../../service/auth.service';
import { CuentaService } from '../../service/cuenta.service';
import { SegundoFactorService } from '../../service/segundo-factor.service';
import { SeguridadDeLaCuentaService } from '../../service/seguridad-de-la-cuenta.service';

/** Como se llama cada factor con que se abrio una sesion. */
export const NOMBRE_FACTOR: Readonly<Record<string, string>> = {
  PASSWORD: $localize`Password`,
  TOTP: $localize`Authenticator app`,
  CODIGO_DE_RECUPERACION: $localize`Recovery code`,
  PROVEEDOR_EXTERNO: $localize`Google or Microsoft`,
  PASSKEY: $localize`Passkey`,
};

/**
 * F14: la seguridad de la cuenta de quien entro: su correo, la aplicacion de autenticacion con su QR, los codigos de
 * recuperacion, sus passkeys, los proveedores vinculados y las sesiones abiertas. Lo que quita un factor pide una
 * verificacion reciente, y el dialogo de la estructura la pide cuando la API lo dice.
 */
@Component({
  selector: 'app-seguridad',
  imports: [FormsModule, FechaPipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './seguridad.component.html',
})
export class SeguridadComponent implements OnInit {
  private readonly authService: AuthService = inject(AuthService);
  private readonly cuentaService: CuentaService = inject(CuentaService);
  private readonly segundoFactor: SegundoFactorService = inject(SegundoFactorService);
  private readonly seguridad: SeguridadDeLaCuentaService = inject(SeguridadDeLaCuentaService);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly usuario: Usuario | null = this.authService.usuarioActual();
  readonly passkeysEnEsteNavegador: boolean = soportaPasskeys();
  readonly nombreFactor: Readonly<Record<string, string>> = NOMBRE_FACTOR;

  cargando: boolean = true;
  estado: EstadoSegundoFactor | null = null;
  passkeys: Passkey[] = [];
  identidades: IdentidadExterna[] = [];
  sesiones: SesionAbierta[] = [];

  /** La inscripcion en curso de la aplicacion: su secreto, su QR y el codigo que la confirma. */
  inscripcion: InscripcionTotp | null = null;
  qr: string | null = null;
  codigoTotp: string = '';
  /** Los codigos de recuperacion que acaban de salir: se ven una vez. */
  codigosNuevos: string[] | null = null;
  etiquetaPasskey: string = '';
  /** La passkey que se esta renombrando, y su nombre nuevo. */
  renombrando: number | null = null;
  etiquetaNueva: string = '';

  ocupado: boolean = false;
  aviso: string | null = null;
  error: string | null = null;

  ngOnInit(): void {
    this.cargar();
  }

  reenviarVerificacion(): void {
    this.hacer(this.cuentaService.pedirVerificacion(), () => {
      this.aviso = $localize`The link is on its way to ${this.usuario?.email}:correo:. It works for 48 hours.`;
    });
  }

  empezarTotp(): void {
    this.hacer(this.segundoFactor.inscribirTotp(), (inscripcion: InscripcionTotp) => {
      this.inscripcion = inscripcion;
      this.codigoTotp = '';
      this.qr = null;
      toDataURL(inscripcion.uri, { margin: 1, width: 192 })
        .then((imagen: string) => (this.qr = imagen))
        .catch(() => (this.qr = null));
    });
  }

  confirmarTotp(): void {
    this.hacer(this.segundoFactor.confirmarTotp(this.codigoTotp.trim()), (codigos: CodigosDeRecuperacion) => {
      this.inscripcion = null;
      this.codigosNuevos = codigos.codigos.length > 0 ? codigos.codigos : null;
      this.aviso = $localize`Your authenticator app is ready: the login asks for its code from now on.`;
      this.cargar();
    });
  }

  cancelarTotp(): void {
    this.inscripcion = null;
    this.qr = null;
  }

  quitarTotp(): void {
    this.hacer(this.segundoFactor.quitarTotp(), () => {
      this.aviso = $localize`The authenticator app was removed.`;
      this.cargar();
    });
  }

  renovarCodigos(): void {
    this.hacer(this.segundoFactor.renovarCodigos(), (codigos: CodigosDeRecuperacion) => {
      this.codigosNuevos = codigos.codigos;
      this.aviso = $localize`Ten new recovery codes. The ones you had stop working.`;
      this.cargar();
    });
  }

  registrarPasskey(): void {
    this.hacer(
      this.segundoFactor.registrarPasskey(this.etiquetaPasskey.trim() || $localize`Passkey`),
      (registrada: PasskeyRegistrada) => {
        this.etiquetaPasskey = '';
        this.codigosNuevos = registrada.codigosDeRecuperacion.length > 0 ? registrada.codigosDeRecuperacion : null;
        this.aviso = $localize`The passkey "${registrada.passkey.etiqueta}:etiqueta:" is registered.`;
        this.cargar();
      },
    );
  }

  empezarARenombrar(passkey: Passkey): void {
    this.renombrando = passkey.id;
    this.etiquetaNueva = passkey.etiqueta;
  }

  renombrar(passkey: Passkey): void {
    this.hacer(this.segundoFactor.renombrarPasskey(passkey.id, this.etiquetaNueva.trim()), () => {
      this.renombrando = null;
      this.cargar();
    });
  }

  quitarPasskey(passkey: Passkey): void {
    this.hacer(this.segundoFactor.quitarPasskey(passkey.id), () => {
      this.aviso = $localize`The passkey "${passkey.etiqueta}:etiqueta:" was removed.`;
      this.cargar();
    });
  }

  desvincular(identidad: IdentidadExterna): void {
    this.hacer(this.seguridad.desvincular(identidad.id), () => {
      this.aviso = $localize`${identidad.proveedor}:proveedor: no longer opens your account.`;
      this.cargar();
    });
  }

  cerrarSesion(sesion: SesionAbierta): void {
    this.hacer(this.seguridad.cerrarSesion(sesion.id), () => {
      if (sesion.actual) {
        // Cerrar esta es salir: las demas pestañas se enteran
        this.authService.cerrarSesionLocal();
        this.router.navigate(['/login']);
        return;
      }
      this.aviso = $localize`That session is closed.`;
      this.cargar();
    });
  }

  cerrarLasDemas(): void {
    this.hacer(this.seguridad.cerrarLasDemas(), () => {
      this.aviso = $localize`Your other sessions are closed.`;
      this.cargar();
    });
  }

  nombresDeFactores(factores: string[]): string {
    return factores.map((factor: string) => this.nombreFactor[factor] ?? factor).join(' + ');
  }

  private cargar(): void {
    const o = <T>(peticion: Observable<T>, vacio: T): Observable<T> => peticion.pipe(catchError(() => of(vacio)));
    forkJoin({
      estado: o<EstadoSegundoFactor | null>(this.segundoFactor.estado(), null),
      passkeys: o<Passkey[]>(this.segundoFactor.passkeys(), []),
      identidades: o<IdentidadExterna[]>(this.seguridad.identidades(), []),
      sesiones: o<SesionAbierta[]>(this.seguridad.sesiones(), []),
    })
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((todo) => {
        this.estado = todo.estado;
        this.passkeys = todo.passkeys;
        this.identidades = todo.identidades;
        this.sesiones = todo.sesiones;
      });
  }

  private hacer<T>(peticion: Observable<T>, alTerminar: (respuesta: T) => void): void {
    this.ocupado = true;
    this.aviso = null;
    this.error = null;
    peticion
      .pipe(
        finalize(() => (this.ocupado = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: alTerminar,
        error: (error: unknown) => {
          if (fueCancelada(error)) {
            return;
          }
          this.error = mensajeDeError(error as HttpErrorResponse);
        },
      });
  }
}
