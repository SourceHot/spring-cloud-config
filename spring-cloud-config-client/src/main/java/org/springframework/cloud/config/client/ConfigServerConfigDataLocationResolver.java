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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;

import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.core.Ordered;
import org.springframework.core.log.LogMessage;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.CONFIG_DISCOVERY_ENABLED;

public class ConfigServerConfigDataLocationResolver
		implements ConfigDataLocationResolver<ConfigServerConfigDataResource>, Ordered {

	/**
	 * Prefix for Config Server imports.
	 */
	public static final String PREFIX = "configserver:";

	private final Log log;

	public ConfigServerConfigDataLocationResolver(Log log) {
		this.log = log;
	}

	@Override
	public int getOrder() {
		return -1;
	}

	protected PropertyHolder loadProperties(ConfigDataLocationResolverContext context, String uris) {
		Binder binder = context.getBinder();
		BindHandler bindHandler = getBindHandler(context);
		ConfigClientProperties configClientProperties = binder
				.bind(ConfigClientProperties.PREFIX, Bindable.of(ConfigClientProperties.class), bindHandler)
				.orElseGet(ConfigClientProperties::new);
		if (!StringUtils.hasText(configClientProperties.getName())) {
			// default to spring.application.name if name isn't set
			String applicationName = binder.bind("spring.application.name", Bindable.of(String.class), bindHandler)
					.orElse("application");
			configClientProperties.setName(applicationName);
		}

		PropertyHolder holder = new PropertyHolder();
		holder.properties = configClientProperties;
		// bind retry, override later
		holder.retryProperties = binder.bind(RetryProperties.PREFIX, RetryProperties.class)
				.orElseGet(RetryProperties::new);

		if (StringUtils.hasText(uris)) {
			String[] uri = StringUtils.commaDelimitedListToStringArray(uris);
			String paramStr = null;
			for (int i = 0; i < uri.length; i++) {
				int paramIdx = uri[i].indexOf('?');
				if (paramIdx > 0) {
					if (i == 0) {
						// only gather params from first uri
						paramStr = uri[i].substring(paramIdx + 1);
					}
					uri[i] = uri[i].substring(0, paramIdx);
				}
			}
			if (StringUtils.hasText(paramStr)) {
				Properties properties = StringUtils
						.splitArrayElementsIntoProperties(StringUtils.delimitedListToStringArray(paramStr, "&"), "=");
				if (properties != null) {
					PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
					map.from(() -> properties.getProperty("fail-fast")).as(Boolean::valueOf)
							.to(configClientProperties::setFailFast);
					map.from(() -> properties.getProperty("max-attempts")).as(Integer::valueOf)
							.to(holder.retryProperties::setMaxAttempts);
					map.from(() -> properties.getProperty("max-interval")).as(Long::valueOf)
							.to(holder.retryProperties::setMaxInterval);
					map.from(() -> properties.getProperty("multiplier")).as(Double::valueOf)
							.to(holder.retryProperties::setMultiplier);
					map.from(() -> properties.getProperty("initial-interval")).as(Long::valueOf)
							.to(holder.retryProperties::setInitialInterval);
				}
			}
			configClientProperties.setUri(uri);
		}

		return holder;
	}

	private BindHandler getBindHandler(ConfigDataLocationResolverContext context) {
		return context.getBootstrapContext().getOrElse(BindHandler.class, null);
	}

	@Deprecated
	protected RestTemplate createRestTemplate(ConfigClientProperties properties) {
		return null;
	}

	protected Log getLog() {
		return this.log;
	}

	/**
	 * 确认是否可解析
	 */
	@Override
	public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
		// 地址前缀不包含configserver:数据表示不可解析
		if (!location.hasPrefix(getPrefix())) {
			return false;
		}
		// 获取spring.cloud.config.enabled数据若为true则表示可解析,反之则表示不可解析
		return context.getBinder().bind(ConfigClientProperties.PREFIX + ".enabled", Boolean.class).orElse(true);
	}

	protected String getPrefix() {
		return PREFIX;
	}

	/**
	 * 解析资源
	 */
	@Override
	public List<ConfigServerConfigDataResource> resolve(ConfigDataLocationResolverContext context,
			ConfigDataLocation location)
			throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException {
		return Collections.emptyList();
	}

	/**
	 * 解析特定配置对象
	 */
	@Override
	public List<ConfigServerConfigDataResource> resolveProfileSpecific(
		ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location, Profiles profiles)
		throws ConfigDataLocationNotFoundException {
		// 获取uri地址集合
		String uris = location.getNonPrefixedValue(getPrefix());
		// 获取配置集合持有器
		PropertyHolder propertyHolder = loadProperties(resolverContext, uris);
		// 获取SpringCloudConfig客户端配置
		ConfigClientProperties properties = propertyHolder.properties;

		// 获取引导上下文
		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		// 注册ConfigClientProperties类型的bean
		bootstrapContext.registerIfAbsent(ConfigClientProperties.class, InstanceSupplier.of(properties));
		// 添加关闭监听器
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory().registerSingleton(
			"configDataConfigClientProperties", event.getBootstrapContext().get(ConfigClientProperties.class)));

		// 注册ConfigClientRequestTemplateFactory类型的bean
		bootstrapContext.registerIfAbsent(ConfigClientRequestTemplateFactory.class,
			context -> new ConfigClientRequestTemplateFactory(log, context.get(ConfigClientProperties.class)));

		// 注册RestTemplate类型的bean
		bootstrapContext.registerIfAbsent(RestTemplate.class, context -> {
			ConfigClientRequestTemplateFactory factory = context.get(ConfigClientRequestTemplateFactory.class);
			RestTemplate restTemplate = createRestTemplate(factory.getProperties());
			if (restTemplate != null) {
				// shouldn't normally happen
				return restTemplate;
			}
			return factory.create();
		});

		// 创建ConfigServerConfigDataResource对象
		ConfigServerConfigDataResource resource = new ConfigServerConfigDataResource(properties, location.isOptional(),
			profiles);
		// 设置日志对象
		resource.setLog(log);
		// 设置重试配置
		resource.setRetryProperties(propertyHolder.retryProperties);

		// 获取spring.cloud.config.discovery.enabled配置
		boolean discoveryEnabled = resolverContext.getBinder()
			.bind(CONFIG_DISCOVERY_ENABLED, Bindable.of(Boolean.class), getBindHandler(resolverContext))
			.orElse(false);

		// 获取spring.cloud.config.fail-fast配置
		boolean retryEnabled = resolverContext.getBinder().bind(ConfigClientProperties.PREFIX + ".fail-fast",
			Bindable.of(Boolean.class), getBindHandler(resolverContext)).orElse(false);

		// spring.cloud.config.discovery.enabled配置为真
		if (discoveryEnabled) {
			log.debug(LogMessage.format("discovery enabled"));
			// register ConfigServerInstanceMonitor
			// 注册ConfigServerInstanceMonitor实例
			bootstrapContext.registerIfAbsent(ConfigServerInstanceMonitor.class, context -> {
				ConfigServerInstanceProvider.Function function = context
					.get(ConfigServerInstanceProvider.Function.class);

				ConfigServerInstanceProvider instanceProvider;
				// 启用重试的情况下进行处理
				if (ConfigClientRetryBootstrapper.RETRY_IS_PRESENT && retryEnabled) {
					log.debug(LogMessage.format("discovery plus retry enabled"));
					// 创建重试模板对象
					RetryTemplate retryTemplate = RetryTemplateFactory.create(propertyHolder.retryProperties, log);
					// 获取Spring Cloud Config 服务实例提供器
					instanceProvider = new ConfigServerInstanceProvider(function) {
						@Override
						public List<ServiceInstance> getConfigServerInstances(String serviceId) {
							return retryTemplate.execute(retryContext -> super.getConfigServerInstances(serviceId));
						}
					};
				} else {
					// 通过构造函数直接创建Spring Cloud Config 服务实例提供器
					instanceProvider = new ConfigServerInstanceProvider(function);
				}
				instanceProvider.setLog(log);

				// 获取SpringCloudConfig客户端配置
				ConfigClientProperties clientProperties = context.get(ConfigClientProperties.class);
				ConfigServerInstanceMonitor instanceMonitor = new ConfigServerInstanceMonitor(log, clientProperties,
					instanceProvider);
				// 设置启动时刷新,设置数据为false
				instanceMonitor.setRefreshOnStartup(false);
				// 刷新
				instanceMonitor.refresh();
				return instanceMonitor;
			});
			// promote ConfigServerInstanceMonitor to bean so updates can be made to
			// config client uri
			// 添加关闭事件监听器
			bootstrapContext.addCloseListener(event -> {
				ConfigServerInstanceMonitor configServerInstanceMonitor = event.getBootstrapContext()
					.get(ConfigServerInstanceMonitor.class);
				// 向引用上下文加入实例监听器
				event.getApplicationContext().getBeanFactory().registerSingleton("configServerInstanceMonitor",
					configServerInstanceMonitor);
			});
		}

		// 创建资源集合将资源对象加入到其中
		List<ConfigServerConfigDataResource> locations = new ArrayList<>();
		locations.add(resource);

		return locations;
	}

	/**
	 * 配置持有器
	 */
	private class PropertyHolder {

		/**
		 * Spring Cloud Config 客户端配置对象
		 */
		ConfigClientProperties properties;

		/**
		 * 重试配置对象
		 */
		RetryProperties retryProperties;

	}

}
