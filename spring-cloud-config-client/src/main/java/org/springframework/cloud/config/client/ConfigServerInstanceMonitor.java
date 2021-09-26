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
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.HeartbeatMonitor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.util.Assert;

/**
 * Spring Cloud Config 服务实例监控器
 */
final class ConfigServerInstanceMonitor implements SmartApplicationListener {

	private final Log log;
	/**
	 * Spring Cloud Config客户端配置
	 */
	private final ConfigClientProperties config;
	/**
	 * Spring Cloud Config 服务实例提供器
	 */
	private final ConfigServerInstanceProvider instanceProvider;
	/**
	 * 心跳监控
	 */
	private final HeartbeatMonitor monitor = new HeartbeatMonitor();

	/**
	 * If bootstrap, this should be true, for config data false.
	 * 是否需要在启动时刷新
	 */
	private boolean refreshOnStartup = true;

	ConfigServerInstanceMonitor(Log log, ConfigClientProperties config, ConfigServerInstanceProvider instanceProvider) {
		this.log = log;
		this.config = config;
		this.instanceProvider = instanceProvider;
	}

	void setRefreshOnStartup(boolean refreshOnStartup) {
		this.refreshOnStartup = refreshOnStartup;
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ContextRefreshedEvent.class.isAssignableFrom(eventType)
				|| HeartbeatEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ContextRefreshedEvent) {
			// startup方法调度
			startup((ContextRefreshedEvent) event);
		} else if (event instanceof HeartbeatEvent) {
			// heartbeat方法调度
			heartbeat((HeartbeatEvent) event);
		}
	}

	public void startup(ContextRefreshedEvent event) {
		if (refreshOnStartup) {
			refresh();
		}
	}

	public void heartbeat(HeartbeatEvent event) {
		if (this.monitor.update(event.getValue())) {
			refresh();
		}
	}

	void refresh() {
		try {
			// 获取服务id
			String serviceId = this.config.getDiscovery().getServiceId();
			// 服务id不存在抛出异常
			Assert.hasText(serviceId, () -> ConfigClientProperties.PREFIX + ".service-id may not be null or empty");
			// 创建url集合
			List<String> listOfUrls = new ArrayList<>();
			// 根据服务id获取服务实例
			List<ServiceInstance> serviceInstances = this.instanceProvider.getConfigServerInstances(serviceId);

			for (int i = 0; i < serviceInstances.size(); i++) {

				// 获取服务实例
				ServiceInstance server = serviceInstances.get(i);
				// 获取url地址
				String url = getHomePage(server);

				// 在元数据中存在密码的情况下为客户端配置设置用户名和密码数据
				if (server.getMetadata().containsKey("password")) {
					String user = server.getMetadata().get("user");
					user = user == null ? "user" : user;
					this.config.setUsername(user);
					String password = server.getMetadata().get("password");
					this.config.setPassword(password);
				}

				// 在元数据中存在configPath数据的情况下修正url地址
				if (server.getMetadata().containsKey("configPath")) {
					String path = server.getMetadata().get("configPath");
					if (url.endsWith("/") && path.startsWith("/")) {
						url = url.substring(0, url.length() - 1);
					}
					url = url + path;
				}

				// 向路由地址集合加入当前处理完成的路由
				listOfUrls.add(url);
			}

			if (log.isDebugEnabled()) {
				log.debug("Updating config uris to " + listOfUrls);
			}

			// 转换为数组设置到客户端配置中
			String[] uri = new String[listOfUrls.size()];
			uri = listOfUrls.toArray(uri);
			this.config.setUri(uri);

		}
		catch (Exception ex) {
			if (this.config.isFailFast()) {
				throw ex;
			}
			else if (log.isWarnEnabled()) {
				log.warn("Could not locate configserver via discovery", ex);
			}
		}
	}

	private String getHomePage(ServiceInstance server) {
		return server.getUri().toString() + "/";
	}

}
