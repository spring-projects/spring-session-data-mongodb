package org.springframework.session.data.mongo.config.annotation.web.http;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.*;

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
/**
 * Qualifier annotation for a {@link org.springframework.data.mongodb.core.MongoOperations} to be injected in
 * {@link org.springframework.session.data.mongo.MongoIndexedSessionRepository}.
 *
 * This will enable us to have multiple MongoOperations in the application.
 *
 * @author Visweshwar Ganesh
 * @since 2.2.0
 */
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE,
        ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface SpringSessionMongoOperations {

}