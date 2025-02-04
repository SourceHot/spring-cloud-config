/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.client;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.cloud.config.client.ConfigServerBootstrapper.LoaderInterceptor;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.ClassUtils;

/**
 * Bootstrapper.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class ConfigClientRetryBootstrapper implements BootstrapRegistryInitializer {

	static final boolean RETRY_IS_PRESENT = ClassUtils.isPresent("org.springframework.retry.annotation.Retryable",
			null);

	@Override
	public void initialize(BootstrapRegistry registry) {
		// 不存在Retryable类结束处理
		if (!RETRY_IS_PRESENT) {
			return;
		}

		// 通过引导注册器注册LoaderInterceptor类
		registry.registerIfAbsent(LoaderInterceptor.class, context -> loadContext -> {
			ConfigServerConfigDataResource resource = loadContext.getResource();
			if (resource.getProperties().isFailFast()) {
				RetryProperties properties = resource.getRetryProperties();
				// 创建重试模板对象
				RetryTemplate retryTemplate = RetryTemplateFactory.create(properties, resource.getLog());
				// 发送
				return retryTemplate.execute(
						retryContext -> loadContext.getInvocation().apply(loadContext.getLoaderContext(), resource));
			}
			return loadContext.getInvocation().apply(loadContext.getLoaderContext(), resource);
		});

	}

}
