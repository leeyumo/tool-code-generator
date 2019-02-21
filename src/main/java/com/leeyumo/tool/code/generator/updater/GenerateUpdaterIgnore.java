package com.leeyumo.tool.code.generator.updater;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Documented
public @interface GenerateUpdaterIgnore {
}
