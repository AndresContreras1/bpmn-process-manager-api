import { BpmnModdle } from 'bpmn-moddle';
import { describe, expect, it } from 'vitest';

import { BPM_MODDLE } from './bpm.moddle';

/** Una tarea con el formulario que exporta la API (PR 69), tal cual lo escribe su EscritorBpmn. */
const CON_FORMULARIO = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
    xmlns:bpm="urn:bpmn-process-manager:bpm:1" id="Definitions_1" targetNamespace="urn:pruebas">
  <bpmn:process id="Process_1">
    <bpmn:userTask id="Activity_1" name="Approve the refund">
      <bpmn:extensionElements>
        <bpm:formulario>
          <bpm:campoFormulario nombre="monto" etiqueta="Amount" tipo="MONEDA" requerido="true" minimo="0"
              maximo="5000000" moneda="COP" porDefecto="order.total"/>
          <bpm:campoFormulario nombre="motivo" etiqueta="Reason" tipo="LISTA" visibleSi="monto &gt; 100">
            <bpm:opcion valor="DANADO" etiqueta="Damaged"/>
            <bpm:opcion valor="TARDE" etiqueta="Late"/>
          </bpm:campoFormulario>
        </bpm:formulario>
      </bpmn:extensionElements>
    </bpmn:userTask>
  </bpmn:process>
</bpmn:definitions>`;

/** Una tarea asignada a la persona de un campo (PR 70), tal cual la escribe el EscritorBpmn de la API. */
const CON_ASIGNACION = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
    xmlns:bpm="urn:bpmn-process-manager:bpm:1" id="Definitions_1" targetNamespace="urn:pruebas">
  <bpmn:process id="Process_1">
    <bpmn:userTask id="Activity_1" name="Approve the refund">
      <bpmn:extensionElements>
        <bpm:asignacion tipo="CAMPO" campo="tarea.reviewOrder.approver"/>
      </bpmn:extensionElements>
    </bpmn:userTask>
    <bpmn:userTask id="Activity_2" name="Pay the refund">
      <bpmn:extensionElements>
        <bpm:asignacion tipo="PERSONA" usuario="5"/>
      </bpmn:extensionElements>
    </bpmn:userTask>
  </bpmn:process>
</bpmn:definitions>`;

/** El plazo de una tarea y del caso, y un borde que escala (PR 71), tal cual los escribe el EscritorBpmn de la API. */
const CON_PLAZOS = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
    xmlns:bpm="urn:bpmn-process-manager:bpm:1" id="Definitions_1" targetNamespace="urn:pruebas">
  <bpmn:process id="Process_1">
    <bpmn:startEvent id="Event_1" name="Refund requested">
      <bpmn:extensionElements>
        <bpm:plazo cantidad="5" unidad="DIAS_HABILES"/>
      </bpmn:extensionElements>
    </bpmn:startEvent>
    <bpmn:userTask id="Activity_1" name="Approve the refund">
      <bpmn:extensionElements>
        <bpm:plazo cantidad="8" unidad="HORAS"/>
      </bpmn:extensionElements>
    </bpmn:userTask>
    <bpmn:boundaryEvent id="Event_2" name="Six hours" cancelActivity="false" attachedToRef="Activity_1">
      <bpmn:extensionElements>
        <bpm:escalamiento accion="REASIGNAR" usuario="5"/>
      </bpmn:extensionElements>
      <bpmn:timerEventDefinition id="Event_2_TimerEventDefinition">
        <bpmn:timeDuration>PT6H</bpmn:timeDuration>
      </bpmn:timerEventDefinition>
    </bpmn:boundaryEvent>
  </bpmn:process>
