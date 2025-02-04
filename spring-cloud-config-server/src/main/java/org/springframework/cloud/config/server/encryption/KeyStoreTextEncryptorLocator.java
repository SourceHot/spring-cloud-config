/*
 * Copyright 2015-2019 the original author or authors.
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

import java.util.Map;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaAlgorithm;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

/**
 * A {@link TextEncryptorLocator} that pulls RSA key pairs out of a keystore. The input
 * map can contain entries for "key" or "secret" or both, or neither. The secret in the
 * input map is not, in general, the secret in the keystore, but is dereferenced through a
 * {@link SecretLocator} (so for example you can keep a table of encrypted secrets and
 * update it separately to the keystore).
 *
 * @author Dave Syer
 *
 */
public class KeyStoreTextEncryptorLocator implements TextEncryptorLocator {
	/**
	 * key键位
	 */
	private final static String KEY = "key";
	/**
	 * secret键位
	 */
	private final static String SECRET = "secret";
	/**
	 * 公钥私钥生成器
	 */
	private KeyStoreKeyFactory keys;
	/**
	 * 默认的secret
	 */
	private String defaultSecret;
	/**
	 * 默认的别名
	 */
	private String defaultAlias;
	/**
	 * rsa加密器
	 */
	private RsaSecretEncryptor defaultEncryptor;
	/**
	 * 秘钥定位器
	 */
	private SecretLocator secretLocator = new PassthruSecretLocator();
	/**
	 * RSA算法类
	 */
	private RsaAlgorithm rsaAlgorithm = RsaAlgorithm.DEFAULT;
	/**
	 *
	 */
	private boolean strong = false;
	/**
	 * 盐
	 */
	private String salt = "deadbeef";

	public KeyStoreTextEncryptorLocator(KeyStoreKeyFactory keys, String defaultSecret, String defaultAlias) {
		this.keys = keys;
		this.defaultAlias = defaultAlias;
		this.defaultSecret = defaultSecret;
	}

	/**
	 * @param secretLocator the secretLocator to set
	 */
	public void setSecretLocator(SecretLocator secretLocator) {
		this.secretLocator = secretLocator;
	}

	public void setRsaAlgorithm(RsaAlgorithm rsaAlgorithm) {
		this.rsaAlgorithm = rsaAlgorithm;
	}

	public void setStrong(boolean strong) {
		this.strong = strong;
	}

	public void setSalt(String salt) {
		this.salt = salt;
	}

	@Override
	public TextEncryptor locate(Map<String, String> keys) {
		// 确认别名
		String alias = keys.containsKey(KEY) ? keys.get(KEY) : this.defaultAlias;
		// 确认秘钥
		String secret = keys.containsKey(SECRET) ? keys.get(SECRET) : this.defaultSecret;
		// 确认别名是否和默认别名相同并且秘钥是否和默认秘钥相同
		if (alias.equals(this.defaultAlias) && secret.equals(this.defaultSecret)) {
			// 默认加密器(类型是rsa加密)为空的情况下设置rsa加密器
			if (this.defaultEncryptor == null) {
				this.defaultEncryptor = rsaSecretEncryptor(alias, secret);
			}
			// 返回对象
			return this.defaultEncryptor;
		} else {
			// 创建RsaSecretEncryptor对象返回
			return rsaSecretEncryptor(alias, secret);
		}
	}

	private RsaSecretEncryptor rsaSecretEncryptor(String alias, String secret) {
		return new RsaSecretEncryptor(this.keys.getKeyPair(alias, this.secretLocator.locate(secret)), this.rsaAlgorithm,
				this.salt, this.strong);
	}

}
