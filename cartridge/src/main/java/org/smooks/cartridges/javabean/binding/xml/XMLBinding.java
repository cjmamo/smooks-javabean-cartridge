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
package org.smooks.cartridges.javabean.binding.xml;

import org.smooks.Smooks;
import org.smooks.api.SmooksConfigException;
import org.smooks.api.converter.TypeConverter;
import org.smooks.api.converter.TypeConverterFactory;
import org.smooks.api.io.Source;
import org.smooks.api.resource.config.Configurable;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.api.resource.config.ResourceConfigSeq;
import org.smooks.api.resource.config.xpath.SelectorPath;
import org.smooks.api.resource.config.xpath.SelectorStep;
import org.smooks.assertion.AssertArgument;
import org.smooks.cartridges.javabean.BeanInstanceCreator;
import org.smooks.cartridges.javabean.BeanInstancePopulator;
import org.smooks.cartridges.javabean.binding.AbstractBinding;
import org.smooks.cartridges.javabean.binding.BeanSerializationException;
import org.smooks.cartridges.javabean.binding.SerializationContext;
import org.smooks.cartridges.javabean.binding.model.Bean;
import org.smooks.cartridges.javabean.binding.model.Binding;
import org.smooks.cartridges.javabean.binding.model.DataBinding;
import org.smooks.cartridges.javabean.binding.model.ModelSet;
import org.smooks.cartridges.javabean.binding.model.WiredBinding;
import org.smooks.cartridges.javabean.binding.model.get.ConstantGetter;
import org.smooks.cartridges.javabean.binding.model.get.GetterGraph;
import org.smooks.engine.lookup.ContentHandlerFactoryLookup;
import org.smooks.engine.lookup.NamespaceManagerLookup;
import org.smooks.engine.lookup.converter.SourceTargetTypeConverterFactoryLookup;
import org.smooks.engine.resource.config.xpath.step.AttributeSelectorStep;
import org.smooks.engine.resource.config.xpath.step.ElementSelectorStep;
import org.smooks.io.source.StringSource;
import org.smooks.support.ClassUtils;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * XML Binding class.
 * <p/>
 * This class is designed specifically for reading and writing XML data (does not work for other data formats)
 * to and from Java Object models using nothing more than standard &lt;jb:bean&gt; configurations i.e.
 * no need to write a template for serializing the Java Objects to an output character based format,
 * as with Smooks v1.4 and before.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @since 1.5
 */
@SuppressWarnings("unchecked")
public class XMLBinding extends AbstractBinding {

    protected ModelSet beanModelSet;
    protected List<XMLElementSerializationNode> graphs;
    protected final Set<QName> rootElementNames = new HashSet<>();
    protected final Map<Class, RootNodeSerializer> serializers = new LinkedHashMap<>();
    protected boolean omitXMLDeclaration = false;

    /**
     * Public constructor.
     * <p/>
     * Must be followed by calls to the {@link #add(java.io.InputStream)} (or {@link #add(String)}) method
     * and then the {@link #initialise()} method.
     */
    public XMLBinding() {
        super();
    }

    /**
     * Public constructor.
     * <p/>
     * Create an instance using a pre-configured Smooks instance.
     *
     * @param smooks The pre-configured Smooks instance.
     */
    public XMLBinding(Smooks smooks) {
        super(smooks);
    }

    @Override
    public XMLBinding add(String smooksConfigURI) throws IOException, SAXException {
        super.add(smooksConfigURI);
        return this;
    }

    @Override
    public XMLBinding add(InputStream smooksConfigStream) throws IOException, SAXException {
        return (XMLBinding) super.add(smooksConfigStream);
    }

    @Override
    public XMLBinding setReportPath(String reportPath) {
        super.setReportPath(reportPath);
        return this;
    }

    @Override
    public XMLBinding initialise() {
        super.initialise();

        beanModelSet = ModelSet.get(getSmooks().getApplicationContext());
        graphs = createExpandedXMLOutputGraphs(getUserDefinedResourceList());
        createRootSerializers(graphs);
        mergeBeanModelsIntoXMLGraphs();

        return this;
    }

