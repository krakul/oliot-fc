/*
 * Copyright (C) 2007 ETH Zurich
 *
 * This file is part of Fosstrak (www.fosstrak.org).
 *
 * Fosstrak is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 2.1, as published by the Free Software Foundation.
 *
 * Fosstrak is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Fosstrak; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package org.fosstrak.ale.client.cfg;

import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.tools.ant.types.Path;
import org.fosstrak.ale.client.exception.FosstrakAleClientException;

/**
 * @author swieland
 * configuration class.
 */
public class Configuration {
	
	// properties file path
	private static final String PROPERTIES_FILE_LOCATION = "/props/client.properties";
	
	// the properties container
	private Properties m_properties;
	
	private Font m_font = null;
	
	// logger.
	private static final Logger s_log = Logger.getLogger(Configuration.class);
	
	/**
	 * construct a new configuration. if the command line provides a 
	 * configuration file, then this file is used. otherwise the default 
	 * property from the jar is used. 
	 * @param cmdLine the command line array.
	 * @throws FosstrakAleClientException when the configuration could not be obtained.
	 */
	private Configuration(String[] cmdLine) throws FosstrakAleClientException, FileNotFoundException {
		
		String cfgPath = PROPERTIES_FILE_LOCATION;
		InputStream inputStream;

		if (cmdLine.length > 0) cfgPath = cmdLine[0];

		// Check if "cfgPath" is an existing resource
		if (this.getClass().getResource(cfgPath) != null) {
			s_log.info(String.format("using configuration resource '%s'", cfgPath));
			inputStream = this.getClass().getResourceAsStream(cfgPath);
		}
		// Check if "cfgPath" is an existing file
		else if (Files.exists(Paths.get(cfgPath)))
		{
			s_log.info(String.format("using configuration file '%s'", cfgPath));
			File cfgFile = new File(cfgPath);
			inputStream = new FileInputStream(cfgFile);
		}
		else
		{
			throw new FosstrakAleClientException(String.format("'%s' is not a resource neither a file", cfgPath));
		}

		// load properties from stream
		try {
			m_properties = new Properties();
			m_properties.load(inputStream);
		} catch (Exception e) {
			m_properties = null;
			s_log.error(String.format("could not load configuration from '%s'", cfgPath));
			throw new FosstrakAleClientException(e);
		}
	}

	/**
	 * @return the window size.
	 */
	public Dimension getWindowSize() {
		final int width = getPropertyAsInteger("org.fosstrak.ale.client.main.width");
		final int height = getPropertyAsInteger("org.fosstrak.ale.client.main.height");
		s_log.debug(String.format("window dimension: %dx%d", width, height));
		return new Dimension(width, height);
	}
	
	/**
	 * @return the exception window size.
	 */
	public Dimension getExceptionWindowSize() {
		final int width = getPropertyAsInteger("org.fosstrak.ale.client.exception.width");
		final int height = getPropertyAsInteger("org.fosstrak.ale.client.exception.height");
		s_log.debug(String.format("exception window dimensions: %dx%d", width, height));
		return new Dimension(width, height);
	}
	
	/**
	 * @return the endpoint window size.
	 */
	public Dimension getEndpointWindowSize() {
		final int width = getPropertyAsInteger("org.fosstrak.ale.client.endpoint.width");
		final int height = getPropertyAsInteger("org.fosstrak.ale.client.endpoint.height");
		s_log.debug(String.format("endpoint window dimensions: %dx%d", width, height));
		return new Dimension(width, height);
	}
	
	/**
	 * @return the language to use.
	 */
	public String getLanguage() {
		return getProperty("org.fosstrak.ale.client.language");
	}
	
	/**
	 * @param prefix the prefix in the configuration file (eg org.fosstrak.ale.client).
	 * @param key the key of the property.
	 * @return a property identified by the concatenation of prefix and key.
	 */
	public String getProperty(String prefix, String key) {
		return getProperty(String.format("%s.%s", prefix, key));
	}
	
	/**
	 * @param key the key (eg. org.fosstrak.ale.client.windowHeight).
	 * @return a property uniquely identified by the given key.
	 */
	public String getProperty(String key) {
		return m_properties.getProperty(key);
	}
	
	/**
	 * @param prefix the prefix in the configuration file (eg org.fosstrak.ale.client).
	 * @param key the key of the property.
	 * @return a property identified by the concatenation of prefix and key.
	 */
	public int getPropertyAsInteger(String prefix, String key) {
		return getPropertyAsInteger(String.format("%s.%s", prefix, key));
	}
	
	/**
	 * @param key the key (eg. org.fosstrak.ale.client.windowHeight).
	 * @return a property uniquely identified by the given key.
	 */
	public int getPropertyAsInteger(String key) {
		return Integer.parseInt(m_properties.getProperty(key));
	}

	/**
	 * @param key the key (eg. org.fosstrak.ale.client.windowHeight).
	 * @return a property uniquely identified by the given key.
	 */
	public boolean getPropertyAsBoolean(String key) {
		return Boolean.parseBoolean(m_properties.getProperty(key, "false"));
	}
	
	/**
	 * @return the font specified in the configuration. the font is created only once and is 
	 * reused later.
	 */
	public final Font getFont()
	{
		if (null == m_font)
		{
			String fontName = null;
			int fontSize = 10;
			try
			{
				fontName = getProperty("org.fosstrak.ale.client.font");
				if (null == fontName)
				{
					throw new Exception("no font found");
				}
				fontSize = getPropertyAsInteger("org.fosstrak.ale.client.font.size");
			} 
			catch (Exception e)
			{
				fontName = "Verdana";
			}			
			m_font = new Font(fontName, Font.PLAIN, fontSize);
		}
		return m_font;
	}
	
	/**
	 * reads the configuration from a configuration given by the command line.
	 * @param cmdLine the command line arguments.
	 * @return a configuration
	 * @throws FosstrakAleClientException when the configuration could not be obtained.
	 */
	public static Configuration getConfiguration(String[] cmdLine) throws FosstrakAleClientException, FileNotFoundException {
		return new Configuration(cmdLine);
	}
	
	/**
	 * reads the default configuration file
	 * @return a configuration
	 * @throws FosstrakAleClientException when the configuration could not be obtained.
	 */
	public static Configuration getConfigurtionDefaultConfig() throws FosstrakAleClientException, FileNotFoundException {
		return getConfiguration(new String[] {} );
	}
}
