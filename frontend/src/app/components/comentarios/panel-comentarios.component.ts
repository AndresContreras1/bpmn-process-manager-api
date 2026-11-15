import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnChanges,
  SimpleChanges,
  ViewEncapsulation,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, Subscription, finalize } from 'rxjs';

import { esConflictoDeVersion, mensajeDeLaApi } from '../../helpers/errores-api';
import {
  Comentario,
  Hilo,
  LARGO_MAXIMO_COMENTARIO,
  contadoresDeHilos,
  hiloDelComentario,
  hilosDe,
  puedeEditarComentario,
  puedeEliminarComentario,
} from '../../models/comentario.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { AuthService } from '../../service/auth.service';
import { ComentarioService } from '../../service/comentario.service';
import { SelectorDeMencionesComponent } from './selector-de-menciones.component';

/** El elemento cuyos hilos se muestran; null es el proceso entero. */
export interface ElementoComentado {
  id: string;
  nombre: string;
}

/**
 * PR F21: los hilos de comentarios del elemento elegido en el lienzo, o del proceso entero sin ninguno, en lo que se
 * mira: el borrador o una version publicada. Lee los hilos del proceso una vez y los cuenta por elemento, que es lo
 * que el lienzo pone encima de cada uno; despues de cada cambio vuelve a leerlos, porque otra persona pudo escribir.
 *
 * El texto se escribe en Markdown y se ve como el HTML que devolvio la API ya limpio; entra por [innerHTML], que lo
 * pasa otra vez por el saneador de Angular. Cualquier rol comenta: comentar no es modelar.
 */
