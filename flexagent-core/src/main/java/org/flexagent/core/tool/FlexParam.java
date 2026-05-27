package org.flexagent.core.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlexParam {
    /**
     * Alias for name().
     */
    String value() default "";

    /**
     * The name of the parameter.
     */
    String name() default "";

    /**
     * Description of the parameter.
     */
    String description() default "";

    /**
     * Whether the parameter is required.
     */
    boolean required() default true;
}
