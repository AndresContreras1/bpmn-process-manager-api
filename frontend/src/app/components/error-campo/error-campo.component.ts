import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { AbstractControl } from '@angular/forms';

/** Mensaje de error de un campo: el de sus validadores o el que devolvio la API para ese campo. */
@Component({
  selector: 'app-error-campo',
  imports: [],
  templateUrl: './error-campo.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './error-campo.component.scss',
})
export class ErrorCampoComponent {
  control = input.required<AbstractControl | null>();
}
