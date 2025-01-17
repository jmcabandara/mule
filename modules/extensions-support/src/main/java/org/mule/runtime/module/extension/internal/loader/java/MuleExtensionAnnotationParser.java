/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.loader.java;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mule.runtime.api.util.Preconditions.checkState;

import org.mule.runtime.api.meta.model.declaration.fluent.BaseDeclaration;
import org.mule.runtime.api.meta.model.display.LayoutModel;
import org.mule.runtime.api.meta.model.display.LayoutModel.LayoutModelBuilder;
import org.mule.runtime.core.api.util.ClassUtils;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.OnException;
import org.mule.runtime.extension.api.annotation.param.display.Password;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Text;
import org.mule.runtime.extension.api.runtime.exception.ExceptionHandler;
import org.mule.runtime.extension.api.runtime.exception.ExceptionHandlerFactory;
import org.mule.runtime.module.extension.api.loader.java.type.AnnotationValueFetcher;
import org.mule.runtime.module.extension.api.loader.java.type.Type;
import org.mule.runtime.module.extension.api.loader.java.type.WithAnnotations;
import org.mule.runtime.module.extension.internal.loader.java.property.DeclaringMemberModelProperty;
import org.mule.runtime.module.extension.internal.runtime.exception.DefaultExceptionHandlerFactory;
import org.mule.runtime.module.extension.internal.util.IntrospectionUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utilities for reading annotations as a mean to describe extensions
 *
 * @since 3.7.0
 */
public final class MuleExtensionAnnotationParser {

  private static final Logger logger = LoggerFactory.getLogger(MuleExtensionAnnotationParser.class);

  public static String getMemberName(BaseDeclaration<?> declaration, String defaultName) {
    return declaration.getModelProperty(DeclaringMemberModelProperty.class).map(p -> p.getDeclaringField().getName())
        .orElse(defaultName);
  }

  public static Extension getExtension(Class<?> extensionType) {
    try {
      Extension extension = extensionType.getAnnotation(Extension.class);
      checkState(extension != null, format("%s is not a Mule extension since it's not annotated with %s", extensionType.getName(),
                                           Extension.class.getName()));
      return extension;
    } catch (Exception e) {
      logger.error(format("%s getting '@Extension' annotation from %s", e.getClass().getName(), extensionType.getName()), e);
      throw e;
    }
  }

  public static <T extends Annotation> List<T> parseRepeatableAnnotation(Class<?> extensionType, Class<T> annotation,
                                                                         Function<Annotation, T[]> containerConsumer) {
    List<T> annotationDeclarations = ImmutableList.of();

    Repeatable repeatableContainer = annotation.getAnnotation(Repeatable.class);
    if (repeatableContainer != null) {
      Annotation container = IntrospectionUtils.getAnnotation(extensionType, repeatableContainer.value());
      if (container != null) {
        annotationDeclarations = ImmutableList.copyOf(containerConsumer.apply(container));
      }
    }

    T singleDeclaration = IntrospectionUtils.getAnnotation(extensionType, annotation);
    if (singleDeclaration != null) {
      annotationDeclarations = ImmutableList.of(singleDeclaration);
    }

    return annotationDeclarations;
  }

  public static <T extends Annotation> List<AnnotationValueFetcher<T>> parseRepeatableAnnotation(Type extensionType,
                                                                                                 Class<T> annotation,
                                                                                                 Function<Annotation, T[]> containerConsumer) {
    List<AnnotationValueFetcher<T>> annotationDeclarations = ImmutableList.of();

    Repeatable repeatableContainer = annotation.getAnnotation(Repeatable.class);
    if (repeatableContainer != null) {
      Optional<? extends AnnotationValueFetcher<? extends Annotation>> container =
          extensionType.getValueFromAnnotation(repeatableContainer.value());
      if (container.isPresent()) {
        annotationDeclarations = container.get().getInnerAnnotations((Function) containerConsumer);
      }
    }

    Optional<AnnotationValueFetcher<T>> singleDeclaration = extensionType.getValueFromAnnotation(annotation);
    if (singleDeclaration.isPresent()) {
      annotationDeclarations = Collections.singletonList(singleDeclaration.get());
    }

    return annotationDeclarations;
  }

  public static List<String> getParamNames(Method method) {
    ImmutableList.Builder<String> paramNames = ImmutableList.builder();
    for (java.lang.reflect.Parameter parameter : method.getParameters()) {
      Alias alias = parameter.getAnnotation(Alias.class);
      if (alias != null) {
        paramNames.add(alias.value());
      } else {
        paramNames.add(parameter.getName());
      }
    }

    return paramNames.build();
  }

