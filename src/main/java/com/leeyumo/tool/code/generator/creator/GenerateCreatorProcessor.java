package com.leeyumo.tool.code.generator.creator;

import com.google.auto.service.AutoService;
import com.leeyumo.tool.code.generator.BasePersistenceProcessor;
import com.leeyumo.tool.code.generator.util.Constants;
import com.leeyumo.tool.code.generator.util.Description;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;

@AutoService(Processor.class)
public class GenerateCreatorProcessor extends BasePersistenceProcessor<GenerateCreator> {
    public GenerateCreatorProcessor() {
        super(GenerateCreator.class);
    }

    @Override
    protected void foreachClass(GenerateCreator generateCreator, Element element, RoundEnvironment roundEnv) {
        String packageName = element.getEnclosingElement().toString() + ".creator";
        String className = "Base" + element.getSimpleName().toString() + "Creator";

        String parentClassName = getParentClassName(generateCreator, element);

        operateCoding(element, packageName, className, parentClassName);
    }

    @Override
    protected FieldConfig toFieldConfig(VariableElement element){
        String name = element.getSimpleName().toString();
        boolean ignore = element.getAnnotation(GenerateCreatorIgnore.class) != null;
        Description description = element.getAnnotation(Description.class);
        String descriptionVar = description == null ? "" : description.value();
        return new FieldConfig(name, ignore, descriptionVar);
    }

    private String getParentClassName(GenerateCreator generateCreator, Element element) {
        String parent = generateCreator.parent();
        if (StringUtils.isNotEmpty(parent)){
            return parent;
        }
        if (element instanceof TypeElement){
            TypeElement typeElement = (TypeElement) element;
            String superClass = typeElement.getSuperclass().toString();
            if (Object.class.getName().equals(superClass)){
                return null;
            }else if(!typeElement.getSuperclass().toString().contains("BaseEntity")){
                return getPackageName(typeElement.getSuperclass().toString()) + ".BaseSuper" + getSuperClassName(typeElement.getSuperclass().toString()) +"Creator";
            }
            else {
                return convertToCreator(superClass);
            }
        }
        return null;
    }

    private String convertToCreator(String superClass) {
        return Constants.CREATOR_PARENT_PATH;
    }

}
