import { ParamMap } from '@angular/router';

import { DestinoDeResultado, ResultadoDeBusqueda } from './busqueda.model';
import { Accion } from './concesion.model';
import {
  CAMPOS_CARACTERIZACION,
  Caracterizacion,
  CampoCaracterizacion,
  Documentacion,
  Manual,
} from './documentacion.model';
import { Documento } from './documento.model';
import { Proceso, ProcesoDetalle } from './proceso.model';
import { RolAcceso } from './usuario.model';

/**
 * F22: el portal de lectura. Lo publicado de la organizacion, para quien lee procesos: carpetas, busqueda, la ficha, el
 * diagrama de la version vigente con la documentacion y los documentos de cada elemento, y el manual. Nunca ofrece
 * cambiar nada; lo unico que puede pedir es el manual, y solo quien puede exportar.
 */

/** La clase con la que el lienzo marca un elemento que tiene documentacion o documentos. */
export const MARCA_DOCUMENTADO = 'marca-documentado';

/** Lo que el portal deja hacer. No hay ningun control de edicion: ni siquiera existe la opcion. */
export interface ControlesDelPortal {
  /** Pedir que se arme el manual de la version vigente. */
  pedirManual: boolean;
}

/**
 * Lo que el portal ofrece, de lo que la API dice que la persona puede hacer con el proceso (GET /procesos/{id}/permisos)
 * y de su rol. Pedir un manual es exportar el proceso, y la matriz de rutas reserva los POST a quien administra o
 * edita: el rol de solo lectura exporta por defecto, pero la API le contestaria 403. Sin la respuesta de permisos no
 * se ofrece nada: el portal es de lectura, y en la duda se queda en lectura.
 */
export function controlesDelPortal(acciones: readonly Accion[] | null, rol: RolAcceso | null): ControlesDelPortal {
  const escribe = rol === 'ADMINISTRADOR' || rol === 'EDITOR';
  return { pedirManual: escribe && acciones !== null && acciones.includes('EXPORTAR') };
}

/** El texto de un elemento, o el del proceso entero con null; solo si tiene algo que mostrar. */
export function documentacionDe(textos: readonly Documentacion[], bpmnId: string | null): Documentacion | null {
  return textos.find((texto) => texto.bpmnId === bpmnId && texto.html.trim() !== '') ?? null;
}

/**
 * Los documentos de un elemento, o los del proceso entero con null, por nombre. Uno que el antivirus bloqueo no se
 * ensena: quien lee no puede hacer nada con el, y nadie lo va a poder bajar.
 */
export function documentosDe(documentos: readonly Documento[], bpmnId: string | null): Documento[] {
  return documentos
    .filter((documento) => documento.bpmnId === bpmnId && documento.estado !== 'BLOQUEADO')
    .sort((uno, otro) => uno.nombre.localeCompare(otro.nombre));
}

/** Solo se baja lo que el antivirus dejo pasar; mientras lo revisa, la API contestaria 409. */
export function sePuedeBajar(documento: Documento): boolean {
  return documento.estado === 'DISPONIBLE';
}

/**
 * Los elementos que tienen algo que leer, marcados en el lienzo: asi quien mira el diagrama sabe donde hacer clic. Las
 * claves son bpmnId; el proceso entero no es un elemento del dibujo y no se marca.
 */
export function marcasDeDocumentados(
  textos: readonly Documentacion[],
  documentos: readonly Documento[],
): Record<string, string[]> {
  const marcas: Record<string, string[]> = {};
  const marcar = (bpmnId: string | null): void => {
    if (bpmnId) {
      marcas[bpmnId] = [MARCA_DOCUMENTADO];
    }
  };
  textos.filter((texto) => texto.html.trim() !== '').forEach((texto) => marcar(texto.bpmnId));
  documentos.filter((documento) => documento.estado !== 'BLOQUEADO').forEach((documento) => marcar(documento.bpmnId));
  return marcas;
}

/**
 * El manual que se baja: uno listo de la version vigente, y de esos el del idioma de quien lee si lo hay. La lista
 * llega del mas nuevo al mas viejo, asi que el primero que sirve es el mas reciente. Un manual de otra version describe
 * un proceso que ya no es el vigente, y no se ofrece. El idioma se compara por la lengua (es, en, fr): es-CO lee el es.
 */
