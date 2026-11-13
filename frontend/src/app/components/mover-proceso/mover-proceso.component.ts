import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { esConflictoDeVersion, mensajeDeError } from '../../helpers/errores-api';
import { retenerFoco } from '../../helpers/teclado';
import { FilaDeCarpeta, aplanarCarpetas } from '../../models/carpeta.model';
import { Proceso, fichaConCarpeta } from '../../models/proceso.model';
import { CarpetaService } from '../../service/carpeta.service';
import { ProcesoService } from '../../service/proceso.service';

/**
 * Mover un proceso a otra carpeta, sin arrastrar: el arbol es un grupo de botones de opcion, que se recorre con las
 * flechas como cualquier otro, y Mover guarda la ficha con la carpeta nueva. Escape cierra, y el foco vuelve al boton
 * que abrio el dialogo.
 */
@Component({
  selector: 'app-mover-proceso',
  imports: [FormsModule],
  templateUrl: './mover-proceso.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class MoverProcesoComponent implements OnInit {
  readonly proceso = input.required<Proceso>();
  readonly movido = output<Proceso>();
  readonly cerrar = output<void>();

  private readonly carpetas: CarpetaService = inject(CarpetaService);
  private readonly procesos: ProcesoService = inject(ProcesoService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly dialogo = viewChild.required<ElementRef<HTMLElement>>('dialogo');
  private readonly anterior: HTMLElement | null = document.activeElement as HTMLElement | null;

  filas: FilaDeCarpeta[] = [];
  destino: number | null = null;
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.anterior?.focus());
  }

  ngOnInit(): void {
    this.destino = this.proceso().carpetaId ?? null;
    this.carpetas
      .listar()
      .pipe(
        finalize(() => {
          this.cargando = false;
          // Con el arbol pintado, el foco va a la carpeta donde esta, que es la opcion marcada
          setTimeout(() => this.enfocarMarcada());
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (carpetas) => (this.filas = aplanarCarpetas(carpetas)),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** La sangria del arbol, que el lector de pantalla no oye: por eso el nivel va tambien en el nombre accesible. */
  sangria(nivel: number): string {
    return `${nivel * 1.25}rem`;
  }

  get sinCambio(): boolean {
    return this.destino === (this.proceso().carpetaId ?? null);
  }

  mover(): void {
    if (this.sinCambio || this.enviando) {
      return;
    }
    this.enviando = true;
    this.error = null;
    const proceso = this.proceso();
    this.procesos
      .editarFicha(proceso.id, fichaConCarpeta(proceso, this.destino))
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (actualizado) => this.movido.emit(actualizado),
        error: (error: HttpErrorResponse) =>
          (this.error = esConflictoDeVersion(error)
            ? $localize`Someone else changed this process: close this, reload the page and try again.`
            : mensajeDeError(error)),
      });
  }

  teclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.cerrar.emit();
    } else if (retenerFoco(evento, this.dialogo().nativeElement)) {
      evento.preventDefault();
    }
  }

  private enfocarMarcada(): void {
    const marcada = this.dialogo().nativeElement.querySelector<HTMLInputElement>('input[type="radio"]:checked');
    (marcada ?? this.dialogo().nativeElement.querySelector<HTMLElement>('input, button'))?.focus();
  }
}
