import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import { PageResponse } from '../../models/page-response.model';
import { HistorialCambio, cambioEnIngles } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { ConfiguracionService } from '../../service/configuracion.service';
import { FechaPipe } from '../../pipes/fecha.pipe';

/** Todo lo que ha pasado en la tienda, de lo mas nuevo a lo mas viejo. */
@Component({
  selector: 'app-historial',
  imports: [FechaPipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './historial.component.html',
})
export class HistorialComponent implements OnInit {
  private readonly configuracionService: ConfiguracionService = inject(ConfiguracionService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly cambioEnIngles: (descripcion: string) => string = cambioEnIngles;
  /** La API responde 403 al que no es administrador, asi que la pantalla no lo intenta. */
  readonly esAdministrador: boolean = inject(AuthService).esAdministrador();

  pagina: PageResponse<HistorialCambio> | null = null;
  cargando: boolean = true;
  error: string | null = null;

  ngOnInit(): void {
    if (this.esAdministrador) {
      this.cargar(0);
    } else {
      this.cargando = false;
    }
  }

  cargar(pagina: number): void {
    this.cargando = true;
    this.error = null;
    this.configuracionService
      .historial(pagina)
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (respuesta: PageResponse<HistorialCambio>) => (this.pagina = respuesta),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }
}
