import RuleProvider from 'diagram-js/lib/features/rules/RuleProvider';

import {
  ModoProceso,
  entradaDePaletaPermitida,
  puedeUnirConMensaje,
  puedeUnirConSecuencia,
  reemplazoPermitido,
} from './reglas';

/* Los modulos propios de bpmn-js. Enchufan las reglas de reglas.ts en la paleta, los menus y las conexiones. */

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Cualquiera = any;

/** El modo del proceso que se esta dibujando: los modulos lo leen cada vez, asi que cambia sin recrear el lienzo. */
export class ModoDelLienzo {
  modo: ModoProceso = 'EJECUTABLE';
}

/** El participante que contiene a un elemento, subiendo por sus padres. */
function participanteDe(elemento: Cualquiera): string | null {
  let actual = elemento;
  while (actual) {
    if (actual.type === 'bpmn:Participant') {
      return actual.id;
    }
    actual = actual.parent;
  }
  return null;
}

function plano(elemento: Cualquiera): { tipo: string; participante: string | null } {
  return { tipo: elemento.type, participante: participanteDe(elemento) };
}

/** Las reglas duras: se preguntan antes que las de bpmn-js, y solo dicen que no. */
class ReglasDelModelo extends RuleProvider {
  static override $inject = ['eventBus'];

  override init(): void {
    const comprobar = (contexto: Cualquiera): boolean | undefined => {
      const { source, target, type } = contexto;
      const tipo = type ?? contexto.connection?.type;
      if (!source || !target) {
        return undefined;
      }
      if (tipo === 'bpmn:SequenceFlow' && !puedeUnirConSecuencia(plano(source), plano(target))) {
        return false;
      }
      if (tipo === 'bpmn:MessageFlow' && !puedeUnirConMensaje(plano(source), plano(target))) {
        return false;
      }
      return undefined;
    };
    this.addRule('connection.create', 2000, comprobar);
    this.addRule('connection.reconnect', 2000, comprobar);
  }
}

/** La paleta y el menu de cambiar de tipo, limitados al modo del proceso. */
class FiltroPorModo {
  static $inject = ['palette', 'popupMenu', 'modoDelLienzo'];

  constructor(palette: Cualquiera, popupMenu: Cualquiera, modoDelLienzo: ModoDelLienzo) {
    palette.registerProvider(500, {
      getPaletteEntries: () => (entradas: Record<string, unknown>) =>
        Object.fromEntries(Object.entries(entradas).filter(([id]) => entradaDePaletaPermitida(id, modoDelLienzo.modo))),
    });
    popupMenu.registerProvider('bpmn-replace', 500, {
      getPopupMenuEntries: () => (entradas: Record<string, unknown>) =>
        Object.fromEntries(Object.entries(entradas).filter(([id]) => reemplazoPermitido(id, modoDelLienzo.modo))),
      getPopupMenuHeaderEntries: () => () => ({}),
    });
  }
}

export const moduloDeModoYReglas = {
  __init__: ['reglasDelModelo', 'filtroPorModo'],
  reglasDelModelo: ['type', ReglasDelModelo],
  filtroPorModo: ['type', FiltroPorModo],
};

/** Solo el modo, para el visor, que no tiene paleta ni reglas. */
export function moduloDelModo(modo: ModoDelLienzo): Cualquiera {
  return { modoDelLienzo: ['value', modo] };
}
