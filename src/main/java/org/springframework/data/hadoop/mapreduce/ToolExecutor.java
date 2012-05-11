/*
 * Copyright 2011-2012 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.hadoop.mapreduce;

import java.security.PrivilegedExceptionAction;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.Tool;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.data.hadoop.configuration.ConfigurationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Base class for configuring a Tool.
 * 
 * @author Costin Leau
 */
abstract class ToolExecutor extends JobGenericOptions implements BeanClassLoaderAware {

	String[] arguments;
	Configuration configuration;
	Properties properties;
	Tool tool;
	String toolClassName;
	Resource jar;
	private ClassLoader beanClassLoader;

	private String user;


	int runTool() throws Exception {
		final Configuration cfg = ConfigurationUtils.createFrom(configuration, properties);

		ClassLoader cl = beanClassLoader;
		Tool t = tool;

		if (t == null) {
			cl = ClassLoadingUtils.createParentLastClassLoader(jar, beanClassLoader, cfg);
			cfg.setClassLoader(cl);
			t = loadTool(toolClassName, cl);
		}

		Thread th = Thread.currentThread();
		ClassLoader oldTccl = th.getContextClassLoader();

		final Tool ft = t;

		try {
			th.setContextClassLoader(cl);

			if (StringUtils.hasText(user)) {
				UserGroupInformation ugi = UserGroupInformation.createProxyUser(user, UserGroupInformation.getLoginUser());
				return ugi.doAs(new PrivilegedExceptionAction<Integer>() {

					@Override
					public Integer run() throws Exception {
						return org.apache.hadoop.util.ToolRunner.run(cfg, ft, arguments);
					}
				});
			}
			else {
				return org.apache.hadoop.util.ToolRunner.run(cfg, ft, arguments);
			}
		} finally {
			th.setContextClassLoader(oldTccl);
		}

	}

	private Tool loadTool(String toolClassName, ClassLoader cl) {
		Class<?> clazz = ClassUtils.resolveClassName(toolClassName, cl);
		return (Tool) BeanUtils.instantiateClass(clazz);
	}

	/**
	 * Sets the tool.
	 *
	 * @param tool The tool to set.
	 */
	public void setTool(Tool tool) {
		Assert.isNull(toolClassName, "a Tool class already set");
		this.tool = tool;
	}

	/**
	 * Sets the tool class by name.
	 *
	 * @param toolClass the new tool class
	 */
	public void setToolClass(String toolClassName) {
		Assert.isNull(tool, "a Tool instance already set");
		this.toolClassName = toolClassName;
	}

	public void setJar(Resource jar) {
		this.jar = jar;
	}

	/**
	 * Sets the arguments.
	 *
	 * @param arguments The arguments to set.
	 */
	public void setArguments(String... arguments) {
		this.arguments = arguments;
	}

	/**
	 * Sets the configuration.
	 *
	 * @param configuration The configuration to set.
	 */
	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	/**
	 * Sets the properties.
	 *
	 * @param properties The properties to set.
	 */
	public void setProperties(Properties properties) {
		this.properties = properties;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	/**
	 * Sets the user impersonation (optional) for running this task.
	 * Should be used when running against a Hadoop Kerberos cluster. 
	 * 
	 * @param user user/group information
	 */
	public void setUser(String user) {
		this.user = user;
	}
}