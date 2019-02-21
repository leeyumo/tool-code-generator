package com.leeyumo.tool.code.generator;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
public @interface Description {
    String value();
}

