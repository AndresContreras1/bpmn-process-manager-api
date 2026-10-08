import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import {
  ConfiguracionTienda,
  LIMITES_DE_SESION,
  NOMBRE_POLITICA,
  PoliticaEstructura,
} from '../../models/configuracion.model';
import { AuthService } from '../../service/auth.service';
import { ConfiguracionService } from '../../service/configuracion.service';

/**
 * La configuracion de la tienda: quien puede tocar la estructura de un diagrama y cuanto duran sus sesiones. Los
 * parametros de los socios simulados se ajustan desde la simulacion, no desde aqui.
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
  readonly limites: typeof LIMITES_DE_SESION = LIMITES_DE_SESION;

  configuracion: ConfiguracionTienda | null = null;
  politica: PoliticaEstructura = 'ADMINISTRADOR_Y_EDITOR';
  inactividad: number = LIMITES_DE_SESION.inactividad.maximo;
  duracion: number = LIMITES_DE_SESION.duracion.maximo;
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  ngOnInit(): void {
    if (!this.esAdministrador) {
      this.cargando = false;
      return;
    }
    this.configuracionService
      .obtener()
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (configuracion: ConfiguracionTienda) => this.mostrar(configuracion),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** Si hay algo distinto de lo guardado, y dentro de lo que la API acepta. */
  get sePuedeGuardar(): boolean {
    const configuracion: ConfiguracionTienda | null = this.configuracion;
    if (!configuracion || !this.dentroDeLosLimites()) {
      return false;
    }
    return (
      this.politica !== configuracion.politicaEstructura ||
      this.inactividad !== configuracion.inactividadSesionMinutos ||
      this.duracion !== configuracion.duracionSesionHoras
    );
  }

  dentroDeLosLimites(): boolean {
    const { inactividad, duracion } = LIMITES_DE_SESION;
    return (
      Number.isInteger(this.inactividad) &&
      this.inactividad >= inactividad.minimo &&
      this.inactividad <= inactividad.maximo &&
      Number.isInteger(this.duracion) &&
      this.duracion >= duracion.minimo &&
      this.duracion <= duracion.maximo
    );
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
        inactividadSesionMinutos: this.inactividad,
        duracionSesionHoras: this.duracion,
        version: configuracion.version,
      })
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (guardada: ConfiguracionTienda) => {
          this.mostrar(guardada);
          this.aviso = 'Saved.';
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private mostrar(configuracion: ConfiguracionTienda): void {
    this.configuracion = configuracion;
    this.politica = configuracion.politicaEstructura;
    this.inactividad = configuracion.inactividadSesionMinutos;
    this.duracion = configuracion.duracionSesionHoras;
  }
}
