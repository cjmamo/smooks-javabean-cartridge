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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smooks.Smooks;
import org.smooks.api.ExecutionContext;
import org.smooks.api.SmooksException;
import org.smooks.api.bean.lifecycle.BeanContextLifecycleEvent;
import org.smooks.api.bean.lifecycle.BeanContextLifecycleObserver;
import org.smooks.api.bean.lifecycle.BeanLifecycle;
import org.smooks.api.delivery.fragment.Fragment;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.assertion.AssertArgument;
import org.smooks.cartridges.javabean.BeanInstancePopulator;
import org.smooks.cartridges.javabean.dynamic.serialize.BeanWriter;
import org.smooks.cartridges.javabean.dynamic.visitor.NamespaceReaper;
import org.smooks.cartridges.javabean.dynamic.visitor.UnknownElementDataReaper;
import org.smooks.engine.delivery.fragment.NodeFragment;
import org.smooks.engine.report.HtmlReportGenerator;
import org.smooks.engine.resource.config.GlobalParamsResourceConfig;
import org.smooks.io.sink.JavaSink;
import org.smooks.io.source.DOMSource;
import org.smooks.io.source.ReaderSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dynamic Model Builder.
 * <p/>
 * Useful for constructing configuration model etc.  Allows you to build a config model
 * for a dynamic configuration namespace i.e. a config namespace that is evolving and being
 * extended all the time.  New namespaces can be easily added or extended.  All that's required
 * is to define the new config XSD and the Smooks Java Binding config to bind the data in the
 * config namespace into the target Java model.
 * <p/>
 * The namespaces all need to be configured in a "descriptor" .properties file located on the classpath.
 * Here's an example:
 * <pre>
 * mycomp.namespace=http://www.acme.com/xsd/mycomp.xsd
 * mycomp.schemaLocation=/META-INF/xsd/mycomp.xsd
 * mycomp.bindingConfigLocation=/META-INF/xsd/mycomp-binding.xml
 * </pre>
 * <p>
 * You should use a unique descriptor path for a given configuration model.  Of course there can be many instances
 * of this file on the classpath i.e. one per module/jar.  This allows you to easily add extensions and updates
 * to your configuration model, without having to define new Java model for the new namespaces (versions) etc.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class ModelBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelBuilder.class);
    private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    static {
        DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);
    }

    private final Descriptor descriptor;
    private boolean validate;
    private String reportPath;

    public ModelBuilder(Descriptor descriptor, boolean validate) throws SAXException, IOException {
        AssertArgument.isNotNull(descriptor, "descriptor");

        this.descriptor = descriptor;
        this.validate = validate;

        configure();
    }

    public ModelBuilder(String descriptorPath, boolean validate) throws SAXException, IOException {
        AssertArgument.isNotNullAndNotEmpty(descriptorPath, "descriptorPath");

        descriptor = new Descriptor(descriptorPath);
        this.validate = validate;

        configure();
    }

    public boolean isValidating() {
        return validate;
    }

    protected Descriptor getDescriptor() {
        return descriptor;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public <T> T readObject(InputStream message, Class<T> returnType) throws SAXException, IOException {
        return readObject(new InputStreamReader(message), returnType);
    }

    public <T> T readObject(Reader message, Class<T> returnType) throws SAXException, IOException {
        Model<JavaSink> model = readModel(message, JavaSink.class);
        return model.getModelRoot().getBean(returnType);
    }

    public <T> Model<T> readModel(InputStream message, Class<T> modelRoot) throws SAXException, IOException {
        return readModel(new InputStreamReader(message), modelRoot);
    }

    public <T> Model<T> readModel(Reader message, Class<T> modelRoot) throws SAXException, IOException {
        AssertArgument.isNotNull(message, "message");
        AssertArgument.isNotNull(modelRoot, "modelRoot");

        JavaSink sink = new JavaSink();
        ExecutionContext executionContext = descriptor.getSmooks().createExecutionContext();
        Map<Class<?>, Map<String, BeanWriter>> beanWriters = descriptor.getBeanWriters();
        BeanTracker beanTracker = new BeanTracker(beanWriters);

        if (reportPath != null) {
            executionContext.getContentDeliveryRuntime().getExecutionEventListeners().add(new HtmlReportGenerator(reportPath, descriptor.getSmooks().getApplicationContext()));
        }

        executionContext.getBeanContext().addObserver(beanTracker);

        if (validate && descriptor.getSchema() != null) {
            // Validate the message against the schemas...
            Document messageDoc = toDocument(message);

            // Validate the document and then filter it through smooks...
            descriptor.getSchema().newValidator().validate(new javax.xml.transform.dom.DOMSource(messageDoc));
            descriptor.getSmooks().filterSource(executionContext, new DOMSource(messageDoc), sink);
        } else {
            descriptor.getSmooks().filterSource(executionContext, new ReaderSource<>(message), sink);
        }

        Model<T> model;

        if (modelRoot == JavaSink.class) {
            model = new Model<>(modelRoot.cast(sink), beanTracker.beans, beanWriters, NamespaceReaper.getNamespacePrefixMappings(executionContext));
        } else {
            model = new Model<>(modelRoot.cast(sink.getBean(modelRoot)), beanTracker.beans, beanWriters, NamespaceReaper.getNamespacePrefixMappings(executionContext));
        }

        return model;
    }

    private Document toDocument(Reader message) {
        DocumentBuilder docBuilder;

        try {
            docBuilder = DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new SmooksException("Unable to parse message and dynamically bind into object model.  DOM Parser confguration exception.", e);
        }

        try {
            return docBuilder.parse(new InputSource(message));
        } catch (SAXException e) {
            throw new SmooksException("Unable to parse message and dynamically bind into object model.  Message format exception.", e);
        } catch (IOException e) {
            throw new SmooksException("Unable to parse message and dynamically bind into object model.  IO exception.", e);
        } finally {
            try {
                message.close();
            } catch (IOException e) {
                LOGGER.debug("Exception closing message reader.", e);
            }
        }
    }

    private void configure() {
        final Smooks smooks = descriptor.getSmooks();
        smooks.addVisitor(new NamespaceReaper());
        //descriptor.getSmooks().addVisitor(new UnknownElementDataReaper(), "*");

        final ResourceConfig globalParamsResourceConfig = new GlobalParamsResourceConfig();
        globalParamsResourceConfig.setParameter(BeanInstancePopulator.NOTIFY_POPULATE, "true");
        smooks.addResourceConfig(globalParamsResourceConfig);

        // Create the execution context so as to force resolution of the config...
        smooks.createExecutionContext();
    }

    private static class BeanTracker implements BeanContextLifecycleObserver {

        private final List<BeanMetadata> beans = new ArrayList<>();
        private final Map<Class<?>, Map<String, BeanWriter>> beanWriterMap;

        public BeanTracker(Map<Class<?>, Map<String, BeanWriter>> beanWriterMap) {
            this.beanWriterMap = beanWriterMap;
        }

        public void onBeanLifecycleEvent(BeanContextLifecycleEvent event) {
            if (event.getLifecycle() == BeanLifecycle.ADD || event.getLifecycle() == BeanLifecycle.CHANGE) {
                Object bean = event.getBean();
                BeanMetadata beanMetadata = new BeanMetadata(bean);
                Map<String, BeanWriter> beanWriters = beanWriterMap.get(bean.getClass());
                Fragment source = event.getSource();

                if (source != null) {
                    String namespaceURI = null;
                    if (source instanceof NodeFragment) {
                        Node node = (Node) source.unwrap();

                        namespaceURI = node.getNamespaceURI();

                        beanMetadata.setNamespace(namespaceURI);
                        beanMetadata.setNamespacePrefix(node.getPrefix());
                        beanMetadata.setCreateSource(source);

                        beans.add(beanMetadata);

                        beanMetadata.setPreText(UnknownElementDataReaper.getPreText((Element) node, beans));
                    }

                    if (beanWriters != null) {
                        BeanWriter beanWriter = beanWriters.get(namespaceURI);

                        if (beanWriter != null) {
                            beanMetadata.setWriter(beanWriter);
                        } else if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("BeanWriters are configured for Object type '" + bean.getClass() + "', but not for namespace '" + namespaceURI + "'.");
                        }
                    } else if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("No BeanWriters configured for Object type '" + bean.getClass() + "'.");
                    }
                }
            } else if (event.getLifecycle() == BeanLifecycle.POPULATE) {
                BeanMetadata beanMetdata = findMetadata(event.getBean());

                beanMetdata.getPopulateSources().add(event.getSource());
            }
        }

        private BeanMetadata findMetadata(Object bean) {
            for (BeanMetadata metaData : beans) {
                if (metaData.getBean() == bean) {
                    return metaData;
                }
            }

            BeanRegistrationException.throwUnregisteredBeanInstanceException(bean);

            return null; // Satisfy compiler
        }
    }
}
