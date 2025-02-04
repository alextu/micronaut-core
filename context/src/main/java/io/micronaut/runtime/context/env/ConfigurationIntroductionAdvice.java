/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.runtime.context.env;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.value.PropertyNotFoundException;

import java.util.Optional;

/**
 * Internal introduction advice used to allow {@link io.micronaut.context.annotation.ConfigurationProperties} on interfaces. Considered internal and not for direct use.
 *
 * @author graemerocher
 * @see ConfigurationAdvice
 * @see io.micronaut.context.annotation.ConfigurationProperties
 * @since 1.3.0
 */
@Prototype
@Internal
@BootstrapContextCompatible
public class ConfigurationIntroductionAdvice implements MethodInterceptor<Object, Object> {

    private static final String MEMBER_BEAN = "bean";
    private static final String MEMBER_NAME = "name";
    private final Environment environment;
    private final BeanContext beanContext;
    private final String name;

    /**
     * Default constructor.
     *
     * @param qualifier   The qualifier
     * @param environment The environment
     * @param beanContext The bean locator
     */
    ConfigurationIntroductionAdvice(Qualifier<?> qualifier, Environment environment, BeanContext beanContext) {
        this.environment = environment;
        this.beanContext = beanContext;
        this.name = qualifier instanceof Named ? ((Named) qualifier).getName() : null;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final ReturnType<Object> rt = context.getReturnType();
        final Class<Object> returnType = rt.getType();
        if (context.isTrue(ConfigurationAdvice.class, MEMBER_BEAN)) {
            if (context.isNullable()) {
                final Object v = beanContext.findBean(returnType).orElse(null);
                if (v != null) {
                    return environment.convertRequired(v, returnType);
                } else {
                    return v;
                }
            } else {
                return environment.convertRequired(
                        beanContext.getBean(returnType),
                        returnType
                );
            }
        } else {
            String property = context.stringValue(Property.class, MEMBER_NAME).orElse(null);
            if (property == null) {
                throw new IllegalStateException("No property name available to resolve");
            }
            boolean iterable = property.indexOf('*') > -1;
            if (iterable) {
                if (name != null) {
                    property = property.replace("*", name);
                }
            }
            final String defaultValue = context.stringValue(Bindable.class, "defaultValue").orElse(null);
            final Argument<Object> argument = rt.asArgument();

            final Optional<Object> value = environment.getProperty(
                    property,
                    argument
            );

            if (defaultValue != null) {
                return value.orElseGet(() -> environment.convertRequired(
                        defaultValue,
                        argument
                ));
            } else if (rt.isOptional()) {
                return value.orElse(Optional.empty());
            } else if (context.isNullable()) {
                return value.orElse(null);
            } else {
                String finalProperty = property;
                return value.orElseThrow(() -> new PropertyNotFoundException(finalProperty, argument.getType()));
            }
        }
    }
}
