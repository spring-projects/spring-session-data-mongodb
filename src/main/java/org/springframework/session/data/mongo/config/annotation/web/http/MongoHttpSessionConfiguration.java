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
package org.springframework.session.data.mongo.config.annotation.web.http;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.session.IndexResolver;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.mongo.AbstractMongoSessionConverter;
import org.springframework.session.data.mongo.JdkMongoSessionConverter;
import org.springframework.session.data.mongo.MongoIndexedSessionRepository;
import org.springframework.session.data.mongo.MongoSession;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Configuration class registering {@code MongoSessionRepository} bean. To import this configuration use
 * {@link EnableMongoHttpSession} annotation.
 *
 * @author Jakub Kubrynski
 * @author Eddú Meléndez
 * @since 1.2
 */
@Configuration(proxyBeanMethods = false)
public class MongoHttpSessionConfiguration extends SpringHttpSessionConfiguration
		implements BeanClassLoaderAware, EmbeddedValueResolverAware, ImportAware {

	private AbstractMongoSessionConverter mongoSessionConverter;
	private Integer maxInactiveIntervalInSeconds;
	private String collectionName;
	private StringValueResolver embeddedValueResolver;
	private List<SessionRepositoryCustomizer<MongoIndexedSessionRepository>> sessionRepositoryCustomizers;
	private ClassLoader classLoader;
	private IndexResolver<MongoSession> indexResolver;
    private MongoOperations mongoOperations;

    @Autowired
    public void setMongoOperations(
            @SpringSessionMongoOperations ObjectProvider<MongoOperations> springSessionMongoOperations,
            ObjectProvider<MongoOperations> mongoOperations) {
        MongoOperations mongoOperationsToUse = springSessionMongoOperations.getIfAvailable();
        if (mongoOperationsToUse == null) {
            mongoOperationsToUse = mongoOperations.getObject();
        }
        this.mongoOperations = mongoOperationsToUse;
    }

	@Bean
	public MongoIndexedSessionRepository mongoSessionRepository() {

		MongoIndexedSessionRepository repository = new MongoIndexedSessionRepository(mongoOperations);
		repository.setMaxInactiveIntervalInSeconds(this.maxInactiveIntervalInSeconds);

		if (this.mongoSessionConverter != null) {
			repository.setMongoSessionConverter(this.mongoSessionConverter);

			if (this.indexResolver != null) {
				this.mongoSessionConverter.setIndexResolver(this.indexResolver);
			}
		} else {
			JdkMongoSessionConverter mongoSessionConverter = new JdkMongoSessionConverter(new SerializingConverter(),
					new DeserializingConverter(this.classLoader),
					Duration.ofSeconds(MongoIndexedSessionRepository.DEFAULT_INACTIVE_INTERVAL));

			if (this.indexResolver != null) {
				mongoSessionConverter.setIndexResolver(this.indexResolver);
			}

			repository.setMongoSessionConverter(mongoSessionConverter);
		}

		if (StringUtils.hasText(this.collectionName)) {
			repository.setCollectionName(this.collectionName);
		}

		this.sessionRepositoryCustomizers
				.forEach(sessionRepositoryCustomizer -> sessionRepositoryCustomizer.customize(repository));

		return repository;
	}

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
        this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
    }

    public void setImportMetadata(AnnotationMetadata importMetadata) {

        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importMetadata.getAnnotationAttributes(EnableMongoHttpSession.class.getName()));

		if (attributes != null) {
			this.maxInactiveIntervalInSeconds = attributes.getNumber("maxInactiveIntervalInSeconds");
		} else {
			this.maxInactiveIntervalInSeconds = MongoIndexedSessionRepository.DEFAULT_INACTIVE_INTERVAL;
		}

        String collectionNameValue = attributes != null ? attributes.getString("collectionName") : "";
        if (StringUtils.hasText(collectionNameValue)) {
            this.collectionName = this.embeddedValueResolver.resolveStringValue(collectionNameValue);
        }
    }

    @Autowired(required = false)
    public void setMongoSessionConverter(AbstractMongoSessionConverter mongoSessionConverter) {
        this.mongoSessionConverter = mongoSessionConverter;
    }

	@Autowired(required = false)
	public void setSessionRepositoryCustomizers(
			ObjectProvider<SessionRepositoryCustomizer<MongoIndexedSessionRepository>> sessionRepositoryCustomizers) {
		this.sessionRepositoryCustomizers = sessionRepositoryCustomizers.orderedStream().collect(Collectors.toList());
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.embeddedValueResolver = resolver;
    }

	@Autowired(required = false)
	public void setIndexResolver(IndexResolver<MongoSession> indexResolver) {
		this.indexResolver = indexResolver;
	}
}
