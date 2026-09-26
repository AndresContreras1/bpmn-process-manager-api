import { Component, ElementRef, input, output, viewChild } from '@angular/core';

import { LaneDibujo, Lienzo } from './lienzo';

/** Donde queda un nodo despues de arrastrarlo, ya en las unidades que guarda la API. */
export interface MovimientoDeNodo {
  id: number;
  posicionX: number;
  posicionY: number;
  /** La lane sobre la que se solto, que puede no ser la de antes. */
  laneId: number;
}

/** Lo que dura un arrastre, mientras dura. */
interface Arrastre {
  id: number;
  desdeX: number;
  desdeY: number;
  dx: number;
  dy: number;
}

/**
 * Dibuja en SVG un diagrama ya ubicado por dibujarDiagrama. Al elegir una tarea, un gateway o un evento, con el
 * mouse o con el teclado, emite su id para que la pagina muestre el detalle.
 *
 * Con editable, ademas, los nodos se arrastran. El dibujo es el mismo en el visor y en el editor a proposito: si
 * fuesen dos componentes acabarian dibujando cosas distintas.
 */
@Component({
  selector: 'app-diagrama-bpmn',
  imports: [],
  templateUrl: './diagrama-bpmn.component.html',
  styleUrl: './diagrama-bpmn.component.scss',
})
export class DiagramaBpmnComponent {
  lienzo = input.required<Lienzo>();
  titulo = input.required<string>();
  seleccionadoId = input<number | null>(null);
  editable = input<boolean>(false);
  seleccionar = output<number>();
  mover = output<MovimientoDeNodo>();

  private readonly svg = viewChild<ElementRef<SVGSVGElement>>('svg');

  arrastre: Arrastre | null = null;

  elegir(id: number, evento?: Event): void {
    // Con la barra espaciadora el navegador bajaria la pagina
    evento?.preventDefault();
    this.seleccionar.emit(id);
  }

  /** Lo que se mueve mientras se arrastra, para no redibujar el diagrama entero en cada pixel. */
  desplazamiento(id: number): string | null {
    const arrastre: Arrastre | null = this.arrastre;
    return arrastre && arrastre.id === id ? `translate(${arrastre.dx} ${arrastre.dy})` : null;
  }

  empezarArrastre(id: number, evento: PointerEvent): void {
    if (!this.editable() || evento.button !== 0) {
      return;
    }
    evento.preventDefault();
    const punto = this.enUnidadesDelLienzo(evento);
    this.arrastre = { id, desdeX: punto.x, desdeY: punto.y, dx: 0, dy: 0 };
    (evento.target as Element).closest('svg')?.setPointerCapture(evento.pointerId);
  }

  seguirArrastre(evento: PointerEvent): void {
    if (!this.arrastre) {
      return;
    }
    const punto = this.enUnidadesDelLienzo(evento);
    this.arrastre = {
      ...this.arrastre,
      dx: punto.x - this.arrastre.desdeX,
      dy: punto.y - this.arrastre.desdeY,
    };
  }

  /**
   * Al soltar se deshace la cuenta que hizo el dibujo: la posicion que guarda la API sale de restar el origen del
   * lienzo, y la lane es aquella sobre la que cayo el nodo. Un arrastre de menos de tres unidades es un clic con
   * mal pulso, no una mudanza, y no se manda nada.
   */
  soltar(evento: PointerEvent): void {
    const arrastre: Arrastre | null = this.arrastre;
    this.arrastre = null;
    if (!arrastre) {
      return;
    }
    (evento.target as Element).closest('svg')?.releasePointerCapture(evento.pointerId);
    if (Math.abs(arrastre.dx) < 3 && Math.abs(arrastre.dy) < 3) {
      return;
    }
    const centro = this.centroDe(arrastre.id);
    if (!centro) {
      return;
    }
    const cx: number = centro.cx + arrastre.dx;
    const cy: number = centro.cy + arrastre.dy;
    const lane: LaneDibujo | undefined = this.laneEn(cy);
    if (!lane) {
      return;
    }
    this.mover.emit({
      id: arrastre.id,
      posicionX: Math.round(cx - this.lienzo().origenX),
      posicionY: Math.round(cy - lane.origenY),
      laneId: lane.id,
    });
  }

  /** El centro que tiene ahora mismo el nodo en el dibujo. */
  private centroDe(id: number): { cx: number; cy: number } | undefined {
    const lienzo: Lienzo = this.lienzo();
    return (
      lienzo.actividades.find((actividad) => actividad.id === id) ??
      lienzo.gateways.find((gateway) => gateway.id === id) ??
      lienzo.eventos.find((evento) => evento.id === id)
    );
  }

  /** La lane cuyo alto contiene ese y; sin ninguna, el nodo se solto fuera de todo y no se mueve. */
  private laneEn(y: number): LaneDibujo | undefined {
    return this.lienzo().lanes.find((lane) => y >= lane.y && y <= lane.y + lane.alto);
  }

  /** Del pixel del navegador a las unidades del viewBox, que son las que usa todo el dibujo. */
  private enUnidadesDelLienzo(evento: PointerEvent): DOMPoint {
    const svg: SVGSVGElement | undefined = this.svg()?.nativeElement;
    const matriz: DOMMatrix | null = svg?.getScreenCTM() ?? null;
    return matriz
      ? new DOMPoint(evento.clientX, evento.clientY).matrixTransform(matriz.inverse())
      : new DOMPoint(evento.clientX, evento.clientY);
  }
}