  public static Map<Class<? extends Annotation>, Annotation> toMap(Annotation[] annotations) {

    Map<Class<? extends Annotation>, Annotation> map = new HashMap<>();

    for (Annotation annotation : annotations) {
      map.put(ClassUtils.resolveAnnotationClass(annotation), annotation);
    }

    return map;
  }

  private static void doParseLayoutAnnotations(AnnotatedElement annotatedElement, LayoutModelBuilder builder) {
    if (annotatedElement.getAnnotation(Password.class) != null
        || annotatedElement.getAnnotation(org.mule.sdk.api.annotation.semantics.security.Password.class) != null) {
      builder.asPassword();

    }
    Text textAnnotation = annotatedElement.getAnnotation(Text.class);
    if (textAnnotation != null) {
      builder.asText();
    }
  }

  private static void doParseLayoutAnnotations(WithAnnotations annotatedElement, LayoutModelBuilder builder) {
    java.util.Optional<Password> passwordAnnotation = annotatedElement.getAnnotation(Password.class);
    if (passwordAnnotation.isPresent()) {
      builder.asPassword();
    }
    java.util.Optional<Text> textAnnotation = annotatedElement.getAnnotation(Text.class);
    if (textAnnotation.isPresent()) {
      builder.asText();
    }
  }

  private static void parsePlacementAnnotation(WithAnnotations annotatedElement, LayoutModelBuilder builder) {
    java.util.Optional<Placement> placementAnnotation = annotatedElement.getAnnotation(Placement.class);
    if (placementAnnotation.isPresent()) {
      Placement placement = placementAnnotation.get();
      builder.order(placement.order()).tabName(placement.tab());
    }
  }

  private static void parsePlacementAnnotation(AnnotatedElement annotatedElement, LayoutModelBuilder builder) {
    Placement placement = annotatedElement.getAnnotation(Placement.class);
    if (placement != null) {
      builder.order(placement.order()).tabName(placement.tab());
    }
  }

  public static Optional<LayoutModel> parseLayoutAnnotations(AnnotatedElement annotatedElement) {
    return parseLayoutAnnotations(annotatedElement, LayoutModel.builder());
  }

  public static Optional<LayoutModel> parseLayoutAnnotations(WithAnnotations annotatedElement) {
    return parseLayoutAnnotations(annotatedElement, LayoutModel.builder());
  }

  public static Optional<LayoutModel> parseLayoutAnnotations(WithAnnotations annotatedElement, LayoutModelBuilder builder) {
    if (isDisplayAnnotationPresent(annotatedElement)) {
      doParseLayoutAnnotations(annotatedElement, builder);
      parsePlacementAnnotation(annotatedElement, builder);
      return of(builder.build());
    }
    return empty();
  }

  public static Optional<LayoutModel> parseLayoutAnnotations(AnnotatedElement annotatedElement, LayoutModelBuilder builder) {
    if (isDisplayAnnotationPresent(annotatedElement)) {
      doParseLayoutAnnotations(annotatedElement, builder);
      parsePlacementAnnotation(annotatedElement, builder);
      return of(builder.build());
    }
    return empty();
  }

  private static boolean isDisplayAnnotationPresent(AnnotatedElement annotatedElement) {
    List<Class> displayAnnotations = Arrays.asList(Password.class, Text.class, Placement.class);
    return displayAnnotations.stream().anyMatch(annotation -> annotatedElement.getAnnotation(annotation) != null);
  }

  private static boolean isDisplayAnnotationPresent(WithAnnotations annotatedElement) {
    List<Class> displayAnnotations = Arrays.asList(Password.class, Text.class, Placement.class);
    return displayAnnotations.stream().anyMatch(annotation -> annotatedElement.getAnnotation(annotation) != null);
  }

  public static java.util.Optional<ExceptionHandlerFactory> getExceptionEnricherFactory(WithAnnotations element) {
    if (element.isAnnotatedWith(OnException.class)) {
      Type classValue = element.getValueFromAnnotation(OnException.class).get().getClassValue(OnException::value);
      return classValue.getDeclaringClass()
          .map(clazz -> new DefaultExceptionHandlerFactory((Class<? extends ExceptionHandler>) clazz));
    }
    return java.util.Optional.empty();
  }
}
