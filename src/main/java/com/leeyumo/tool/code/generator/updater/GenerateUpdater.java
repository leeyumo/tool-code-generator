package com.leeyumo.tool.code.generator.updater;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
public @interface GenerateUpdater {
    String parent() default "";
}
