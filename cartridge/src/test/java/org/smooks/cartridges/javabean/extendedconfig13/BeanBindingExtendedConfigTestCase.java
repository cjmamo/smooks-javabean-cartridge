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
package org.smooks.cartridges.javabean.extendedconfig13;

import org.junit.Ignore;
import org.junit.Test;
import org.smooks.Smooks;
import org.smooks.api.ExecutionContext;
import org.smooks.api.SmooksException;
import org.smooks.cartridges.javabean.B;
import org.smooks.cartridges.javabean.Header;
import org.smooks.cartridges.javabean.OrderItem;
import org.smooks.cartridges.javabean.extendedconfig.ExtendedOrder;
import org.smooks.io.payload.JavaResult;
import org.smooks.support.ClassUtils;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@SuppressWarnings("unchecked")
public class BeanBindingExtendedConfigTestCase {

    @Ignore
    @Test
    public void test() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_01.xml"));
        JavaResult result = new JavaResult();
        ExecutionContext execContext = smooks.createExecutionContext();

        //execContext.setEventListener(new HtmlReportGenerator("/zap/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("order-01.xml")), result);

        ExtendedOrder order = (ExtendedOrder) result.getBean("order");
        assertOrderOK(order, true);

        Map<String, String> headerHash = (Map) result.getBean("headerBeanHash");
        assertThat(headerHash, hasEntry("date", "Wed Nov 15 13:45:28 EST 2006"));
        assertThat(headerHash, hasEntry("privatePerson", ""));
        assertThat(headerHash, hasEntry("customer", "Joe"));
    }

    private InputStream getInput(String file) {
        return ClassUtils.getResourceAsStream("/org/smooks/cartridges/javabean/extendedconfig/" + file, this.getClass());
    }

    public static void assertOrderOK(ExtendedOrder order, boolean checkArrays) {

        // Order total...
        assertEquals(54.2d, order.getTotal(), 0d);

        // Header...
        assertEquals("Joe", order.getHeader().getCustomerName());
        assertEquals(new Long(123123), order.getHeader().getCustomerNumber());
        assertEquals(1163616328000L, order.getHeader().getDate().getTime());
        assertTrue(order.getHeader().getPrivatePerson());
        assertTrue(order == order.getHeader().getOrder());

        // OrderItems list...
        assertEquals(2, order.getOrderItems().size());
        assertTrue(order == order.getOrderItems().get(0).getOrder());
        assertEquals(8.9d, order.getOrderItems().get(0).getPrice(), 0d);
        assertEquals(111, order.getOrderItems().get(0).getProductId());
        assertEquals(new Integer(2), order.getOrderItems().get(0).getQuantity());
        assertTrue(order == order.getOrderItems().get(1).getOrder());
        assertEquals(5.2d, order.getOrderItems().get(1).getPrice(), 0d);
        assertEquals(222, order.getOrderItems().get(1).getProductId());
        assertEquals(new Integer(7), order.getOrderItems().get(1).getQuantity());

        if (checkArrays) {
            // OrderItems array...
            assertEquals(2, order.getOrderItemsArray().length);
            assertTrue(order == order.getOrderItemsArray()[0].getOrder());
            assertEquals(8.9d, order.getOrderItemsArray()[0].getPrice(), 0d);
            assertEquals(111, order.getOrderItemsArray()[0].getProductId());
            assertEquals(new Integer(2), order.getOrderItemsArray()[0].getQuantity());
            assertTrue(order == order.getOrderItemsArray()[1].getOrder());
            assertEquals(5.2d, order.getOrderItemsArray()[1].getPrice(), 0d);
            assertEquals(222, order.getOrderItemsArray()[1].getProductId());
            assertEquals(new Integer(7), order.getOrderItemsArray()[1].getQuantity());
        }
    }

    @Test
    public void test_error_for_List_property() throws IOException, SAXException {
        try {
            new Smooks(getClass().getResourceAsStream("test_bean_02.xml"));
            fail("Expected SmooksException");
        } catch (SmooksException e) {
            assertEquals("'wiring' binding specifies a 'property' attribute.  This is not valid for a Collection target.", e.getMessage());
        }
    }

    @Test
    public void test_error_for_Array_property() throws IOException, SAXException {
        try {
            new Smooks(getClass().getResourceAsStream("test_bean_03.xml"));
            fail("Expected SmooksException");
        } catch (SmooksException e) {
            assertEquals("'wiring' binding specifies a 'property' attribute.  This is not valid for an Array target.", e.getMessage());
        }
    }

    @Test
    public void test_error_for_no_property_on_non_list_or_array() throws IOException, SAXException {
        try {
            new Smooks(getClass().getResourceAsStream("test_bean_04.xml"));
            fail("Expected SmooksException");
        } catch (SmooksException e) {
            assertEquals("'wiring' binding for bean class 'org.smooks.cartridges.javabean.extendedconfig.ExtendedOrder' must specify a 'property' or 'setterMethod' attribute.", e.getMessage());
        }
    }

    @Test
    public void test_error_for_property_and_setterMethod() throws IOException, SAXException {
        try {
            new Smooks(getClass().getResourceAsStream("test_bean_10.xml"));
            fail("Expected SmooksException");
        } catch (SmooksException e) {
            assertEquals("'wiring' binding specifies a 'property' and a 'setterMethod' attribute.  Only one of both may be set.", e.getMessage());
        }
    }

    @Test
    public void test_order_update() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_05.xml"));

        ExtendedOrder inExtendedOrder = new ExtendedOrder();
        List<OrderItem> inOrderItems = new ArrayList<OrderItem>();
        Header inHeader = new Header();

        JavaResult result = new JavaResult();
        result.getResultMap().put("order", inExtendedOrder);
        result.getResultMap().put("orderItemList", inOrderItems);
        result.getResultMap().put("headerBean", inHeader);

        ExecutionContext execContext = smooks.createExecutionContext();

        //execContext.setEventListener(new HtmlReportGenerator("/target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("order-01.xml")), result);

        ExtendedOrder order = (ExtendedOrder) result.getBean("order");

        assertSame(inExtendedOrder, order);
        assertSame(inOrderItems, order.getOrderItems());
        assertSame(inHeader, order.getHeader());

        assertOrderOK(order, false);
    }

    @Test
    public void test_error_for_no_wireOnElement() throws IOException, SAXException {
        try {
            new Smooks(getClass().getResourceAsStream("test_bean_06.xml"));
            fail("Expected SmooksException");
        } catch (SmooksException e) {
            assertEquals("The bindings attribute 'createOnElement' and wiring attribute 'wireOnElement' are both not set. " +
                    "One of them must at least be set. If the result of this binding should be a new populated Object then " +
                    "you need to set the 'createOnElement' bindings attribute. If you want to update an existing object in " +
                    "the bean context then you must set the 'wireOnElement' attribute.", e.getMessage());
        }
    }

    @Test
    public void test_flat_xml_set_in_binding() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_07.xml"));

        JavaResult result = new JavaResult();

        ExecutionContext execContext = smooks.createExecutionContext();

        //execContext.setEventListener(new HtmlReportGenerator("target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("flat-01.xml")), result);

        assertFlatResult(result);
    }

    @Test
    public void test_flat_xml_set_global() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_08.xml"));

        JavaResult result = new JavaResult();

        ExecutionContext execContext = smooks.createExecutionContext();

        //execContext.setEventListener(new HtmlReportGenerator("target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("flat-01.xml")), result);

        assertFlatResult(result);
    }

    private void assertFlatResult(JavaResult result) {
        @SuppressWarnings("unchecked")
        ArrayList<ArrayList<B>> root = (ArrayList<ArrayList<B>>) result.getBean("root");

        assertNotNull("root should not be null", root);

        assertEquals(2, root.size());
        assertEquals(3, root.get(0).size());
        assertEquals(3, root.get(1).size());
    }

    @Test
    public void test_profile() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_09.xml"));

        JavaResult result = new JavaResult();

        ExecutionContext execContext = smooks.createExecutionContext("A");

        //execContext.setEventListener(new HtmlReportGenerator("target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("order-01.xml")), result);

        ExtendedOrder order = (ExtendedOrder) result.getBean("order");
        assertEquals(2d, order.getTotal(), 0d);

        execContext = smooks.createExecutionContext("B");

        //execContext.setEventListener(new HtmlReportGenerator("target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("order-01.xml")), result);

        order = (ExtendedOrder) result.getBean("order");
        assertEquals(4d, order.getTotal(), 0d);
    }

    @Test
    public void test_condition() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_11.xml"));

        JavaResult result = new JavaResult();

        ExecutionContext execContext = smooks.createExecutionContext();

        //execContext.setEventListener(new HtmlReportGenerator("target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("order-01.xml")), result);

        ExtendedOrder order = (ExtendedOrder) result.getBean("order");
        assertEquals(2d, order.getTotal(), 0d);
    }

    @Test
    public void test_expression_initVal() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_12.xml"));

        JavaResult result = new JavaResult();

        ExecutionContext execContext = smooks.createExecutionContext();

        //execContext.setEventListener(new HtmlReportGenerator("target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("order-01.xml")), result);

        Map order = (Map) result.getBean("orderItem");
        assertEquals(154.2d, (Double) order.get("total"), 0d);
    }

    @Test
    public void test_factory() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_13.xml"));

        JavaResult result = new JavaResult();

        ExecutionContext execContext = smooks.createExecutionContext();

        //execContext.setEventListener(new HtmlReportGenerator("target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("order-01.xml")), result);

        Map<?, ?> order = (Map<?, ?>) result.getBean("order");

        assertTrue(order instanceof HashMap);
    }

    @Test
    public void test_factory_alias() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_14.xml"));

        JavaResult result = new JavaResult();

        ExecutionContext execContext = smooks.createExecutionContext();

        //execContext.setEventListener(new HtmlReportGenerator("target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("order-01.xml")), result);

        Map<?, ?> order1 = (Map<?, ?>) result.getBean("default_order");
        Map<?, ?> order2 = (Map<?, ?>) result.getBean("mvel_order");
        Map<?, ?> order3 = (Map<?, ?>) result.getBean("mvel_class_order");
        Map<?, ?> order4 = (Map<?, ?>) result.getBean("basic_order");

        assertTrue(order1 instanceof HashMap);
        assertTrue(order2 instanceof HashMap);
        assertTrue(order3 instanceof HashMap);
        assertTrue(order4 instanceof HashMap);
    }

    @Test
    public void test_factory_global_mvel() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("test_bean_15.xml"));

        JavaResult result = new JavaResult();

        ExecutionContext execContext = smooks.createExecutionContext();

        //execContext.setEventListener(new HtmlReportGenerator("target/report.html"));
        smooks.filterSource(execContext, new StreamSource(getInput("order-01.xml")), result);

        Map<?, ?> order = (Map<?, ?>) result.getBean("order");

        assertTrue(order instanceof HashMap);
    }
}
