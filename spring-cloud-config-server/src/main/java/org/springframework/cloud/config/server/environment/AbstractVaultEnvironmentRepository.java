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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotEmpty;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.config.client.ConfigClientProperties.STATE_HEADER;

/**
 * @author Spencer Gibb
 * @author Mark Paluch
 * @author Haroun Pacquee
 * @author Haytham Mohamed
 * @author Scott Frederick
 */
public abstract class AbstractVaultEnvironmentRepository implements EnvironmentRepository, Ordered {

	// TODO: move to watchState:String on findOne?

	/**
	 * 提供请求的对象
	 */
	protected final ObjectProvider<HttpServletRequest> request;

	/**
	 * 环境观察接口
	 */
	protected final EnvironmentWatch watch;

	/**
	 * The key in vault shared by all applications. Defaults to application. Set to empty
	 * to disable.
	 * 默认秘钥
	 */
	protected String defaultKey;

	/**
	 * Vault profile separator. Defaults to comma.
	 * Vault配置文件分隔符,默认逗号
	 */
	@NotEmpty
	protected String profileSeparator;

	/**
	 * 序号
	 */
	protected int order;

	public AbstractVaultEnvironmentRepository(ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch,
			VaultEnvironmentProperties properties) {
		this.defaultKey = properties.getDefaultKey();
		this.profileSeparator = properties.getProfileSeparator();
		this.order = properties.getOrder();
		this.request = request;
		this.watch = watch;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		// 获取profile集合
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		// 将profile集合中的default移除
		List<String> scrubbedProfiles = scrubProfiles(profiles);


		// 组合应用和profiles数据
		List<String> keys = findKeys(application, scrubbedProfiles);

		// 创建环境对象
		Environment environment = new Environment(application, profiles, label, null, getWatchState());

		// 循环应用和profiles组合数据
		for (String key : keys) {
			// read raw 'data' key from vault
			// 读取数据
			String data = read(key);
			// 数据不为空的情况下
			if (data != null) {
				// data is in json format of which, yaml is a superset, so parse

				// 通过yaml进行解析
				final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
				yaml.setResources(new ByteArrayResource(data.getBytes()));
				Properties properties = yaml.getObject();

				// yaml解析结果不为空的情况下加入到环境对象中
				if (!properties.isEmpty()) {
					environment.add(new PropertySource("vault:" + key, properties));
				}
			}
		}

		return environment;
	}

	protected abstract String read(String key);

	private String getWatchState() {
		HttpServletRequest servletRequest = this.request.getIfAvailable();
		if (servletRequest != null) {
			String state = servletRequest.getHeader(STATE_HEADER);
			return this.watch.watch(state);
		}
		return null;
	}

	private List<String> findKeys(String application, List<String> profiles) {
		List<String> keys = new ArrayList<>();

		// 是否存在成员变量defaultKey并且成员变量defaultKey和application不相同,向结果加入defaultKey数据
		if (StringUtils.hasText(this.defaultKey) && !this.defaultKey.equals(application)) {
			keys.add(this.defaultKey);
			// 向结果集加入profiles相关内容
			addProfiles(keys, this.defaultKey, profiles);
		}

		// application may have comma-separated list of names
		// 切分application参数
		String[] applications = StringUtils.commaDelimitedListToStringArray(application);
		// 循环处理单个application数据
		for (String app : applications) {
			// 加入结果集
			keys.add(app);
			// 向结果集加入profiles相关内容
			addProfiles(keys, app, profiles);
		}

		Collections.reverse(keys);
		return keys;
	}

	private List<String> scrubProfiles(String[] profiles) {
		List<String> scrubbedProfiles = new ArrayList<>(Arrays.asList(profiles));
		scrubbedProfiles.remove("default");
		return scrubbedProfiles;
	}

	private void addProfiles(List<String> contexts, String baseContext, List<String> profiles) {
		for (String profile : profiles) {
			contexts.add(baseContext + this.profileSeparator + profile);
		}
	}

	public void setDefaultKey(String defaultKey) {
		this.defaultKey = defaultKey;
	}

	public void setProfileSeparator(String profileSeparator) {
		this.profileSeparator = profileSeparator;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

}
