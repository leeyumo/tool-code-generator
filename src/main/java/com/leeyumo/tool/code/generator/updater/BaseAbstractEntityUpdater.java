package com.leeyumo.tool.code.generator.updater;


import org.springframework.data.domain.AbstractAggregateRoot;

public abstract class BaseAbstractEntityUpdater<T extends BaseAbstractEntityUpdater> {
    public void accept(AbstractAggregateRoot target) {
    }

    public BaseAbstractEntityUpdater() {
    }
}
