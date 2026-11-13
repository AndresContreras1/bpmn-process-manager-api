import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';

import { EstadoDeBusqueda, buscarMientrasSeEscribe } from '../../helpers/buscador';
import { mensajeDeError } from '../../helpers/errores-api';
import { retenerFoco, siguienteOpcion } from '../../helpers/teclado';
import {
  GrupoDeResultados,
  ResultadoDeBusqueda,
  agruparResultados,
  enOrdenDeGrupos,
} from '../../models/busqueda.model';
import { BusquedaGlobalService } from '../../service/busqueda-global.service';
import { BusquedaService } from '../../service/busqueda.service';
import { FragmentoComponent } from './fragmento.component';

/**
 * PR 65 en la web: la busqueda en lo que la persona puede ver, con Ctrl+K o Cmd+K desde cualquier pantalla.
 *
 * Sigue el patron combobox de WAI-ARIA: el foco se queda en el campo, las flechas mueven la opcion activa
 * (aria-activedescendant), Enter abre la activa y Escape cierra. Al cerrarse, el foco vuelve a donde estaba al abrirse.
 */
@Component({
  selector: 'app-busqueda-global',
  imports: [FormsModule, FragmentoComponent],
  templateUrl: './busqueda-global.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './busqueda-global.component.scss',
})
export class BusquedaGlobalComponent {
  private readonly busquedaGlobal: BusquedaGlobalService = inject(BusquedaGlobalService);
  private readonly busqueda: BusquedaService = inject(BusquedaService);
  private readonly router: Router = inject(Router);
  private readonly campo = viewChild.required<ElementRef<HTMLInputElement>>('campo');
  private readonly dialogo = viewChild.required<ElementRef<HTMLElement>>('dialogo');
  private readonly textos$ = new Subject<string>();
  /** Lo que tenia el foco al abrirse, que lo recupera al cerrarse. */
  private readonly anterior: HTMLElement | null = document.activeElement as HTMLElement | null;

  readonly etiqueta = {
    opcion: (resultado: ResultadoDeBusqueda): string =>
      resultado.tipo === 'PROCESO' ? resultado.titulo : $localize`${resultado.titulo} in ${resultado.proceso}`,
  };

  texto: string = '';
  estado: EstadoDeBusqueda | null = null;
  grupos: GrupoDeResultados[] = [];
  activa: number = -1;
  private enOrden: ResultadoDeBusqueda[] = [];

  constructor() {
    buscarMientrasSeEscribe(this.textos$, (texto) => this.busqueda.buscar(texto))
      .pipe(takeUntilDestroyed())
      .subscribe((estado) => this.mostrar(estado));
    afterNextRender(() => this.campo().nativeElement.focus());
    inject(DestroyRef).onDestroy(() => this.anterior?.focus());
  }

  escribir(texto: string): void {
    this.textos$.next(texto);
  }

  get error(): string | null {
    const error: HttpErrorResponse | null | undefined = this.estado?.error;
    return error ? mensajeDeError(error) : null;
  }

  idDe(indice: number): string {
    return `busqueda-resultado-${indice}`;
  }

  /** Las teclas del campo: las flechas recorren, Enter abre y Escape cierra. */
  teclear(evento: KeyboardEvent): void {
    if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
      evento.preventDefault();
      this.activa = siguienteOpcion(this.activa, this.enOrden.length, evento.key);
      document.getElementById(this.idDe(this.activa))?.scrollIntoView({ block: 'nearest' });
    } else if (evento.key === 'Enter') {
      evento.preventDefault();
      const elegido = this.enOrden[this.activa] ?? this.enOrden[0];
      if (elegido) {
        this.abrir(elegido);
      }
    }
  }

  /** Escape cierra desde cualquier parte del dialogo, y Tab no se sale de el. */
  teclearEnDialogo(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.cerrar();
    } else if (retenerFoco(evento, this.dialogo().nativeElement)) {
      evento.preventDefault();
    }
  }

  abrir(resultado: ResultadoDeBusqueda): void {
    this.cerrar();
    this.router.navigate(['/procesos', resultado.procesoId]);
  }

  cerrar(): void {
    this.busquedaGlobal.cerrar();
  }

  private mostrar(estado: EstadoDeBusqueda): void {
    this.estado = estado;
    this.grupos = agruparResultados(estado.resultados);
    this.enOrden = enOrdenDeGrupos(this.grupos);
    this.activa = this.enOrden.length > 0 ? 0 : -1;
  }
}
