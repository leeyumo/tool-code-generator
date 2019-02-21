package com.leeyumo.tool.code.generator.updater;

import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
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
public class GenerateUpdaterProcessor extends BaseProcessor<GenerateUpdater> {
    public GenerateUpdaterProcessor() {
        super(GenerateUpdater.class);
    }

    @Override
    protected void foreachClass(GenerateUpdater generateUpdater, Element element, RoundEnvironment roundEnv) {
        String packageName = element.getEnclosingElement().toString() ;
        String className = "Base" + element.getSimpleName().toString() + "Updater";

        String parentClassName = getParentClassName(generateUpdater, element);

        Set<TypeAndName> publicSetter = findPublicSetter(element);

        TypeVariableName typeVariableName = TypeVariableName.get("T extends " + className);

        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className)
                .addTypeVariable(typeVariableName)
                .addAnnotation(Data.class)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

        if (StringUtils.isNotEmpty(parentClassName)){
            ClassName parent = ClassName.bestGuess(parentClassName);
            TypeName typeName = TypeVariableName.get("T");
            ParameterizedTypeName parameterizedTypeName = ParameterizedTypeName.get(parent, typeName);
            typeSpecBuilder.superclass(parameterizedTypeName);
        }

        MethodSpec.Builder acceptMethodBuilder = MethodSpec.methodBuilder("accept")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(ParameterSpec.builder(TypeName.get(element.asType()), "target")
                        .build());
        if (StringUtils.isNotEmpty(parentClassName)){
            acceptMethodBuilder.addStatement("super.accept(target)");
        }

        for (TypeAndName typeAndName : publicSetter) {
            String acceptMethodName = "accept" + typeAndName.getName().substring(0, 1).toUpperCase() + typeAndName.getName().substring(1, typeAndName.getName().length());
            String targetSetterName = "set" + typeAndName.getName().substring(0, 1).toUpperCase() + typeAndName.getName().substring(1, typeAndName.getName().length());
            acceptMethodBuilder.addStatement("this.$L(target::$L)", acceptMethodName, targetSetterName);

            //生成属性声明
//            ParameterizedTypeName parameterizedTypeName = ParameterizedTypeName.get(ClassName.get(DataOptional.class), typeAndName.getType());
//            FieldSpec fieldSpec = FieldSpec.builder(parameterizedTypeName, typeAndName.getName(), Modifier.PRIVATE)
            FieldSpec fieldSpec = FieldSpec.builder(typeAndName.getType(), typeAndName.getName(), Modifier.PRIVATE)
                    .addAnnotation(AnnotationSpec.builder(Setter.class)
                            .addMember("value", "$T.PRIVATE", AccessLevel.class)
                            .build())
                    .addAnnotation(AnnotationSpec.builder(Getter.class)
                            .addMember("value", "$T.PUBLIC", AccessLevel.class)
                            .build())
                    .addAnnotation(AnnotationSpec.builder(ApiModelProperty.class)
                            .addMember("value", "$S",typeAndName.getDescription())
                            .addMember("name", "$S", typeAndName.getName())
                            .build())
                    .build();
            typeSpecBuilder.addField(fieldSpec);

            //生成builder属性方法，目的是可以连环调用给Updater各个属性赋值
            MethodSpec methodSpec = MethodSpec.methodBuilder(typeAndName.getName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeVariableName.get("T"))
                    .addParameter(typeAndName.getType(), typeAndName.getName())
//                    .addStatement("this.$L = DataOptional.of($L)", typeAndName.getName(), typeAndName.getName())
                    .addStatement("this.$L = $L", typeAndName.getName(), typeAndName.getName())
                    .addStatement("return (T) this")
                    .build();
            typeSpecBuilder.addMethod(methodSpec);


            //生成通过lambda表达式为目标单个属性赋值的方法，目的是在调用此方法时同时调用lambda中的::对应的set方法
            ParameterizedTypeName consumerTypeName = ParameterizedTypeName.get(ClassName.get(Consumer.class), typeAndName.getType());
            MethodSpec applyMethodSpec = MethodSpec.methodBuilder(acceptMethodName)
                    .addModifiers(Modifier.PUBLIC)
//                    .returns(TypeVariableName.get("T"))
                    .returns(TypeName.VOID)
                    .addParameter(consumerTypeName, "consumer")
                    .addCode(CodeBlock.builder()
                            .add("if(this.$L != null){ \n", typeAndName.getName())
//                            .add("\tconsumer.accept(this.$L.getValue());\n", typeAndName.getName())
                            .add("\tconsumer.accept(this.$L);\n", typeAndName.getName())
                            .add("}\n")
                            .build())
//                    .addStatement("return (T) this")
                    .build();
            typeSpecBuilder.addMethod(applyMethodSpec);
        }

