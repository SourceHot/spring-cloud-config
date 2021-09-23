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

import java.util.Optional;

import org.eclipse.jgit.transport.HttpTransport;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.support.TransportConfigCallbackFactory;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author Dylan Roberts
 */
public class MultipleJGitEnvironmentRepositoryFactory
		implements EnvironmentRepositoryFactory<MultipleJGitEnvironmentRepository, MultipleJGitEnvironmentProperties> {

	private ConfigurableEnvironment environment;

	private ConfigServerProperties server;

	private Optional<ConfigurableHttpConnectionFactory> connectionFactory;

	private final TransportConfigCallbackFactory transportConfigCallbackFactory;

	@Deprecated
	public MultipleJGitEnvironmentRepositoryFactory(ConfigurableEnvironment environment, ConfigServerProperties server,
			TransportConfigCallbackFactory transportConfigCallbackFactory) {
		this(environment, server, Optional.empty(), transportConfigCallbackFactory);
	}

	public MultipleJGitEnvironmentRepositoryFactory(ConfigurableEnvironment environment, ConfigServerProperties server,
			Optional<ConfigurableHttpConnectionFactory> connectionFactory,
			TransportConfigCallbackFactory transportConfigCallbackFactory) {
		this.environment = environment;
		this.server = server;
		this.connectionFactory = connectionFactory;
		this.transportConfigCallbackFactory = transportConfigCallbackFactory;
	}

	@Override
	public MultipleJGitEnvironmentRepository build(MultipleJGitEnvironmentProperties environmentProperties)
			throws Exception {
		// 确认ConfigurableHttpConnectionFactory存在
		if (this.connectionFactory.isPresent()) {
			// 设置工厂
			HttpTransport.setConnectionFactory(this.connectionFactory.get());
			// 添加环境配置
			this.connectionFactory.get().addConfiguration(environmentProperties);
		}

		// 创建git环境操作库
		MultipleJGitEnvironmentRepository repository = new MultipleJGitEnvironmentRepository(this.environment,
			environmentProperties);
		// 设置回调
		repository.setTransportConfigCallback(transportConfigCallbackFactory.build(environmentProperties));
		if (this.server.getDefaultLabel() != null) {
			repository.setDefaultLabel(this.server.getDefaultLabel());
		}
		return repository;
	}

}