export function manualParaBajar(manuales: readonly Manual[], version: number | null, idioma: string): Manual | null {
  const listos = manuales.filter((manual) => manual.estado === 'DISPONIBLE' && manual.version === version);
  const lengua = lenguaDe(idioma);
  return listos.find((manual) => lenguaDe(manual.idioma) === lengua) ?? listos[0] ?? null;
}

/** El manual que la cola esta armando para la version vigente, si hay uno: no se pide otro mientras tanto. */
export function manualEnCurso(manuales: readonly Manual[], version: number | null): Manual | null {
  return manuales.find((manual) => manual.estado === 'PENDIENTE' && manual.version === version) ?? null;
}

function lenguaDe(idioma: string): string {
  return idioma.split(/[-_]/)[0].toLowerCase();
}

/** Un campo de la ficha de caracterizacion con lo que dice. */
export interface CampoLeido {
  campo: CampoCaracterizacion;
  texto: string;
}

/** Los campos de la ficha que alguien lleno, en su orden de lectura: los vacios no se ensenan. */
export function camposLlenos(ficha: Caracterizacion | null): CampoLeido[] {
  if (!ficha) {
    return [];
  }
  return CAMPOS_CARACTERIZACION.map((campo) => ({ campo, texto: (ficha[campo] ?? '').trim() })).filter(
    (leido) => leido.texto !== '',
  );
}

/** Si la revision del proceso paso sin que nadie confirmara que sigue vigente; la API lo calcula con su reloj. */
export function revisionVencida(proceso: Pick<Proceso, 'revisionVencida'>): boolean {
  return proceso.revisionVencida === true;
}

/**
 * Los resultados de la busqueda que tienen sentido en el portal: los comentarios son conversacion de trabajo, no lo
 * publicado. Un elemento llega una sola vez aunque este en el borrador y en la version vigente (la API se queda con
 * uno), asi que no se puede saber por el resultado si esta publicado: si no lo esta, su pagina se abre sin elegirlo.
 */
export function resultadosDelPortal(resultados: readonly ResultadoDeBusqueda[]): ResultadoDeBusqueda[] {
  return resultados.filter((resultado) => resultado.tipo !== 'COMENTARIO');
}

/**
 * Adonde lleva un resultado dentro del portal: la pagina de su proceso, con su elemento ya elegido cuando el resultado
 * es un elemento o un documento pegado a uno (?elemento=), que es donde se leen su documentacion y sus documentos.
 */
export function destinoEnPortal(resultado: ResultadoDeBusqueda): DestinoDeResultado {
  const conElemento = resultado.tipo === 'ELEMENTO' || resultado.tipo === 'DOCUMENTO';
  const consulta: Record<string, string> = conElemento && resultado.bpmnId ? { elemento: resultado.bpmnId } : {};
  return { ruta: ['/portal/procesos', resultado.procesoId], consulta };
}

/** Lo que el portal lee de su direccion: la carpeta, lo que se busca y la pagina. */
export interface DireccionDelPortal {
  carpetaId: number | null;
  texto: string;
  pagina: number;
}

/**
 * La direccion del portal, sin confiar en lo que traiga: una carpeta que no es un numero positivo es ninguna, y una
 * pagina negativa o con decimales es la primera, que la API rechazaria.
 */
export function leerDireccionDelPortal(parametros: ParamMap): DireccionDelPortal {
  const carpeta = Number(parametros.get('carpeta'));
  const pagina = Math.trunc(Number(parametros.get('pagina')));
  return {
    carpetaId: Number.isInteger(carpeta) && carpeta > 0 ? carpeta : null,
    texto: (parametros.get('q') ?? '').trim(),
    pagina: pagina > 0 ? pagina : 0,
  };
}

/** Lo que el portal necesita de un proceso, todo de una vez (PortalService.abrir). */
export interface ProcesoDelPortal {
  detalle: ProcesoDetalle;
  /** El XML de la version vigente; null si el proceso todavia no tiene una publicada. */
  xml: string | null;
  acciones: Accion[] | null;
  caracterizacion: Caracterizacion | null;
  textos: Documentacion[];
  documentos: Documento[];
  manuales: Manual[];
}
