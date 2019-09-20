package com.leeyumo.tool.code.generator.updater;

import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import com.leeyumo.tool.code.generator.BasePersistenceProcessor;
import com.leeyumo.tool.code.generator.BaseProcessor;
import com.leeyumo.tool.code.generator.util.Constants;
import com.leeyumo.tool.code.generator.util.Description;
import com.squareup.javapoet.*;
import io.swagger.annotations.ApiModelProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@AutoService(Processor.class)
public class GenerateUpdaterProcessor extends BasePersistenceProcessor<GenerateUpdater> {
    public GenerateUpdaterProcessor() {
        super(GenerateUpdater.class);
    }

    @Override
    protected void foreachClass(GenerateUpdater generateUpdater, Element element, RoundEnvironment roundEnv) {
        String packageName = element.getEnclosingElement().toString() + ".updater";
        String className = "Base" + element.getSimpleName().toString() + "Updater";

        String parentClassName = getParentClassName(generateUpdater, element);

        operateCoding(element, packageName, className, parentClassName);
    }

    @Override
    protected FieldConfig toFieldConfig(VariableElement element){
        String name = element.getSimpleName().toString();
        boolean ignore = element.getAnnotation(GenerateUpdaterIgnore.class) != null;
        Description description = element.getAnnotation(Description.class);
        String descriptionVar = description == null ? "" : description.value();
        return new FieldConfig(name, ignore, descriptionVar);
    }

    private String getParentClassName(GenerateUpdater generateUpdater, Element element) {
        String parent = generateUpdater.parent();
        if (StringUtils.isNotEmpty(parent)){
            return parent;
        }
        if (element instanceof TypeElement){
            TypeElement typeElement = (TypeElement) element;
            String superClass = typeElement.getSuperclass().toString();
            if (Object.class.getName().equals(superClass)){
                return null;
            }else if(!typeElement.getSuperclass().toString().contains(Constants.ABSTRACT_BASE_ENTITY_NAME)){
                return getPackageName(typeElement.getSuperclass().toString()) + ".BaseSuper" + getSuperClassName(typeElement.getSuperclass().toString()) +"Updater";
            }
            else {
                return convertToUpdater(superClass);
            }
        }
        return null;
    }

    private String convertToUpdater(String superClass) {
        return Constants.UPDATER_PARENT_PATH;
    }
}