    /**
     * Turn on/off outputting of the XML declaration when executing the {@link #toXML(Object, java.io.Writer)} method.
     *
     * @param omitXMLDeclaration True if the order is to be omitted, otherwise false.
     * @return <code>this</code> instance.
     */
    public XMLBinding setOmitXMLDeclaration(boolean omitXMLDeclaration) {
        this.omitXMLDeclaration = omitXMLDeclaration;
        return this;
    }

    /**
     * Bind from the XML into the Java Object model.
     *
     * @param inputSource The XML input.
     * @param toType      The Java type to which the XML data is to be bound.
     * @param <T>         The Java type to which the XML data is to be bound.
     * @return The populated Java instance.
     */
    public <T> T fromXML(String inputSource, Class<T> toType) {
        try {
            return bind(new StringSource(inputSource), toType);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IOException from a String input.", e);
        }
    }

    /**
     * Bind from the XML into the Java Object model.
     *
     * @param inputSource The XML input.
     * @param toType      The Java type to which the XML data is to be bound.
     * @param <T>         The Java type to which the XML data is to be bound.
     * @return The populated Java instance.
     */
    public <T> T fromXML(Source inputSource, Class<T> toType) throws IOException {
        return bind(inputSource, toType);
    }

    /**
     * Write the supplied Object instance to XML.
     *
     * @param object       The Object instance.
     * @param outputWriter The output writer.
     * @param <W>          The Writer type.
     * @return The supplied {@link Writer} instance}.
     * @throws BeanSerializationException Error serializing the bean.
     * @throws IOException                Error writing to the supplied Writer instance.
     */
    public <W extends Writer> W toXML(Object object, W outputWriter) throws BeanSerializationException, IOException {
        AssertArgument.isNotNull(object, "object");
        assertInitialized();

        Class<?> objectClass = object.getClass();
        RootNodeSerializer rootNodeSerializer = serializers.get(objectClass);
        if (rootNodeSerializer == null) {
            throw new BeanSerializationException("No serializer for Java type '" + objectClass.getName() + "'.");
        }
        if (!omitXMLDeclaration) {
            outputWriter.write("<?xml version=\"1.0\"?>\n");
        }

        XMLElementSerializationNode serializer = rootNodeSerializer.serializer;
        serializer.serialize(outputWriter, new SerializationContext(object, rootNodeSerializer.beanId));
        outputWriter.flush();

        return outputWriter;
    }

    /**
     * Write the supplied Object instance to XML.
     * <p/>
     * This is a simple wrapper on the {@link #toXML(Object, java.io.Writer)} method.
     *
     * @param object The Object instance.
     * @return The XML as a String.
     * @throws BeanSerializationException Error serializing the bean.
     */
    public String toXML(Object object) throws BeanSerializationException {
        StringWriter writer = new StringWriter();
        try {
            toXML(object, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected IOException writing to a StringWriter.", e);
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                throw new IllegalStateException("Unexpected IOException closing a StringWriter.", e);
            }
        }
    }

    protected void mergeBeanModelsIntoXMLGraphs() {
        Set<Map.Entry<Class, RootNodeSerializer>> serializerSet = serializers.entrySet();

        for (Map.Entry<Class, RootNodeSerializer> rootNodeSerializer : serializerSet) {
            Bean model = beanModelSet.getModel(rootNodeSerializer.getKey());
            if (model == null) {
                throw new IllegalStateException("Unexpected error.  No Bean model for type '" + rootNodeSerializer.getKey().getName() + "'.");
            }
            merge(rootNodeSerializer.getValue().serializer, model);
        }
    }

