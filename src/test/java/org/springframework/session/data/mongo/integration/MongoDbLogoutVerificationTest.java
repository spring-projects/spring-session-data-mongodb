/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.session.data.mongo.integration;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import java.io.IOException;
import java.net.URI;

import de.flapdoodle.embed.mongo.MongodExecutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.test.StepVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.session.data.mongo.config.annotation.web.reactive.EnableMongoWebSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.SocketUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.BodyInserters;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;

/**
 * @author Greg Turnquist
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration
public class MongoDbLogoutVerificationTest {

	@Autowired ApplicationContext ctx;

	WebTestClient client;

	@BeforeEach
	void setUp() {
		this.client = WebTestClient.bindToApplicationContext(this.ctx).build();
	}

	@Test
	void logoutShouldDeleteOldSessionIdFromMongoDB() {

		// 1. `curl -i -v -X POST --data "username=admin&password=password" localhost:8080/login` - Save SESSION cookie and
		// use it it nex step as {cookie-value-1}

		FluxExchangeResult<String> loginResult = this.client.post().uri("/login")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED) //
				.body(BodyInserters //
						.fromFormData("username", "admin") //
						.with("password", "password")) //
				.exchange() //
				.returnResult(String.class);

		assertThat(loginResult.getResponseHeaders().getLocation()).isEqualTo(URI.create("/"));

		String originalSessionId = loginResult.getResponseCookies().getFirst("SESSION").getValue();

		// 2. `curl -i -L -v -X GET --cookie "SESSION=48eb6ab2-2c08-43b7-a303-46099bfef231" localhost:8080/hello` - response
		// status will be 200, body will be "HelloWorld"

		this.client.get().uri("/hello") //
				.cookie("SESSION", originalSessionId) //
				.exchange() //
				.expectStatus().isOk() //
				.returnResult(String.class).getResponseBody() //
				.as(StepVerifier::create) //
				.expectNext("HelloWorld") //
				.verifyComplete();

		// 3. `curl -i -L -v -X POST --cookie "SESSION=48eb6ab2-2c08-43b7-a303-46099bfef231" localhost:8080/logout` - Save
		// SESSION cookie and use it it nex step as {cookie-value-2}

		String newSessionId = this.client.post().uri("/logout") //
				.cookie("SESSION", originalSessionId) //
				.exchange() //
				.expectStatus().isFound() //
				.returnResult(String.class)
				.getResponseCookies().getFirst("SESSION").getValue();

		assertThat(newSessionId).isNotEqualTo(originalSessionId);

		// 4. `curl -i -L -v -X GET --cookie "SESSION=3b20200c-cf5e-4529-b3af-3c37ed365f5a" localhost:8080/hello` - response
		// status will be 302, body will be empty

		this.client.get().uri("/hello") //
				.cookie("SESSION", newSessionId) //
				.exchange() //
				.expectStatus().isFound() //
				.expectHeader().value(HttpHeaders.LOCATION, value -> assertThat(value).isEqualTo("/login"));

		// 5. `curl -i -L -v -X GET --cookie "SESSION=48eb6ab2-2c08-43b7-a303-46099bfef231" localhost:8080/hello` - response
		// status will be 200, body will be "HelloWorld", but it should be the same as step 4

		this.client.get().uri("/hello") //
				.cookie("SESSION", originalSessionId) //
				.exchange() //
				.expectStatus().isFound() //
				.expectHeader().value(HttpHeaders.LOCATION, value -> assertThat(value).isEqualTo("/login"));
	}

	@RestController
	static class TestController {

		@GetMapping("/hello")
		public ResponseEntity<String> hello() {
			return ResponseEntity.ok("HelloWorld");
		}

	}

	@EnableWebFluxSecurity
	static class SecurityConfig {

		@Bean
		public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

			return http //
					.logout()//
					/**/.and() //
					.formLogin() //
					/**/.and() //
					.csrf().disable() //
					.authorizeExchange() //
					.anyExchange().authenticated() //
					/**/.and() //
					.build();
		}

		@Bean
		public MapReactiveUserDetailsService userDetailsService() {

			return new MapReactiveUserDetailsService(User.withDefaultPasswordEncoder() //
					.username("admin") //
					.password("password") //
					.roles("USER,ADMIN") //
					.build());
		}
	}

	@Configuration
	@EnableWebFlux
	@EnableMongoWebSession
	static class Config {

		private int embeddedMongoPort = SocketUtils.findAvailableTcpPort();

		@Bean(initMethod = "start", destroyMethod = "stop")
		public MongodExecutable embeddedMongoServer() throws IOException {
			return MongoITestUtils.embeddedMongoServer(this.embeddedMongoPort);
		}

		@Bean
		public ReactiveMongoOperations mongoOperations(MongodExecutable embeddedMongoServer) {

			MongoClient mongo = MongoClients.create("mongodb://localhost:" + this.embeddedMongoPort);
			return new ReactiveMongoTemplate(mongo, "test");
		}

		@Bean
		TestController controller() {
			return new TestController();
		}
	}
}
