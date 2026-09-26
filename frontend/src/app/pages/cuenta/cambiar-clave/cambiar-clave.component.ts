import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ErrorCampoComponent } from '../../../components/error-campo/error-campo.component';
import { marcarErroresDelServidor, mensajeDeError } from '../../../helpers/errores-api';
import { AuthService } from '../../../service/auth.service';

/**
 * Cambiar la propia contrasena. La API cierra todas las sesiones del usuario, esta incluida, y devuelve las de una
 * nueva: el servicio de sesion las guarda, asi que quien la cambia sigue dentro sin volver a entrar.
 */
@Component({
  selector: 'app-cambiar-clave',
  imports: [ReactiveFormsModule, ErrorCampoComponent],
  templateUrl: './cambiar-clave.component.html',
})
export class CambiarClaveComponent {
  private readonly authService: AuthService = inject(AuthService);

  /** True cuando entro con una temporal: hasta cambiarla la API le responde 403 a todo lo demas. */
  readonly obligatorio = input<boolean>(false);
  readonly cambiada = output<void>();

  readonly claveForm = new FormGroup({
    actual: new FormControl('', Validators.required),
    nueva: new FormControl('', [Validators.required, Validators.minLength(8)]),
  });

  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  guardar(): void {
    if (this.claveForm.invalid) {
      this.claveForm.markAllAsTouched();
      return;
    }
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    const valores = this.claveForm.getRawValue();
    this.authService
      .cambiarClave(valores.actual ?? '', valores.nueva ?? '')
      .pipe(finalize(() => (this.enviando = false)))
      .subscribe({
        next: () => {
          this.aviso = 'Your password was changed. The sessions you had open elsewhere were closed.';
          this.claveForm.reset({ actual: '', nueva: '' });
          this.cambiada.emit();
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 400) {
            marcarErroresDelServidor(this.claveForm, error);
            this.error = 'Check the highlighted fields.';
          } else if (error.status === 401) {
            this.claveForm.get('actual')?.setErrors({ servidor: 'That is not your current password.' });
            this.error = 'Check the highlighted fields.';
          } else {
            this.error = mensajeDeError(error);
          }
        },
      });
  }
}
