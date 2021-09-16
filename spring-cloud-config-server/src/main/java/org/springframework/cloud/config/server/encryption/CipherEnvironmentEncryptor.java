/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.cloud.config.server.encryption;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * EnvironmentEncryptor that can decrypt property values prefixed with {cipher} marker.
 *
 * @author Dave Syer
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 *
 */
@Component
public class CipherEnvironmentEncryptor implements EnvironmentEncryptor {

	private static Log logger = LogFactory.getLog(CipherEnvironmentEncryptor.class);

	private final TextEncryptorLocator encryptor;

	private EnvironmentPrefixHelper helper = new EnvironmentPrefixHelper();

	@Autowired
	public CipherEnvironmentEncryptor(TextEncryptorLocator encryptor) {
		this.encryptor = encryptor;
	}

	@Override
	public Environment decrypt(Environment environment) {
		// 如果成员变量encryptor存在则进行解密，反之则直接将环境对象返回
		return this.encryptor != null ? decrypt(environment, this.encryptor) : environment;
	}

	private Environment decrypt(Environment environment, TextEncryptorLocator encryptor) {
		// 创建环境对象
		Environment result = new Environment(environment);
		// 获取环境对象中的属性源
		for (PropertySource source : environment.getPropertySources()) {
			// 属性源数据转换成map对象
			Map<Object, Object> map = new LinkedHashMap<Object, Object>(source.getSource());
			// 循环处理单个属性源
			for (Map.Entry<Object, Object> entry : new LinkedHashSet<>(map.entrySet())) {
				// 获取属性键
				Object key = entry.getKey();
				// 获取属性键的string表达
				String name = key.toString();
				// 如果属性值存在并且是以{cipher}开头
				if (entry.getValue() != null && entry.getValue().toString().startsWith("{cipher}")) {
					// 提取值
					String value = entry.getValue().toString();
					// 从属性源数据中移除当前处理的数据
					map.remove(key);
					// 解密
					try {
						value = value.substring("{cipher}".length());
						value = encryptor
							.locate(this.helper.getEncryptorKeys(name,
								StringUtils.arrayToCommaDelimitedString(environment.getProfiles()), value))
							.decrypt(this.helper.stripPrefix(value));
					} catch (Exception e) {
						value = "<n/a>";
						name = "invalid." + name;
						String message = "Cannot decrypt key: " + key + " (" + e.getClass() + ": " + e.getMessage()
							+ ")";
						if (logger.isDebugEnabled()) {
							logger.debug(message, e);
						} else if (logger.isWarnEnabled()) {
							logger.warn(message);
						}
					}
					// 重新放入属性源对象中
					map.put(name, value);
				}
			}
			// 放入结果集中
			result.add(new PropertySource(source.getName(), map));
		}
		return result;
	}

}