@Component({
  selector: 'app-panel-comentarios',
  imports: [FormsModule, NgTemplateOutlet, FechaPipe, SelectorDeMencionesComponent],
  templateUrl: './panel-comentarios.component.html',
  styleUrl: './panel-comentarios.component.scss',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelComentariosComponent implements OnChanges {
  readonly procesoId = input.required<number>();
  /** La version publicada que se mira; null es el borrador. */
  readonly version = input<number | null>(null);
  readonly elemento = input<ElementoComentado | null>(null);
  /** El comentario al que lleva un aviso o un correo (?comentario=): su hilo se elige y se abre. */
  readonly enfocar = input<number | null>(null);
  /** Cuantos hilos abiertos tiene cada elemento, para el lienzo. */
  readonly contadores = output<Record<string, number>>();
  /** El primer comentario del hilo al que se llega por un enlace: su elemento y su version, para mostrarlos. */
  readonly irAlHilo = output<Comentario>();

  private readonly servicio = inject(ComentarioService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cambios = inject(ChangeDetectorRef);
  private readonly selectorNuevo = viewChild<SelectorDeMencionesComponent>('selectorNuevo');

  readonly largoMaximo: number = LARGO_MAXIMO_COMENTARIO;
  readonly usuarioId: number | null = this.auth.usuarioActual()?.id ?? null;
  private readonly esAdministrador: boolean = this.auth.esAdministrador();

  private todos: Hilo[] = [];
  private carga: Subscription | null = null;
  hilos: Hilo[] = [];
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  /** Lo que se dice en la region aria-live tras cada cambio. */
  estado: string = '';
  /** El hilo al que llevo un enlace, resaltado hasta que se elija otro elemento. */
  destacado: number | null = null;
  /** El comentario del enlace cuyo hilo ya recibio el foco, para no robarlo otra vez tras cada cambio. */
  private enfocado: number | null = null;

  texto: string = '';
  mencionesNuevas: number[] = [];
  /** El comentario al que se esta respondiendo, o el que se esta editando: uno a la vez. */
  respondiendo: number | null = null;
  editando: number | null = null;
  borrando: number | null = null;
  textoAparte: string = '';
  mencionesAparte: number[] = [];

  ngOnChanges(cambios: SimpleChanges): void {
    if (cambios['procesoId'] || cambios['version']) {
      this.cargar();
    } else if (cambios['enfocar'] && this.enfocar() !== null) {
      this.enfocado = null;
      this.llevarAlEnfocado();
    } else if (cambios['elemento']) {
      this.filtrar();
      if (!this.hilos.some((hilo) => hilo.comentario.id === this.destacado)) {
        this.destacado = null;
      }
      this.enfocarSiSeVe();
    }
  }

  get titulo(): string {
    const elemento = this.elemento();
    return elemento
      ? $localize`Comments on ${elemento.nombre || elemento.id}:elemento:`
      : $localize`Comments on the whole process`;
  }

  /** Lee otra vez los hilos del proceso y los cuenta. */
  cargar(): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.error = null;
    this.carga = this.servicio
      .hilos(this.procesoId(), this.version())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (hilos) => {
          this.todos = hilos;
          this.cargando = false;
          this.contadores.emit(contadoresDeHilos(hilos, this.version()));
          this.filtrar();
          if (this.enfocar() !== null && this.enfocado !== this.enfocar()) {
            this.llevarAlEnfocado();
          }
          this.cambios.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          this.cargando = false;
          this.error = mensajeDeLaApi(error);
          this.cambios.markForCheck();
        },
      });
  }

  puedeEditar(comentario: Comentario): boolean {
    return puedeEditarComentario(comentario, this.usuarioId);
  }

  puedeEliminar(comentario: Comentario): boolean {
    return puedeEliminarComentario(comentario, this.usuarioId, this.esAdministrador);
  }

  comentar(): void {
    const markdown = this.texto.trim();
    if (!markdown || this.enviando) {
      return;
    }
    const elemento = this.elemento();
    this.enviar(
      this.servicio.comentar(this.procesoId(), {
        markdown,
        bpmnId: elemento?.id ?? null,
        versionDelProceso: this.version(),
        menciones: this.mencionesNuevas,
      }),
      $localize`Comment added.`,
      () => {
        this.texto = '';
        this.mencionesNuevas = [];
        this.selectorNuevo()?.limpiar();
      },
    );
  }

  abrirRespuesta(comentario: Comentario): void {
    this.cerrarAparte();
    this.respondiendo = comentario.id;
  }

  abrirEdicion(comentario: Comentario): void {
    this.cerrarAparte();
    this.editando = comentario.id;
    this.textoAparte = comentario.markdown;
    this.mencionesAparte = comentario.menciones.map((persona) => persona.id);
  }

  cerrarAparte(): void {
    this.respondiendo = null;
    this.editando = null;
    this.borrando = null;
    this.textoAparte = '';
    this.mencionesAparte = [];
  }

  responder(comentario: Comentario): void {
    const markdown = this.textoAparte.trim();
    if (!markdown) {
      return;
    }
    this.enviar(
      this.servicio.responder(comentario.id, { markdown, menciones: this.mencionesAparte }),
      $localize`Reply added.`,
      () => this.cerrarAparte(),
    );
  }

  guardarEdicion(comentario: Comentario): void {
    const markdown = this.textoAparte.trim();
    if (!markdown) {
      return;
    }
    this.enviar(
      this.servicio.editar(comentario.id, {
        markdown,
        menciones: this.mencionesAparte,
        version: comentario.version,
      }),
      $localize`Comment saved.`,
      () => this.cerrarAparte(),
    );
  }

  resolver(hilo: Hilo): void {
    this.enviar(this.servicio.resolver(hilo.comentario.id), $localize`Thread resolved.`);
  }

  reabrir(hilo: Hilo): void {
    this.enviar(this.servicio.reabrir(hilo.comentario.id), $localize`Thread reopened.`);
  }

  eliminar(comentario: Comentario): void {
    this.enviar(this.servicio.eliminar(comentario.id), $localize`Comment deleted.`, () => this.cerrarAparte());
  }

  /** Cada cambio vuelve a leer los hilos: la respuesta trae solo un comentario, y otra persona pudo escribir. */
  private enviar(peticion: Observable<unknown>, hecho: string, despues: () => void = () => undefined): void {
    this.enviando = true;
    this.error = null;
    this.estado = '';
    peticion
      .pipe(
        finalize(() => {
          this.enviando = false;
          this.cambios.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          despues();
          this.estado = hecho;
          this.cargar();
        },
        error: (error: HttpErrorResponse) => {
          this.error = esConflictoDeVersion(error)
            ? $localize`Someone changed this comment while you were editing it. Cancel to see the new text.`
            : mensajeDeLaApi(error);
          this.cambios.markForCheck();
        },
      });
  }

  private filtrar(): void {
    this.hilos = hilosDe(this.todos, this.elemento()?.id ?? null, this.version());
  }

  /** El hilo del comentario del enlace: se pide su elemento al lienzo y se resalta. */
  private llevarAlEnfocado(): void {
    const id = this.enfocar();
    const hilo = id === null ? null : hiloDelComentario(this.todos, id);
    if (!hilo) {
      return;
    }
    this.destacado = hilo.comentario.id;
    this.irAlHilo.emit(hilo.comentario);
    this.enfocarSiSeVe();
  }

  /** El foco va al hilo del enlace una sola vez, cuando ya esta en pantalla: el lienzo puede tardar en elegirlo. */
  private enfocarSiSeVe(): void {
    const destacado = this.destacado;
    if (
      destacado === null ||
      this.enfocado === this.enfocar() ||
      !this.hilos.some((h) => h.comentario.id === destacado)
    ) {
      return;
    }
    this.enfocado = this.enfocar();
    setTimeout(() => document.getElementById(`hilo-${destacado}`)?.focus());
  }
}
