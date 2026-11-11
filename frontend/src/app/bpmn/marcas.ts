import { Hallazgo } from '../models/diagnostico.model';
import { Diagrama } from '../models/diagrama.model';

/** Cada clase de elemento numera sus ids en su tabla: lo que dice de cual es la etiqueta del hallazgo. */
function bpmnIdDe(hallazgo: Hallazgo, diagrama: Diagrama): string | null {
  const { elemento, elementoId } = hallazgo;
  if (!elemento || elementoId == null) {
    return null;
  }
  const buscar = (lista: { id: number; bpmnId?: string }[]): string | null =>
    lista.find((uno) => uno.id === elementoId)?.bpmnId ?? null;
  if (elemento.startsWith('Pool')) {
    return buscar(diagrama.pools);
  }
  if (elemento.startsWith('Lane')) {
    return buscar(diagrama.lanes);
  }
  if (elemento.startsWith('Actividad')) {
    return buscar(diagrama.actividades);
  }
  if (elemento.startsWith('Gateway')) {
    return buscar(diagrama.gateways);
  }
  if (elemento.startsWith('Evento')) {
    return buscar(diagrama.eventos);
  }
  if (elemento.startsWith('Mensaje')) {
    return buscar(diagrama.mensajes);
  }
  return buscar(diagrama.arcos);
}

/** Los hallazgos como marcas del lienzo: un error pesa mas que un aviso sobre el mismo elemento. */
export function marcasDe(hallazgos: Hallazgo[], diagrama: Diagrama): Record<string, string[]> {
  const marcas: Record<string, string[]> = {};
  for (const hallazgo of hallazgos) {
    const id = bpmnIdDe(hallazgo, diagrama);
    if (id) {
      const esError = hallazgo.severidad === 'ALTA';
      if (esError || !marcas[id]) {
        marcas[id] = [esError ? 'marca-error' : 'marca-aviso'];
      }
    }
  }
  return marcas;
}

/** El bpmnId del elemento de un hallazgo, para llevar la vista hasta el. */
export function elementoDe(hallazgo: Hallazgo, diagrama: Diagrama): string | null {
  return bpmnIdDe(hallazgo, diagrama);
}
