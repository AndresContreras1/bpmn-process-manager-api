import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  OnChanges,
  afterNextRender,
  inject,
  input,
} from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ClaseDeNodo, Fila, NodoDelArbol, armarArbol, filasVisibles, pasoDeTecla } from './estructura';
import { ElementoElegido, LienzoBpmnComponent } from './lienzo-bpmn.component';

interface Opcion {
  id: string;
  nombre: string;
}

/**
 * El mismo diagrama como un arbol que se recorre con el teclado: pools, lanes, nodos y salidas, con lo que se hace en el
 * lienzo y sin arrastrar nada. Cada accion pasa por los comandos de bpmn-js: se deshace y se guarda como cualquier otra.
 */
@Component({
  selector: 'app-vista-estructura',
  imports: [FormsModule],
  templateUrl: './vista-estructura.component.html',
  styleUrl: './vista-estructura.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class VistaEstructuraComponent implements OnChanges {
  readonly lienzo = input.required<LienzoBpmnComponent>();
  /** Sube con cada cambio del dibujo, venga de donde venga. */
  readonly revision = input<number>(0);
  readonly elegido = input<ElementoElegido | null>(null);
  /** Cuando otra persona esta editando: se recorre, pero no se cambia. */
  readonly soloLectura = input<boolean>(false);

  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly inyector = inject(Injector);

  readonly tiposNuevos: Opcion[] = [
    { id: 'bpmn:UserTask', nombre: $localize`User task` },
    { id: 'bpmn:ServiceTask', nombre: $localize`Service task` },
    { id: 'bpmn:ExclusiveGateway', nombre: $localize`Exclusive gateway` },
    { id: 'bpmn:ParallelGateway', nombre: $localize`Parallel gateway` },
    { id: 'bpmn:StartEvent', nombre: $localize`Start event` },
    { id: 'bpmn:EndEvent', nombre: $localize`End event` },
  ];

  filas: Fila[] = [];
  foco: string | null = null;
  anuncio = '';
  cerrados = new Set<string>();
  nombre = '';
  tipoNuevo = 'bpmn:UserTask';
  nombreNuevo = '';
  destino = '';
  laneDestino = '';
  nodos: Opcion[] = [];
  lanes: Opcion[] = [];
  private arbol: NodoDelArbol[] = [];
  private nombres = new Map<string, string>();

  get actual(): NodoDelArbol | null {
    return this.filas.find((fila) => fila.nodo.id === this.foco)?.nodo ?? null;
  }

  get clase(): ClaseDeNodo | null {
    return this.actual?.clase ?? null;
  }

  ngOnChanges(): void {
    this.releer();
    const elegido = this.elegido();
    if (elegido && this.filas.some((fila) => fila.nodo.id === elegido.id) && elegido.id !== this.foco) {
      this.fijar(elegido.id);
    }
  }

  rotulo(nodo: NodoDelArbol): string {
    const tipo = rotuloDeTipo(nodo.tipo);
    if (nodo.clase === 'salida') {
      const destino = this.nombres.get(nodo.destino ?? '') || $localize`(no name)`;
      return nodo.nombre ? `${tipo} "${nodo.nombre}": ${destino}` : `${tipo}: ${destino}`;
    }
    return `${tipo}: ${nodo.nombre || $localize`(no name)`}`;
  }

  abierto(nodo: NodoDelArbol): boolean | null {
    return nodo.hijos.length ? !this.cerrados.has(nodo.id) : null;
  }

  teclear(evento: KeyboardEvent): void {
    if (!this.foco) {
      return;
    }
    if (evento.key === 'Enter' || evento.key === ' ') {
      this.lienzo().mostrar(this.foco);
      this.hecho(evento);
      return;
    }
    if (evento.key === 'Delete' && this.actual?.clase !== 'pool' && !this.soloLectura()) {
      this.borrar();
      this.hecho(evento);
      return;
    }
    if (evento.key === 'F2') {
      this.anfitrion.nativeElement.querySelector<HTMLInputElement>('#estructura-nombre')?.focus();
      this.hecho(evento);
      return;
    }
    const paso = pasoDeTecla(this.filas, this.foco, evento.key, this.cerrados);
    if (['ArrowDown', 'ArrowUp', 'ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(evento.key)) {
      this.hecho(evento);
    }
    if (paso) {
      if (paso.abrir) {
        this.cerrados.delete(paso.abrir);
      }
      if (paso.cerrar) {
        this.cerrados.add(paso.cerrar);
      }
      this.filas = filasVisibles(this.arbol, this.cerrados);
      this.enfocar(paso.foco);
    }
  }

  /** Un clic o una flecha: el foco se queda en la fila y el formulario de abajo pasa a hablar de ella. */
  enfocar(id: string): void {
    this.fijar(id);
    afterNextRender(() => this.anfitrion.nativeElement.querySelector<HTMLElement>(`[data-fila="${id}"]`)?.focus(), {
      injector: this.inyector,
    });
  }

  agregar(): void {
    if (!this.foco) {
      return;
    }
    const id = this.lienzo().agregar(this.foco, this.tipoNuevo, this.nombreNuevo.trim());
    if (!id) {
      this.anuncio = $localize`That element cannot go there.`;
      return;
    }
    this.anuncio = $localize`Added ${this.nombreNuevo.trim() || rotuloDeTipo(this.tipoNuevo)}.`;
    this.nombreNuevo = '';
    this.releer();
    this.enfocar(id);
  }

  renombrar(): void {
    if (this.foco) {
      this.lienzo().renombrar(this.foco, this.nombre.trim());
      this.anuncio = $localize`Renamed to ${this.nombre.trim()}.`;
      this.releer();
    }
  }

  conectar(): void {
    if (!this.foco || !this.destino) {
      return;
    }
    const id = this.lienzo().conectar(this.foco, this.destino);
    const destino = this.nombres.get(this.destino) ?? '';
    this.anuncio = id ? $localize`Connected to ${destino}.` : $localize`Those two elements cannot be connected.`;
    this.destino = '';
    this.releer();
  }

  mover(): void {
    if (!this.foco || !this.laneDestino) {
      return;
    }
    const movido = this.lienzo().moverALane(this.foco, this.laneDestino);
    const lane = this.nombres.get(this.laneDestino) ?? '';
    this.anuncio = movido ? $localize`Moved to lane ${lane}.` : $localize`It cannot move to that lane.`;
    this.laneDestino = '';
    this.releer();
    this.enfocar(this.foco);
  }

  borrar(): void {
    const actual = this.actual;
    if (!actual) {
      return;
    }
    const indice = this.filas.findIndex((fila) => fila.nodo.id === actual.id);
    const vecino = this.filas[indice - 1]?.nodo.id ?? null;
    this.lienzo().borrar(actual.id);
    this.anuncio = $localize`Deleted ${this.rotulo(actual)}.`;
    this.releer();
    if (vecino && this.filas.some((fila) => fila.nodo.id === vecino)) {
      this.enfocar(vecino);
    }
  }

  private fijar(id: string): void {
    this.foco = id;
    this.nombre = this.actual?.nombre ?? '';
    this.destino = '';
    this.laneDestino = '';
  }

  private releer(): void {
    const planos = this.lienzo().estructura();
    this.arbol = armarArbol(planos);
    this.filas = filasVisibles(this.arbol, this.cerrados);
    this.nombres = new Map(planos.map((plano) => [plano.id, plano.nombre]));
    const esNodo = (tipo: string): boolean => tipo !== 'bpmn:Participant' && tipo !== 'bpmn:Lane';
    this.nodos = planos
      .filter((plano) => esNodo(plano.tipo))
      .map((plano) => ({ id: plano.id, nombre: plano.nombre || rotuloDeTipo(plano.tipo) }));
    this.lanes = planos
      .filter((plano) => plano.tipo === 'bpmn:Lane')
      .map((plano) => ({ id: plano.id, nombre: plano.nombre || rotuloDeTipo(plano.tipo) }));
    if (!this.foco || !this.filas.some((fila) => fila.nodo.id === this.foco)) {
      this.foco = this.filas[0]?.nodo.id ?? null;
    }
    this.nombre = this.actual?.nombre ?? '';
  }

  private hecho(evento: KeyboardEvent): void {
    evento.preventDefault();
    evento.stopPropagation();
  }
}

function rotuloDeTipo(tipo: string): string {
  const corto = tipo.replace('bpmn:', '');
  if (corto.endsWith('Task')) {
    return $localize`Task`;
  }
  const rotulos: Record<string, string> = {
    Participant: $localize`Pool`,
    Lane: $localize`Lane`,
    StartEvent: $localize`Start event`,
    EndEvent: $localize`End event`,
    IntermediateCatchEvent: $localize`Intermediate event`,
    IntermediateThrowEvent: $localize`Intermediate event`,
    BoundaryEvent: $localize`Boundary event`,
    ExclusiveGateway: $localize`Exclusive gateway`,
    ParallelGateway: $localize`Parallel gateway`,
    InclusiveGateway: $localize`Inclusive gateway`,
    SubProcess: $localize`Subprocess`,
    CallActivity: $localize`Call activity`,
    SequenceFlow: $localize`Flow to`,
    MessageFlow: $localize`Message to`,
  };
  return rotulos[corto] ?? corto;
}
