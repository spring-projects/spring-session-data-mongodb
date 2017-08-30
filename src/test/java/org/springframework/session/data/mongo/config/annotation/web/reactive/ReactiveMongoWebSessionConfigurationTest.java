/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.session.data.mongo.config.annotation.web.reactive;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;

import org.junit.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.session.EnableSpringWebSession;
import org.springframework.session.ReactorSessionRepository;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.JacksonMongoSessionConverter;
import org.springframework.session.data.mongo.JdkMongoSessionConverter;
import org.springframework.session.data.mongo.ReactiveMongoOperationsSessionRepository;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.server.session.WebSessionManager;

/**
 * Verify various configurations through {@link EnableSpringWebSession}.
 *
 * @author Greg Turnquist
 */
public class ReactiveMongoWebSessionConfigurationTest {

	@Test
	public void enableSpringWebSessionConfiguresThings() {

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(GoodConfig.class);
		ctx.refresh();

		WebSessionManager webSessionManagerFoundByType = ctx.getBean(WebSessionManager.class);
		Object webSessionManagerFoundByName = ctx.getBean(WebHttpHandlerBuilder.WEB_SESSION_MANAGER_BEAN_NAME);

		assertThat(webSessionManagerFoundByType).isNotNull();
		assertThat(webSessionManagerFoundByName).isNotNull();
		assertThat(webSessionManagerFoundByType).isEqualTo(webSessionManagerFoundByName);

		assertThat(ctx.getBean(ReactorSessionRepository.class)).isNotNull();
	}

	@Test
	public void missingReactorSessionRepositoryBreaksAppContext() {

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(BadConfig.class);

		assertThatExceptionOfType(UnsatisfiedDependencyException.class)
				.isThrownBy(ctx::refresh)
				.withMessageContaining("Error creating bean with name 'webSessionManager'")
				.withMessageContaining("No qualifying bean of type '" + ReactiveMongoOperations.class.getCanonicalName());
	}

	@Test
	public void defaultSessionConverterShouldBeJacksonWhenOnClasspath() throws IllegalAccessException {

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(GoodConfig.class);
		ctx.refresh();

		ReactiveMongoOperationsSessionRepository repository = ctx.getBean(ReactiveMongoOperationsSessionRepository.class);

		AbstractMongoSessionConverter converter = findMongoSessionConverter(repository);

		assertThat(converter)
			.extracting(AbstractMongoSessionConverter::getClass)
			.contains(JacksonMongoSessionConverter.class);
	}

	@Test
	public void overridingMongoSessionConverterWithBeanShouldWork() throws IllegalAccessException {

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(OverrideSessionConverterConfig.class);
		ctx.refresh();

		ReactiveMongoOperationsSessionRepository repository = ctx.getBean(ReactiveMongoOperationsSessionRepository.class);

		AbstractMongoSessionConverter converter = findMongoSessionConverter(repository);

		assertThat(converter)
			.extracting(AbstractMongoSessionConverter::getClass)
			.contains(JdkMongoSessionConverter.class);
	}

	/**
	 * Reflectively extract the {@link AbstractMongoSessionConverter} from the {@link ReactiveMongoOperationsSessionRepository}.
	 * This is to avoid expanding the surface area of the API.
	 * 
	 * @param repository
	 * @return
	 */
	private AbstractMongoSessionConverter findMongoSessionConverter(ReactiveMongoOperationsSessionRepository repository) {

		Field field = ReflectionUtils.findField(ReactiveMongoOperationsSessionRepository.class, "mongoSessionConverter");
		ReflectionUtils.makeAccessible(field);
		try {
			return (AbstractMongoSessionConverter) field.get(repository);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * A configuration with all the right parts.
	 */
	@EnableMongoWebSession
	static class GoodConfig {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}
	}

	/**
	 * A configuration where no {@link ReactiveMongoOperations} is defined. It's BAD!
	 */
	@EnableMongoWebSession
	static class BadConfig {

	}

	@EnableMongoWebSession
	static class OverrideSessionConverterConfig {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}

		@Bean
		JdkMongoSessionConverter mongoSessionConverter() {
			return new JdkMongoSessionConverter();
		}
	}
}
