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

package org.springframework.cloud.config.client;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import static org.springframework.util.StringUtils.hasText;

/**
 * @author Spencer Gibb
 */
public class ConfigClientWatch implements Closeable, EnvironmentAware {

	private static Log log = LogFactory.getLog(ConfigServicePropertySourceLocator.class);
	/**
	 * 是否处于运行状态
	 */
	private final AtomicBoolean running = new AtomicBoolean(false);
	/**
	 * 刷新上下文对象,主要用于刷新上下文
	 */
	private final ContextRefresher refresher;
	/**
	 * 环境对象
	 */
	private Environment environment;

	public ConfigClientWatch(ContextRefresher refresher) {
		this.refresher = refresher;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@PostConstruct
	public void start() {
		this.running.compareAndSet(false, true);
	}

	@Scheduled(initialDelayString = "${spring.cloud.config.watch.initialDelay:180000}",
			fixedDelayString = "${spring.cloud.config.watch.delay:500}")
	public void watchConfigServer() {
		// 确认是否启动
		if (this.running.get()) {
			// 获取config.client.state数据
			String newState = this.environment.getProperty("config.client.state");
			// 获取历史数据
			String oldState = ConfigClientStateHolder.getState();

			// only refresh if state has changed
			// 确认是否需要刷新，newState数据和oldState数据不相同则刷新
			if (stateChanged(oldState, newState)) {
				// 修正历史数据
				ConfigClientStateHolder.setState(newState);
				// 刷新上下文
				this.refresher.refresh();
			}
		}
	}

	/* for testing */ boolean stateChanged(String oldState, String newState) {
		return (!hasText(oldState) && hasText(newState)) || (hasText(oldState) && !oldState.equals(newState));
	}

	@Override
	public void close() {
		this.running.compareAndSet(true, false);
	}

}
