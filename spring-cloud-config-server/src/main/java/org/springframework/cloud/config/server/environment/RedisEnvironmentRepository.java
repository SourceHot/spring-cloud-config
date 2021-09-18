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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * @author Piotr Mińkowski
 */
public class RedisEnvironmentRepository implements EnvironmentRepository {
	/**
	 * redis操作对象
	 */
	private final StringRedisTemplate redis;
	/**
	 * redis环境配置
	 */
	private final RedisEnvironmentProperties properties;

	public RedisEnvironmentRepository(StringRedisTemplate redis, RedisEnvironmentProperties properties) {
		this.redis = redis;
		this.properties = properties;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		// 切分profile
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		// 创建环境对象
		Environment environment = new Environment(application, profiles, label, null, null);
		// 处理application和profile之间的关系
		final List<String> keys = addKeys(application, Arrays.asList(profiles));
		// 循环处理keys
		keys.forEach(it -> {
			// 通过redis读取数据并将其加入到环境对象中
			Map<?, ?> m = redis.opsForHash().entries(it);
			environment.add(new PropertySource("redis:" + it, m));
		});
		// 返回环境对象
		return environment;
	}

	private List<String> addKeys(String application, List<String> profiles) {
		// 创建结果集
		List<String> keys = new ArrayList<>();
		// 加入application参数
		keys.add(application);
		// 循环profiles将application+"-"+profile组合后加入到结果集
		for (String profile : profiles) {
			keys.add(application + "-" + profile);
		}
		// 翻转
		Collections.reverse(keys);
		// 返回
		return keys;
	}

}
