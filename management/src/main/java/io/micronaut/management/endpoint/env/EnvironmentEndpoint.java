/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.management.endpoint.env;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import jakarta.inject.Inject;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Endpoint} that displays information about the environment and its property sources.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.2.0
 */
@Endpoint(
        id = EnvironmentEndpoint.NAME,
        defaultEnabled = EnvironmentEndpoint.DEFAULT_ENABLED
)
public class EnvironmentEndpoint {

    /**
     * Endpoint name.
     */
    public static final String NAME = "env";

    /**
     * Endpoint default enabled.
     */
    public static final boolean DEFAULT_ENABLED = false;

    private final Environment environment;
    private final EnvironmentEndpointFilter environmentFilter;

    /**
     * @param environment The {@link Environment}
     */
    public EnvironmentEndpoint(Environment environment) {
        this(environment, null);
    }

    /**
     * @param environment The {@link Environment}
     * @param environmentFilter The registered {@link EnvironmentEndpointFilter} bean if one is registered
     */
    @Inject
    public EnvironmentEndpoint(Environment environment, @Nullable EnvironmentEndpointFilter environmentFilter) {
        this.environment = environment;
        this.environmentFilter = environmentFilter;
    }

    /**
     * @param principal The current {@link Principal} if one exists
     * @return The environment information as a map with the following keys: activeEnvironments, packages and
     * propertySources.
     */
    @Read
    public Map<String, Object> getEnvironmentInfo(@Nullable Principal principal) {
        EnvironmentFilterSpecification filter = createFilterSpecification(principal);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeEnvironments", environment.getActiveNames());
        result.put("packages", environment.getPackages());
        Collection<Map<String, Object>> propertySources = new ArrayList<>();
        environment.getPropertySources()
                .stream()
                .sorted(Comparator.comparing(PropertySource::getOrder))
                .map(ps -> buildPropertySourceInfo(ps, filter))
                .forEach(propertySources::add);
        result.put("propertySources", propertySources);
        return result;
    }

    /**
     * @param propertySourceName The {@link PropertySource} name
     * @param principal The current {@link Principal} if one exists
     * @return a map with all the properties defined in the property source if it exists; null otherwise.
     */
    @Read
    public Map<String, Object> getProperties(@Selector String propertySourceName, @Nullable Principal principal) {
        EnvironmentFilterSpecification filter = createFilterSpecification(principal);

        return environment.getPropertySources()
                .stream()
                .filter(ps -> ps.getName().equals(propertySourceName))
                .findFirst()
                .map(ps -> buildPropertySourceInfo(ps, filter))
                .orElse(null);
    }

    private EnvironmentFilterSpecification createFilterSpecification(@Nullable Principal principal) {
        EnvironmentFilterSpecification filter = new EnvironmentFilterSpecification(principal);
        if (environmentFilter != null) {
            environmentFilter.specifyFiltering(filter);
        }
        return filter;
    }

    private Map<String, Object> getAllProperties(PropertySource propertySource, EnvironmentFilterSpecification filter) {
        Map<String, Object> properties = new LinkedHashMap<>();
        propertySource.forEach(k -> {
            EnvironmentFilterSpecification.FilterResult test = filter.test(k);
            if (test != EnvironmentFilterSpecification.FilterResult.HIDE) {
                properties.put(k, test == EnvironmentFilterSpecification.FilterResult.MASK ? "*****" : propertySource.get(k));
            }
        });
        return properties;
    }

    private Map<String, Object> buildPropertySourceInfo(PropertySource propertySource, EnvironmentFilterSpecification filter) {
        Map<String, Object> propertySourceInfo = new LinkedHashMap<>();
        propertySourceInfo.put("name", propertySource.getName());
        propertySourceInfo.put("order", propertySource.getOrder());
        propertySourceInfo.put("convention", propertySource.getConvention().name());
        propertySourceInfo.put("properties", getAllProperties(propertySource, filter));
        return propertySourceInfo;
    }
}
