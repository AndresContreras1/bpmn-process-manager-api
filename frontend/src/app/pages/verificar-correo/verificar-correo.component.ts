import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { mensajeDeError } from '../../helpers/errores-api';
import { tokenDelEnlace } from '../../helpers/token-del-enlace';
import { AuthService } from '../../service/auth.service';
import { CuentaService } from '../../service/cuenta.service';

/** F14: el enlace del correo de verificacion. Lo confirma al abrirse; sirve una sola vez. */
@Component({
  selector: 'app-verificar-correo',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './verificar-correo.component.html',
})
export class VerificarCorreoComponent implements OnInit {
  private readonly cuentaService: CuentaService = inject(CuentaService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly token: string | null = tokenDelEnlace();
  readonly conSesion: boolean = inject(AuthService).estaAutenticado();

  estado: 'verificando' | 'verificado' | 'fallo' = 'verificando';
  error: string | null = null;

  ngOnInit(): void {
    if (!this.token) {
      this.fallar($localize`This link has no token. Open the one in the e-mail again, whole.`);
      return;
    }
    this.cuentaService
      .verificarCorreo(this.token)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => (this.estado = 'verificado'),
        error: (error: HttpErrorResponse) =>
          this.fallar(
            error.status === 400
              ? $localize`This link does not work any more: it was used already or it expired. Ask for another one from your account.`
              : mensajeDeError(error),
          ),
      });
  }

  private fallar(mensaje: string): void {
    this.estado = 'fallo';
    this.error = mensaje;
  }
}
