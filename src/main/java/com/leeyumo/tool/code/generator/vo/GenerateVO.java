package com.leeyumo.tool.code.generator.vo;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GenerateVO {
    String pkgName() default "";

    String parent() default "";
}
