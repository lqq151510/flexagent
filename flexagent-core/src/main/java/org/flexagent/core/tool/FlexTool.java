package org.flexagent.core.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlexTool {
    /**
     * Alias for name().
     */
    String value() default "";

    /**
     * The name of the tool. If empty, the method name is used.
     */
    String name() default "";

    /**
     * Description of the tool.
     */
    String description() default "";
}
