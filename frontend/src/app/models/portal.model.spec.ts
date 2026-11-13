import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { ResultadoDeBusqueda } from './busqueda.model';
import { Accion } from './concesion.model';
import { Caracterizacion, Documentacion, Manual } from './documentacion.model';
import { Documento } from './documento.model';
import {
  MARCA_DOCUMENTADO,
  camposLlenos,
  controlesDelPortal,
  destinoEnPortal,
  documentacionDe,
  documentosDe,
  leerDireccionDelPortal,
  manualEnCurso,
  manualParaBajar,
  marcasDeDocumentados,
  resultadosDelPortal,
  revisionVencida,
  sePuedeBajar,
} from './portal.model';

function texto(bpmnId: string | null, html: string): Documentacion {
  return { bpmnId, markdown: html, html, version: 0, modificadoPor: 1, fechaModificacion: '2026-11-12T10:00:00' };
}

function documento(id: number, cambios: Partial<Documento> = {}): Documento {
  return {
    id,
    procesoId: 7,
    bpmnId: null,
    nombre: `doc-${id}.pdf`,
    versionActual: 1,
    estado: 'DISPONIBLE',
    motivo: null,
    revisado: true,
    tipo: 'application/pdf',
    tamano: 1024,
    version: 0,
    fechaModificacion: '2026-11-12T10:00:00',
    ...cambios,
  };
}

function manual(id: number, cambios: Partial<Manual> = {}): Manual {
  return {
    id,
    procesoId: 7,
    version: 2,
    idioma: 'es-CO',
    estado: 'DISPONIBLE',
    motivo: null,
    pedidoEn: '2026-11-12T10:00:00',
    listoEn: '2026-11-12T10:01:00',
    ...cambios,
  };
}

function resultado(cambios: Partial<ResultadoDeBusqueda> = {}): ResultadoDeBusqueda {
  return {
    tipo: 'PROCESO',
    procesoId: 7,
    proceso: 'Order fulfillment',
    titulo: 'Order fulfillment',
    bpmnId: null,
    tipoDeElemento: null,
    version: null,
    documentoId: null,
    comentarioId: null,
    hiloId: null,
    fragmento: [],
    ...cambios,
  };
}

