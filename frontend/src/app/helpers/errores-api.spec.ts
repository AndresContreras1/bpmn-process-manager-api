import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';

import { esConflictoDeVersion, marcarErroresDelServidor, mensajeConLosCampos, mensajeDeError } from './errores-api';

function error(status: number, cuerpo: unknown = null): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: cuerpo });
}

describe('errores de la API', () => {
  it('dice que el servidor no responde cuando no hay respuesta, y que el rol no alcanza en un 403', () => {
    expect(mensajeDeError(error(0))).toContain('cannot be reached');
    expect(mensajeDeError(error(403, { detail: 'No importa' }))).toBe('Your role does not allow this action.');
  });

  it('muestra el detalle del ProblemDetail, o uno general si no lo trae', () => {
    expect(mensajeDeError(error(422, { detail: 'El proceso ya esta publicado.' }))).toBe(
      'El proceso ya esta publicado.',
    );
    expect(mensajeDeError(error(500))).toBe('Something went wrong. Please try again.');
  });

  it('suma al detalle el mensaje de cada campo rechazado, y sin campos es el detalle solo', () => {
    const cuerpo = {
      detail: 'Las respuestas no cumplen el formulario.',
      errors: { 'datos.monto': 'Es obligatorio.', 'datos.pedido': 'No cumple el patron.' },
    };

    expect(mensajeConLosCampos(error(400, cuerpo))).toBe(
      'Las respuestas no cumplen el formulario. datos.monto: Es obligatorio. datos.pedido: No cumple el patron.',
    );
    expect(mensajeConLosCampos(error(409, { detail: 'La tarea ya no espera.' }))).toBe('La tarea ya no espera.');
    expect(mensajeConLosCampos(error(0))).toContain('cannot be reached');
  });

  it('reconoce el conflicto de version por el titulo, no por el 409 solo', () => {
    expect(esConflictoDeVersion(error(409, { title: 'Conflicto de versión' }))).toBe(true);
    expect(esConflictoDeVersion(error(409, { title: 'Regla de negocio' }))).toBe(false);
    expect(esConflictoDeVersion(error(400, { title: 'Conflicto de versión' }))).toBe(false);
  });

  it('marca cada control con el mensaje de su campo, y no toca los que no vienen', () => {
    const formulario = new FormGroup({ nombre: new FormControl(''), correo: new FormControl('') });

    marcarErroresDelServidor(formulario, error(400, { errors: { nombre: 'El nombre ya existe.', otro: 'x' } }));

    expect(formulario.get('nombre')?.errors).toEqual({ servidor: 'El nombre ya existe.' });
    expect(formulario.get('correo')?.errors).toBeNull();
  });
});
