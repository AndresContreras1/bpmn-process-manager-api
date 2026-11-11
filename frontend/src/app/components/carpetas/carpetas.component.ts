import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { mensajeDeError } from '../../helpers/errores-api';
import { Carpeta, FilaDeCarpeta, aplanarCarpetas } from '../../models/carpeta.model';
import { CarpetaService } from '../../service/carpeta.service';

/** El arbol de carpetas al lado de la lista: elegir una filtra, y quien edita las crea, renombra y borra aqui. */
@Component({
  selector: 'app-carpetas',
  imports: [FormsModule],
  templateUrl: './carpetas.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class CarpetasComponent implements OnInit {
  readonly puedeEditar = input<boolean>(false);
  readonly elegir = output<number | null>();

  private readonly carpetas = inject(CarpetaService);
  private readonly destroyRef = inject(DestroyRef);

  filas: FilaDeCarpeta[] = [];
  actual: Carpeta | null = null;
  nombre = '';
  error: string | null = null;

  ngOnInit(): void {
    this.cargar();
  }

  elegirCarpeta(carpeta: Carpeta | null): void {
    this.actual = carpeta;
    this.nombre = '';
    this.error = null;
    this.elegir.emit(carpeta?.id ?? null);
  }

  crear(): void {
    if (this.nombre.trim()) {
      this.hacer(this.carpetas.crear(this.nombre.trim(), this.actual?.id ?? null));
    }
  }

  renombrar(): void {
    if (this.actual && this.nombre.trim()) {
      this.hacer(this.carpetas.editar(this.actual, this.nombre.trim(), this.actual.padreId));
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
          this.cargar();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private hacer(peticion: ReturnType<CarpetaService['crear']>): void {
    this.error = null;
    peticion.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.nombre = '';
        this.cargar();
      },
      error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
    });
  }

  private cargar(): void {
    this.carpetas
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (carpetas) => {
          this.filas = aplanarCarpetas(carpetas);
          // Lo elegido se vuelve a leer: su version cambio si se acaba de renombrar
          this.actual = carpetas.find((una) => una.id === this.actual?.id) ?? null;
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }
}
