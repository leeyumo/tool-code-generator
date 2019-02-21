package com.leeyumo.tool.code.generator.creator;


import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
public @interface GenerateCreatorIgnore {
}
