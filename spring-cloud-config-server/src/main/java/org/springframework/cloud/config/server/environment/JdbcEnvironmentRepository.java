/*
 * Copyright 2016-2019 the original author or authors.
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.util.StringUtils;

/**
 * An {@link EnvironmentRepository} that picks up data from a relational database. The
 * database should have a table called "PROPERTIES" with columns "APPLICATION", "PROFILE",
 * "LABEL" (with the usual {@link Environment} meaning), plus "KEY" and "VALUE" for the
 * key and value pairs in {@link Properties} style. Property values behave in the same way
 * as they would if they came from Spring Boot properties files named
 * <code>{application}-{profile}.properties</code>, including all the encryption and
 * decryption, which will be applied as post-processing steps (i.e. not in this repository
 * directly).
 *
 * @author Dave Syer
 *
 */
public class JdbcEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final Log logger = LogFactory.getLog(JdbcEnvironmentRepository.class);
	/**
	 * jdbc操作类
	 */
	private final JdbcTemplate jdbc;
	/**
	 * 属性值提取器
	 */
	private final PropertiesResultSetExtractor extractor = new PropertiesResultSetExtractor();
	/**
	 * 序号
	 */
	private int order;
	/**
	 *sql
	 */
	private String sql;
	/**
	 * 异常处理是否抛出
	 */
	private boolean failOnError;

	public JdbcEnvironmentRepository(JdbcTemplate jdbc, JdbcEnvironmentProperties properties) {
		this.jdbc = jdbc;
		this.order = properties.getOrder();
		this.sql = properties.getSql();
		this.failOnError = properties.isFailOnError();
	}

	public String getSql() {
		return this.sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		// 确认config、label、profile数据信息
		String config = application;
		if (StringUtils.isEmpty(label)) {
			label = "master";
		}
		if (StringUtils.isEmpty(profile)) {
			profile = "default";
		}
		if (!profile.startsWith("default")) {
			profile = "default," + profile;
		}
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		// 创建环境对象
		Environment environment = new Environment(application, profiles, label, null, null);
		if (!config.startsWith("application")) {
			config = "application," + config;
		}
		// 拆分应用名称
		List<String> applications = new ArrayList<>(
			new LinkedHashSet<>(Arrays.asList(StringUtils.commaDelimitedListToStringArray(config))));
		// 拆分profiles数据
		List<String> envs = new ArrayList<>(new LinkedHashSet<>(Arrays.asList(profiles)));
		Collections.reverse(applications);
		Collections.reverse(envs);
		// 循环应用名称集合将其中的数据和profiles数据进行整合，通过jdbc查询，将查询结果放入到环境对象中
		for (String app : applications) {
			for (String env : envs) {
				try {
					Map<String, String> next = this.jdbc.query(this.sql, this.extractor, app, env, label);
					if (next != null && !next.isEmpty()) {
						environment.add(new PropertySource(app + "-" + env, next));
					}
				} catch (DataAccessException e) {
					if (!failOnError) {
						if (logger.isDebugEnabled()) {
							logger.debug("Failed to retrieve configuration from JDBC Repository", e);
						}
					} else {
						throw e;
					}
				}
			}
		}
		return environment;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public boolean isFailOnError() {
		return failOnError;
	}

	public void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
	}

	public static class PropertiesResultSetExtractor implements ResultSetExtractor<Map<String, String>> {

		@Override
		public Map<String, String> extractData(ResultSet rs) throws SQLException, DataAccessException {
			Map<String, String> map = new LinkedHashMap<>();
			while (rs.next()) {
				map.put(rs.getString(1), rs.getString(2));
			}
			return map;
		}

	}

}
