/*
 * Copyright 2014-2017 the original author or authors.
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

package org.springframework.session.data.mongo;

import static org.springframework.session.data.mongo.MongoSessionUtils.*;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;

import com.mongodb.DBObject;

/**
 * Session repository implementation which stores sessions in Mongo. Uses
 * {@link AbstractMongoSessionConverter} to transform session objects from/to native Mongo
 * representation ({@code DBObject}).
 *
 * Repository is also responsible for removing expired sessions from database. Cleanup is
 * done every minute.
 *
 * @author Jakub Kubrynski
 * @author Greg Turnquist
 * @since 1.2
 */
public class MongoOperationsSessionRepository
		implements FindByIndexNameSessionRepository<MongoSession>, ApplicationEventPublisherAware, InitializingBean {

	/**
	 * The default time period in seconds in which a session will expire.
	 */
	public static final int DEFAULT_INACTIVE_INTERVAL = 1800;

	/**
	 * the default collection name for storing session.
	 */
	public static final String DEFAULT_COLLECTION_NAME = "sessions";

	private static final Logger logger = LoggerFactory.getLogger(MongoOperationsSessionRepository.class);

	private final MongoOperations mongoOperations;

	private Integer maxInactiveIntervalInSeconds = DEFAULT_INACTIVE_INTERVAL;
	private String collectionName = DEFAULT_COLLECTION_NAME;
	private AbstractMongoSessionConverter mongoSessionConverter = new JdkMongoSessionConverter(
		Duration.ofSeconds(this.maxInactiveIntervalInSeconds));
	private ApplicationEventPublisher eventPublisher;

	public MongoOperationsSessionRepository(MongoOperations mongoOperations) {
		this.mongoOperations = mongoOperations;
	}

	@Override
	public MongoSession createSession() {

		MongoSession session = new MongoSession();

		if (this.maxInactiveIntervalInSeconds != null) {
			session.setMaxInactiveInterval(Duration.ofSeconds(this.maxInactiveIntervalInSeconds));
		}

		publishEvent(new SessionCreatedEvent(this, session));
		
		return session;
	}

	@Override
	public void save(MongoSession session) {

		DBObject sessionDbObject = convertToDBObject(this.mongoSessionConverter, session);
		this.mongoOperations.save(sessionDbObject, this.collectionName);
	}

	@Override
	public MongoSession findById(String id) {

		Document sessionWrapper = findSession(id);

		if (sessionWrapper == null) {
			return null;
		}

		MongoSession session = convertToSession(this.mongoSessionConverter, sessionWrapper);

		if (session.isExpired()) {
			publishEvent(new SessionExpiredEvent(this, session));
			deleteById(id);
			return null;
		}
		
		return session;
	}

	/**
	 * Currently this repository allows only querying against
	 * {@code PRINCIPAL_NAME_INDEX_NAME}.
	 *
	 * @param indexName the name if the index (i.e.
	 * {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME})
	 * @param indexValue the value of the index to search for.
	 * @return sessions map
	 */
	@Override
	public Map<String, MongoSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {

		HashMap<String, MongoSession> result = new HashMap<String, MongoSession>();

		Query query = this.mongoSessionConverter.getQueryForIndex(indexName, indexValue);

		if (query == null) {
			return Collections.emptyMap();
		}

		List<Document> mapSessions = this.mongoOperations.find(query, Document.class, this.collectionName);

		for (Document dbSession : mapSessions) {
			MongoSession mapSession = convertToSession(this.mongoSessionConverter, dbSession);
			result.put(mapSession.getId(), mapSession);
		}
		
		return result;
	}

	@Override
	public void deleteById(String id) {
		Optional.ofNullable(findSession(id))
			.ifPresent(document -> {
				publishEvent(new SessionDeletedEvent(this, convertToSession(this.mongoSessionConverter, document)));
				this.mongoOperations.remove(document, this.collectionName);
			});
	}

	@Override
	public void afterPropertiesSet() {

		IndexOperations indexOperations = this.mongoOperations.indexOps(this.collectionName);
		this.mongoSessionConverter.ensureIndexes(indexOperations);
	}

	Document findSession(String id) {
		return this.mongoOperations.findById(id, Document.class, this.collectionName);
	}

	public void setMongoSessionConverter(AbstractMongoSessionConverter mongoSessionConverter) {
		this.mongoSessionConverter = mongoSessionConverter;
	}

	public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	private void publishEvent(ApplicationEvent event) {
		try {
			this.eventPublisher.publishEvent(event);
		}
		catch (Throwable ex) {
			logger.error("Error publishing " + event + ".", ex);
		}
	}

}
