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

import java.util.Map;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.environment.PropertyValueDescriptor;

/**
 * @author Dave Syer
 * @author Michael Prankl
 */
public class EnvironmentCleaner {

	public Environment clean(Environment value, String workingDir, String uri) {
		// 创建环境对象
		Environment result = new Environment(value);
		// 处理环境对象中的属性源
		for (PropertySource source : value.getPropertySources()) {
			// 获取属性源的名称将其进行字符串修正
			String name = source.getName().replace(workingDir, "");
			name = name.replace("applicationConfig: [", "");
			name = uri + "/" + name.replace("]", "");
			result.add(new PropertySource(name, clean(source.getSource(), uri)));
		}
		return result;
	}

	protected Map<?, ?> clean(Map<?, ?> source, String uri) {
		// 处理属性源数据
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			// 如果属性值是PropertyValueDescriptor类型
			if (entry.getValue() instanceof PropertyValueDescriptor) {
				PropertyValueDescriptor descriptor = (PropertyValueDescriptor) entry.getValue();
				// 补充最后"/"
				if (!uri.endsWith("/")) {
					uri = uri + "/";
				}
				// 修正描述对象中的origin数据
				String updated = descriptor.getOrigin().replace("[", "[" + uri);
				descriptor.setOrigin(updated);
			}
		}
		return source;
	}

}
