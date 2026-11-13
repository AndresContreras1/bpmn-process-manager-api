import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnChanges,
  OnInit,
  inject,
  input,
  output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { mensajeDeError } from '../../helpers/errores-api';
import { Carpeta, FilaDeCarpeta, aplanarCarpetas, destinosDeCarpeta } from '../../models/carpeta.model';
import { CarpetaService } from '../../service/carpeta.service';

/**
 * El arbol de carpetas al lado de la lista: elegir una filtra, y quien edita las crea, renombra, mueve y borra aqui.
 * F19: la carpeta elegida llega de la direccion, asi que un enlace o una recarga la dejan marcada.
 */
@Component({
  selector: 'app-carpetas',
  imports: [FormsModule, RouterLink],
  templateUrl: './carpetas.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class CarpetasComponent implements OnInit, OnChanges {
  readonly puedeEditar = input<boolean>(false);
  /** El id de la carpeta que filtra la lista, o null para todas. */
  readonly seleccionada = input<number | null>(null);
  readonly elegir = output<number | null>();

  private readonly carpetas = inject(CarpetaService);
  private readonly destroyRef = inject(DestroyRef);

  private todas: Carpeta[] = [];
  filas: FilaDeCarpeta[] = [];
  /** Adonde se puede mover la carpeta elegida: ni ella ni lo que tiene dentro. */
  destinos: FilaDeCarpeta[] = [];
  actual: Carpeta | null = null;
  nombre = '';
  /** El padre nuevo al mover: null es la raiz. */
  destino: number | null = null;
  error: string | null = null;
  aviso: string | null = null;

  ngOnInit(): void {
    this.cargar();
  }

  ngOnChanges(): void {
    this.marcar(this.seleccionada());
  }

  elegirCarpeta(carpeta: Carpeta | null): void {
    this.marcar(carpeta?.id ?? null);
    this.nombre = '';
    this.error = null;
    this.aviso = null;
    this.elegir.emit(carpeta?.id ?? null);
  }

  crear(): void {
    if (this.nombre.trim()) {
      this.hacer(this.carpetas.crear(this.nombre.trim(), this.actual?.id ?? null), $localize`Folder created.`);
    }
  }

  renombrar(): void {
    if (this.actual && this.nombre.trim()) {
      this.hacer(this.carpetas.editar(this.actual, this.nombre.trim(), this.actual.padreId), $localize`Renamed.`);
    }
  }

  /** Moverla es cambiarle el padre con su nombre de siempre; la API se niega si quedaria dentro de si misma. */
  mover(): void {
    if (this.actual && this.destino !== this.actual.padreId) {
      this.hacer(this.carpetas.editar(this.actual, this.actual.nombre, this.destino), $localize`Folder moved.`);
    }
  }

  borrar(): void {
    if (!this.actual) {
      return;
    }
    this.carpetas
      .eliminar(this.actual.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.elegirCarpeta(null);
          this.aviso = $localize`Folder deleted.`;
          this.cargar();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private hacer(peticion: ReturnType<CarpetaService['crear']>, aviso: string): void {
    this.error = null;
    this.aviso = null;
    peticion.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.nombre = '';
        this.aviso = aviso;
        this.cargar();
      },
      error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
    });
  }

  /** Deja marcada la carpeta con ese id, con su version de la ultima lectura: cambia al renombrarla o moverla. */
  private marcar(id: number | null): void {
    this.actual = this.todas.find((una) => una.id === id) ?? null;
    this.destinos = this.actual ? destinosDeCarpeta(this.todas, this.actual.id) : [];
    this.destino = this.actual?.padreId ?? null;
  }

  private cargar(): void {
    this.carpetas
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (carpetas) => {
          this.todas = carpetas;
          this.filas = aplanarCarpetas(carpetas);
          this.marcar(this.actual?.id ?? this.seleccionada());
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }
}
