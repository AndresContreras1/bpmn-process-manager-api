import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import { tokenDelEnlace } from '../../helpers/token-del-enlace';
import { CuentaService } from '../../service/cuenta.service';

/**
 * F14: quien vuelve de Google o Microsoft con el correo de una cuenta que ya existia confirma que es suya, con su clave
 * o con un codigo si tiene segundo factor. Desde entonces el proveedor tambien la abre.
 */
@Component({
  selector: 'app-vincular',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './vincular.component.html',
})
export class VincularComponent {
  private readonly cuentaService: CuentaService = inject(CuentaService);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  readonly token: string | null = tokenDelEnlace();

  conCodigo: boolean = false;
  clave: string = '';
  codigo: string = '';
  enviando: boolean = false;
  error: string | null = null;

  vincular(): void {
    if (!this.token) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.cuentaService
      .vincular(this.token, this.conCodigo ? null : this.clave, this.conCodigo ? this.codigo.trim() : null)
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.router.navigateByUrl('/procesos'),
        error: (error: HttpErrorResponse) =>
          (this.error =
            error.status === 401 ? $localize`That did not confirm it. Check it and try again.` : mensajeDeError(error)),
      });
  }
}
