import { ChangeDetectionStrategy, Component, OnChanges, input } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ElementoElegido, LienzoBpmnComponent } from './lienzo-bpmn.component';

// Lo que bpmn-js devuelve de un elemento es dinamico
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Cualquiera = any;

const TIEMPOS = ['timeDuration', 'timeDate', 'timeCycle'] as const;
type Tiempo = (typeof TIEMPOS)[number];

/**
 * Las propiedades del elemento elegido. Cada cambio entra como un comando de bpmn-js: se deshace con Ctrl+Z y dispara
 * el autoguardado como cualquier otro.
 */
@Component({
  selector: 'app-panel-propiedades',
  imports: [FormsModule],
  templateUrl: './panel-propiedades.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class PanelPropiedadesComponent implements OnChanges {
  readonly elemento = input.required<ElementoElegido>();
  readonly lienzo = input.required<LienzoBpmnComponent>();
  readonly soloLectura = input<boolean>(false);

  nombre = '';
  condicion = '';
  porDefecto = false;
  rol = '';
  contenido = '';
  destino = '';
  siFalla = 'CONTINUAR';
  variable = '';
  criterio = '';
  campo = '';
  tipoTiempo: Tiempo = 'timeDuration';
  expresion = '';
  llamado = '';

  get tipo(): string {
    return this.elemento().tipo;
  }

  get esFlujo(): boolean {
    return this.tipo === 'bpmn:SequenceFlow';
  }

  get esLane(): boolean {
    return this.tipo === 'bpmn:Lane';
  }

  get esMensaje(): boolean {
    return this.tipo === 'bpmn:MessageFlow';
  }

  get esLlamada(): boolean {
    return this.tipo === 'bpmn:CallActivity';
  }

  get esTemporizador(): boolean {
    return this.definicionDeTiempo() !== undefined;
  }

  ngOnChanges(): void {
    const negocio = this.negocio();
    if (!negocio) {
      return;
    }
    this.nombre = negocio.name ?? negocio.text ?? '';
    this.condicion = negocio.conditionExpression?.body ?? '';
    this.porDefecto = this.esFlujo && negocio.sourceRef?.default === negocio;
    this.rol = this.extension('bpm:Lane')?.rol ?? '';
    const mensaje = this.extension('bpm:Mensaje');
    this.contenido = mensaje?.contenido ?? '';
    this.destino = mensaje?.destino ?? '';
    this.siFalla = mensaje?.siFalla ?? 'CONTINUAR';
    this.variable = mensaje?.variable ?? '';
    this.criterio = mensaje?.correlacion?.criterio ?? '';
    this.campo = mensaje?.correlacion?.campo ?? '';
    const definicion = this.definicionDeTiempo();
    this.tipoTiempo = TIEMPOS.find((tiempo) => definicion?.[tiempo]) ?? 'timeDuration';
    this.expresion = definicion?.[this.tipoTiempo]?.body ?? '';
    this.llamado = negocio.calledElement ?? '';
  }

  guardarNombre(): void {
    const campo = this.tipo === 'bpmn:TextAnnotation' ? 'text' : 'name';
    this.lienzo().actualizar(this.elemento().id, { [campo]: this.nombre });
  }

  guardarCondicion(): void {
    const expresion = this.condicion.trim()
      ? this.lienzo().crear('bpmn:FormalExpression', { body: this.condicion.trim() })
      : undefined;
    this.lienzo().actualizar(this.elemento().id, { conditionExpression: expresion });
  }

  guardarPorDefecto(): void {
    const negocio = this.negocio();
    this.lienzo().actualizar(negocio.sourceRef.id, { default: this.porDefecto ? negocio : undefined });
  }

  guardarRol(): void {
    this.guardarExtension('bpm:Lane', { rol: this.rol.trim(), orden: this.extension('bpm:Lane')?.orden ?? 0 });
  }

  guardarMensaje(): void {
    const correlacion = this.criterio.trim()
      ? this.lienzo().crear('bpm:Correlacion', {
          criterio: this.criterio.trim(),
          campo: this.campo.trim() || undefined,
        })
      : undefined;
    this.guardarExtension('bpm:Mensaje', {
      contenido: this.contenido,
      destino: this.destino || undefined,
      siFalla: this.siFalla === 'CONTINUAR' ? undefined : this.siFalla,
      variable: this.variable.trim() || undefined,
      correlacion,
    });
  }

  guardarTiempo(): void {
    const definicion = this.definicionDeTiempo();
    if (!definicion) {
      return;
    }
    TIEMPOS.forEach((tiempo) => delete definicion[tiempo]);
    if (this.expresion.trim()) {
      definicion[this.tipoTiempo] = this.lienzo().crear('bpmn:FormalExpression', { body: this.expresion.trim() });
    }
    // La definicion es de dentro del evento: se avisa con un cambio del evento para que entre en el guardado
    this.lienzo().actualizar(this.elemento().id, { eventDefinitions: this.negocio().eventDefinitions });
  }

  guardarLlamado(): void {
    this.lienzo().actualizar(this.elemento().id, { calledElement: this.llamado.trim() || undefined });
  }

  private negocio(): Cualquiera {
    return this.lienzo().negocio(this.elemento().id);
  }

  private definicionDeTiempo(): Cualquiera {
    return this.negocio()?.eventDefinitions?.find((uno: Cualquiera) => uno.$type === 'bpmn:TimerEventDefinition');
  }

  private extension(tipo: string): Cualquiera {
    return this.negocio()?.extensionElements?.values?.find((uno: Cualquiera) => uno.$type === tipo);
  }

  private guardarExtension(tipo: string, propiedades: Record<string, unknown>): void {
    const negocio = this.negocio();
    const lienzo = this.lienzo();
    const otras = (negocio.extensionElements?.values ?? []).filter((uno: Cualquiera) => uno.$type !== tipo);
    const nueva = lienzo.crear(tipo, propiedades);
    const contenedor = lienzo.crear('bpmn:ExtensionElements', { values: [...otras, nueva] });
    nueva.$parent = contenedor;
    contenedor.$parent = negocio;
    lienzo.actualizar(this.elemento().id, { extensionElements: contenedor });
  }
}
