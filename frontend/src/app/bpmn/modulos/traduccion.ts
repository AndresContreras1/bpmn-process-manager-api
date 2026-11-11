/** Lo que bpmn-js escribe en la paleta y en los menus, en espanol. */
const ES: Record<string, string> = {
  'Open minimap': 'Abrir el minimapa',
  'Close minimap': 'Cerrar el minimapa',
  'Activate hand tool': 'Activar la mano',
  'Activate lasso tool': 'Activar el lazo',
  'Activate create/remove space tool': 'Activar crear o quitar espacio',
  'Activate global connect tool': 'Activar conectar',
  'Create start event': 'Crear un evento de inicio',
  'Create intermediate/boundary event': 'Crear un evento intermedio o de borde',
  'Create end event': 'Crear un evento de fin',
  'Create gateway': 'Crear una compuerta',
  'Create task': 'Crear una tarea',
  'Create data object reference': 'Crear un objeto de datos',
  'Create data store reference': 'Crear un almacen de datos',
  'Create expanded sub-process': 'Crear un subproceso',
  'Create pool/participant': 'Crear un pool',
  'Create group': 'Crear un grupo',
  'Append end event': 'Agregar un evento de fin',
  'Append gateway': 'Agregar una compuerta',
  'Append task': 'Agregar una tarea',
  'Append intermediate/boundary event': 'Agregar un evento intermedio',
  'Add text annotation': 'Agregar una anotacion',
  'Connect to other element': 'Conectar con otro elemento',
  Delete: 'Borrar',
  'Change element': 'Cambiar el tipo',
  'Add lane above': 'Agregar una lane arriba',
  'Add lane below': 'Agregar una lane abajo',
  'Divide into two lanes': 'Dividir en dos lanes',
  'Divide into three lanes': 'Dividir en tres lanes',
  Task: 'Tarea',
  'User task': 'Tarea de usuario',
  'Service task': 'Tarea de servicio',
  'Send task': 'Tarea de envio',
  'Receive task': 'Tarea de recepcion',
  'Call activity': 'Actividad de llamada',
  'Sub-process (collapsed)': 'Subproceso colapsado',
  'Sub-process (expanded)': 'Subproceso',
  'Exclusive gateway': 'Compuerta exclusiva',
  'Parallel gateway': 'Compuerta paralela',
  'Inclusive gateway': 'Compuerta inclusiva',
  'Start event': 'Evento de inicio',
  'End event': 'Evento de fin',
  'Message start event': 'Inicio por mensaje',
  'Timer start event': 'Inicio por temporizador',
  'Message end event': 'Fin de mensaje',
  'Terminate end event': 'Fin de terminacion',
  'Message intermediate catch event': 'Espera de mensaje',
  'Timer intermediate catch event': 'Espera de temporizador',
  'Timer boundary event': 'Temporizador de borde',
  'Timer boundary event (non-interrupting)': 'Temporizador de borde que no interrumpe',
  'Search in diagram': 'Buscar en el diagrama',
};

/** Pone en un texto los valores de sus marcadores: {element}. */
function llenar(texto: string, valores: Record<string, string>): string {
  return texto.replace(/\{([^}]+)\}/g, (entero, clave: string) => valores[clave] ?? entero);
}

/** El modulo {@code translate} de bpmn-js para un idioma; los que no son espanol quedan en ingles, que es el original. */
export function traduccion(idioma: string): {
  translate: [string, (texto: string, valores?: Record<string, string>) => string];
} {
  const espanol = idioma.toLowerCase().startsWith('es');
  return {
    translate: ['value', (texto, valores = {}) => llenar(espanol ? (ES[texto] ?? texto) : texto, valores)],
  };
}
