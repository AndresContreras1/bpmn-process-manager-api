import { Component, input, output } from '@angular/core';

import { Lienzo } from './lienzo';

/**
 * Dibuja en SVG un diagrama ya ubicado por dibujarDiagrama. Al elegir una tarea o un gateway, con el mouse o con el
 * teclado, emite su id para que la pagina muestre el detalle.
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
  seleccionar = output<number>();

  elegir(id: number, evento?: Event): void {
    // Con la barra espaciadora el navegador bajaria la pagina
    evento?.preventDefault();
    this.seleccionar.emit(id);
  }
}
