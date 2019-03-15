/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.session.data.mongo.integration;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import java.lang.reflect.Field;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.JdkMongoSessionConverter;
import org.springframework.util.ReflectionUtils;

/**
 * Verify container's {@link ClassLoader} is injected into session converter (reactive and traditional).
 * 
 * @author Greg Turnquist
 */
public abstract class AbstractClassLoaderTest<T> extends AbstractITest {

	@Autowired
	T sessionRepository;
	
	@Autowired
	ApplicationContext applicationContext;

	@Test
	public void verifyContainerClassLoaderLoadedIntoConverter() {

		Field mongoSessionConverterField = ReflectionUtils.findField(sessionRepository.getClass(), "mongoSessionConverter");
		ReflectionUtils.makeAccessible(mongoSessionConverterField);
		AbstractMongoSessionConverter sessionConverter = (AbstractMongoSessionConverter) ReflectionUtils.getField(mongoSessionConverterField, this.sessionRepository);

		assertThat(sessionConverter).isInstanceOf(JdkMongoSessionConverter.class);

		JdkMongoSessionConverter jdkMongoSessionConverter = (JdkMongoSessionConverter) sessionConverter;

		Field converterField = ReflectionUtils.findField(JdkMongoSessionConverter.class, "deserializer");
		ReflectionUtils.makeAccessible(converterField);
		DeserializingConverter deserializingConverter = (DeserializingConverter) ReflectionUtils.getField(converterField, jdkMongoSessionConverter);

		Field deserializerField = ReflectionUtils.findField(DeserializingConverter.class, "deserializer");
		ReflectionUtils.makeAccessible(deserializerField);
		DefaultDeserializer deserializer = (DefaultDeserializer) ReflectionUtils.getField(deserializerField, deserializingConverter);

		Field classLoaderField = ReflectionUtils.findField(DefaultDeserializer.class, "classLoader");
		ReflectionUtils.makeAccessible(classLoaderField);
		ClassLoader classLoader = (ClassLoader) ReflectionUtils.getField(classLoaderField, deserializer);

		assertThat(classLoader).isEqualTo(applicationContext.getClassLoader());
	}

}
