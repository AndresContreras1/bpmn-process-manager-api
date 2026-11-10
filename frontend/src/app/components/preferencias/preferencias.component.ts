import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import { zonasHorarias } from '../../helpers/zonas';
import { Idioma, Preferencias, PreferenciasRequest } from '../../models/preferencias.model';
import { IDIOMAS, IdiomaService, OpcionDeIdioma } from '../../service/idioma.service';
import { PreferenciasService, zonaDelNavegador } from '../../service/preferencias.service';

/**
 * F12: el idioma y la zona horaria, en dos sabores. Los propios, en la cuenta, donde vacio quiere decir seguir a la
 * organizacion; y los de la organizacion, en su configuracion, que valen para quien no eligio los suyos. Guardar un
 * idioma distinto del de esta compilacion abre la misma pantalla en el idioma nuevo.
 */
@Component({
  selector: 'app-preferencias',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './preferencias.component.html',
})
export class PreferenciasComponent implements OnInit {
  private readonly preferenciasService: PreferenciasService = inject(PreferenciasService);
  private readonly idiomaService: IdiomaService = inject(IdiomaService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  /** De quien son: de la persona que entro o de su organizacion. */
  readonly de = input<'persona' | 'organizacion'>('persona');
  /** El comienzo de los id de la tarjeta, que es lo que buscan las pruebas: cuenta-idioma, configuracion-zona... */
  readonly prefijo = input<string>('preferencias');

  readonly idiomas: readonly OpcionDeIdioma[] = IDIOMAS;
  readonly zonaDelNavegador: string = zonaDelNavegador();

  preferencias: Preferencias | null = null;
  zonas: string[] = zonasHorarias();
  /** Lo elegido; vacio, en las propias, es seguir a la organizacion. */
  idioma: Idioma | '' = '';
  zona: string = '';
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  ngOnInit(): void {
    this.preferenciasService
      .cargar()
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (preferencias: Preferencias) => this.mostrar(preferencias),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  nombreDe(codigo: Idioma): string {
    return IDIOMAS.find((opcion: OpcionDeIdioma) => opcion.codigo === codigo)?.nombre ?? codigo;
  }

  get hayCambios(): boolean {
    const guardadas: Preferencias | null = this.preferencias;
    if (!guardadas) {
      return false;
    }
    if (this.de() === 'organizacion') {
      return this.idioma !== guardadas.idiomaDeLaOrganizacion || this.zona !== guardadas.zonaHorariaDeLaOrganizacion;
    }
    return this.idioma !== (guardadas.idiomaPropio ?? '') || this.zona !== (guardadas.zonaHorariaPropia ?? '');
  }

  guardar(): void {
    if (!this.hayCambios || this.enviando) {
      return;
    }
    const cambio: PreferenciasRequest = { idioma: this.idioma || null, zonaHoraria: this.zona || null };
    const peticion =
      this.de() === 'organizacion'
        ? this.preferenciasService.cambiarLasDeLaOrganizacion(cambio)
        : this.preferenciasService.cambiarLasPropias(cambio);
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    peticion
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (preferencias: Preferencias) => {
          this.mostrar(preferencias);
          this.aviso = $localize`Saved. Dates are now shown in ${preferencias.zonaHoraria}:zona:.`;
          // El idioma que vale es otra compilacion: la misma pantalla, en la carpeta de ese idioma
          this.idiomaService.ir(preferencias.idioma);
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private mostrar(preferencias: Preferencias): void {
    this.preferencias = preferencias;
    if (this.de() === 'organizacion') {
      this.idioma = preferencias.idiomaDeLaOrganizacion;
      this.zona = preferencias.zonaHorariaDeLaOrganizacion;
    } else {
      this.idioma = preferencias.idiomaPropio ?? '';
      this.zona = preferencias.zonaHorariaPropia ?? '';
    }
    this.zonas = zonasHorarias(preferencias.zonaHorariaPropia, preferencias.zonaHorariaDeLaOrganizacion);
  }
}
