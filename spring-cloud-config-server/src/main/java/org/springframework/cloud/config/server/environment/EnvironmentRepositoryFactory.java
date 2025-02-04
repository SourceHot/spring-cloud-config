/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;

/**
 * 环境操作库生成工厂
 * @param <T> {@link EnvironmentRepository} type
 * @param <P> {@link EnvironmentRepositoryProperties} type
 * @author Dylan Roberts
 */
public interface EnvironmentRepositoryFactory<T extends EnvironmentRepository, P extends EnvironmentRepositoryProperties> {

	T build(P environmentProperties) throws Exception;

}
