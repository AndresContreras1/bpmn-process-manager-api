import { describe, expect, it } from 'vitest';

import {
  ElementoPlano,
  entradaDePaletaPermitida,
  puedeUnirConMensaje,
  puedeUnirConSecuencia,
  reemplazoPermitido,
} from './reglas';

const tarea = (participante: string | null = 'A'): ElementoPlano => ({ tipo: 'bpmn:Task', participante });

describe('reglas del modelo', () => {
  it('un inicio no recibe y un fin no emite', () => {
    expect(puedeUnirConSecuencia(tarea(), { tipo: 'bpmn:StartEvent', participante: 'A' })).toBe(false);
    expect(puedeUnirConSecuencia({ tipo: 'bpmn:EndEvent', participante: 'A' }, tarea())).toBe(false);
    expect(puedeUnirConSecuencia(tarea(), { tipo: 'bpmn:EndEvent', participante: 'A' })).toBe(true);
  });

  it('a un evento de borde no le llega un flujo', () => {
    expect(puedeUnirConSecuencia(tarea(), { tipo: 'bpmn:BoundaryEvent', participante: 'A' })).toBe(false);
  });

  it('un flujo no cruza de un pool a otro', () => {
    expect(puedeUnirConSecuencia(tarea('A'), tarea('B'))).toBe(false);
    expect(puedeUnirConSecuencia(tarea('A'), tarea('A'))).toBe(true);
  });

  it('un mensaje solo va entre pools distintos', () => {
    expect(puedeUnirConMensaje(tarea('A'), tarea('B'))).toBe(true);
    expect(puedeUnirConMensaje(tarea('A'), tarea('A'))).toBe(false);
    expect(puedeUnirConMensaje(tarea(null), tarea('B'))).toBe(false);
  });

  it('la paleta de un proceso ejecutable no ofrece subprocesos', () => {
    expect(entradaDePaletaPermitida('create.subprocess-expanded', 'EJECUTABLE')).toBe(false);
    expect(entradaDePaletaPermitida('create.subprocess-expanded', 'DOCUMENTAL')).toBe(true);
    expect(entradaDePaletaPermitida('create.task', 'EJECUTABLE')).toBe(true);
  });

  it('el menu de reemplazo solo ofrece lo que el modelo tiene', () => {
    expect(reemplazoPermitido('replace-with-user-task', 'EJECUTABLE')).toBe(true);
    expect(reemplazoPermitido('replace-with-signal-start', 'DOCUMENTAL')).toBe(false);
  });

  it('un proceso ejecutable no ofrece lo que no puede correr', () => {
    expect(reemplazoPermitido('replace-with-timer-start', 'EJECUTABLE')).toBe(false);
    expect(reemplazoPermitido('replace-with-timer-start', 'DOCUMENTAL')).toBe(true);
    expect(reemplazoPermitido('replace-with-task', 'EJECUTABLE')).toBe(false);
  });
});
