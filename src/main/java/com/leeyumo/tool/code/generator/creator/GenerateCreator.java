package com.leeyumo.tool.code.generator.creator;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
public @interface GenerateCreator {
    String parent() default "";
}
