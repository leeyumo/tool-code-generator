package com.leeyumo.tool.code.generator;

import com.google.common.collect.Sets;
import com.leeyumo.tool.code.generator.util.Constants;
import com.squareup.javapoet.*;
import io.swagger.annotations.ApiModelProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public abstract class BasePersistenceProcessor<A extends Annotation> extends BaseProcessor<A> {

    public BasePersistenceProcessor(Class<A> aClass) {
        super(aClass);
    }

    private Set<TypeAndName> findPublicSetter(Element element) {
        Map<String, FieldConfig> configMap = findFields(element).stream()
                .map(this::toFieldConfig)
                .collect(toMap(FieldConfig::getName, config->config));

        Set<ExecutableElement> getterMethods = findSetter(element);

        Set<TypeAndName> result = Sets.newHashSet();
        // 对Setter方法进行处理
        Set<TypeAndName> getterMethodResult = getterMethods.stream()
                .filter(element1 -> !element1.getModifiers().contains(Modifier.PRIVATE))
                .map(element1 -> {
                    String fieldName = getFieldNameFromSetter(element1.getSimpleName().toString());
                    BaseProcessor.FieldConfig fieldConfig = configMap.get(fieldName);
                    return getTypeAndNameByFieldConfig(fieldConfig,element1);
                }).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        result.addAll(getterMethodResult);

        // 对lombok的Set方法进行处理
        if (element.getAnnotation(Data.class) != null || element.getAnnotation(Setter.class) != null){
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
                    }).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            result.addAll(lombokSetter);
        }
        return result;
    }

    private TypeAndName getTypeAndNameByFieldConfig(BaseProcessor.FieldConfig fieldConfig, Element element){
        boolean fieldIgnore = fieldConfig != null && fieldConfig.isIgnore();
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

    protected void operateCoding(Element element, String packageName, String className, String parentClassName) {
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

        boolean instantChangeFlag = false;
        for (TypeAndName typeAndName : publicSetter) {
            String acceptMethodName = "accept" + typeAndName.getName().substring(0, 1).toUpperCase() + typeAndName.getName().substring(1, typeAndName.getName().length());
            String targetSetterName = "target::set" + typeAndName.getName().substring(0, 1).toUpperCase() + typeAndName.getName().substring(1, typeAndName.getName().length());
            TypeName typeNameToGen = typeAndName.getType();

            //处理Java8时间戳类型的instant情况
            if (Objects.equals(TypeName.get(Instant.class),typeAndName.getType())){
                instantChangeFlag = true;
                typeNameToGen = TypeName.get(Long.class);
                targetSetterName = "l -> target.set"
                        + typeAndName.getName().substring(0, 1).toUpperCase() + typeAndName.getName().substring(1, typeAndName.getName().length())
                        + "(this."+Constants.CONVERT_LONG_TO_INSTANT_METHOD+"(l))";
            }

            acceptMethodBuilder.addStatement("this.$L($L)", acceptMethodName, targetSetterName);
            //生成属性声明
            FieldSpec fieldSpec = FieldSpec.builder(typeNameToGen, typeAndName.getName(), Modifier.PRIVATE)
                    .addAnnotation(AnnotationSpec.builder(ApiModelProperty.class)
                            .addMember("value", "$S",typeAndName.getDescription())
                            .addMember("name", "$S", typeAndName.getName())
                            .build())
                    .build();
            typeSpecBuilder.addField(fieldSpec);

            //生成builder属性方法，目的是可以连环调用给creator各个属性赋值
//            MethodSpec methodSpec = MethodSpec.methodBuilder(typeAndName.getName())
//                    .addModifiers(Modifier.PUBLIC)
//                    .returns(TypeVariableName.get("T"))
//                    .addParameter(typeNameToGen, typeAndName.getName())
////                    .addStatement("this.$L = DataOptional.of($L)", typeAndName.getName(), typeAndName.getName())
//                    .addStatement("this.$L = $L", typeAndName.getName(), typeAndName.getName())
//                    .addStatement("return (T) this")
//                    .build();
//            typeSpecBuilder.addMethod(methodSpec);

            //生成通过lambda表达式为目标单个属性赋值的方法，目的是在调用此方法时同时调用lambda中的::对应的set方法
            ParameterizedTypeName consumerTypeName = ParameterizedTypeName.get(ClassName.get(Consumer.class), typeNameToGen);
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
        if (instantChangeFlag){
            MethodSpec longConvertInstant = MethodSpec.methodBuilder(Constants.CONVERT_LONG_TO_INSTANT_METHOD)
                    .addModifiers(Modifier.PRIVATE)
                    .returns(TypeName.get(Instant.class))
                    .addParameter(TypeName.get(Long.class),"timeStamp")
                    .addStatement("return Instant.ofEpochMilli(timeStamp)")
                    .build();
            typeSpecBuilder.addMethod(longConvertInstant);
        }

        //生成最后的核心accept方法，为目标对象各个属性赋值
        typeSpecBuilder.addMethod(acceptMethodBuilder.build());

        createJavaFile(typeSpecBuilder, packageName);
    }

    protected abstract FieldConfig toFieldConfig(VariableElement element);

}
