/*-
 * ========================LICENSE_START=================================
 * smooks-javabean-cartridge
 * %%
 * Copyright (C) 2020 Smooks
 * %%
 * Licensed under the terms of the Apache License Version 2.0, or
 * the GNU Lesser General Public License version 3.0 or later.
 *
 * SPDX-License-Identifier: Apache-2.0 OR LGPL-3.0-or-later
 *
 * ======================================================================
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ======================================================================
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * =========================LICENSE_END==================================
 */
package org.smooks.cartridges.javabean.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smooks.api.ApplicationContext;
import org.smooks.api.converter.TypeConverterException;
import org.smooks.engine.lookup.GlobalParamsLookup;
import org.smooks.support.ClassUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * A factory definition string is an expression that instructs how to create a certain object.
 * A {@link FactoryDefinitionParser} can parse the factory definition and create a {@link Factory} object which
 * can create the object according to the definition.
 * <p>
 * A {@link FactoryDefinitionParser} must have a public argumentless constructor. The {@link FactoryDefinitionParser}
 * must be thread safe. The parse method can be called concurrently. If the {@link FactoryDefinitionParser} is created
 * with the {@link FactoryDefinitionParserFactory} then it will be created only once.
 *
 * @author <a href="mailto:maurice.zeijen@smies.com">maurice.zeijen@smies.com</a>
 */
@SuppressWarnings("deprecation")
public interface FactoryDefinitionParser {

    /**
     * Parses the factory definition string and creates a factory object
     * that can create the object according to the definition.
     *
     * @param factoryDefinition The factory definition
     * @return The Factory object that creates the target object according to the definition.
     * @throws InvalidFactoryDefinitionException If the factoryDefinition is invalid
     * @throws FactoryException                  If something went wrong while creating the factory
     */
    Factory<?> parse(String factoryDefinition);


    @SuppressWarnings("unchecked")
    class FactoryDefinitionParserFactory {

        private static final Logger LOGGER = LoggerFactory.getLogger(FactoryDefinitionParserFactory.class);

        public static String GLOBAL_DEFAULT_FACTORY_DEFINITION_PARSER_CLASS = "factory.definition.parser.class";
        public static String DEFAULT_FACTORY_DEFINITION_PARSER_CLASS = "org.smooks.cartridges.javabean.factory.BasicFactoryDefinitionParser";

        public static final String DEFAULT_ALIAS = "default";

        private static volatile ConcurrentMap<String, FactoryDefinitionParser> instances = new ConcurrentHashMap<String, FactoryDefinitionParser>();

        private static volatile Map<String, Class<? extends FactoryDefinitionParser>> aliasToClassMap;

        public static FactoryDefinitionParser getInstance(String alias, ApplicationContext applicationContext) {

            String className;
            if (alias == null || alias.isEmpty() || alias.equals(DEFAULT_ALIAS)) {
                className = applicationContext.getRegistry().lookup(new GlobalParamsLookup()).getParameterValue(GLOBAL_DEFAULT_FACTORY_DEFINITION_PARSER_CLASS, DEFAULT_FACTORY_DEFINITION_PARSER_CLASS);
            } else {
                loadAliasToClassMap();

                Class<? extends FactoryDefinitionParser> clazz = aliasToClassMap.get(alias);
                if (clazz == null) {

                    //We couldn't find any class that uses that alias so maybe the alias is a class name.
                    try {
                        clazz = (Class<? extends FactoryDefinitionParser>) ClassUtils.forName(alias, FactoryDefinitionParser.class);

                        className = clazz.getName();
                    } catch (ClassNotFoundException e) {
                        throw new IllegalFactoryAliasException("The FactoryDefinitionParser alias '" + alias + "' can't be found and doesn't seem to be a classname.", e);
                    }
                }
                className = clazz.getName();
            }

            FactoryDefinitionParser factoryDefinitionParser = instances.get(className);
            if (factoryDefinitionParser == null) {

                try {
                    @SuppressWarnings("unchecked")
                    Class<FactoryDefinitionParser> factoryDefinitionParserClass = (Class<FactoryDefinitionParser>) ClassUtils.forName(className, FactoryDefinitionParser.class);

                    FactoryDefinitionParser newFactoryDefinitionParser = factoryDefinitionParserClass.newInstance();

                    instances.putIfAbsent(className, newFactoryDefinitionParser);

                    // We do an extra get to make sure that there is always only one factoryDefinitionParser instance
                    factoryDefinitionParser = instances.get(className);

                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("The FactoryDefinitionParser class '" + className + "' can't be found", e);
                } catch (InstantiationException e) {
                    throw new IllegalArgumentException("The FactoryDefinitionParser class '" + className + "'can't be instantiated. The FactoryDefinitionParser class must have a argumentless public constructor.", e);
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException("The FactoryDefinitionParser class '" + className + "' can't be instantiated.", e);
                }

            }

            return factoryDefinitionParser;
        }

        public static FactoryDefinitionParser getInstance(ApplicationContext applicationContext) {
            return getInstance("default", applicationContext);
        }

        public static Map<String, Class<? extends FactoryDefinitionParser>> getAliasToClassMap() {
            loadAliasToClassMap();

            return Collections.unmodifiableMap(aliasToClassMap);
        }

        private synchronized static void loadAliasToClassMap() throws TypeConverterException {
            if (aliasToClassMap == null) {
                synchronized (FactoryDefinitionParserFactory.class) {
                    if (aliasToClassMap == null) {
                        List<Class<FactoryDefinitionParser>> factories = ClassUtils.getClasses("META-INF/smooks-javabean-factory-definition-parsers.inf", FactoryDefinitionParser.class);

                        Set<String> toRemove = new HashSet<String>();

                        aliasToClassMap = new HashMap<String, Class<? extends FactoryDefinitionParser>>();
                        for (Class<? extends FactoryDefinitionParser> factory : factories) {
                            Alias alias = factory.getAnnotation(Alias.class);
                            if (alias != null) {
                                String[] names = alias.value();

                                for (String name : names) {
                                    if (name.equals(DEFAULT_ALIAS)) {
                                        throw new IllegalFactoryAliasException("The alias 'default' is a reserved alias name. Please use a different name");
                                    }
                                    if (aliasToClassMap.containsKey(name)) {
                                        Class<? extends FactoryDefinitionParser> prevClass = aliasToClassMap.get(name);

                                        LOGGER.warn("More than one FactoryDefinitionParser has the alias '" + name + "' on the classpath. Previous: '" + prevClass.getName() + "'. Current '" + factory.getName() + "'. To use one of these factories you will have to declare the complete class name as alias.");

                                        toRemove.add(name); // We register that we need to remove that one. We keep it for to be able to give clear warning messages.
                                    }

                                    aliasToClassMap.put(name, factory);
                                }
                            }
                        }
                        //We remove all alias that we defined multiple times
                        for (String name : toRemove) {
                            aliasToClassMap.remove(name);
                        }
                    }
                }
            }
        }
    }
}
