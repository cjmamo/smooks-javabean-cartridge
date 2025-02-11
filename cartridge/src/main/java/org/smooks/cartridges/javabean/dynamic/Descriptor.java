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
package org.smooks.cartridges.javabean.dynamic;

import org.smooks.Smooks;
import org.smooks.api.ApplicationContextBuilder;
import org.smooks.api.SmooksConfigException;
import org.smooks.api.SmooksException;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.api.resource.config.ResourceConfigSeq;
import org.smooks.api.resource.config.xpath.SelectorStep;
import org.smooks.assertion.AssertArgument;
import org.smooks.cartridges.javabean.dynamic.ext.BeanWriterFactory;
import org.smooks.cartridges.javabean.dynamic.resolvers.AbstractResolver;
import org.smooks.cartridges.javabean.dynamic.resolvers.DefaultBindingConfigResolver;
import org.smooks.cartridges.javabean.dynamic.resolvers.DefaultSchemaResolver;
import org.smooks.cartridges.javabean.dynamic.serialize.BeanWriter;
import org.smooks.engine.DefaultFilterSettings;
import org.smooks.engine.resource.config.xpath.IndexedSelectorPath;
import org.smooks.engine.resource.config.xpath.step.NamedSelectorStep;
import org.smooks.support.ClassUtils;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Model Descriptor.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class Descriptor {

    public static final String DESCRIPTOR_NAMESPACE_POSTFIX = ".namespace";
    public static final String DESCRIPTOR_SCHEMA_LOCATION_POSTFIX = ".schemaLocation";
    public static final String DESCRIPTOR_BINDING_CONFIG_LOCATION_POSTFIX = ".bindingConfigLocation";
    public static final String DESCRIPTOR_ORDER_POSTFIX = ".order";

    protected Smooks smooks;
    protected Schema schema;
    protected ClassLoader classloader = Descriptor.class.getClassLoader();

    public Descriptor(List<Properties> descriptors) throws SAXException, IOException {
        AssertArgument.isNotNullAndNotEmpty(descriptors, "descriptors");

        initialize(descriptors, new DefaultSchemaResolver(descriptors), new DefaultBindingConfigResolver(descriptors));
    }

    public Descriptor(String descriptorPath) throws SAXException, IOException {
        AssertArgument.isNotNullAndNotEmpty(descriptorPath, "descriptorPath");

        List<Properties> descriptors = loadDescriptors(descriptorPath, getClass().getClassLoader());
        initialize(descriptors, new DefaultSchemaResolver(descriptors), new DefaultBindingConfigResolver(descriptors));
    }

    public Descriptor(String descriptorPath, EntityResolver schemaResolver, EntityResolver bindingResolver, ClassLoader classloader) throws SAXException, IOException {
        AssertArgument.isNotNullAndNotEmpty(descriptorPath, "descriptorPath");
        AssertArgument.isNotNull(bindingResolver, "bindingResolver");
        AssertArgument.isNotNull(classloader, "classloader");

        this.classloader = classloader;

        List<Properties> descriptors = loadDescriptors(descriptorPath, classloader);
        initialize(descriptors, schemaResolver, bindingResolver);
    }

    public Descriptor(List<Properties> descriptors, EntityResolver schemaResolver, EntityResolver bindingResolver, ClassLoader classloader) throws SAXException, IOException {
        AssertArgument.isNotNullAndNotEmpty(descriptors, "descriptors");
        AssertArgument.isNotNull(bindingResolver, "bindingResolver");
        AssertArgument.isNotNull(classloader, "classloader");

        this.classloader = classloader;

        initialize(descriptors, schemaResolver, bindingResolver);
    }

    public Smooks getSmooks() {
        return smooks;
    }

    public Schema getSchema() {
        return schema;
    }

    public Map<Class<?>, Map<String, BeanWriter>> getBeanWriters() {
        return BeanWriterFactory.getBeanWriters(smooks.getApplicationContext());
    }

    public static List<Properties> loadDescriptors(String descriptorPath, ClassLoader classLoader) {
        List<Properties> descriptorFiles = new ArrayList<Properties>();

        try {
            List<URL> resources = ClassUtils.getResources(descriptorPath, classLoader);

            if (resources.isEmpty()) {
                throw new IllegalStateException("Failed to locate any model descriptor file by the name '" + descriptorPath + "' on the classpath.");
            }

            for (URL resource : resources) {
                InputStream resStream = resource.openStream();
                descriptorFiles.add(loadDescriptor(resStream));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IO Exception when reading Dynamic Namespace Descriptor files from classpath.", e);
        }

        return descriptorFiles;
    }

    public static Properties loadDescriptor(InputStream descriptorStream) throws IOException {
        AssertArgument.isNotNull(descriptorStream, "descriptorStream");
        try {
            Properties descriptor = new Properties();
            descriptor.load(descriptorStream);
            return descriptor;
        } finally {
            descriptorStream.close();
        }
    }

    protected void initialize(List<Properties> descriptors, EntityResolver schemaResolver, EntityResolver bindingResolver) throws SAXException, IOException {
        if (schemaResolver instanceof AbstractResolver) {
            if (((AbstractResolver) schemaResolver).getClassLoader() != classloader) {
                throw new SmooksException("Schema EntityResolver '" + schemaResolver.getClass().getName() + "' not using the same ClassLoader as this Descriptor instance.");
            }
        }
        if (bindingResolver instanceof AbstractResolver) {
            if (((AbstractResolver) bindingResolver).getClassLoader() != classloader) {
                throw new SmooksException("Binding EntityResolver '" + bindingResolver.getClass().getName() + "' not using the same ClassLoader as this Descriptor instance.");
            }
        }

        if (schemaResolver != null) {
            this.schema = newSchemaInstance(descriptors, schemaResolver);
        }
        this.smooks = newSmooksInstance(descriptors, bindingResolver);
    }

    protected Schema newSchemaInstance(List<Properties> descriptors, EntityResolver schemaResolver) throws SAXException, IOException {
        List<Source> schemas = getSchemas(descriptors, schemaResolver);

        try {
            // Create the merged Schema instance and from that, create the Validator instance...
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return schemaFactory.newSchema(schemas.toArray(new Source[schemas.size()]));
        } finally {
            for (Source schemaSource : schemas) {
                if (schemaSource instanceof StreamSource) {
                    StreamSource streamSource = (StreamSource) schemaSource;
                    if (streamSource.getInputStream() != null) {
                        streamSource.getInputStream().close();
                    } else if (streamSource.getReader() != null) {
                        streamSource.getReader().close();
                    }
                }
            }
        }
    }

    protected List<Source> getSchemas(List<Properties> descriptors, EntityResolver schemaResolver) throws SAXException, IOException {
        Set<Namespace> namespaces = resolveNamespaces(descriptors);
        List<Source> xsdSources = new ArrayList<>();

        for (Namespace namespace : namespaces) {
            InputSource schemaSource = schemaResolver.resolveEntity(namespace.uri, namespace.uri);

            if (schemaSource != null) {
                if (schemaSource.getByteStream() != null) {
                    xsdSources.add(new StreamSource(schemaSource.getByteStream()));
                } else if (schemaSource.getCharacterStream() != null) {
                    xsdSources.add(new StreamSource(schemaSource.getCharacterStream()));
                } else {
                    throw new SAXException("Schema resolver '" + schemaResolver.getClass().getName() + "' failed to resolve schema for namespace '" + namespace + "'.  Resolver must return a Reader or InputStream in the InputSource.");
                }
            }
        }

        return xsdSources;
    }

    protected Smooks newSmooksInstance(List<Properties> descriptors, EntityResolver bindingResolver) throws SAXException, IOException {
        AssertArgument.isNotNullAndNotEmpty(descriptors, "descriptors");
        AssertArgument.isNotNull(bindingResolver, "bindingResolver");

        Set<Namespace> namespaces = resolveNamespaces(descriptors);

        // Now create a Smooks instance for processing configurations for these namespaces...
        ApplicationContextBuilder applicationContextBuilder = ServiceLoader.load(ApplicationContextBuilder.class).iterator().next();
        Smooks smooks = new Smooks(applicationContextBuilder.withClassLoader(classloader).withFilterSettings(new DefaultFilterSettings().setMaxNodeDepth(Integer.MAX_VALUE)).build());

        for (Namespace namespace : namespaces) {
            InputSource bindingSource = bindingResolver.resolveEntity(namespace.uri, namespace.uri);

            if (bindingSource != null) {
                if (bindingSource.getByteStream() != null) {
                    ResourceConfigSeq resourceConfigSeq;

                    resourceConfigSeq = smooks.getApplicationContext().getResourceConfigLoader().load(bindingSource.getByteStream(), "./", classloader);
                    for (int i = 0; i < resourceConfigSeq.size(); i++) {
                        ResourceConfig config = resourceConfigSeq.get(i);
                        SelectorStep selectorStep = ((IndexedSelectorPath) config.getSelectorPath()).getTargetSelectorStep();

                        // And if there isn't a namespace prefix specified on the element (unresolved at this point),
                        // then assign the binding config namespace...
                        if (((NamedSelectorStep) selectorStep).getQName().getPrefix().equals(XMLConstants.DEFAULT_NS_PREFIX)) {
                            config.getSelectorPath().getNamespaces().put(namespace.id, namespace.uri);
                        }
                    }

                    smooks.getApplicationContext().getRegistry().registerResourceConfigSeq(resourceConfigSeq);
                } else {
                    throw new SAXException("Binding configuration resolver '" + bindingResolver.getClass().getName() + "' failed to resolve binding configuration for namespace '" + namespace + "'.  Resolver must return an InputStream in the InputSource.");
                }
            }
        }

        return smooks;
    }

    protected static Set<Namespace> resolveNamespaces(List<Properties> descriptors) {
        List<Namespace> namespaces = new ArrayList<>();

        for (Properties descriptor : descriptors) {
            extractNamespaceDecls(descriptor, namespaces);
        }

        Comparator<Namespace> namespaceSorter = Comparator.comparingInt(o -> o.order);

        Namespace[] namespaceArray = new Namespace[namespaces.size()];
        namespaces.toArray(namespaceArray);
        Arrays.sort(namespaceArray, namespaceSorter);

        Set<Namespace> orderedNamespaceSet = new LinkedHashSet<>();
        orderedNamespaceSet.addAll(Arrays.asList(namespaceArray));

        return orderedNamespaceSet;
    }

    protected static List<Namespace> extractNamespaceDecls(Properties descriptor, List<Namespace> namespaces) {
        Set<Map.Entry<Object, Object>> properties = descriptor.entrySet();
        for (Map.Entry<Object, Object> property : properties) {
            String key = ((String) property.getKey()).trim();
            if (key.endsWith(DESCRIPTOR_NAMESPACE_POSTFIX)) {
                Namespace namespace = new Namespace();
                String namespaceUri = (String) property.getValue();
                String namespaceId = getNamespaceId(namespaceUri, descriptor);

                if (namespaceId == null) {
                    throw new SmooksConfigException("Unable to resolve namespace ID for namespace URI '" + namespaceUri + "'.");
                }

                String namespaceOrder = descriptor.getProperty(namespaceId + DESCRIPTOR_ORDER_POSTFIX, Integer.toString(Integer.MAX_VALUE)).trim();

                namespace.uri = namespaceUri;
                namespace.id = namespaceId;
                try {
                    namespace.order = Integer.parseInt(namespaceOrder);
                } catch (NumberFormatException e) {
                    throw new SmooksConfigException("Invalid value for descriptor config value '" + namespaceId + DESCRIPTOR_ORDER_POSTFIX + "'.  Must be a valid Integer value.");
                }

                namespaces.add(namespace);
            }
        }

        return namespaces;
    }

    public static String getNamespaceId(String namespaceURI, List<Properties> descriptors) {
        for (Properties descriptor : descriptors) {
            String id = getNamespaceId(namespaceURI, descriptor);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    protected static String getNamespaceId(String namespaceURI, Properties descriptor) {
        Set<Map.Entry<Object, Object>> properties = descriptor.entrySet();
        for (Map.Entry<Object, Object> property : properties) {
            String key = ((String) property.getKey()).trim();
            String value = ((String) property.getValue()).trim();
            if (key.endsWith(DESCRIPTOR_NAMESPACE_POSTFIX) && value.equals(namespaceURI)) {
                return key.substring(0, (key.length() - DESCRIPTOR_NAMESPACE_POSTFIX.length()));
            }
        }
        return null;
    }

    public static String getSchemaLocation(String namespaceId, List<Properties> descriptors) {
        return getDescriptorValue(namespaceId + DESCRIPTOR_SCHEMA_LOCATION_POSTFIX, descriptors);
    }

    public static String getBindingConfigLocation(String namespaceId, List<Properties> descriptors) {
        return getDescriptorValue(namespaceId + DESCRIPTOR_BINDING_CONFIG_LOCATION_POSTFIX, descriptors);
    }

    protected static String getDescriptorValue(String name, List<Properties> descriptors) {
        for (Properties descriptor : descriptors) {
            String value = descriptor.getProperty(name);
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    protected static class Namespace {
        protected String uri;
        protected String id;
        protected int order;
    }
}