    protected void merge(XMLElementSerializationNode serializer, Bean bean) {
        boolean isCollection = bean.isCollection();

        for (Binding binding : bean.getBindings()) {
            BeanInstancePopulator populator = binding.getPopulator();

            if (!isCollection && binding instanceof DataBinding) {
                XMLSerializationNode node = serializer.findNode(populator.getConfig().getSelectorPath());
                if (node != null) {
                    node.setGetter(constructContextualGetter((DataBinding) binding));
                    Method getterMethodByProperty = ClassUtils.getGetterMethodByProperty(binding.getProperty(), bean.getBeanClass(), null);
                    TypeConverterFactory<?, ?> beanPopulatorTypeConverterFactory = binding.getPopulator().getTypeConverterFactory(getSmooks().createExecutionContext().getContentDeliveryRuntime().getContentDeliveryConfig());
                    TypeConverter<?, ?> beanPopulatorTypeConverter = beanPopulatorTypeConverterFactory.createTypeConverter();
                    TypeConverterFactory<?, ? extends String> xmlBindingTypeFactory = getSmooks().getApplicationContext().getRegistry().lookup(new SourceTargetTypeConverterFactoryLookup<>(getterMethodByProperty.getReturnType(), String.class));
                    if (xmlBindingTypeFactory != null) {
                        TypeConverter<?, ? extends String> xmlBindingTypeConverter = xmlBindingTypeFactory.createTypeConverter();
                        if (xmlBindingTypeConverter instanceof Configurable && beanPopulatorTypeConverter instanceof Configurable) {
                            ((Configurable) xmlBindingTypeConverter).setConfiguration(((Configurable) beanPopulatorTypeConverter).getConfiguration());
                        }
                        node.setTypeConverter(xmlBindingTypeConverter);
                    }
                }
            } else if (binding instanceof WiredBinding) {
                Bean wiredBean = ((WiredBinding) binding).getWiredBean();
                XMLElementSerializationNode node = (XMLElementSerializationNode) serializer.findNode(wiredBean.getCreator().getConfig().getSelectorPath());

                if (node != null) {
                    if (isCollection) {
                        // Mark the node that creates the wiredBean as being a collection item node...
                        Bean collectionBean = wiredBean.getWiredInto();
                        GetterGraph getter = constructContextualGetter(collectionBean);

                        node.setIsCollection(true);
                        node.setCollectionGetter(wiredBean.getBeanId(), getter);
                    } else {
                        node.setGetter(constructContextualGetter(wiredBean));
                    }
                }

                merge(serializer, wiredBean);
            }
        }
    }

    protected void createRootSerializers(List<XMLElementSerializationNode> graphs) {
        Collection<Bean> beanModels = beanModelSet.getModels().values();

        for (Bean model : beanModels) {
            BeanInstanceCreator creator = model.getCreator();
            SelectorPath selectorPath = creator.getConfig().getSelectorPath();
            XMLElementSerializationNode createNode = (XMLElementSerializationNode) findNode(graphs, selectorPath);

            // Only create serializers for routed elements...
            if (rootElementNames.contains(createNode.getQName())) {
                createNode = ((XMLElementSerializationNode) createNode.clone());
                createNode.setParent(null);

                Class<?> beanClass = creator.getBeanRuntimeInfo().getPopulateType();
                if (!Collection.class.isAssignableFrom(beanClass)) {
                    // Ignore Collections... don't allow them to be serialized.... not enough type info.
                    serializers.put(beanClass, new RootNodeSerializer(creator.getBeanId(), createNode));
                    addNamespaceAttributes(createNode);
                }
            }
        }
    }

    protected void addNamespaceAttributes(XMLElementSerializationNode serializer) {
        Properties namespaces = getSmooks().getApplicationContext().getRegistry().lookup(new NamespaceManagerLookup()).orElse(null);
        if (namespaces != null) {
            Enumeration<String> namespacePrefixes = (Enumeration<String>) namespaces.propertyNames();
            while (namespacePrefixes.hasMoreElements()) {
                String prefix = namespacePrefixes.nextElement();
                String namespace = namespaces.getProperty(prefix);
                QName nsAttributeName = new QName(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix, XMLConstants.XMLNS_ATTRIBUTE);
                XMLAttributeSerializationNode nsAttribute = new XMLAttributeSerializationNode(nsAttributeName);

                serializer.getAttributes().add(nsAttribute);
                nsAttribute.setGetter(new ConstantGetter(namespace));
            }
        }
    }

