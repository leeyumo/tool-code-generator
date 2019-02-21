package com.leeyumo.tool.code.generator;

import com.google.common.collect.Sets;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import lombok.Value;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class BaseProcessor<A extends Annotation> extends AbstractProcessor {
    protected final Class aClass;
    protected Filer filer;

    public BaseProcessor(Class<A> aClass) {
        this.aClass = aClass;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Sets.newHashSet(aClass.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<Element> elements = roundEnv.getElementsAnnotatedWith(this.aClass);
        for (Element element : ElementFilter.typesIn(elements)) {
            A a = (A) element.getAnnotation(this.aClass);
            foreachClass(a, element, roundEnv);

        }
        return false;
    }

    protected abstract void foreachClass(A a, Element element, RoundEnvironment roundEnv);

    protected Set<TypeAndName> findFields(Element element, Predicate<Element> filter) {
        return ElementFilter.fieldsIn(element.getEnclosedElements()).stream()
                .filter(filter)
                .map(variableElement -> new TypeAndName(variableElement,""))
                .collect(Collectors.toSet());
    }

    protected Set<VariableElement> findFields(Element element) {
        return ElementFilter.fieldsIn(element.getEnclosedElements()).stream()
                .collect(Collectors.toSet());
    }

    protected Set<TypeAndName> findGetter(Element element, Predicate<Element> filter) {
        return ElementFilter.methodsIn(element.getEnclosedElements()).stream()
                .filter(filter)
                .filter(executableElement -> isGetter(executableElement.getSimpleName().toString()))
                .map(executableElement -> new TypeAndName(executableElement,""))
                .collect(Collectors.toSet());

    }

    protected Set<ExecutableElement> findGetter(Element element) {
        return ElementFilter.methodsIn(element.getEnclosedElements()).stream()
                .filter(executableElement -> isGetter(executableElement))
                .collect(Collectors.toSet());

    }

    protected Set<ExecutableElement> findSetter(Element element){
        return ElementFilter.methodsIn(element.getEnclosedElements()).stream()
                .filter(executableElement -> isSetter(executableElement))
                .collect(Collectors.toSet());
    }

    private boolean isGetter(ExecutableElement element) {
        String s = element.getSimpleName().toString();
        TypeMirror typeMirror = element.getReturnType();
        boolean is = s.startsWith("is") && typeMirror.getKind() == TypeKind.BOOLEAN;
        boolean getter = s.startsWith("get") && typeMirror.getKind() != TypeKind.VOID && !element.getModifiers().contains(Modifier.STATIC);
        return is || getter;
    }

    private boolean isSetter(ExecutableElement element){
        String name = element.getSimpleName().toString();
        List<? extends VariableElement> parameters = element.getParameters();
        return name.startsWith("set") && parameters.size() == 1;
    }

    private boolean isGetter(String s) {
        return s.startsWith("is") || s.startsWith("get");
    }

    protected <I extends Annotation> Predicate<Element> filterForIgorne(Class<I> iClass) {
        return new IgnoreFilter<I>(iClass);
    }

    protected static class IgnoreFilter<I extends Annotation> implements Predicate<Element> {
        private final Class<I> iClass;

        public IgnoreFilter(Class<I> iClass) {
            this.iClass = iClass;
        }

        @Override
        public boolean test(Element variableElement) {
            return variableElement.getAnnotation(iClass) == null;
        }
    }


    protected void createJavaFile(TypeSpec.Builder typeSpecBuilder, String pkgName) {
        try {
            JavaFile javaFile = JavaFile.builder(pkgName, typeSpecBuilder.build())
                    .addFileComment(" This codes are generated automatically. Don't modify!")
                    .build();
            javaFile.writeTo(filer);
            System.out.println(javaFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Value
    protected static class TypeAndName {
        private final String name;
        private final TypeName type;
        private final String description;

        public TypeAndName(VariableElement variableElement, String description) {
            this.name = variableElement.getSimpleName().toString();
            this.type = TypeName.get(variableElement.asType());
            this.description = description;

        }

        public TypeAndName(ExecutableElement executableElement,String description) {
            this.name = getFieldName(executableElement.getSimpleName().toString());
            this.type = TypeName.get(executableElement.getReturnType());
            this.description = description;
        }

        public TypeAndName(String name, TypeName type) {
            this.name = name;
            this.type = type;
            this.description = "";
        }

        private String getFieldName(String s) {
            return BaseProcessor.getFieldNameFromGetter(s);
        }
        private String getDescription(Description description){
            return description != null ? description.value() : "";
        }
    }

    public String firstLetter2Low(String name){
        if(null != name && name.length() > 0){
            return name.substring(0,1).toUpperCase()+name.substring(1);
        }
        return "";
    }


    protected static String getFieldNameFromGetter(String s) {
        String r = null;
        if (s.startsWith("get")) {
            r = s.substring(3, s.length());
        } else if (s.startsWith("is")) {
            r = s.substring(2, s.length());
        } else {
            r = s;
        }
        return r.substring(0, 1).toLowerCase() + r.substring(1, r.length());
    }

    protected static String getFieldNameFromSetter(String s) {
        String r = null;
        if (s.startsWith("set")) {
            r = s.substring(3, s.length());
        } else {
            r = s;
        }
        return r.substring(0, 1).toLowerCase() + r.substring(1, r.length());
    }

    @Value
    protected static class FieldConfig{
        private final String name;
        private final boolean ignore;
        private final String description;
    }

    protected String getSuperClassName(String superClass){
        String clsName = superClass.substring(superClass.lastIndexOf('.') + 1, superClass.length());
        return clsName;
    }

    protected String getPackageName(String superClass){
        return  superClass.substring(0, superClass.lastIndexOf('.'));
    }


}
