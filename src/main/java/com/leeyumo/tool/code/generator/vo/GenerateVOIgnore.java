package com.leeyumo.tool.code.generator.vo;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GenerateVOIgnore {
}