describe('portal.model', () => {
  describe('controlesDelPortal', () => {
    const todas: Accion[] = ['VER', 'COMENTAR', 'MODELAR', 'EXPORTAR', 'ADMINISTRAR'];

    it('ofrece pedir el manual a quien edita o administra y puede exportar', () => {
      expect(controlesDelPortal(['VER', 'EXPORTAR'], 'EDITOR')).toEqual({ pedirManual: true });
      expect(controlesDelPortal(todas, 'ADMINISTRADOR')).toEqual({ pedirManual: true });
    });

    it('no lo ofrece sin EXPORTAR, aunque se pueda modelar', () => {
      expect(controlesDelPortal(['VER', 'MODELAR', 'PUBLICAR'], 'EDITOR')).toEqual({ pedirManual: false });
    });

    it('no lo ofrece al rol de solo lectura aunque los permisos digan que exporta: la API le diria 403', () => {
      expect(controlesDelPortal(['VER', 'COMENTAR', 'EXPORTAR'], 'SOLO_LECTURA')).toEqual({ pedirManual: false });
    });

    it('sin la respuesta de permisos o sin sesion se queda en lectura', () => {
      expect(controlesDelPortal(null, 'ADMINISTRADOR')).toEqual({ pedirManual: false });
      expect(controlesDelPortal(todas, null)).toEqual({ pedirManual: false });
    });
  });

  describe('documentacionDe', () => {
    const textos = [texto(null, '<p>Whole</p>'), texto('Task_1', '<p>Pack</p>'), texto('Task_2', '   ')];

    it('da el texto del elemento pedido, o el del proceso con null', () => {
      expect(documentacionDe(textos, 'Task_1')?.html).toBe('<p>Pack</p>');
      expect(documentacionDe(textos, null)?.html).toBe('<p>Whole</p>');
    });

    it('un elemento sin texto, o con uno en blanco, no tiene documentacion', () => {
      expect(documentacionDe(textos, 'Task_9')).toBeNull();
      expect(documentacionDe(textos, 'Task_2')).toBeNull();
      expect(documentacionDe([texto('Task_1', '<p>x</p>')], null)).toBeNull();
    });
  });

  describe('documentosDe', () => {
    const documentos = [
      documento(1, { bpmnId: 'Task_1', nombre: 'zeta.pdf' }),
      documento(2, { bpmnId: 'Task_1', nombre: 'alfa.pdf', estado: 'PENDIENTE' }),
      documento(3, { bpmnId: 'Task_1', nombre: 'beta.pdf', estado: 'BLOQUEADO' }),
      documento(4, { bpmnId: null, nombre: 'general.pdf' }),
      documento(5, { bpmnId: 'Task_2', nombre: 'otro.pdf' }),
    ];

    it('da los del elemento por nombre, sin los bloqueados', () => {
      expect(documentosDe(documentos, 'Task_1').map((uno) => uno.id)).toEqual([2, 1]);
    });

    it('con null da los del proceso entero, no los de sus elementos', () => {
      expect(documentosDe(documentos, null).map((uno) => uno.id)).toEqual([4]);
    });

    it('no cambia el orden de la lista que recibe', () => {
      documentosDe(documentos, 'Task_1');
      expect(documentos.map((uno) => uno.id)).toEqual([1, 2, 3, 4, 5]);
    });
  });

  it('solo se baja lo disponible', () => {
    expect(sePuedeBajar(documento(1))).toBe(true);
    expect(sePuedeBajar(documento(1, { estado: 'PENDIENTE' }))).toBe(false);
    expect(sePuedeBajar(documento(1, { estado: 'BLOQUEADO' }))).toBe(false);
  });

  describe('marcasDeDocumentados', () => {
    it('marca cada elemento con texto o con un documento que no esta bloqueado, una sola vez', () => {
      const marcas = marcasDeDocumentados(
        [texto(null, '<p>Whole</p>'), texto('Task_1', '<p>Pack</p>'), texto('Task_2', ' ')],
        [
          documento(1, { bpmnId: 'Task_1' }),
          documento(2, { bpmnId: 'Task_3', estado: 'PENDIENTE' }),
          documento(3, { bpmnId: 'Task_4', estado: 'BLOQUEADO' }),
          documento(4, { bpmnId: null }),
        ],
      );
      expect(marcas).toEqual({ Task_1: [MARCA_DOCUMENTADO], Task_3: [MARCA_DOCUMENTADO] });
    });

    it('sin nada que leer no marca nada', () => {
      expect(marcasDeDocumentados([], [])).toEqual({});
    });
  });

  describe('manualParaBajar', () => {
    it('prefiere el listo de la version vigente en la lengua de quien lee', () => {
      const manuales = [manual(3, { idioma: 'en-AU' }), manual(2, { idioma: 'es' }), manual(1, { idioma: 'es-CO' })];
      expect(manualParaBajar(manuales, 2, 'es-CO')?.id).toBe(2);
      expect(manualParaBajar(manuales, 2, 'en_US')?.id).toBe(3);
    });

    it('sin uno en su lengua da el mas nuevo listo de la version vigente', () => {
      const manuales = [manual(3, { idioma: 'en-AU' }), manual(2, { idioma: 'es-CO' })];
      expect(manualParaBajar(manuales, 2, 'fr-FR')?.id).toBe(3);
    });

    it('no da uno de otra version, ni uno pendiente o fallido', () => {
      const manuales = [
        manual(4, { version: 1 }),
        manual(3, { estado: 'PENDIENTE', listoEn: null }),
        manual(2, { estado: 'FALLIDO', motivo: 'x' }),
      ];
      expect(manualParaBajar(manuales, 2, 'es-CO')).toBeNull();
      expect(manualParaBajar(manuales, 1, 'es-CO')?.id).toBe(4);
      expect(manualParaBajar([], null, 'es-CO')).toBeNull();
    });
  });

  it('el manual en curso es el pendiente de la version vigente', () => {
    const manuales = [manual(3, { version: 1, estado: 'PENDIENTE' }), manual(2, { estado: 'PENDIENTE' }), manual(1)];
    expect(manualEnCurso(manuales, 2)?.id).toBe(2);
    expect(manualEnCurso(manuales, 3)).toBeNull();
    expect(manualEnCurso([manual(1)], 2)).toBeNull();
  });

  describe('camposLlenos', () => {
    it('da los campos con texto, sin espacios de sobra, en el orden de la ficha', () => {
      const ficha: Caracterizacion = {
        objetivo: '  Deliver on time ',
        alcance: '',
        entradas: null,
        salidas: 'A packed order',
        proveedores: '   ',
        clientes: null,
        indicadores: null,
        riesgos: 'Delays',
        normativa: null,
        version: 1,
        fechaModificacion: null,
      };
      expect(camposLlenos(ficha)).toEqual([
        { campo: 'objetivo', texto: 'Deliver on time' },
        { campo: 'salidas', texto: 'A packed order' },
        { campo: 'riesgos', texto: 'Delays' },
      ]);
    });

    it('sin ficha no hay campos', () => {
      expect(camposLlenos(null)).toEqual([]);
    });
  });

  it('la revision esta vencida solo si la API lo dice', () => {
    expect(revisionVencida({ revisionVencida: true })).toBe(true);
    expect(revisionVencida({ revisionVencida: false })).toBe(false);
    expect(revisionVencida({ revisionVencida: null })).toBe(false);
    expect(revisionVencida({})).toBe(false);
  });

  describe('resultadosDelPortal', () => {
    it('deja procesos, elementos y documentos, en su orden, sin los comentarios', () => {
      const encontrados = resultadosDelPortal([
        resultado({ titulo: 'proceso' }),
        resultado({ tipo: 'COMENTARIO', titulo: 'comentario' }),
        resultado({ tipo: 'ELEMENTO', bpmnId: 'Task_1', version: null, titulo: 'del borrador' }),
        resultado({ tipo: 'ELEMENTO', bpmnId: 'Task_2', version: 2, titulo: 'publicado' }),
        resultado({ tipo: 'DOCUMENTO', documentoId: 5, titulo: 'documento' }),
      ]);
      expect(encontrados.map((uno) => uno.titulo)).toEqual(['proceso', 'del borrador', 'publicado', 'documento']);
    });

    it('sin resultados no hay nada', () => {
      expect(resultadosDelPortal([])).toEqual([]);
    });
  });

  describe('destinoEnPortal', () => {
    it('un proceso abre su pagina del portal sin elemento', () => {
      expect(destinoEnPortal(resultado({ bpmnId: 'Task_1' }))).toEqual({ ruta: ['/portal/procesos', 7], consulta: {} });
    });

    it('un elemento, o un documento pegado a uno, lo abre elegido', () => {
      expect(destinoEnPortal(resultado({ tipo: 'ELEMENTO', bpmnId: 'Task_1' })).consulta).toEqual({
        elemento: 'Task_1',
      });
      expect(destinoEnPortal(resultado({ tipo: 'DOCUMENTO', bpmnId: 'Task_2' })).consulta).toEqual({
        elemento: 'Task_2',
      });
    });

    it('un documento del proceso entero lo abre sin elemento', () => {
      expect(destinoEnPortal(resultado({ tipo: 'DOCUMENTO', bpmnId: null })).consulta).toEqual({});
    });
  });

  describe('leerDireccionDelPortal', () => {
    it('lee la carpeta, el texto sin espacios de sobra y la pagina', () => {
      expect(leerDireccionDelPortal(convertToParamMap({ carpeta: '4', q: '  packing ', pagina: '2' }))).toEqual({
        carpetaId: 4,
        texto: 'packing',
        pagina: 2,
      });
    });

    it('sin nada es la raiz, sin texto y la primera pagina', () => {
      expect(leerDireccionDelPortal(convertToParamMap({}))).toEqual({ carpetaId: null, texto: '', pagina: 0 });
    });

    it('no confia en lo que traiga: una carpeta rara es ninguna y una pagina rara es la primera', () => {
      for (const carpeta of ['abc', '0', '-3', '1.5']) {
        expect(leerDireccionDelPortal(convertToParamMap({ carpeta })).carpetaId).toBeNull();
      }
      expect(leerDireccionDelPortal(convertToParamMap({ pagina: '-1' })).pagina).toBe(0);
      expect(leerDireccionDelPortal(convertToParamMap({ pagina: 'x' })).pagina).toBe(0);
      expect(leerDireccionDelPortal(convertToParamMap({ pagina: '2.9' })).pagina).toBe(2);
      expect(leerDireccionDelPortal(convertToParamMap({ pagina: '1' })).pagina).toBe(1);
    });
  });
});
