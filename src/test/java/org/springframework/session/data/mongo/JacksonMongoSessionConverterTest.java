/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.session.data.mongo;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import java.lang.reflect.Field;

import org.junit.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.ReflectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DBObject;

/**
 * @author Jakub Kubrynski
 * @author Greg Turnquist
 */
public class JacksonMongoSessionConverterTest extends AbstractMongoSessionConverterTest {

	JacksonMongoSessionConverter mongoSessionConverter = new JacksonMongoSessionConverter();

	@Override
	AbstractMongoSessionConverter getMongoSessionConverter() {
		return this.mongoSessionConverter;
	}

	@Test
	public void shouldSaveIdField() throws Exception {

		// given
		MongoSession session = new MongoSession();

		// when
		DBObject convert = this.mongoSessionConverter.convert(session);

		// then
		assertThat(convert.get("_id")).isEqualTo(session.getId());
		assertThat(convert.get("id")).isNull();
	}

	@Test
	public void shouldQueryAgainstAttribute() throws Exception {

		// when
		Query cart = this.mongoSessionConverter.getQueryForIndex("cart", "my-cart");

		// then
		assertThat(cart.getQueryObject().get("attrs.cart")).isEqualTo("my-cart");
	}

	@Test
	public void shouldAllowCustomObjectMapper() {

		// given
		ObjectMapper myMapper = new ObjectMapper();

		// when
		JacksonMongoSessionConverter converter = new JacksonMongoSessionConverter(myMapper);

		// then
		Field objectMapperField = ReflectionUtils.findField(JacksonMongoSessionConverter.class, "objectMapper");
		ReflectionUtils.makeAccessible(objectMapperField);
		ObjectMapper converterMapper = (ObjectMapper) ReflectionUtils.getField(objectMapperField, converter);

		assertThat(converterMapper).isEqualTo(myMapper);
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldNotAllowNullObjectMapperToBeInjected() {

		new JacksonMongoSessionConverter((ObjectMapper) null);
	}
}
