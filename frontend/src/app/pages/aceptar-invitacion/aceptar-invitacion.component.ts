import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize, switchMap } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { tokenDelEnlace } from '../../helpers/token-del-enlace';
import { LoginResponse } from '../../models/login.model';
import { Usuario, inicioDe } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';
import { CuentaService } from '../../service/cuenta.service';

/**
 * F14: el enlace de una invitacion por correo. Quien la acepta pone su nombre y su contraseña, entra con el rol que le
 * dieron y su correo queda verificado, porque el enlace le llego.
 */
@Component({
  selector: 'app-aceptar-invitacion',
  imports: [ReactiveFormsModule, ErrorCampoComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './aceptar-invitacion.component.html',
})
export class AceptarInvitacionComponent {
  private readonly cuentaService: CuentaService = inject(CuentaService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  readonly token: string | null = tokenDelEnlace();

  readonly formulario = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(15), Validators.maxLength(72)],
    }),
  });
  enviando: boolean = false;
  error: string | null = null;

  aceptar(): void {
    if (!this.token || this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const { nombre, password } = this.formulario.getRawValue();
    this.enviando = true;
    this.error = null;
    this.cuentaService
      .aceptarInvitacion(this.token, nombre.trim(), password)
      .pipe(
        // La invitacion crea la cuenta; entrar con ella es el paso que la persona espera despues
        switchMap((usuario: Usuario) => this.authService.login({ email: usuario.email, password })),
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (respuesta: LoginResponse) => this.router.navigateByUrl(inicioDe(respuesta.usuario.rolAcceso)),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }
}
