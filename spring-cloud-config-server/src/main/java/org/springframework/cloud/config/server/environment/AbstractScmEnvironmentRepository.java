/*
 * Copyright 2015-2019 the original author or authors.
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

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.support.AbstractScmAccessor;
import org.springframework.cloud.config.server.support.AbstractScmAccessorProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author Dave Syer
 *
 */
public abstract class AbstractScmEnvironmentRepository extends AbstractScmAccessor
		implements EnvironmentRepository, SearchPathLocator, Ordered {

	/**
	 * 环境清理器
	 */
	private EnvironmentCleaner cleaner = new EnvironmentCleaner();

	private int order = Ordered.LOWEST_PRECEDENCE;

	public AbstractScmEnvironmentRepository(ConfigurableEnvironment environment) {
		super(environment);
	}

	public AbstractScmEnvironmentRepository(ConfigurableEnvironment environment,
			AbstractScmAccessorProperties properties) {
		super(environment, properties);
		this.order = properties.getOrder();
	}

	@Override
	public synchronized Environment findOne(String application, String profile, String label) {
		return findOne(application, profile, label, false);
	}

	@Override
	public synchronized Environment findOne(String application, String profile, String label, boolean includeOrigin) {
		// 创建NativeEnvironmentRepository对象
		NativeEnvironmentRepository delegate = new NativeEnvironmentRepository(getEnvironment(),
			new NativeEnvironmentProperties());
		// 获取Locations对象
		Locations locations = getLocations(application, profile, label);
		// 设置搜索路径
		delegate.setSearchLocations(locations.getLocations());
		// 创建环境对象
		Environment result = delegate.findOne(application, profile, "", includeOrigin);
		// 设置版本
		result.setVersion(locations.getVersion());
		// 设置标签
		result.setLabel(label);
		// 清理数据
		return this.cleaner.clean(result, getWorkingDirectory().toURI().toString(), getUri());
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

}
