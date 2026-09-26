import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import {
  ConfiguracionTienda,
  NOMBRE_POLITICA,
  PoliticaEstructura,
} from '../../models/configuracion.model';
import { AuthService } from '../../service/auth.service';
import { ConfiguracionService } from '../../service/configuracion.service';

/**
 * La configuracion de la tienda. Por ahora una sola decision, la que cambia quien puede tocar la estructura de un
 * diagrama; los parametros de los socios simulados se ajustan desde la simulacion, no desde aqui.
 */
@Component({
  selector: 'app-configuracion',
  imports: [FormsModule],
  templateUrl: './configuracion.component.html',
})
export class ConfiguracionComponent implements OnInit {
  private readonly configuracionService: ConfiguracionService = inject(ConfiguracionService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly esAdministrador: boolean = inject(AuthService).esAdministrador();
  readonly nombrePolitica: Record<PoliticaEstructura, string> = NOMBRE_POLITICA;
  readonly politicas: PoliticaEstructura[] = ['SOLO_ADMINISTRADOR', 'ADMINISTRADOR_Y_EDITOR'];

  configuracion: ConfiguracionTienda | null = null;
  politica: PoliticaEstructura = 'ADMINISTRADOR_Y_EDITOR';
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  ngOnInit(): void {
    this.configuracionService
      .obtener()
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (configuracion: ConfiguracionTienda) => {
          this.configuracion = configuracion;
          this.politica = configuracion.politicaEstructura;
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  guardar(): void {
    const configuracion: ConfiguracionTienda | null = this.configuracion;
    if (!configuracion) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    this.configuracionService
      .guardar({
        politicaEstructura: this.politica,
        modoSimulacion: configuracion.modoSimulacion,
        // Vacio a proposito: lo de los socios se ajusta donde se simula
        simulacion: null,
        version: configuracion.version,
      })
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (guardada: ConfiguracionTienda) => {
          this.configuracion = guardada;
          this.aviso = 'Saved.';
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }
}
