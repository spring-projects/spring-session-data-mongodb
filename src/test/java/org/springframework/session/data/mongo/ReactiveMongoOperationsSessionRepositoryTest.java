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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.result.DeleteResult;

/**
 * Tests for {@link ReactiveMongoOperationsSessionRepository}.
 *
 * @author Jakub Kubrynski
 * @author Vedran Pavic
 * @author Greg Turnquist
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ReactiveMongoOperationsSessionRepositoryTest {

	@Mock
	private AbstractMongoSessionConverter converter;

	@Mock
	private ReactiveMongoOperations mongoOperations;

	private ReactiveMongoOperationsSessionRepository repository;

	@Before
	public void setUp() throws Exception {
		
		this.repository = new ReactiveMongoOperationsSessionRepository(this.mongoOperations);
		this.repository.setMongoSessionConverter(this.converter);
	}

	@Test
	public void shouldCreateSession() throws Exception {

		// when
		Mono<MongoSession> session = this.repository.createSession();

		// then
		StepVerifier.create(session)
			.expectNextMatches(mongoSession -> {
				assertThat(mongoSession.getId()).isNotEmpty();
				assertThat(mongoSession.getMaxInactiveInterval().getSeconds())
					.isEqualTo(ReactiveMongoOperationsSessionRepository.DEFAULT_INACTIVE_INTERVAL);
				return true;
			});
	}

	@Test
	public void shouldCreateSessionWhenMaxInactiveIntervalNotDefined() throws Exception {

		// when
		this.repository.setMaxInactiveIntervalInSeconds(null);
		Mono<MongoSession> session = this.repository.createSession();

		// then
		StepVerifier.create(session)
			.expectNextMatches(mongoSession -> {
				assertThat(mongoSession.getId()).isNotEmpty();
				assertThat(mongoSession.getMaxInactiveInterval().getSeconds())
					.isEqualTo(ReactiveMongoOperationsSessionRepository.DEFAULT_INACTIVE_INTERVAL);
				return true;
			});
	}

	@Test
	public void shouldSaveSession() throws Exception {

		// given
		MongoSession session = new MongoSession();
		BasicDBObject dbSession = new BasicDBObject();

		given(this.converter.convert(session,
				TypeDescriptor.valueOf(MongoSession.class),
				TypeDescriptor.valueOf(DBObject.class))).willReturn(dbSession);

		given(this.mongoOperations.save(dbSession, "sessions")).willReturn(Mono.just(dbSession));

		// when
		StepVerifier.create(this.repository.save(session))
			.expectNextMatches(aVoid -> {
				// then
				verify(this.mongoOperations).save(dbSession, ReactiveMongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME);
				return true;
			});
	}

	@Test
	public void shouldGetSession() throws Exception {

		// given
		String sessionId = UUID.randomUUID().toString();
		Document sessionDocument = new Document();

		given(this.mongoOperations.findById(sessionId, Document.class,
			ReactiveMongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME)).willReturn(Mono.just(sessionDocument));

		MongoSession session = new MongoSession();

		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
				TypeDescriptor.valueOf(MongoSession.class))).willReturn(session);

		// when
		StepVerifier.create(this.repository.findById(sessionId))
			.expectNextMatches(retrievedSession -> {
				// then
				assertThat(retrievedSession).isEqualTo(session);
				return true;
			});
	}

	@Test
	public void shouldHandleExpiredSession() throws Exception {

		// given
		String sessionId = UUID.randomUUID().toString();
		Document sessionDocument = new Document();

		given(this.mongoOperations.findById(sessionId, Document.class,
			ReactiveMongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME)).willReturn(Mono.just(sessionDocument));

		MongoSession session = mock(MongoSession.class);

		given(session.isExpired()).willReturn(true);
		given(this.converter.convert(sessionDocument, TypeDescriptor.valueOf(Document.class),
			TypeDescriptor.valueOf(MongoSession.class))).willReturn(session);

		// when
		StepVerifier.create(this.repository.findById(sessionId))
			.expectNextMatches(mongoSession -> {
				// then
				verify(this.mongoOperations).remove(any(Document.class),
					eq(ReactiveMongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME));
				return true;
			});

	}

	@Test
	public void shouldDeleteSession() throws Exception {
		
		// given
		String sessionId = UUID.randomUUID().toString();

		Document sessionDocument = new Document();

		given(this.mongoOperations.findById(eq(sessionId), eq(Document.class),
			eq(ReactiveMongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME))).willReturn(Mono.just(sessionDocument));
		given(this.mongoOperations.remove((Mono<? extends Object>) any(), eq("sessions"))).willReturn(Mono.just(DeleteResult.acknowledged(1)));

		// when
		StepVerifier.create(this.repository.deleteById(sessionId))
			.expectNextMatches(aVoid -> {
				// then
				verify(this.mongoOperations).remove(any(Document.class),
					eq(ReactiveMongoOperationsSessionRepository.DEFAULT_COLLECTION_NAME));
				return true;
			});
	}
}
