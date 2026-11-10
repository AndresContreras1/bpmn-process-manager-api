import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { CuentaService } from '../../service/cuenta.service';

/**
 * F14: pedir el enlace para elegir una contraseña nueva. Responde lo mismo exista o no la cuenta, para no decirle a
 * nadie que correos tienen una.
 */
@Component({
  selector: 'app-recuperar-clave',
  imports: [ReactiveFormsModule, RouterLink, ErrorCampoComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './recuperar-clave.component.html',
})
export class RecuperarClaveComponent {
  private readonly cuentaService: CuentaService = inject(CuentaService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly email = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.email, Validators.maxLength(254)],
  });
  enviando: boolean = false;
  enviado: boolean = false;
  error: string | null = null;

  pedir(): void {
    if (this.email.invalid) {
      this.email.markAsTouched();
      return;
    }
    this.enviando = true;
    this.error = null;
    this.cuentaService
      .pedirRecuperacion(this.email.value.trim())
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => (this.enviado = true),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }
}
