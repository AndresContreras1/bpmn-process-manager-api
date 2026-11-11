import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  LOCALE_ID,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
  AfterViewInit,
  input,
  output,
  inject,
} from '@angular/core';

import { ElementoPlano, huecoEnLane } from './estructura';
import { BPM_MODDLE } from './modulos/bpm.moddle';
import { ModoDelLienzo, moduloDeModoYReglas, moduloDelModo } from './modulos/modulos-del-lienzo';
import { ModoProceso } from './modulos/reglas';
import { traduccion } from './modulos/traduccion';

/** Para que se abre el lienzo: ver un proceso, editarlo, seguir un caso o comparar. */
export type ModoLienzo = 'ver' | 'editar' | 'caso' | 'comparar';

/** Lo que el resto de la pagina sabe del elemento elegido. */
export interface ElementoElegido {
  id: string;
  tipo: string;
  nombre: string;
}

// bpmn-js no publica tipos utiles para su inyector: lo que entra y sale de el es dinamico
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Cualquiera = any;

/** Los colores del producto, y su letra, en el dibujo. */
const TEMA = {
  bpmnRenderer: { defaultFillColor: '#ffffff', defaultStrokeColor: '#1f2328' },
  textRenderer: {
    defaultStyle: { fontFamily: 'system-ui, sans-serif', fontSize: 12 },
    externalStyle: { fontFamily: 'system-ui, sans-serif', fontSize: 12 },
  },
};

/**
 * El unico lienzo del producto, sobre bpmn-js: editar y ver son el mismo componente. bpmn-js se carga al abrirlo, para
 * que no pese en las pantallas que no dibujan. El logo de bpmn.io que trae se deja visible y sin tapar: es su licencia.
 */
