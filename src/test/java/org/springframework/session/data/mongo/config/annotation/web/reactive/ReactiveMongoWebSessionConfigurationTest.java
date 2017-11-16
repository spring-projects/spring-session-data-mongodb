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
import static org.mockito.BDDMockito.*;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.config.annotation.web.server.EnableSpringWebSession;
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

	private AnnotationConfigApplicationContext context;
	
	@After
	public void tearDown() {

		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void enableSpringWebSessionConfiguresThings() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(GoodConfig.class);
		this.context.refresh();

		WebSessionManager webSessionManagerFoundByType = this.context.getBean(WebSessionManager.class);
		Object webSessionManagerFoundByName = this.context.getBean(WebHttpHandlerBuilder.WEB_SESSION_MANAGER_BEAN_NAME);

		assertThat(webSessionManagerFoundByType).isNotNull();
		assertThat(webSessionManagerFoundByName).isNotNull();
		assertThat(webSessionManagerFoundByType).isEqualTo(webSessionManagerFoundByName);

		assertThat(this.context.getBean(ReactiveSessionRepository.class)).isNotNull();
	}

	@Test
	public void missingReactorSessionRepositoryBreaksAppContext() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(BadConfig.class);

		assertThatExceptionOfType(UnsatisfiedDependencyException.class)
				.isThrownBy(this.context::refresh)
				.withMessageContaining("Error creating bean with name 'reactiveMongoOperationsSessionRepository'")
				.withMessageContaining("No qualifying bean of type '" + ReactiveMongoOperations.class.getCanonicalName());
	}

	@Test
	public void defaultSessionConverterShouldBeJdkWhenOnClasspath() throws IllegalAccessException {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(GoodConfig.class);
		this.context.refresh();

		ReactiveMongoOperationsSessionRepository repository = this.context.getBean(ReactiveMongoOperationsSessionRepository.class);

		AbstractMongoSessionConverter converter = findMongoSessionConverter(repository);

		assertThat(converter)
			.extracting(AbstractMongoSessionConverter::getClass)
			.contains(JdkMongoSessionConverter.class);
	}

	@Test
	public void overridingMongoSessionConverterWithBeanShouldWork() throws IllegalAccessException {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(OverrideSessionConverterConfig.class);
		this.context.refresh();

		ReactiveMongoOperationsSessionRepository repository = this.context.getBean(ReactiveMongoOperationsSessionRepository.class);

		AbstractMongoSessionConverter converter = findMongoSessionConverter(repository);

		assertThat(converter)
			.extracting(AbstractMongoSessionConverter::getClass)
			.contains(JacksonMongoSessionConverter.class);
	}

	@Test
	public void overridingIntervalAndCollectionNameThroughAnnotationShouldWork() throws IllegalAccessException {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(OverrideMongoParametersConfig.class);
		this.context.refresh();

		ReactiveMongoOperationsSessionRepository repository = this.context.getBean(ReactiveMongoOperationsSessionRepository.class);

		Field inactiveField = ReflectionUtils.findField(ReactiveMongoOperationsSessionRepository.class, "maxInactiveIntervalInSeconds");
		ReflectionUtils.makeAccessible(inactiveField);
		Integer inactiveSeconds = (Integer) inactiveField.get(repository);

		Field collectionNameField = ReflectionUtils.findField(ReactiveMongoOperationsSessionRepository.class, "collectionName");
		ReflectionUtils.makeAccessible(collectionNameField);
		String collectionName = (String) collectionNameField.get(repository);

		assertThat(inactiveSeconds).isEqualTo(123);
		assertThat(collectionName).isEqualTo("test-case");
	}

	@Test
	public void reactiveAndBlockingMongoOperationsShouldEnsureIndexing() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(ConfigWithReactiveAndImperativeMongoOperations.class);
		this.context.refresh();

		MongoOperations operations = this.context.getBean(MongoOperations.class);
		IndexOperations indexOperations = this.context.getBean(IndexOperations.class);

		verify(operations, times(1)).indexOps((String) any());
		verify(indexOperations, times(1)).getIndexInfo();
		verify(indexOperations, times(1)).ensureIndex(any());
	}

	@Test
	public void overrideCollectionAndInactiveIntervalThroughConfigurationOptions() {

		this.context = new AnnotationConfigApplicationContext();
		this.context.register(CustomizedReactiveConfiguration.class);
		this.context.refresh();

		ReactiveMongoOperationsSessionRepository repository = this.context.getBean(ReactiveMongoOperationsSessionRepository.class);
		assertThat(repository.getCollectionName()).isEqualTo("custom-collection");
		assertThat(repository.getMaxInactiveIntervalInSeconds()).isEqualTo(123);
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
		AbstractMongoSessionConverter mongoSessionConverter() {
			return new JacksonMongoSessionConverter();
		}
	}

	@EnableMongoWebSession(maxInactiveIntervalInSeconds = 123, collectionName = "test-case")
	static class OverrideMongoParametersConfig {

		@Bean
		ReactiveMongoOperations operations() {
			return mock(ReactiveMongoOperations.class);
		}
	}

	@EnableMongoWebSession
	static class ConfigWithReactiveAndImperativeMongoOperations {

		@Bean
		ReactiveMongoOperations reactiveMongoOperations() {
			return mock(ReactiveMongoOperations.class);
		}

		@Bean
		IndexOperations indexOperations() {

			IndexOperations indexOperations = mock(IndexOperations.class);
			given(indexOperations.getIndexInfo()).willReturn(Collections.emptyList());
			return indexOperations;
		}

		@Bean
		MongoOperations mongoOperations(IndexOperations indexOperations) {
			
			MongoOperations mongoOperations = mock(MongoOperations.class);
			given(mongoOperations.indexOps((String) any())).willReturn(indexOperations);
			return mongoOperations;
		}
	}

	@EnableSpringWebSession
	static class CustomizedReactiveConfiguration extends ReactiveMongoWebSessionConfiguration {

		@Bean
		ReactiveMongoOperations reactiveMongoOperations() {
			return mock(ReactiveMongoOperations.class);
		}

		public CustomizedReactiveConfiguration() {

			this.setCollectionName("custom-collection");
			this.setMaxInactiveIntervalInSeconds(123);
		}
	}
}
