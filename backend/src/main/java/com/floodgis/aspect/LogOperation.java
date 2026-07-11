package com.floodgis.aspect;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogOperation {
    String action() default "";
    String module() default "";
    String description() default "";
}
