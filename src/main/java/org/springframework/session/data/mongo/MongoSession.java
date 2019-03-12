/*
 * Copyright 2014-2016 the original author or authors.
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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.session.Session;

/**
 * Session object providing additional information about the datetime of expiration.
 *
 * @author Jakub Kubrynski
 * @author Greg Turnquist
 * @since 1.2
 */
@EqualsAndHashCode(of = { "id" })
public class MongoSession implements Session {

	/**
	 * Mongo doesn't support {@literal dot} in field names. We replace it with a very rarely used character
	 */
	private static final char DOT_COVER_CHAR = '\uF607';

	@Getter private String id;
	private long createdMillis = System.currentTimeMillis();
	private long accessedMillis;
	private long intervalSeconds;
	@Getter @Setter private Date expireAt;
	private Map<String, Object> attrs = new HashMap<>();

	public MongoSession() {
		this(MongoOperationsSessionRepository.DEFAULT_INACTIVE_INTERVAL);
	}

	public MongoSession(long maxInactiveIntervalInSeconds) {
		this(UUID.randomUUID().toString(), maxInactiveIntervalInSeconds);
	}

	public MongoSession(String id, long maxInactiveIntervalInSeconds) {

		this.id = id;
		this.intervalSeconds = maxInactiveIntervalInSeconds;
		setLastAccessedTime(Instant.ofEpochMilli(this.createdMillis));
	}

	static String coverDot(String attributeName) {
		return attributeName.replace('.', DOT_COVER_CHAR);
	}

	static String uncoverDot(String attributeName) {
		return attributeName.replace(DOT_COVER_CHAR, '.');
	}

	public String changeSessionId() {

		String changedId = UUID.randomUUID().toString();
		this.id = changedId;
		return changedId;
	}

	@Override
	public <T> T getAttribute(String attributeName) {
		return (T) this.attrs.get(coverDot(attributeName));
	}

	public Set<String> getAttributeNames() {

		return this.attrs.keySet().stream().map(MongoSession::uncoverDot).collect(Collectors.toSet());
	}

	public void setAttribute(String attributeName, Object attributeValue) {

		if (attributeValue == null) {
			removeAttribute(coverDot(attributeName));
		} else {
			this.attrs.put(coverDot(attributeName), attributeValue);
		}
	}

	public void removeAttribute(String attributeName) {
		this.attrs.remove(coverDot(attributeName));
	}

	public Instant getCreationTime() {
		return Instant.ofEpochMilli(this.createdMillis);
	}

	public void setCreationTime(long created) {
		this.createdMillis = created;
	}

	public Instant getLastAccessedTime() {
		return Instant.ofEpochMilli(this.accessedMillis);
	}

	public void setLastAccessedTime(Instant lastAccessedTime) {

		this.accessedMillis = lastAccessedTime.toEpochMilli();
		this.expireAt = Date.from(lastAccessedTime.plus(Duration.ofSeconds(this.intervalSeconds)));
	}

	public Duration getMaxInactiveInterval() {
		return Duration.ofSeconds(this.intervalSeconds);
	}

	public void setMaxInactiveInterval(Duration interval) {
		this.intervalSeconds = interval.getSeconds();
	}

	public boolean isExpired() {
		return this.intervalSeconds >= 0 && new Date().after(this.expireAt);
	}
}
