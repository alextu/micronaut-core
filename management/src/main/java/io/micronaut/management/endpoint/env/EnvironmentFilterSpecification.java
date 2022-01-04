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

import io.micronaut.core.annotation.Nullable;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class is passed to an instance of {@link EnvironmentEndpointFilter} (if one is defined) each time the {@link EnvironmentEndpoint}
 * is invoked.
 *
 * @author Tim Yates
 * @since 3.3.0
 */
public class EnvironmentFilterSpecification {

    private static final String[] PROPERTY_NAMES_TO_MASK = new String[]{
            "password", "credential", "certificate", "key", "secret", "token"
    };

    @Nullable
    private final Principal principal;
    private boolean allMasked;
    private final List<Pattern> maskedPatterns = new ArrayList<>();
    private final List<Pattern> allowedPatterns = new ArrayList<>();

    EnvironmentFilterSpecification(@Nullable Principal principal) {
        this.principal = principal;
        this.allMasked = true;
    }

    /**
     * @return The current {@link Principal} that is making the request (if any)
     */
    @Nullable
    public Principal getPrincipal() {
        return principal;
    }

    /**
     * Turn on global masking. Items can be unmasked by adding their {@link Pattern} to {@link EnvironmentFilterSpecification#maskPatterns(Pattern...)}.
     *
     * @return itself for chaining calls.
     */
    public EnvironmentFilterSpecification maskAll() {
        allMasked = true;
        return this;
    }

    /**
     * Turn off global masking. Items can be masked by adding their {@link Pattern} to {@link EnvironmentFilterSpecification#maskPatterns(Pattern...)} (Pattern...)}.
     *
     * @return itself for chaining calls.
     */
    public EnvironmentFilterSpecification maskNone() {
        allMasked = false;
        return this;
    }

    /**
     * Adds patterns to the list of known mask patterns.  If the {@link EnvironmentFilterSpecification#maskAll()} flag is set
     * then this list will be used for exceptions (values in clear-text).  If the {@link EnvironmentFilterSpecification#maskNone()}
     * flag is set then this list will be those values that are masked.
     *
     * @param propertySourceNameMask one or more Patterns to match against property names for masking.
     * @return itself for chaining calls.
     */
    public EnvironmentFilterSpecification maskPatterns(Pattern... propertySourceNameMask) {
        maskedPatterns.addAll(Arrays.asList(propertySourceNameMask));
        return this;
    }

    /**
     * Adds the masking rules from legacy pre 3.3.0.  Effectively the same as calling {@link EnvironmentFilterSpecification#maskNone()}
     * and adding patterns via {@link EnvironmentFilterSpecification#maskPatterns(Pattern...)} to mask anything containing the words
     * {@code password, credential, certificate, key, secret} or {@code token}.
     *
     * @return itself for chaining calls.
     */
    public EnvironmentFilterSpecification legacyMasking() {
        allMasked = false;
        maskedPatterns.addAll(
                Arrays.stream(PROPERTY_NAMES_TO_MASK)
                        .map(s -> Pattern.compile(".*" + s + ".*", Pattern.CASE_INSENSITIVE))
                        .collect(Collectors.toList())
        );
        return this;
    }

    FilterResult test(String propertySourceName) {
        if (maskedPatterns.stream().anyMatch(p -> p.matcher(propertySourceName).matches())) {
            return allMasked ? FilterResult.PLAIN : FilterResult.MASK;
        }
        return allMasked ? FilterResult.MASK : FilterResult.PLAIN;
    }

    enum FilterResult {
        HIDE,
        MASK,
        PLAIN
    }
}
