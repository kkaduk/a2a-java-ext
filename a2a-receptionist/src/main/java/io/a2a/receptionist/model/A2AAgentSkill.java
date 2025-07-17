package io.a2a.receptionist.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface A2AAgentSkill {
    String id();
    String name();
    String description() default "";
    String[] tags() default {};
    String[] examples() default {};
    String[] inputModes() default {};
    String[] outputModes() default {};
}
