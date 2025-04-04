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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.bson.types.Binary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.lang.Nullable;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.Assert;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * {@code AbstractMongoSessionConverter} implementation using standard Java serialization.
 *
 * @author Jakub Kubrynski
 * @author Rob Winch
 * @author Greg Turnquist
 * @since 1.2
 */
public class JdkMongoSessionConverter extends AbstractMongoSessionConverter {

	private String idFieldName = "_id";
	private String creationTimeFieldName = "created";
	private String lastAccessedTimeFieldName = "accessed";
	private String maxIntervalFieldName = "interval";
	private String attributesFieldName = "attr";
	private String principalFieldName = "principal";

	private final Converter<Object, byte[]> serializer;
	private final Converter<byte[], Object> deserializer;

	private Duration maxInactiveInterval;

	public JdkMongoSessionConverter(Duration maxInactiveInterval) {
		this(new SerializingConverter(), new DeserializingConverter(), maxInactiveInterval);
	}

	public JdkMongoSessionConverter(Converter<Object, byte[]> serializer, Converter<byte[], Object> deserializer,
			Duration maxInactiveInterval) {

		Assert.notNull(serializer, "serializer cannot be null");
		Assert.notNull(deserializer, "deserializer cannot be null");
		Assert.notNull(maxInactiveInterval, "maxInactiveInterval cannot be null");

		this.serializer = serializer;
		this.deserializer = deserializer;
		this.maxInactiveInterval = maxInactiveInterval;
	}

	@Override
	@Nullable
	public Query getQueryForIndex(String indexName, Object indexValue) {

		if (FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Query.query(Criteria.where(principalFieldName).is(indexValue));
		} else {
			return null;
		}
	}

	@Override
	protected DBObject convert(MongoSession session) {

		BasicDBObject basicDBObject = new BasicDBObject();

		basicDBObject.put(idFieldName, session.getId());
		basicDBObject.put(creationTimeFieldName, session.getCreationTime());
		basicDBObject.put(lastAccessedTimeFieldName, session.getLastAccessedTime());
		basicDBObject.put(maxIntervalFieldName, session.getMaxInactiveInterval());
		basicDBObject.put(principalFieldName, extractPrincipal(session));
		basicDBObject.put(getExpireAtFieldName(), session.getExpireAt());
		basicDBObject.put(attributesFieldName, serializeattributesFieldName(session));

		return basicDBObject;
	}

	@Override
	protected MongoSession convert(Document sessionWrapper) {

		Object maxInterval = sessionWrapper.getOrDefault(maxIntervalFieldName, this.maxInactiveInterval);

		Duration maxIntervalDuration = (maxInterval instanceof Duration) ? (Duration) maxInterval
				: Duration.parse(maxInterval.toString());

		MongoSession session = new MongoSession(sessionWrapper.getString(idFieldName),
				maxIntervalDuration.getSeconds());

		Object creationTime = sessionWrapper.get(creationTimeFieldName);
		if (creationTime instanceof Instant) {
			session.setCreationTime(((Instant) creationTime).toEpochMilli());
		} else if (creationTime instanceof Date) {
			session.setCreationTime(((Date) creationTime).getTime());
		}

		Object lastAccessedTime = sessionWrapper.get(lastAccessedTimeFieldName);
		if (lastAccessedTime instanceof Instant) {
			session.setLastAccessedTime((Instant) lastAccessedTime);
		} else if (lastAccessedTime instanceof Date) {
			session.setLastAccessedTime(Instant.ofEpochMilli(((Date) lastAccessedTime).getTime()));
		}

		session.setExpireAt((Date) sessionWrapper.get(getExpireAtFieldName()));

		deserializeattributesFieldName(sessionWrapper, session);

		return session;
	}

	@Nullable
	private byte[] serializeattributesFieldName(Session session) {

		Map<String, Object> attributesFieldName = new HashMap<>();

		for (String attrName : session.getAttributeNames()) {
			attributesFieldName.put(attrName, session.getAttribute(attrName));
		}

		return this.serializer.convert(attributesFieldName);
	}

	@SuppressWarnings("unchecked")
	private void deserializeattributesFieldName(Document sessionWrapper, Session session) {

		Object sessionattributesFieldName = sessionWrapper.get(attributesFieldName);

		byte[] attributesFieldNameBytes = (sessionattributesFieldName instanceof Binary
				? ((Binary) sessionattributesFieldName).getData()
				: (byte[]) sessionattributesFieldName);

		Map<String, Object> attributesFieldName = (Map<String, Object>) this.deserializer
				.convert(attributesFieldNameBytes);

		if (attributesFieldName != null) {
			for (Map.Entry<String, Object> entry : attributesFieldName.entrySet()) {
				session.setAttribute(entry.getKey(), entry.getValue());
			}
		}
	}

	public String getIdFieldName() {
		return idFieldName;
	}

	public void setIdFieldName(String idFieldName) {
		this.idFieldName = idFieldName;
	}

	public String getCreationTimeFieldName() {
		return creationTimeFieldName;
	}

	public void setCreationTimeFieldName(String creationTimeFieldName) {
		this.creationTimeFieldName = creationTimeFieldName;
	}

	public String getLastAccessedTimeFieldName() {
		return lastAccessedTimeFieldName;
	}

	public void setLastAccessedTimeFieldName(String lastAccessedTimeFieldName) {
		this.lastAccessedTimeFieldName = lastAccessedTimeFieldName;
	}

	public String getMaxIntervalFieldName() {
		return maxIntervalFieldName;
	}

	public void setMaxIntervalFieldName(String maxIntervalFieldName) {
		this.maxIntervalFieldName = maxIntervalFieldName;
	}

	public String getPrincipalFieldName() {
		return principalFieldName;
	}

	public void setPrincipalFieldName(String principalFieldName) {
		this.principalFieldName = principalFieldName;
	}

	public String getAttributesFieldName() {
		return attributesFieldName;
	}

	public void setAttributesFieldName(String attributesFieldName) {
		this.attributesFieldName = attributesFieldName;
	}

}
