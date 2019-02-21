package com.leeyumo.tool.code.generator.creator;


import org.springframework.data.domain.AbstractAggregateRoot;

public abstract class BaseAbstractEntityCreator<T extends BaseAbstractEntityCreator>{
    public void accept(AbstractAggregateRoot target) {
    }

    public BaseAbstractEntityCreator() {
    }
}
