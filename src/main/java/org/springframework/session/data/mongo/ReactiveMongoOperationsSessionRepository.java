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
package org.springframework.session.data.mongo;

import static org.springframework.session.data.mongo.MongoSessionUtils.*;

import java.time.Duration;

import javax.annotation.PostConstruct;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;

/**
 * @author Greg Turnquist
 */
public class ReactiveMongoOperationsSessionRepository
	implements ReactiveSessionRepository<MongoSession>, ApplicationEventPublisherAware {

	/**
	 * The default time period in seconds in which a session will expire.
	 */
	public static final int DEFAULT_INACTIVE_INTERVAL = 1800;

	/**
	 * The default collection name for storing session.
	 */
	public static final String DEFAULT_COLLECTION_NAME = "sessions";

	private static final Logger logger = LoggerFactory.getLogger(ReactiveMongoOperationsSessionRepository.class);

	private final ReactiveMongoOperations mongoOperations;

	private Integer maxInactiveIntervalInSeconds = DEFAULT_INACTIVE_INTERVAL;
	private String collectionName = DEFAULT_COLLECTION_NAME;
	private AbstractMongoSessionConverter mongoSessionConverter = new JdkMongoSessionConverter(
		Duration.ofSeconds(this.maxInactiveIntervalInSeconds));

	private MongoOperations blockingMongoOperations;
	private ApplicationEventPublisher eventPublisher;

	public ReactiveMongoOperationsSessionRepository(ReactiveMongoOperations mongoOperations) {
		this.mongoOperations = mongoOperations;
	}

	/**
	 * Creates a new {@link MongoSession} that is capable of being persisted by this
	 * {@link ReactiveSessionRepository}.
	 * <p>
	 * This allows optimizations and customizations in how the {@link MongoSession} is
	 * persisted. For example, the implementation returned might keep track of the changes
	 * ensuring that only the delta needs to be persisted on a save.
	 * </p>
	 *
	 * @return a new {@link MongoSession} that is capable of being persisted by this
	 * {@link ReactiveSessionRepository}
	 */
	@Override
	public Mono<MongoSession> createSession() {

		return Mono.justOrEmpty(this.maxInactiveIntervalInSeconds)
			.map(MongoSession::new)
			.map(mongoSession -> {
				publishEvent(new SessionCreatedEvent(this, mongoSession));
				return mongoSession;
			})
			.switchIfEmpty(Mono.just(new MongoSession()));
	}

	/**
	 * Ensures the {@link MongoSession} created by
	 * {@link ReactiveSessionRepository#createSession()} is saved.
	 * <p>
	 * Some implementations may choose to save as the {@link MongoSession} is updated by
	 * returning a {@link MongoSession} that immediately persists any changes. In this case,
	 * this method may not actually do anything.
	 * </p>
	 *
	 * @param session the {@link MongoSession} to save
	 */
	@Override
	public Mono<Void> save(MongoSession session) {

		return this.mongoOperations
			.save(convertToDBObject(this.mongoSessionConverter, session), this.collectionName)
			.then();
	}

	/**
	 * Gets the {@link MongoSession} by the {@link MongoSession#getId()} or null if no
	 * {@link MongoSession} is found.
	 *
	 * @param id the {@link MongoSession#getId()} to lookup
	 * @return the {@link MongoSession} by the {@link MongoSession#getId()} or null if no
	 * {@link MongoSession} is found.
	 */
	@Override
	public Mono<MongoSession> findById(String id) {

		return findSession(id)
			.map(document -> convertToSession(this.mongoSessionConverter, document))
			.filter(mongoSession -> !mongoSession.isExpired())
			.switchIfEmpty(Mono.defer(() -> this.deleteById(id).then(Mono.empty())));
	}

	/**
	 * Deletes the {@link MongoSession} with the given {@link MongoSession#getId()} or does nothing
	 * if the {@link MongoSession} is not found.
	 *
	 * @param id the {@link MongoSession#getId()} to delete
	 */
	@Override
	public Mono<Void> deleteById(String id) {

		return findSession(id)
			.flatMap(document -> this.mongoOperations.remove(document, this.collectionName).then(Mono.just(document)))
			.map(document -> convertToSession(this.mongoSessionConverter, document))
			.map(mongoSession -> Mono.fromRunnable(() -> publishEvent(new SessionDeletedEvent(this, mongoSession))))
			.then();
	}

	/**
	 * Do not use {@link org.springframework.data.mongodb.core.index.ReactiveIndexOperations} to ensure indexes exist.
	 * Instead, get a blocking {@link IndexOperations} and use that instead, if possible.
	 */
	@PostConstruct
	public void ensureIndexesAreCreated() {

		if (this.blockingMongoOperations != null) {
			IndexOperations indexOperations = this.blockingMongoOperations.indexOps(this.collectionName);
			this.mongoSessionConverter.ensureIndexes(indexOperations);
		}
	}

	private Mono<Document> findSession(String id) {
		return this.mongoOperations.findById(id, Document.class, this.collectionName);
	}

	public void setMongoSessionConverter(AbstractMongoSessionConverter mongoSessionConverter) {
		this.mongoSessionConverter = mongoSessionConverter;
	}

	public void setMaxInactiveIntervalInSeconds(Integer maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	public Integer getMaxInactiveIntervalInSeconds() {
		return maxInactiveIntervalInSeconds;
	}

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}

	public String getCollectionName() {
		return collectionName;
	}

	public MongoOperations getBlockingMongoOperations() {
		return this.blockingMongoOperations;
	}

	public void setBlockingMongoOperations(MongoOperations blockingMongoOperations) {
		this.blockingMongoOperations = blockingMongoOperations;
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
