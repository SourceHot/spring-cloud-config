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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.environment.PropertyValueDescriptor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * Simple implementation of {@link EnvironmentRepository} that just reflects an existing
 * Spring Environment.
 *
 * @author Dave Syer
 * @author Roy Clarkson
 */
public class PassthruEnvironmentRepository implements EnvironmentRepository {

	/**
	 * 默认标记
	 */
	private static final String DEFAULT_LABEL = "master";

	/**
	 * 标准配置来源
	 */
	private Set<String> standardSources = new HashSet<String>(
			Arrays.asList("vcap", StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
					StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
					StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME,
					StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME,
					StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME));

	/**
	 * 环境配置
	 */
	private ConfigurableEnvironment environment;

	public PassthruEnvironmentRepository(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	public String getDefaultLabel() {
		return DEFAULT_LABEL;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		return findOne(application, profile, label, false);
	}

	@Override
	public Environment findOne(String application, String profile, String label, boolean includeOrigin) {
		// 创建环境对象
		Environment result = new Environment(application, StringUtils.commaDelimitedListToStringArray(profile), label,
			null, null);

		// 提取环境对象中的属性源
		for (org.springframework.core.env.PropertySource<?> source : this.environment.getPropertySources()) {
			// 获取属性源名称
			String name = source.getName();
			// 不在默认数据源中，数据源类型是否是MapPropertySource
			if (!this.standardSources.contains(name) && source instanceof MapPropertySource) {
				// 向结果集合加入数据
				result.add(new PropertySource(name, getMap(source, includeOrigin), source));
			}
		}
		return result;

	}

	@SuppressWarnings("unchecked")
	private Map<?, ?> getMap(org.springframework.core.env.PropertySource<?> source, boolean includeOrigin) {
		// 结果集合
		Map<Object, Object> map = new LinkedHashMap<>();
		Map<?, ?> input = (Map<?, ?>) source.getSource();
		if (includeOrigin && source instanceof OriginLookup) {
			// origin查询器
			OriginLookup<String> originLookup = (OriginLookup<String>) source;
			for (Object key : input.keySet()) {
				// 通过origin查询器获取origin对象
				Origin origin = originLookup.getOrigin(key.toString());
				// origin对象为空
				if (origin == null) {
					// 向结果集合中加入数据
					map.put(key, source.getProperty(key.toString()));
					continue;
				}
				// 远端描述
				String originDesc;
				// origin类型是TextResourceOrigin
				if (origin instanceof TextResourceOrigin) {
					TextResourceOrigin tro = (TextResourceOrigin) origin;
					// 将描述设置为location数据
					originDesc = tro.getLocation().toString();
				} else {
					// 将origin设置给描述
					originDesc = origin.toString();
				}
				// 取值
				Object value = source.getProperty(key.toString());
				// 设置到结果集合中
				map.put(key, new PropertyValueDescriptor(value, originDesc));
			}
		} else {
			// 处理属性源中的数据
			for (Object key : input.keySet()) {
				// Spring Boot wraps the property values in an "origin" detector, so we
				// need
				// to extract the string values
				// 设置数据
				map.put(key, source.getProperty(key.toString()));
			}
		}
		return map;
	}

}
