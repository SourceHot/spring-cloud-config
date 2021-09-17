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

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for any @{link
 * org.springframework.cloud.config.server.encryption.ResourceEncryptor} implementations.
 * Meant to house shared configuration and logic.
 *
 * @author Sean Stiglitz
 */
abstract class AbstractCipherResourceEncryptor implements ResourceEncryptor {

	/**
	 * cipher标记
	 */
	protected final String CIPHER_MARKER = "{cipher}";

	/**
	 * 加密文本定位器
	 */
	private final TextEncryptorLocator encryptor;

	/**
	 * 前缀处理工具
	 */
	private EnvironmentPrefixHelper helper = new EnvironmentPrefixHelper();

	AbstractCipherResourceEncryptor(TextEncryptorLocator encryptor) {
		this.encryptor = encryptor;
	}

	@Override
	public abstract List<String> getSupportedExtensions();

	@Override
	public abstract String decrypt(String text, Environment environment) throws IOException;

	/**
	 * 使用jackson进行解密
	 */
	protected String decryptWithJacksonParser(String text, String name, String[] profiles, JsonFactory factory)
			throws IOException {

		// 需要解密的值
		Set<String> valsToDecrpyt = new HashSet<String>();
		// 生成json解析器
		JsonParser parser = factory.createParser(text);
		JsonToken token;

		// 通过jsontoken将需要解密的数据进行提取放入到待解密容器中
		while ((token = parser.nextToken()) != null) {
			if (token.equals(JsonToken.VALUE_STRING) && parser.getValueAsString().startsWith(CIPHER_MARKER)) {
				valsToDecrpyt.add(parser.getValueAsString().trim());
			}
		}

		// 处理解密容器进行解密
		for (String value : valsToDecrpyt) {
			// 解密
			String decryptedValue = decryptValue(value.replace(CIPHER_MARKER, ""), name, profiles);
			// 替换原有数据
			text = text.replace(value, decryptedValue);
		}

		return text;
	}

	protected String decryptValue(String value, String name, String[] profiles) {
		return encryptor
				.locate(this.helper.getEncryptorKeys(name, StringUtils.arrayToCommaDelimitedString(profiles), value))
				.decrypt(this.helper.stripPrefix(value));
	}

}
