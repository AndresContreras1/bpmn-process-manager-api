/**
 * El espacio {@code bpm:} para bpmn-js: lo que BPMN no tiene y el producto guarda (tipo y orden del participante, rol
 * de la lane, datos del mensaje y, desde el PR 69, el formulario de una tarea o de un inicio). Es el mismo que declara
 * {@code bpmn/xsd/bpm.xsd} en la API. Un tipo que no estuviera aqui se perderia al guardar desde el editor: bpmn-js
 * descarta lo que no reconoce de un espacio que conoce.
 */
export const BPM_MODDLE = {
  name: 'BPM',
  uri: 'urn:bpmn-process-manager:bpm:1',
  prefix: 'bpm',
  xml: { tagAlias: 'lowerCase' },
  types: [
    {
      name: 'Proceso',
      superClass: ['Element'],
      properties: [{ name: 'categoria', isAttr: true, type: 'String' }],
    },
    {
      name: 'Participante',
      superClass: ['Element'],
      properties: [
        { name: 'tipo', isAttr: true, type: 'String' },
        { name: 'integracion', isAttr: true, type: 'String' },
        { name: 'orden', isAttr: true, type: 'Integer' },
      ],
    },
    {
      name: 'Lane',
      superClass: ['Element'],
      properties: [
        { name: 'rol', isAttr: true, type: 'String' },
        { name: 'orden', isAttr: true, type: 'Integer' },
      ],
    },
    {
      name: 'Mensaje',
      superClass: ['Element'],
      properties: [
        { name: 'destino', isAttr: true, type: 'String' },
        { name: 'siFalla', isAttr: true, type: 'String' },
        { name: 'manejoError', isAttr: true, type: 'String' },
        { name: 'origenExterno', isAttr: true, type: 'Boolean' },
        { name: 'variable', isAttr: true, type: 'String' },
        { name: 'respuesta', isAttr: true, type: 'String' },
        { name: 'contenido', type: 'String' },
        { name: 'usoDeLosDatos', type: 'String' },
        { name: 'campos', type: 'Campo', isMany: true },
        { name: 'correlacion', type: 'Correlacion' },
      ],
    },
    {
      name: 'Campo',
      properties: [
        { name: 'nombre', isAttr: true, type: 'String' },
        { name: 'tipo', isAttr: true, type: 'String' },
      ],
    },
    {
      name: 'Formulario',
      superClass: ['Element'],
      properties: [{ name: 'campos', type: 'CampoFormulario', isMany: true }],
    },
    {
      name: 'CampoFormulario',
      properties: [
        { name: 'nombre', isAttr: true, type: 'String' },
        { name: 'etiqueta', isAttr: true, type: 'String' },
        { name: 'tipo', isAttr: true, type: 'String' },
        { name: 'ayuda', isAttr: true, type: 'String' },
        { name: 'requerido', isAttr: true, type: 'Boolean' },
        { name: 'minimo', isAttr: true, type: 'String' },
        { name: 'maximo', isAttr: true, type: 'String' },
        { name: 'largoMinimo', isAttr: true, type: 'Integer' },
        { name: 'largoMaximo', isAttr: true, type: 'Integer' },
        { name: 'patron', isAttr: true, type: 'String' },
        { name: 'desde', isAttr: true, type: 'String' },
        { name: 'hasta', isAttr: true, type: 'String' },
        { name: 'moneda', isAttr: true, type: 'String' },
        { name: 'porDefecto', isAttr: true, type: 'String' },
        { name: 'visibleSi', isAttr: true, type: 'String' },
        { name: 'requeridoSi', isAttr: true, type: 'String' },
        { name: 'opciones', type: 'Opcion', isMany: true },
      ],
    },
    {
      name: 'Opcion',
      properties: [
        { name: 'valor', isAttr: true, type: 'String' },
        { name: 'etiqueta', isAttr: true, type: 'String' },
      ],
    },
    {
      name: 'Correlacion',
      properties: [
        { name: 'criterio', isAttr: true, type: 'String' },
        { name: 'campo', isAttr: true, type: 'String' },
        { name: 'sinCaso', isAttr: true, type: 'String' },
      ],
    },
  ],
};
