/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.OrderComparator;

/**
 * An {@link EnvironmentRepository} composed of multiple ordered
 * {@link EnvironmentRepository}s.
 *
 * @author Ryan Baxter
 */
public class CompositeEnvironmentRepository implements EnvironmentRepository {

	Log log = LogFactory.getLog(getClass());

	protected List<EnvironmentRepository> environmentRepositories;

	private boolean failOnError;

	/**
	 * Creates a new {@link CompositeEnvironmentRepository}.
	 * @param environmentRepositories The list of {@link EnvironmentRepository}s to create
	 * the composite from.
	 * @param failOnError whether to throw an exception if there is an error.
	 */
	public CompositeEnvironmentRepository(List<EnvironmentRepository> environmentRepositories, boolean failOnError) {
		// Sort the environment repositories by the priority
		Collections.sort(environmentRepositories, OrderComparator.INSTANCE);
		this.environmentRepositories = environmentRepositories;
		this.failOnError = failOnError;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		return findOne(application, profile, label, false);
	}

	@Override
	public Environment findOne(String application, String profile, String label, boolean includeOrigin) {
		// 创建环境对象
		Environment env = new Environment(application, new String[]{profile}, label, null, null);
		// 如果成员变量environmentRepositories数据为1直接获取单个元素进行搜索
		if (this.environmentRepositories.size() == 1) {
			Environment envRepo = this.environmentRepositories.get(0).findOne(application, profile, label,
				includeOrigin);
			env.addAll(envRepo.getPropertySources());
			env.setVersion(envRepo.getVersion());
			env.setState(envRepo.getState());
		} else {
			// 循环搜索，将单个搜索结果放入到环境对象中
			for (EnvironmentRepository repo : environmentRepositories) {
				try {
					env.addAll(repo.findOne(application, profile, label, includeOrigin).getPropertySources());
				} catch (Exception e) {
					if (failOnError) {
						throw e;
					} else {
						log.info("Error adding environment for " + repo);
					}
				}
			}
		}
		return env;
	}

}