</bpmn:definitions>`;

describe('el espacio bpm: en bpmn-js', () => {
  it('el formulario de una tarea pasa entero por el editor: se lee sin avisos y se vuelve a escribir', async () => {
    const moddle = new BpmnModdle({ bpm: BPM_MODDLE });

    const { rootElement, warnings } = await moddle.fromXML(CON_FORMULARIO);
    const { xml } = await moddle.toXML(rootElement);

    expect(warnings).toEqual([]);
    expect(xml).toContain('<bpm:formulario>');
    expect(xml).toContain('tipo="MONEDA"');
    expect(xml).toContain('requerido="true"');
    expect(xml).toContain('maximo="5000000"');
    expect(xml).toMatch(/visibleSi="monto (&gt;|&#62;) 100"/);
    expect(xml).toContain('<bpm:opcion valor="TARDE" etiqueta="Late" />');
  });

  it('sin el tipo declarado, bpmn-js lo descartaria al guardar', async () => {
    const sinFormulario = {
      ...BPM_MODDLE,
      types: BPM_MODDLE.types.filter((tipo) => !['Formulario', 'CampoFormulario', 'Opcion'].includes(tipo.name)),
    };
    const moddle = new BpmnModdle({ bpm: sinFormulario });

    const { rootElement, warnings } = await moddle.fromXML(CON_FORMULARIO);
    const { xml } = await moddle.toXML(rootElement);

    expect(warnings.length).toBeGreaterThan(0);
    expect(xml).not.toContain('bpm:formulario');
  });

  it('a quien le llega una tarea pasa entero por el editor: se lee sin avisos y se vuelve a escribir', async () => {
    const moddle = new BpmnModdle({ bpm: BPM_MODDLE });

    const { rootElement, warnings } = await moddle.fromXML(CON_ASIGNACION);
    const { xml } = await moddle.toXML(rootElement);

    expect(warnings).toEqual([]);
    expect(xml).toContain('<bpm:asignacion tipo="CAMPO" campo="tarea.reviewOrder.approver" />');
    expect(xml).toContain('<bpm:asignacion tipo="PERSONA" usuario="5" />');
  });

  it('los plazos y el escalamiento pasan enteros por el editor: se leen sin avisos y se vuelven a escribir', async () => {
    const moddle = new BpmnModdle({ bpm: BPM_MODDLE });

    const { rootElement, warnings } = await moddle.fromXML(CON_PLAZOS);
    const { xml } = await moddle.toXML(rootElement);

    expect(warnings).toEqual([]);
    expect(xml).toContain('<bpm:plazo cantidad="5" unidad="DIAS_HABILES" />');
    expect(xml).toContain('<bpm:plazo cantidad="8" unidad="HORAS" />');
    expect(xml).toContain('<bpm:escalamiento accion="REASIGNAR" usuario="5" />');
  });

  it('sin el plazo y el escalamiento declarados, bpmn-js los descartaria al guardar', async () => {
    const sinPlazos = {
      ...BPM_MODDLE,
      types: BPM_MODDLE.types.filter((tipo) => !['Plazo', 'Escalamiento'].includes(tipo.name)),
    };
    const moddle = new BpmnModdle({ bpm: sinPlazos });

    const { rootElement, warnings } = await moddle.fromXML(CON_PLAZOS);
    const { xml } = await moddle.toXML(rootElement);

    expect(warnings.length).toBeGreaterThan(0);
    expect(xml).not.toContain('bpm:plazo');
    expect(xml).not.toContain('bpm:escalamiento');
  });

  it('sin la asignacion declarada, bpmn-js la descartaria al guardar', async () => {
    const sinAsignacion = { ...BPM_MODDLE, types: BPM_MODDLE.types.filter((tipo) => tipo.name !== 'Asignacion') };
    const moddle = new BpmnModdle({ bpm: sinAsignacion });

    const { rootElement, warnings } = await moddle.fromXML(CON_ASIGNACION);
    const { xml } = await moddle.toXML(rootElement);

    expect(warnings.length).toBeGreaterThan(0);
    expect(xml).not.toContain('bpm:asignacion');
  });
});
