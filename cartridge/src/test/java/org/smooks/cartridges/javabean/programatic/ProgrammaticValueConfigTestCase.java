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
package org.smooks.cartridges.javabean.programatic;

import org.junit.jupiter.api.Test;
import org.smooks.Smooks;
import org.smooks.cartridges.javabean.Value;
import org.smooks.engine.converter.BooleanConverterFactory;
import org.smooks.engine.converter.StringToIntegerConverterFactory;
import org.smooks.io.sink.JavaSink;
import org.smooks.io.source.StreamSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Programmatic Binding config test for the Value class.
 *
 * @author <a href="mailto:maurice.zeijen@smies.com">maurice.zeijen@smies.com</a>
 *
 */
public class ProgrammaticValueConfigTestCase {

	@Test
	public void test_01() {
		Smooks smooks = new Smooks();
		smooks.addVisitors(new Value("customerName", "customer", smooks.getApplicationContext().getRegistry()));
		
		Value customerNumberValue = new Value("customerNumber", "customer/@number", smooks.getApplicationContext().getRegistry());
		customerNumberValue.setTypeConverter(new StringToIntegerConverterFactory().createTypeConverter());
		smooks.addVisitors(customerNumberValue);

		Value privatePersonValue = new Value("privatePerson", "privatePerson", smooks.getApplicationContext().getRegistry());
		privatePersonValue.setTypeConverter(new BooleanConverterFactory().createTypeConverter()).setDefaultValue("true");
		smooks.addVisitors(privatePersonValue);

		JavaSink sink = new JavaSink();
        smooks.filterSource(new StreamSource<>(getClass().getResourceAsStream("/order-01.xml")), sink);

        assertEquals("Joe", sink.getBean("customerName"));
		assertEquals(123123, sink.getBean("customerNumber"));
		assertEquals(Boolean.TRUE, sink.getBean("privatePerson"));
	}

	@Test
	public void test_02() {

		Smooks smooks = new Smooks();

		smooks.addVisitors(new Value("customerNumber1", "customer/@number", Integer.class, smooks.getApplicationContext().getRegistry()));
		smooks.addVisitors(new Value("customerNumber2", "customer/@number", smooks.getApplicationContext().getRegistry()).setType(Integer.class));

		JavaSink sink = new JavaSink();
        smooks.filterSource(new StreamSource<>(getClass().getResourceAsStream("/order-01.xml")), sink);

		assertEquals(123123, sink.getBean("customerNumber1"));
		assertEquals(123123, sink.getBean("customerNumber2"));
	}

}
