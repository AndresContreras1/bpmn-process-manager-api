import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Observable, catchError, finalize, of } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import {
  ConfiguracionTienda,
  LIMITES_DE_SESION,
  NOMBRE_POLITICA,
  PoliticaEstructura,
} from '../../models/configuracion.model';
import { PreferenciasComponent } from '../../components/preferencias/preferencias.component';
import { Dominio, PoliticaSegundoFactor } from '../../models/seguridad.model';
import { Empresa } from '../../models/empresa.model';
import { NOMBRE_ROL, RolAcceso } from '../../models/usuario.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { AuthService } from '../../service/auth.service';
import { SeguridadDeLaCuentaService } from '../../service/seguridad-de-la-cuenta.service';
import { ConfiguracionService } from '../../service/configuracion.service';

/**
 * La configuracion de la tienda: quien puede tocar la estructura de un diagrama y cuanto duran sus sesiones. Los
 * parametros de los socios simulados se ajustan desde la simulacion, no desde aqui.
 */
@Component({
  selector: 'app-configuracion',
  imports: [FormsModule, PreferenciasComponent, FechaPipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './configuracion.component.html',
})
export class ConfiguracionComponent implements OnInit {
  private readonly configuracionService: ConfiguracionService = inject(ConfiguracionService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly seguridad: SeguridadDeLaCuentaService = inject(SeguridadDeLaCuentaService);

  readonly esAdministrador: boolean = inject(AuthService).esAdministrador();
  readonly nombrePolitica: Record<PoliticaEstructura, string> = NOMBRE_POLITICA;
  readonly politicas: PoliticaEstructura[] = ['SOLO_ADMINISTRADOR', 'ADMINISTRADOR_Y_EDITOR'];
  readonly limites: typeof LIMITES_DE_SESION = LIMITES_DE_SESION;
  readonly politicasDeSegundoFactor: PoliticaSegundoFactor[] = ['OPCIONAL', 'ADMINISTRADORES', 'TODOS'];
  readonly nombrePoliticaDeSegundoFactor: Record<PoliticaSegundoFactor, string> = {
    OPCIONAL: $localize`Optional: each person decides`,
    ADMINISTRADORES: $localize`Required for administrators`,
    TODOS: $localize`Required for everybody`,
  };
  readonly roles: RolAcceso[] = ['SOLO_LECTURA', 'EDITOR', 'ADMINISTRADOR'];
  readonly nombreRol: Record<RolAcceso, string> = NOMBRE_ROL;

  configuracion: ConfiguracionTienda | null = null;
  politica: PoliticaEstructura = 'ADMINISTRADOR_Y_EDITOR';
  inactividad: number = LIMITES_DE_SESION.inactividad.maximo;
  /** F14: a quien pide el login un segundo factor, y si su gente entra solo con Google o Microsoft. */
  politicaSegundoFactor: PoliticaSegundoFactor = 'OPCIONAL';
  soloEntradaExterna: boolean = false;
  /** F14: los dominios de correo de la organizacion; quien entra con uno verificado se une solo. */
  dominios: Dominio[] = [];
  dominioNuevo: string = '';
  rolDelDominio: RolAcceso = 'SOLO_LECTURA';
  /** F14: la organizacion, para su baja; y lo que se escribe para confirmarla. */
  empresa: Empresa | null = null;
  confirmacionDeBaja: string = '';
  ocupado: boolean = false;
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
    this.cargarDominios();
    this.configuracionService
      .empresa()
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((empresa: Empresa | null) => (this.empresa = empresa));
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
      this.duracion !== configuracion.duracionSesionHoras ||
      this.politicaSegundoFactor !== configuracion.politicaSegundoFactor ||
      this.soloEntradaExterna !== configuracion.soloEntradaExterna
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
        politicaSegundoFactor: this.politicaSegundoFactor,
        soloEntradaExterna: this.soloEntradaExterna,
        version: configuracion.version,
      })
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (guardada: ConfiguracionTienda) => {
          this.mostrar(guardada);
          this.aviso = $localize`Saved.`;
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private mostrar(configuracion: ConfiguracionTienda): void {
    this.configuracion = configuracion;
    this.politica = configuracion.politicaEstructura;
    this.inactividad = configuracion.inactividadSesionMinutos;
    this.duracion = configuracion.duracionSesionHoras;
    this.politicaSegundoFactor = configuracion.politicaSegundoFactor;
    this.soloEntradaExterna = configuracion.soloEntradaExterna;
  }

  agregarDominio(): void {
    this.hacer(this.seguridad.agregarDominio(this.dominioNuevo.trim().toLowerCase(), this.rolDelDominio), () => {
      this.dominioNuevo = '';
      this.cargarDominios();
    });
  }

  verificarDominio(dominio: Dominio): void {
    this.hacer(this.seguridad.verificarDominio(dominio.id), (verificado: Dominio) => {
      this.aviso = verificado.verificado
        ? $localize`${dominio.dominio}:dominio: is verified.`
        : $localize`The TXT record of ${dominio.dominio}:dominio: was not found yet. DNS can take a while.`;
      this.cargarDominios();
    });
  }

  quitarDominio(dominio: Dominio): void {
    this.hacer(this.seguridad.quitarDominio(dominio.id), () => this.cargarDominios());
  }

  /** La confirmacion es el nombre de la organizacion, escrito otra vez; pide ademas una verificacion reciente. */
  pedirBaja(): void {
    this.hacer(this.seguridad.pedirBaja(this.confirmacionDeBaja), (empresa: Empresa) => {
      this.empresa = empresa;
      this.confirmacionDeBaja = '';
    });
  }

  cancelarBaja(): void {
    this.hacer(this.seguridad.cancelarBaja(), (empresa: Empresa) => (this.empresa = empresa));
  }

  private cargarDominios(): void {
    if (!this.esAdministrador) {
      return;
    }
    this.seguridad
      .dominios()
      .pipe(
        catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((dominios: Dominio[]) => (this.dominios = dominios));
  }

  private hacer<T>(peticion: Observable<T>, alTerminar: (respuesta: T) => void): void {
    this.ocupado = true;
    this.error = null;
    this.aviso = null;
    peticion
      .pipe(
        finalize(() => (this.ocupado = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: alTerminar, error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)) });
  }
}
