import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { tokenDelEnlace } from '../../helpers/token-del-enlace';
import { CuentaService } from '../../service/cuenta.service';

/**
 * F14: el enlace del correo de recuperacion. La contraseña nueva cierra todas las sesiones que la persona tenia
 * abiertas; despues entra con ella.
 */
@Component({
  selector: 'app-restablecer-clave',
  imports: [ReactiveFormsModule, RouterLink, ErrorCampoComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './restablecer-clave.component.html',
})
export class RestablecerClaveComponent {
  private readonly cuentaService: CuentaService = inject(CuentaService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  readonly token: string | null = tokenDelEnlace();

  readonly nueva = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(15), Validators.maxLength(72)],
  });
  enviando: boolean = false;
  hecho: boolean = false;
  error: string | null = null;

  guardar(): void {
    if (!this.token || this.nueva.invalid) {
      this.nueva.markAsTouched();
      return;
    }
    this.enviando = true;
    this.error = null;
    this.cuentaService
      .recuperarClave(this.token, this.nueva.value)
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => (this.hecho = true),
        // Una clave que no pasa la politica no gasta el enlace: el mensaje de la API dice que corregir
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }
}
