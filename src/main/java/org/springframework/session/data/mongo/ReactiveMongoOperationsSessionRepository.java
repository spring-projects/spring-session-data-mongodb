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

import org.bson.Document;
import reactor.core.publisher.Mono;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.session.ReactorSessionRepository;

/**
 * @author Greg Turnquist
 */
public class ReactiveMongoOperationsSessionRepository implements ReactorSessionRepository<MongoSession> {

	/**
	 * The default time period in seconds in which a session will expire.
	 */
	public static final int DEFAULT_INACTIVE_INTERVAL = 1800;

	/**
	 * the default collection name for storing session.
	 */
	public static final String DEFAULT_COLLECTION_NAME = "sessions";

	private final ReactiveMongoOperations mongoOperations;

	private AbstractMongoSessionConverter mongoSessionConverter = SessionConverterProvider.getDefaultMongoConverter();

	private Integer maxInactiveIntervalInSeconds = DEFAULT_INACTIVE_INTERVAL;
	private String collectionName = DEFAULT_COLLECTION_NAME;

	public ReactiveMongoOperationsSessionRepository(ReactiveMongoOperations mongoOperations) {
		this.mongoOperations = mongoOperations;
	}

	/**
	 * Creates a new {@link MongoSession} that is capable of being persisted by this
	 * {@link ReactorSessionRepository}.
	 * <p>
	 * This allows optimizations and customizations in how the {@link MongoSession} is
	 * persisted. For example, the implementation returned might keep track of the changes
	 * ensuring that only the delta needs to be persisted on a save.
	 * </p>
	 *
	 * @return a new {@link MongoSession} that is capable of being persisted by this
	 * {@link ReactorSessionRepository}
	 */
	@Override
	public Mono<MongoSession> createSession() {

		return Mono.justOrEmpty(this.maxInactiveIntervalInSeconds)
			.map(MongoSession::new)
			.switchIfEmpty(Mono.just(new MongoSession()));
	}

	/**
	 * Ensures the {@link MongoSession} created by
	 * {@link ReactorSessionRepository#createSession()} is saved.
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
			.switchIfEmpty(Mono.defer(() -> this.delete(id).then(Mono.empty())));
	}

	/**
	 * Deletes the {@link MongoSession} with the given {@link MongoSession#getId()} or does nothing
	 * if the {@link MongoSession} is not found.
	 *
	 * @param id the {@link MongoSession#getId()} to delete
	 */
	@Override
	public Mono<Void> delete(String id) {
		return this.mongoOperations.remove(findSession(id), this.collectionName).then();
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

	public void setCollectionName(String collectionName) {
		this.collectionName = collectionName;
	}
	
}