        //生成最后的核心accept方法，为目标对象各个属性赋值
        typeSpecBuilder.addMethod(acceptMethodBuilder.build());

        createJavaFile(typeSpecBuilder, packageName);
    }

    private Set<TypeAndName> findPublicSetter(Element element) {
        Map<String, FieldConfig> configMap = findFields(element).stream()
                .map(variableElement -> toFieldConfig(variableElement))
                .collect(toMap(config->config.getName(), config->config));

        Set<ExecutableElement> getterMethods = findSetter(element);

        Set<TypeAndName> result = Sets.newHashSet();
        // 对Setter方法进行处理
        Set<TypeAndName> getterMethodResult = getterMethods.stream()
                .filter(element1 -> !element1.getModifiers().contains(Modifier.PRIVATE))
                .map(element1 -> {
                    String fieldName = getFieldNameFromSetter(element1.getSimpleName().toString());
                    BaseProcessor.FieldConfig fieldConfig = configMap.get(fieldName);
                    return getTypeAndNameByFieldConfig(fieldConfig,element1);
                }).filter(typeAndName -> typeAndName != null)
                .collect(Collectors.toSet());
        result.addAll(getterMethodResult);

        // 对lombok的Set方法进行处理
        if (element.getAnnotation(Data.class) != null){
            Set<TypeAndName> lombokSetter = findFields(element).stream()
                    .filter(element1 -> {
                        String filedName = element1.getSimpleName().toString();
                        return getterMethodResult.stream().noneMatch(typeAndName -> typeAndName.getName().equals(filedName));
                    }).filter(element1 -> {
                        Setter setter = element1.getAnnotation(Setter.class);
                        return setter == null || setter.value() != AccessLevel.PRIVATE;
                    }).map(element1 -> {
                        String fieldName = getFieldNameFromSetter(element1.getSimpleName().toString());
                        BaseProcessor.FieldConfig fieldConfig = configMap.get(fieldName);
                        return getTypeAndNameByFieldConfig(fieldConfig,element1);
                    }).filter(typeAndName -> typeAndName != null)
                    .collect(Collectors.toSet());
            result.addAll(lombokSetter);
        }
        return result;
    }

    private TypeAndName getTypeAndNameByFieldConfig(BaseProcessor.FieldConfig fieldConfig,Element element){
        boolean fieldIgnore = fieldConfig != null ? fieldConfig.isIgnore() : false;
        if (fieldIgnore){
            return null;
        }else {
            String fieldDescription = fieldConfig != null ? fieldConfig.getDescription() : "";
            if (element instanceof VariableElement) {
                return new TypeAndName((VariableElement) element, fieldDescription);
            }else if(element instanceof ExecutableElement){
                return new TypeAndName((ExecutableElement) element, fieldDescription);
            }else {
                return null;
            }
        }
    }

    private FieldConfig toFieldConfig(VariableElement element){
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
            }else if(!typeElement.getSuperclass().toString().contains("BaseEntity")){
                return getPackageName(typeElement.getSuperclass().toString()) + ".BaseSuper" + getSuperClassName(typeElement.getSuperclass().toString()) +"Updater";
            }
            else {
                return convertToUpdater(superClass);
            }
        }
        return null;
    }

    private String convertToUpdater(String superClass) {
//        String pkgName = superClass.substring(0, superClass.lastIndexOf('.'));
//        String clsName = superClass.substring(superClass.lastIndexOf('.') + 1, superClass.length());
//        return pkgName + ".updater.Base" + clsName + "Updater";
        return Constants.UPDATER_PARENT_PATH;
    }
}
