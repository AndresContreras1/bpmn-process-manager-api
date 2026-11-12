import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnChanges,
  ViewEncapsulation,
  inject,
  input,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription, finalize } from 'rxjs';

import { esConflictoDeVersion, mensajeDeError } from '../../helpers/errores-api';
import { Documentacion } from '../../models/documentacion.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { DocumentacionService } from '../../service/documentacion.service';

/** El tope de la API para un texto. */
export const LARGO_MAXIMO_DOCUMENTACION: number = 100_000;

type Vista = 'escribir' | 'previa';

/**
 * PR F20: la documentacion en Markdown del proceso entero o de uno de sus elementos. La web no convierte el Markdown:
 * la vista previa es el HTML que la API devuelve ya saneado, el mismo que guardaria, y entra por [innerHTML], que lo
 * pasa otra vez por el saneador de Angular. Quien solo puede mirar ve el HTML guardado y nada mas.
 */
@Component({
  selector: 'app-editor-documentacion',
  imports: [ReactiveFormsModule, FechaPipe],
  templateUrl: './editor-documentacion.component.html',
  styleUrl: './editor-documentacion.component.scss',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class EditorDocumentacionComponent implements OnChanges {
  readonly procesoId = input.required<number>();
  /** El bpmnId del elemento; vacio para el texto del proceso entero. */
  readonly elemento = input<string | null>(null);
  readonly soloLectura = input<boolean>(false);
  /** En el panel del modelador, que es angosto: un area de texto mas baja. */
  readonly compacto = input<boolean>(false);

  private readonly servicio = inject(DocumentacionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly texto = new FormControl<string>('', {
    nonNullable: true,
    validators: [Validators.maxLength(LARGO_MAXIMO_DOCUMENTACION)],
  });

  /** Lo que hay en el servidor; null mientras nadie escribio nada. */
  guardada: Documentacion | null = null;
  vista: Vista = 'escribir';
  previa: string | null = null;
  cargando: boolean = true;
  previsualizando: boolean = false;
  guardando: boolean = false;
  /** Alguien guardo otro texto mientras este se escribia: lo escrito se queda hasta que se pida recargar. */
  conflicto: boolean = false;
  error: string | null = null;
  aviso: string | null = null;
  private carga: Subscription | null = null;

  ngOnChanges(): void {
    this.cargar();
  }

  /** Lo del servidor, reemplazando lo escrito: tambien es lo que hace el boton de recargar tras un conflicto. */
  cargar(): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.conflicto = false;
    this.error = null;
    this.aviso = null;
    this.vista = 'escribir';
    this.previa = null;
    const bpmnId: string | null = this.elemento() || null;
    this.carga = this.servicio
      .listar(this.procesoId())
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (textos: Documentacion[]) => {
          this.guardada = textos.find((texto: Documentacion) => (texto.bpmnId || null) === bpmnId) ?? null;
          this.texto.reset(this.guardada?.markdown ?? '');
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  escribir(): void {
    this.vista = 'escribir';
  }

  /** Lo que la API haria con el texto de ahora, sin guardarlo. */
  verPrevia(): void {
    this.vista = 'previa';
    this.previa = null;
    this.error = null;
    this.previsualizando = true;
    this.servicio
      .vistaPrevia(this.texto.value)
      .pipe(
        finalize(() => (this.previsualizando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (html: string) => (this.previa = html),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** Con la version leida, o sin ella si todavia no habia texto; vacio lo borra. */
  guardar(): void {
    if (this.texto.invalid || this.guardando) {
      return;
    }
    this.guardando = true;
    this.error = null;
    this.aviso = null;
    this.servicio
      .guardar(this.procesoId(), {
        elemento: this.elemento() || null,
        markdown: this.texto.value,
        version: this.guardada?.version ?? null,
      })
      .pipe(
        finalize(() => (this.guardando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (guardada: Documentacion) => {
          const borrada: boolean = guardada.version === null;
          this.guardada = borrada ? null : guardada;
          this.texto.reset(guardada.markdown);
          if (this.vista === 'previa') {
            this.previa = guardada.html;
          }
          this.aviso = borrada ? $localize`Documentation removed.` : $localize`Documentation saved.`;
        },
        error: (error: HttpErrorResponse) => {
          if (esConflictoDeVersion(error)) {
            this.conflicto = true;
          } else {
            this.error = mensajeDeError(error);
          }
        },
      });
  }
}
