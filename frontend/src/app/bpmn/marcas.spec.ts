import { describe, expect, it } from 'vitest';

import { Hallazgo } from '../models/diagnostico.model';
import { Diagrama } from '../models/diagrama.model';
import { elementoDe, marcasDe, marcasDeComparacion } from './marcas';

const diagrama = {
  pools: [{ id: 5, bpmnId: 'Participant_5' }],
  lanes: [{ id: 5, bpmnId: 'Lane_5' }],
  actividades: [{ id: 5, bpmnId: 'Activity_5' }],
  gateways: [{ id: 6, bpmnId: 'Gateway_6' }],
  eventos: [{ id: 7, bpmnId: 'Event_7' }],
  arcos: [{ id: 5, bpmnId: 'Flow_5' }],
  mensajes: [{ id: 5, bpmnId: 'MessageFlow_5' }],
} as unknown as Diagrama;

const hallazgo = (elemento: string, elementoId: number, severidad: string): Hallazgo =>
  ({ codigo: 'E-01', elemento, elementoId, severidad, problema: 'x', sugerencia: 'y' }) as unknown as Hallazgo;

describe('marcas del diagnostico', () => {
  it('cada clase de elemento se busca en su lista, aunque los ids coincidan', () => {
    const marcas = marcasDe(
      [
        hallazgo('Pool "A"', 5, 'ALTA'),
        hallazgo('Lane "A"', 5, 'MEDIA'),
        hallazgo('Actividad "A"', 5, 'ALTA'),
        hallazgo('Mensaje "A"', 5, 'BAJA'),
        hallazgo('Flujo de A a B', 5, 'ALTA'),
      ],
      diagrama,
    );
    expect(marcas).toEqual({
      Participant_5: ['marca-error'],
      Lane_5: ['marca-aviso'],
      Activity_5: ['marca-error'],
      MessageFlow_5: ['marca-aviso'],
      Flow_5: ['marca-error'],
    });
  });

  it('un error pesa mas que un aviso sobre el mismo elemento', () => {
    const marcas = marcasDe([hallazgo('Gateway "G"', 6, 'MEDIA'), hallazgo('Gateway "G"', 6, 'ALTA')], diagrama);
    expect(marcas['Gateway_6']).toEqual(['marca-error']);
    const alReves = marcasDe([hallazgo('Gateway "G"', 6, 'ALTA'), hallazgo('Gateway "G"', 6, 'MEDIA')], diagrama);
    expect(alReves['Gateway_6']).toEqual(['marca-error']);
  });

  it('un hallazgo del proceso entero no marca nada', () => {
    const general = { codigo: 'A-13', severidad: 'BAJA', problema: 'x', sugerencia: 'y' } as unknown as Hallazgo;
    expect(marcasDe([general], diagrama)).toEqual({});
    expect(elementoDe(general, diagrama)).toBeNull();
  });
});

describe('marcasDeComparacion', () => {
  const uno = (bpmnId: string) => ({ bpmnId, tipo: 'ACTIVIDAD', nombre: null });

  it('pinta cada elemento segun lo que le paso', () => {
    expect(
      marcasDeComparacion({
        de: '1',
        a: 'borrador',
        agregados: [uno('A')],
        quitados: [],
        cambiados: [{ ...uno('B'), campos: [] }],
        movidos: [uno('C')],
      }),
    ).toEqual({ A: ['marca-agregado'], B: ['marca-cambiado'], C: ['marca-movido'] });
  });

  it('lo quitado no se pinta: ya no esta en el dibujo', () => {
    expect(
      marcasDeComparacion({ de: '1', a: '2', agregados: [], quitados: [uno('X')], cambiados: [], movidos: [] }),
    ).toEqual({});
  });
});
