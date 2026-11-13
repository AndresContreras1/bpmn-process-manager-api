import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnInit,
  inject,
  input,
  output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { ESPERA_AL_ESCRIBIR_MS } from '../../helpers/buscador';
import { SelectorDeMenciones } from '../../helpers/menciones';
import { Persona } from '../../models/comentario.model';
import { ComentarioService } from '../../service/comentario.service';

/** Para que cada selector de la pagina tenga ids propios: puede haber uno por hilo abierto. */
let siguiente = 0;

/**
 * PR F21: a quien menciona un comentario. Es un combobox (WAI-ARIA 1.2): se escribe parte del nombre, la API dice a
 * quien se puede mencionar en el proceso, las flechas recorren la lista y Enter elige. Lo elegido queda como
 * etiquetas con su boton de quitar, y viaja como ids: el Markdown no cambia.
 */
@Component({
  selector: 'app-selector-de-menciones',
  templateUrl: './selector-de-menciones.component.html',
  // WCAG 2.5.8: el boton de quitar mide 24 px aunque la etiqueta sea chica
  styles: `
    .mencion-quitar {
      width: 24px;
      height: 24px;
      padding: 0;
      background-size: 0.6rem;
    }
    .menciones-opciones {
      z-index: 1060;
      max-height: 14rem;
      overflow-y: auto;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectorDeMencionesComponent implements OnInit {
  readonly procesoId = input.required<number>();
  /** Las que ya tenia el comentario que se edita. */
  readonly iniciales = input<readonly Persona[]>([]);
  readonly cambio = output<number[]>();

  private readonly comentarios = inject(ComentarioService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cambios = inject(ChangeDetectorRef);
  private readonly textos = new Subject<string>();

  readonly id: string = `menciones-${++siguiente}`;
  readonly selector = new SelectorDeMenciones();
  texto: string = '';
  buscando: boolean = false;
  sinResultados: boolean = false;

  ngOnInit(): void {
    this.selector.reiniciar(this.iniciales());
    this.textos
      .pipe(
        debounceTime(ESPERA_AL_ESCRIBIR_MS),
        distinctUntilChanged(),
        switchMap((texto) => {
          this.buscando = true;
          return this.comentarios.mencionables(this.procesoId(), texto).pipe(catchError(() => of([] as Persona[])));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((personas) => {
        this.buscando = false;
        this.selector.ofrecer(personas);
        this.sinResultados = !this.selector.abierto && this.texto.trim() !== '';
        this.cambios.markForCheck();
      });
  }

  /** El id de la opcion activa, para aria-activedescendant. */
  get activa(): string | null {
    return this.selector.activa >= 0 ? `${this.id}-opcion-${this.selector.activa}` : null;
  }

  escribir(evento: Event): void {
    this.texto = (evento.target as HTMLInputElement).value;
    this.textos.next(this.texto);
  }

  /** Al entrar al campo, las primeras personas, aunque no haya nada escrito. */
  enfocar(): void {
    if (!this.selector.abierto && !this.selector.lleno) {
      this.buscarYa();
    }
  }

  /** Salir del campo cierra la lista; las opciones se eligen con mousedown, antes de que el foco se vaya. */
  salir(): void {
    this.selector.cerrar();
  }

  teclear(evento: KeyboardEvent): void {
    switch (evento.key) {
      case 'ArrowDown':
      case 'ArrowUp':
        evento.preventDefault();
        if (this.selector.abierto) {
          this.selector.mover(evento.key);
        } else {
          this.buscarYa();
        }
        break;
      case 'Home':
      case 'End':
        if (this.selector.abierto) {
          evento.preventDefault();
          this.selector.mover(evento.key);
        }
        break;
      case 'Enter':
        // Enter elige; sin lista abierta no hace nada, para no mandar el comentario a medias
        evento.preventDefault();
        this.elegir();
        break;
      case 'Escape':
        if (this.selector.abierto) {
          evento.preventDefault();
          evento.stopPropagation();
          this.selector.cerrar();
        }
        break;
    }
  }

  elegir(indice?: number): void {
    if (this.selector.elegir(indice) !== null) {
      this.texto = '';
      this.sinResultados = false;
      this.cambio.emit(this.selector.ids);
    }
  }

  /** El nombre del boton de quitar una mencion, con la persona: la X sola no dice nada. */
  etiquetaDeQuitar(persona: Persona): string {
    return $localize`Remove the mention of ${persona.nombre}:nombre:`;
  }

  /** Despues de enviar: sin nadie elegido y sin texto. */
  limpiar(): void {
    this.selector.reiniciar();
    this.texto = '';
    this.sinResultados = false;
    this.cambios.markForCheck();
  }

  quitar(persona: Persona): void {
    this.selector.quitar(persona.id);
    this.cambio.emit(this.selector.ids);
  }

  /** Quien termina de escribir y baja con la flecha no espera a la pausa: pregunta ya. */
  private buscarYa(): void {
    this.buscando = true;
    this.comentarios
      .mencionables(this.procesoId(), this.texto)
      .pipe(
        catchError(() => of([] as Persona[])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((personas) => {
        this.buscando = false;
        this.selector.ofrecer(personas);
        this.cambios.markForCheck();
      });
  }
}