    protected List<XMLElementSerializationNode> createExpandedXMLOutputGraphs(final ResourceConfigSeq resourceConfigSeq) {
        final List<XMLElementSerializationNode> graphRoots = new ArrayList<>();

        for (int i = 0; i < resourceConfigSeq.size(); i++) {
            final ResourceConfig resourceConfig = resourceConfigSeq.get(i);
            final Object javaResource;
            if (resourceConfig.isJavaResource()) {
                javaResource = getSmooks().getApplicationContext().getRegistry().lookup(new ContentHandlerFactoryLookup("class")).create(resourceConfig);
            } else {
                javaResource = null;
            }

            if (javaResource instanceof BeanInstanceCreator) {
                assertSelectorOK(resourceConfig);
                constructNodePath(resourceConfig.getSelectorPath(), graphRoots);
            } else if (javaResource instanceof BeanInstancePopulator) {
                assertSelectorOK(resourceConfig);
                constructNodePath(resourceConfig.getSelectorPath(), graphRoots);
            }
        }

        return graphRoots;
    }

    protected XMLSerializationNode constructNodePath(SelectorPath selectorPath, List<XMLElementSerializationNode> graphRoots) {
        if (selectorPath == null || selectorPath.size() == 0) {
            throw new IllegalStateException("Invalid binding configuration.  All <jb:bean> configuration elements must specify fully qualified selector paths (createOnElement, data, executeOnElement attributes etc.).");
        }

        SelectorStep rootSelectorStep = selectorPath.get(1);
        XMLElementSerializationNode root = XMLElementSerializationNode.getElement(rootSelectorStep, graphRoots, true);

        if (selectorPath.size() == 3 && selectorPath.get(2) instanceof AttributeSelectorStep) {
            // It's an attribute node...
            return XMLElementSerializationNode.addAttributeNode(root, selectorPath.get(2), true);
        } else if (selectorPath.size() > 2) {
            return root.getPathNode(selectorPath, 2, true);
        } else {
            return root;
        }
    }

    protected XMLSerializationNode findNode(List<XMLElementSerializationNode> graphs, SelectorPath selectorPath) {
        XMLElementSerializationNode root = XMLElementSerializationNode.getElement(selectorPath.get(1), graphs, false);
        XMLSerializationNode node = root;

        if (selectorPath.size() > 2) {
            node = root.getPathNode(selectorPath, 2, false);
        }

        if (node == null) {
            throw new IllegalStateException("Unexpected exception.  Failed to locate the node '" + selectorPath + "'.");
        }

        return node;
    }

    protected void assertSelectorOK(ResourceConfig resourceConfig) {
        String selector = resourceConfig.getSelectorPath().getSelector();

        if (selector != null) {
            if (selector.contains(ResourceConfig.DOCUMENT_FRAGMENT_SELECTOR)) {
                throw new SmooksConfigException("Cannot use the document selector with the XMLBinding class.  Must use an absolute path.  Selector value '" + selector + "'.");
            }
            if (!selector.startsWith("/") && !selector.startsWith(" ${") && !selector.startsWith("#")) {
                throw new SmooksConfigException("Invalid selector value '" + selector + "'.  Selector paths must be absolute.");
            }
            rootElementNames.add(((ElementSelectorStep) resourceConfig.getSelectorPath().get(1)).getQName());
        }
    }

    protected static class RootNodeSerializer {
        protected final String beanId;
        protected final XMLElementSerializationNode serializer;

        protected RootNodeSerializer(String beanId, XMLElementSerializationNode serializer) {
            this.beanId = beanId;
            this.serializer = serializer;
        }
    }
}