@Component({
  selector: 'app-lienzo-bpmn',
  template: '<div #contenedor class="lienzo-bpmn" [attr.aria-label]="titulo()" role="application"></div>',
  styles: ':host { display: block; height: 100%; } .lienzo-bpmn { height: 100%; min-height: 420px; }',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LienzoBpmnComponent implements AfterViewInit, OnChanges, OnDestroy {
  readonly xml = input.required<string>();
  readonly modo = input<ModoLienzo>('ver');
  readonly modoProceso = input<ModoProceso>('EJECUTABLE');
  readonly titulo = input<string>('');
  /** Clases para pintar elementos: por bpmnId. */
  readonly marcas = input<Record<string, string[]>>({});
  /** Numeros superpuestos, como los tokens de un caso: por bpmnId. Se ven si son mas de uno. */
  readonly contadores = input<Record<string, number>>({});

  readonly listo = output<void>();
  readonly cambio = output<void>();
  readonly elegir = output<ElementoElegido | null>();
  readonly fallo = output<string>();

  @ViewChild('contenedor', { static: true }) private contenedor!: ElementRef<HTMLElement>;

  private readonly idioma: string = inject(LOCALE_ID);
  private readonly modoDelLienzo = new ModoDelLienzo();
  private bpmn: Cualquiera | null = null;
  private marcasPuestas: Record<string, string[]> = {};
  private xmlCargado: string | null = null;

  async ngAfterViewInit(): Promise<void> {
    cargarEstilos();
    this.modoDelLienzo.modo = this.modoProceso();
    const opciones = {
      container: this.contenedor.nativeElement,
      moddleExtensions: { bpm: BPM_MODDLE },
      keyboard: { bind: true },
      ...TEMA,
    };
    const comunes = [moduloDelModo(this.modoDelLienzo), traduccion(this.idioma)];
    if (this.modo() === 'editar') {
      const [{ default: Modeler }, { default: minimapa }] = await Promise.all([
        import('bpmn-js/lib/Modeler'),
        import('diagram-js-minimap'),
      ]);
      this.bpmn = new Modeler({ ...opciones, additionalModules: [...comunes, moduloDeModoYReglas, minimapa] });
    } else {
      const { default: NavigatedViewer } = await import('bpmn-js/lib/NavigatedViewer');
      this.bpmn = new NavigatedViewer({ ...opciones, additionalModules: comunes });
    }
    this.escuchar();
    await this.cargar(this.xml());
  }

  async ngOnChanges(cambios: SimpleChanges): Promise<void> {
    if (!this.bpmn) {
      return;
    }
    if (cambios['modoProceso']) {
      this.modoDelLienzo.modo = this.modoProceso();
    }
    if (cambios['xml'] && this.xml() !== this.xmlCargado) {
      await this.cargar(this.xml());
    } else if (cambios['marcas'] || cambios['contadores']) {
      this.pintar();
    }
  }

  ngOnDestroy(): void {
    this.bpmn?.destroy();
  }

  /** El XML de lo que se ve ahora. */
  async leerXml(): Promise<string> {
    const { xml } = await this.bpmn.saveXML({ format: true });
    return xml;
  }

  async leerSvg(): Promise<string> {
    const { svg } = await this.bpmn.saveSVG();
    return svg;
  }

  deshacer(): void {
    this.bpmn.get('commandStack').undo();
  }

  rehacer(): void {
    this.bpmn.get('commandStack').redo();
  }

  puedeDeshacer(): boolean {
    return !!this.bpmn?.get('commandStack', false)?.canUndo();
  }

  puedeRehacer(): boolean {
    return !!this.bpmn?.get('commandStack', false)?.canRedo();
  }

  buscar(): void {
    this.bpmn.get('editorActions').trigger('find');
  }

  ajustar(): void {
    this.bpmn.get('canvas').zoom('fit-viewport', 'auto');
  }

  alternarMinimapa(): void {
    this.bpmn.get('minimap', false)?.toggle();
  }

  /** El objeto de negocio de un elemento, para quien edita sus propiedades. */
  negocio(id: string): Cualquiera {
    return this.bpmn.get('elementRegistry').get(id)?.businessObject;
  }

  /** Cambia propiedades de un elemento como un comando: se deshace y dispara el autoguardado. */
  actualizar(id: string, propiedades: Record<string, unknown>): void {
    const elemento = this.bpmn.get('elementRegistry').get(id);
    if (elemento) {
      this.bpmn.get('modeling').updateProperties(elemento, propiedades);
    }
  }

  /** Crea un elemento moddle, como un {@code bpm:mensaje}. */
  crear(tipo: string, propiedades: Record<string, unknown> = {}): Cualquiera {
    return this.bpmn.get('moddle').create(tipo, propiedades);
  }

  /** Lleva la vista a un elemento y lo selecciona. */
  mostrar(id: string): void {
    const elemento = this.bpmn.get('elementRegistry').get(id);
    if (elemento) {
      this.bpmn.get('selection', false)?.select(elemento);
      this.bpmn.get('canvas').scrollToElement(elemento);
    }
  }

  /** El dibujo como datos, para la vista de estructura. */
  estructura(): ElementoPlano[] {
    if (!this.bpmn) {
      return [];
    }
    const todos: Cualquiera[] = this.bpmn.get('elementRegistry').getAll();
    const lanes = todos.filter((elemento) => elemento.type === 'bpmn:Lane');
    return todos
      .filter((elemento) => !elemento.labelTarget && !elemento.waypoints && esDeLaEstructura(elemento))
      .sort((uno, otro) => uno.y - otro.y || uno.x - otro.x)
      .map((elemento) => ({
        id: elemento.id,
        tipo: elemento.type,
        nombre: elemento.businessObject.name ?? '',
        padre: padreDe(elemento, lanes),
        salidas: (elemento.outgoing ?? []).map((salida: Cualquiera) => ({
          id: salida.id,
          tipo: salida.type,
          nombre: salida.businessObject.name ?? '',
          destino: salida.target.id,
        })),
      }));
  }

  /** Crea un elemento en una lane, en un hueco libre. Devuelve su id, o null si las reglas no lo dejan. */
  agregar(laneId: string, tipo: string, nombre: string): string | null {
    const lane = this.bpmn.get('elementRegistry').get(laneId);
    const forma = this.bpmn.get('elementFactory').createShape({ type: tipo });
    if (!lane || !this.bpmn.get('rules').allowed('shape.create', { shape: forma, target: lane })) {
      return null;
    }
    const modelado = this.bpmn.get('modeling');
    const creado = modelado.createShape(forma, huecoEnLane(lane, this.ocupadosEn(lane)), lane);
    if (nombre) {
      modelado.updateLabel(creado, nombre);
    }
    return creado.id;
  }

  renombrar(id: string, nombre: string): void {
    const elemento = this.bpmn.get('elementRegistry').get(id);
    if (elemento) {
      this.bpmn.get('modeling').updateLabel(elemento, nombre);
    }
  }

  /** Une dos nodos con lo que las reglas permitan. Devuelve el id de la conexion, o null si no se puede. */
  conectar(origenId: string, destinoId: string): string | null {
    const registro = this.bpmn.get('elementRegistry');
    const source = registro.get(origenId);
    const target = registro.get(destinoId);
    const permitido = source && target && this.bpmn.get('rules').allowed('connection.create', { source, target });
    if (!permitido) {
      return null;
    }
    const conexion = this.bpmn.get('modeling').connect(source, target, permitido === true ? undefined : permitido);
    return conexion?.id ?? null;
  }

  /** Lleva un nodo a un hueco de otra lane; bpmn-js actualiza a que lane pertenece por su posicion. */
  moverALane(id: string, laneId: string): boolean {
    const registro = this.bpmn.get('elementRegistry');
    const elemento = registro.get(id);
    const lane = registro.get(laneId);
    if (!elemento || !lane || lane.parent !== elemento.parent) {
      return false;
    }
    const hueco = huecoEnLane(lane, this.ocupadosEn(lane));
    const delta = {
      x: hueco.x - (elemento.x + elemento.width / 2),
      y: hueco.y - (elemento.y + elemento.height / 2),
    };
    this.bpmn.get('modeling').moveElements([elemento], delta);
    return true;
  }

  borrar(id: string): void {
    const elemento = this.bpmn.get('elementRegistry').get(id);
    if (elemento) {
      this.bpmn.get('modeling').removeElements([elemento]);
    }
  }

  private ocupadosEn(lane: Cualquiera): Cualquiera[] {
    const dentro = (elemento: Cualquiera): boolean =>
      elemento.y + elemento.height / 2 > lane.y && elemento.y + elemento.height / 2 < lane.y + lane.height;
    return this.bpmn
      .get('elementRegistry')
      .getAll()
      .filter(
        (elemento: Cualquiera) =>
          !elemento.labelTarget && !elemento.waypoints && esNodoDeFlujo(elemento) && dentro(elemento),
      );
  }

  private async cargar(xml: string): Promise<void> {
    try {
      await this.bpmn.importXML(xml);
      this.xmlCargado = xml;
      this.bpmn.get('canvas').zoom('fit-viewport', 'auto');
      this.marcasPuestas = {};
      this.pintar();
      this.listo.emit();
    } catch (error) {
      this.fallo.emit(error instanceof Error ? error.message : String(error));
    }
  }

  private escuchar(): void {
    const eventos = this.bpmn.get('eventBus');
    eventos.on('commandStack.changed', () => this.cambio.emit());
    eventos.on('selection.changed', ({ newSelection }: Cualquiera) => {
      const elemento = newSelection.length === 1 ? newSelection[0] : null;
      this.elegir.emit(
        elemento ? { id: elemento.id, tipo: elemento.type, nombre: elemento.businessObject?.name ?? '' } : null,
      );
    });
  }

  /** Quita las marcas de antes y pone las de ahora; un elemento que ya no esta se salta. */
  private pintar(): void {
    const lienzo = this.bpmn.get('canvas');
    const registro = this.bpmn.get('elementRegistry');
    Object.entries(this.marcasPuestas).forEach(([id, clases]) => {
      if (registro.get(id)) {
        clases.forEach((clase) => lienzo.removeMarker(id, clase));
      }
    });
    const puestas: Record<string, string[]> = {};
    Object.entries(this.marcas()).forEach(([id, clases]) => {
      if (registro.get(id)) {
        clases.forEach((clase) => lienzo.addMarker(id, clase));
        puestas[id] = clases;
      }
    });
    this.marcasPuestas = puestas;
    this.contar();
  }

  private contar(): void {
    const superpuestos = this.bpmn.get('overlays');
    const registro = this.bpmn.get('elementRegistry');
    superpuestos.remove({ type: 'contador' });
    Object.entries(this.contadores()).forEach(([id, cuantos]) => {
      if (cuantos > 1 && registro.get(id)) {
        const insignia = document.createElement('span');
        insignia.className = 'contador-bpmn';
        insignia.textContent = String(cuantos);
        superpuestos.add(id, 'contador', { position: { top: -8, right: 8 }, html: insignia });
      }
    });
  }
}

function esNodoDeFlujo(elemento: Cualquiera): boolean {
  return !!elemento.businessObject?.$instanceOf?.('bpmn:FlowNode');
}

function esDeLaEstructura(elemento: Cualquiera): boolean {
  return elemento.type === 'bpmn:Participant' || elemento.type === 'bpmn:Lane' || esNodoDeFlujo(elemento);
}

/** En bpmn-js un nodo cuelga del pool: su lane se sabe por la referencia que la lane guarda de el. */
function padreDe(elemento: Cualquiera, lanes: Cualquiera[]): string | null {
  if (elemento.type === 'bpmn:Participant') {
    return null;
  }
  if (elemento.type === 'bpmn:Lane' || elemento.parent?.type === 'bpmn:SubProcess') {
    return elemento.parent?.id ?? null;
  }
  const lane = lanes.find((una) => (una.businessObject.flowNodeRef ?? []).includes(elemento.businessObject));
  return lane?.id ?? elemento.parent?.id ?? null;
}

/** La hoja de estilos de bpmn-js se pide al abrir el primer lienzo: en las demas pantallas no pesa. */
function cargarEstilos(): void {
  if (!document.getElementById('estilos-bpmn')) {
    const enlace = document.createElement('link');
    enlace.id = 'estilos-bpmn';
    enlace.rel = 'stylesheet';
    enlace.href = 'bpmn.css';
    document.head.appendChild(enlace);
  }
}
