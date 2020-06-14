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

import javax.xml.transform.stream.StreamSource;

import org.smooks.Smooks;
import org.smooks.cartridges.javabean.Value;
import org.smooks.javabean.decoders.BooleanDecoder;
import org.smooks.javabean.decoders.IntegerDecoder;
import org.smooks.payload.JavaResult;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Programmatic Binding config test for the Value class.
 *
 * @author <a href="mailto:maurice.zeijen@smies.com">maurice.zeijen@smies.com</a>
 *
 */
public class ProgrammaticValueConfigTest {

        @Test
	public void test_01() {

		Smooks smooks = new Smooks();

		smooks.addVisitor(new Value("customerName", "customer"));
		smooks.addVisitor(new Value("customerNumber", "customer/@number")
								.setDecoder(new IntegerDecoder()));
		smooks.addVisitor(new Value("privatePerson", "privatePerson")
								.setDecoder(new BooleanDecoder())
								.setDefaultValue("true"));

		JavaResult result = new JavaResult();
        smooks.filterSource(new StreamSource(getClass().getResourceAsStream("../order-01.xml")), result);

        assertEquals("Joe", result.getBean("customerName"));
		assertEquals(123123, result.getBean("customerNumber"));
		assertEquals(Boolean.TRUE, result.getBean("privatePerson"));
	}

        @Test
	public void test_02() {

		Smooks smooks = new Smooks();

		smooks.addVisitor(new Value("customerNumber1", "customer/@number", Integer.class));
		smooks.addVisitor(new Value("customerNumber2", "customer/@number").setType(Integer.class));

		JavaResult result = new JavaResult();
        smooks.filterSource(new StreamSource(getClass().getResourceAsStream("../order-01.xml")), result);

		assertEquals(123123, result.getBean("customerNumber1"));
		assertEquals(123123, result.getBean("customerNumber2"));
	}

}
