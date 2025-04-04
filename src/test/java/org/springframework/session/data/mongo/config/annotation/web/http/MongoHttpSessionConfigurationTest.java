/*
 * Copyright 2014-2017 the original author or authors.
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
package org.springframework.session.data.mongo.config.annotation.web.http;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.net.UnknownHostException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.*;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.session.IndexResolver;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.JacksonMongoSessionConverter;
import org.springframework.session.data.mongo.MongoIndexedSessionRepository;
import org.springframework.session.data.mongo.MongoSession;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests for {@link MongoHttpSessionConfiguration}.
 *
 * @author Eddú Meléndez
 * @author Vedran Pavic
 */
public class MongoHttpSessionConfigurationTest {

	private static final String COLLECTION_NAME = "testSessions";

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@AfterEach
	public void after() {

		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void noMongoOperationsConfiguration() {

		assertThatExceptionOfType(BeanCreationException.class).isThrownBy(() -> {
			registerAndRefresh(EmptyConfiguration.class);
		}).withMessageContaining("expected at least 1 bean which qualifies as autowire candidate");
	}

	@Test
	public void defaultConfiguration() {

		registerAndRefresh(DefaultConfiguration.class);

		assertThat(this.context.getBean(MongoIndexedSessionRepository.class)).isNotNull();
	}

	@Test
	public void customCollectionName() {

		registerAndRefresh(CustomCollectionNameConfiguration.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);

		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "collectionName")).isEqualTo(COLLECTION_NAME);
	}

	@Test
	public void setCustomCollectionName() {

		registerAndRefresh(CustomCollectionNameSetConfiguration.class);

		MongoHttpSessionConfiguration session = this.context.getBean(MongoHttpSessionConfiguration.class);

		assertThat(session).isNotNull();
		assertThat(ReflectionTestUtils.getField(session, "collectionName")).isEqualTo(COLLECTION_NAME);
	}

	@Test
	public void customMaxInactiveIntervalInSeconds() {

		registerAndRefresh(CustomMaxInactiveIntervalInSecondsConfiguration.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);

		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "maxInactiveIntervalInSeconds"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void setCustomMaxInactiveIntervalInSeconds() {

		registerAndRefresh(CustomMaxInactiveIntervalInSecondsSetConfiguration.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);

		assertThat(repository).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "maxInactiveIntervalInSeconds"))
				.isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@Test
	public void setCustomSessionConverterConfiguration() {

		registerAndRefresh(CustomSessionConverterConfiguration.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);
		AbstractMongoSessionConverter mongoSessionConverter = this.context.getBean(AbstractMongoSessionConverter.class);

		assertThat(repository).isNotNull();
		assertThat(mongoSessionConverter).isNotNull();
		assertThat(ReflectionTestUtils.getField(repository, "mongoSessionConverter")).isEqualTo(mongoSessionConverter);
	}

	@Test
	public void resolveCollectionNameByPropertyPlaceholder() {

		this.context.setEnvironment(new MockEnvironment().withProperty("session.mongo.collectionName", COLLECTION_NAME));
		registerAndRefresh(CustomMongoJdbcSessionConfiguration.class);

		MongoHttpSessionConfiguration configuration = this.context.getBean(MongoHttpSessionConfiguration.class);

		assertThat(ReflectionTestUtils.getField(configuration, "collectionName")).isEqualTo(COLLECTION_NAME);
	}

	@Test
	public void sessionRepositoryCustomizer() {

		registerAndRefresh(MongoConfiguration.class, SessionRepositoryCustomizerConfiguration.class);

		MongoIndexedSessionRepository sessionRepository = this.context.getBean(MongoIndexedSessionRepository.class);

		assertThat(sessionRepository).hasFieldOrPropertyWithValue("maxInactiveIntervalInSeconds", 10000);
	}

	@Test
	void customIndexResolverConfigurationWithDefaultMongoSessionConverter() {

		registerAndRefresh(MongoConfiguration.class, CustomIndexResolverConfigurationWithDefaultMongoSessionConverter.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);
		IndexResolver<MongoSession> indexResolver = this.context.getBean(IndexResolver.class);

		assertThat(repository).isNotNull();
		assertThat(indexResolver).isNotNull();
		assertThat(repository).extracting("mongoSessionConverter").hasFieldOrPropertyWithValue("indexResolver", indexResolver);
	}

	@Test
	void customIndexResolverConfigurationWithProvidedMongoSessionConverter() {

		registerAndRefresh(MongoConfiguration.class, CustomIndexResolverConfigurationWithProvidedMongoSessionConverter.class);

		MongoIndexedSessionRepository repository = this.context.getBean(MongoIndexedSessionRepository.class);
		IndexResolver<MongoSession> indexResolver = this.context.getBean(IndexResolver.class);

		assertThat(repository).isNotNull();
		assertThat(indexResolver).isNotNull();
		assertThat(repository).extracting("mongoSessionConverter").hasFieldOrPropertyWithValue("indexResolver", indexResolver);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {

		this.context.register(annotatedClasses);
		this.context.refresh();
	}

    @Test
    public void multipleDataSourceConfiguration() {
        assertThatExceptionOfType(BeanCreationException.class)
                .isThrownBy(() -> registerAndRefresh(MongoOperationConfiguration.class,
                        MultipleMongoOperationsConfiguration.class))
                .withMessageContaining("expected single matching bean but found 2");
    }


    @Test
    public void primaryMongoOperationConfiguration() {

        registerAndRefresh(MongoOperationConfiguration.class,
                PrimaryMongoOperationsConfiguration.class);


		MongoIndexedSessionRepository repository = this.context
                .getBean(MongoIndexedSessionRepository.class);
        MongoOperations mongoOperations = this.context.getBean("primaryMongoOperations",
                MongoOperations.class);
        assertThat(repository).isNotNull();
        assertThat(mongoOperations).isNotNull();
        MongoOperations mongoOperationsReflection = (MongoOperations) ReflectionTestUtils
                .getField(repository, "mongoOperations");
        assertThat(mongoOperationsReflection).isNotNull();
        assertThat((mongoOperationsReflection))
                .isEqualTo(mongoOperations);
    }


    @Test
    public void qualifiedDataSourceConfiguration() {
        registerAndRefresh(MongoOperationConfiguration.class,
                QualifiedMongoOperationsConfiguration.class);

		MongoIndexedSessionRepository repository = this.context
                .getBean(MongoIndexedSessionRepository.class);
        MongoOperations mongoOperations = this.context.getBean("qualifiedMongoOperations",
                MongoOperations.class);
        assertThat(repository).isNotNull();
        assertThat(mongoOperations).isNotNull();
        MongoOperations mongoOperationsReflection = (MongoOperations) ReflectionTestUtils
                .getField(repository, "mongoOperations");
        assertThat(mongoOperationsReflection).isNotNull();
        assertThat(mongoOperationsReflection)
                .isEqualTo(mongoOperations);
    }


    @Test
    public void qualifiedAndPrimaryDataSourceConfiguration() {
        registerAndRefresh(MongoOperationConfiguration.class,
                QualifiedAndPrimaryMongoConfiguration.class);

		MongoIndexedSessionRepository repository = this.context
                .getBean(MongoIndexedSessionRepository.class);
        MongoOperations mongoOperations = this.context.getBean("qualifiedMongoOperations",
                MongoOperations.class);
        assertThat(repository).isNotNull();
        assertThat(mongoOperations).isNotNull();
        MongoOperations mongoOperationsReflection = (MongoOperations) ReflectionTestUtils
                .getField(repository, "mongoOperations");
        assertThat(mongoOperations).isNotNull();
        assertThat(mongoOperationsReflection)
                .isEqualTo(mongoOperations);
    }


    @Configuration
    @EnableMongoHttpSession
    static class EmptyConfiguration {

	}

    @Configuration
    static class MongoOperationConfiguration {

        @Bean
        public MongoOperations defaultMongoOperations() {
            MongoOperations mongoOperations = mock(MongoOperations.class);
            IndexOperations indexOperations = mock(IndexOperations.class);

            given(mongoOperations.indexOps(anyString())).willReturn(indexOperations);

            return mongoOperations;
        }

    }

    static class BaseConfiguration {

		@Bean
		public MongoOperations mongoOperations() throws UnknownHostException {

			MongoOperations mongoOperations = mock(MongoOperations.class);
			IndexOperations indexOperations = mock(IndexOperations.class);

			given(mongoOperations.indexOps(anyString())).willReturn(indexOperations);

			return mongoOperations;
		}

	}

	@Configuration
	@EnableMongoHttpSession
	static class DefaultConfiguration extends BaseConfiguration {

	}

	@Configuration
	static class MongoConfiguration extends BaseConfiguration {

	}

	@Configuration
	@EnableMongoHttpSession(collectionName = COLLECTION_NAME)
	static class CustomCollectionNameConfiguration extends BaseConfiguration {

	}

	@Configuration
	@Import(MongoConfiguration.class)
	static class CustomCollectionNameSetConfiguration extends MongoHttpSessionConfiguration {

		CustomCollectionNameSetConfiguration() {
			setCollectionName(COLLECTION_NAME);
		}

	}

	@Configuration
	@EnableMongoHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class CustomMaxInactiveIntervalInSecondsConfiguration extends BaseConfiguration {

	}

	@Configuration
	@Import(MongoConfiguration.class)
	static class CustomMaxInactiveIntervalInSecondsSetConfiguration extends MongoHttpSessionConfiguration {

		CustomMaxInactiveIntervalInSecondsSetConfiguration() {
			setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		}

	}

	@Configuration
	@Import(MongoConfiguration.class)
	static class CustomSessionConverterConfiguration extends MongoHttpSessionConfiguration {

		@Bean
		public AbstractMongoSessionConverter mongoSessionConverter() {
			return mock(AbstractMongoSessionConverter.class);
		}

	}

	@Configuration
	@EnableMongoHttpSession(collectionName = "${session.mongo.collectionName}")
	static class CustomMongoJdbcSessionConfiguration extends BaseConfiguration {

		@Bean
		public PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
			return new PropertySourcesPlaceholderConfigurer();
		}

	}


    @EnableMongoHttpSession
    static class NoMongoOperationsConfiguration {

    }


    @EnableMongoHttpSession
    static class MultipleMongoOperationsConfiguration {

        @Bean
        public MongoOperations secondaryDataSource() {
            return mock(MongoOperations.class);
        }

    }


    @EnableMongoHttpSession
    static class PrimaryMongoOperationsConfiguration {

        @Bean
        @Primary
        public MongoOperations primaryMongoOperations() {
            MongoOperations mongoOperations = mock(MongoOperations.class);
            IndexOperations indexOperations = mock(IndexOperations.class);

            given(mongoOperations.indexOps(anyString())).willReturn(indexOperations);

            return mongoOperations;
        }

    }

    @EnableMongoHttpSession
    static class QualifiedMongoOperationsConfiguration {

        @Bean
        @SpringSessionMongoOperations
        public MongoOperations qualifiedMongoOperations() {
            MongoOperations mongoOperations = mock(MongoOperations.class);
            IndexOperations indexOperations = mock(IndexOperations.class);

            given(mongoOperations.indexOps(anyString())).willReturn(indexOperations);

            return mongoOperations;
        }

    }

    @EnableMongoHttpSession
    static class QualifiedAndPrimaryMongoConfiguration {

        @Bean
        @SpringSessionMongoOperations
        public MongoOperations qualifiedMongoOperations() {
            MongoOperations mongoOperations = mock(MongoOperations.class);
            IndexOperations indexOperations = mock(IndexOperations.class);

            given(mongoOperations.indexOps(anyString())).willReturn(indexOperations);

            return mongoOperations;
        }


        @Bean
        @Primary
        public MongoOperations primaryMongoOperations() {
            MongoOperations mongoOperations = mock(MongoOperations.class);
            IndexOperations indexOperations = mock(IndexOperations.class);

            given(mongoOperations.indexOps(anyString())).willReturn(indexOperations);

            return mongoOperations;
        }


    }

	@EnableMongoHttpSession
	static class SessionRepositoryCustomizerConfiguration {

		@Bean
		@Order(0)
		public SessionRepositoryCustomizer<MongoIndexedSessionRepository> sessionRepositoryCustomizerOne() {
			return sessionRepository -> sessionRepository.setMaxInactiveIntervalInSeconds(0);
		}

		@Bean
		@Order(1)
		public SessionRepositoryCustomizer<MongoIndexedSessionRepository> sessionRepositoryCustomizerTwo() {
			return sessionRepository -> sessionRepository.setMaxInactiveIntervalInSeconds(10000);
		}
	}

	@Configuration
	@EnableMongoHttpSession
	static class CustomIndexResolverConfigurationWithDefaultMongoSessionConverter {

		@Bean
		@SuppressWarnings("unchecked")
		public IndexResolver<MongoSession> indexResolver() {
			return mock(IndexResolver.class);
		}
	}

	@Configuration
	@EnableMongoHttpSession
	static class CustomIndexResolverConfigurationWithProvidedMongoSessionConverter {

		@Bean
		public AbstractMongoSessionConverter mongoSessionConverter() {
			return new JacksonMongoSessionConverter();
		}

		@Bean
		@SuppressWarnings("unchecked")
		public IndexResolver<MongoSession> indexResolver() {
			return mock(IndexResolver.class);
		}
	}
}
