package com.facimus.procesos.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PR 55: el constructor que MapStruct usa cuando un record tiene mas de uno. MapStruct reconoce cualquier anotacion
 * que se llame {@code Default}, sea del paquete que sea; esta es la del proyecto.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.CONSTRUCTOR)
public @interface Default {
}
