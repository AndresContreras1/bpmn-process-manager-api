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
    } else if (cambios['marcas']) {
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
  }
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
