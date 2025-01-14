/*
 * Copyright (C) 2014 KAIST
 * @author Janggwan Im <limg00n@kaist.ac.kr> 
 * 
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

package org.fosstrak.ale.client.tabs;


import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.PropertyResourceBundle;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSPasswordCallback;
import org.apache.ws.security.handler.WSHandlerConstants;
import org.fosstrak.ale.client.FosstrakAleClient;
import org.fosstrak.ale.client.exception.FosstrakAleClientException;
import org.fosstrak.ale.client.exception.FosstrakAleClientServiceDownException;
import org.fosstrak.ale.wsdl.aletm.epcglobal.ALETMServicePortType;

/**
 * @author swieland
 *
 * abstract super class for all the sub tabs in the GUI. the class provides helper 
 * methods to maintain the service proxy, default panels, ...
 */
public abstract class AbstractTab extends JPanel {

	/** 
	 * serial version uid.
	 */
	private static final long serialVersionUID = 1L;

	/** 
	 * system default language.
	 */
	private static final Locale SYSTEM_DEFAULT_LOCALE = Locale.getDefault();
	
	/** 
	 * default language (if language is not defined in property file and system default language does not exists).
	 */
	private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
	
	/**
	 * index of the undefined command.
	 */
	protected static final int CMD__UNDEFINED_COMMAND = -1;
	
	/**
	 *  the proxy object.
	 */
	protected Object m_proxy;
	
	/**
	 * test method to call for the connection establishment test.
	 */
	final protected Method m_testMethod;
	
	/**
	 * parameter object for the test method call.
	 */
	final protected Object m_testMethodParameter;
	
	/**
	 * class of the proxy stub.
	 */
	protected final Class<?> m_clzz;
	
	/**
	 * key to the endpoint in the properties.
	 */
	protected final String m_endpointKey;
	
	/**
	 * parent frame.
	 */
	protected final JFrame m_parent;
	
	/** 
	 * resource bundle containing all user visible texts in specified language.
	 */
	protected PropertyResourceBundle m_guiText;
	
	/** 
	 * text area which contains the results.
	 */
	protected final JTextArea m_resultTextArea = new JTextArea(17, 30);
	
	/** 
	 * panel which contains the command panel.
	 */
	protected final JPanel m_commandSuperPanel = new JPanel();
	
	/** 
	 * scroll pane which contains the result text area.
	 */
	protected final JScrollPane m_resultScrollPane = new JScrollPane(m_resultTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
	/** 
	 * command panel. 
	 */
	protected final JPanel m_commandPanel = new JPanel();
	
	/** 
	 * combobox which contains all defined specification names.
	 */
	protected JComboBox m_specNameComboBox;
	
	/** 
	 * combobox which contains all possible commands.
	 */
	protected final JComboBox m_commandSelection = new JComboBox();
	
	/** 
	 * panel which contains the execute button.
	 */
	protected JPanel m_execButtonPanel = null;
	
	/** 
	 * text field which contains the file path.
	 */
	protected JTextField m_filePathField;
	
	/**
	 * the font to be used for all components.
	 */
	protected final Font m_font;
	
	/**
	 * 
	 * @param clzz the class of the proxy stub.
	 * @param endpointKey key to the endpoint in the properties.
	 * @param parent the parent frame.
	 */
	public AbstractTab(Class<?> clzz, String endpointKey, JFrame parent, Method testMethod, Object testMethodParameter) {
		m_clzz = clzz;
		m_endpointKey = endpointKey;
		m_parent = parent;
		m_testMethod = testMethod;
		m_testMethodParameter = testMethodParameter;
		
		m_font = FosstrakAleClient.instance().getConfiguration().getFont();
	}
	
	/**
	 * @return the proxy object. if null, display the connect dialog.
	 */
	protected Object getProxy() throws FosstrakAleClientException {
		if (null == m_proxy) {

			// Needed to get rid of CXF exception: "Cannot create a secure XMLInputFactory"
			// Doesn't matter if security is used or not
			System.setProperty("org.apache.cxf.stax.allowInsecureParser", "true");

			// display the connection endpoint dialog
			String address = FosstrakAleClient.instance().showConnectDialog(m_endpointKey);

			// Create service factory
			JaxWsProxyFactoryBean factory;
			factory = new JaxWsProxyFactoryBean();
			factory.setServiceClass(m_clzz);
			factory.setAddress(address);
			Object toReturn = factory.create();

			// Use SOAP WS-Security or not ?
			boolean useWsse = FosstrakAleClient.instance().getConfiguration().getPropertyAsBoolean("useWsse");
			if (useWsse) {

				// Get username and password from configuration
				String userId = FosstrakAleClient.instance().getConfiguration().getProperty("userId");
				CallbackHandler cpc = new CallbackHandler() {

					@Override
					public void handle(Callback[] callbacks) throws IOException,
							UnsupportedCallbackException {
						WSPasswordCallback pc = (WSPasswordCallback) callbacks[0];
						String password = FosstrakAleClient.instance().getConfiguration().getProperty("password");
						pc.setPassword(password);
					}

				};

				Map<String, Object> outProps = new HashMap<String, Object>();
				outProps.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
				outProps.put(WSHandlerConstants.USER, userId);
				outProps.put(WSHandlerConstants.PW_CALLBACK_REF, cpc);
				outProps.put(WSHandlerConstants.PASSWORD_TYPE, WSConstants.PW_TEXT);
				outProps.put(WSHandlerConstants.MUST_UNDERSTAND, "false");

				WSS4JOutInterceptor wssOut = new WSS4JOutInterceptor(outProps);

				Client c = ClientProxy.getClient(toReturn);
				Endpoint cxfEndpoint = c.getEndpoint();
				cxfEndpoint.getOutInterceptors().add(wssOut);
			}

			m_proxy = toReturn;
			
			// We try to perform a test method call (if it's defined)
			if (m_testMethod != null) {
				// If that call fails, we assume the connection to be down.
				try {
					m_testMethod.invoke(m_proxy, m_testMethodParameter);
				} catch (Exception e) {
					m_proxy = null;
					throw new FosstrakAleClientServiceDownException(e);
				}
			}
		}
		return m_proxy;
	}

	/**
	 * @return the key for the base name of the language files (eg /props/ALEClient). 
	 */
	abstract protected String getBaseNameKey(); 

	/**
	 * This method initializes the class, by loading properties and texts.
	 * 
	 * @throws FosstrakAleClientException when the language files could not be loaded.
	 */
	public void initialize() throws FosstrakAleClientException {
		
		String baseNameKey = getBaseNameKey();
		
		try {
			// get the base name (eg /props/ALEClient
			String baseName = FosstrakAleClient.instance().getConfiguration().getProperty(baseNameKey);
		
			// load text
			InputStream languageStream = null;
			
			// try to get language form property file
			String language = FosstrakAleClient.instance().getConfiguration().getLanguage();
			if (null != language) {
				languageStream = this.getClass().getResourceAsStream(String.format("%s_%s.lang", baseName, language));
			}
			
			// try system default language
			if (languageStream == null) {
				languageStream = this.getClass().getResourceAsStream(String.format("%s_%s.lang", baseName, SYSTEM_DEFAULT_LOCALE.getLanguage()));
			}
			
			// try default language
			if (languageStream == null) {
				languageStream = this.getClass().getResourceAsStream(String.format("%s_%s.lang", baseName, DEFAULT_LOCALE.getLanguage()));
			}
			
			if (languageStream == null) {
				throw new IllegalArgumentException("Could not load language package from classpath (" + DEFAULT_LOCALE + ")");
			}
			m_guiText = new PropertyResourceBundle(languageStream);        
			initializeGUI();
		} catch (Exception e) {
			throw new FosstrakAleClientException(e);
		}
	}
	
	/**
	 * This method initializes the gui and set it visible.
	 */
	protected void initializeGUI() {
		
		this.setLayout(new GridLayout());
		
		JLayeredPane contentPane = new JLayeredPane();
		contentPane.setFont(m_font);
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		add(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));
		
		JLayeredPane leftPane = new JLayeredPane();
		contentPane.add(leftPane, BorderLayout.WEST);
		leftPane.setFont(m_font);
		leftPane.setLayout(new BorderLayout());
		leftPane.add(createCommandSelectionPanel(), BorderLayout.NORTH);
		leftPane.add(m_commandSuperPanel, BorderLayout.CENTER);
		
		contentPane.add(m_resultScrollPane, BorderLayout.CENTER);
		
		m_commandSuperPanel.setLayout(new BorderLayout());
		m_commandSuperPanel.add(m_commandPanel, BorderLayout.NORTH);
		
		m_resultScrollPane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(m_guiText.getString("ResultPanelTitle")), BorderFactory.createEmptyBorder(5,5,5,5)));
		
		m_resultTextArea.setEditable(false);
		
		m_commandSuperPanel.setFont(m_font);
		m_commandPanel.setFont(m_font);
		m_resultTextArea.setFont(m_font);
		
		this.setVisible(true);
	}
	
	/**
	 * This method creates the command selection panel.
	 * 
	 * @return command selection panel
	 */
	protected JPanel createCommandSelectionPanel() {
		
		JPanel selectionPanel = new JPanel();
		selectionPanel.setLayout(new GridLayout(1,1));
		selectionPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(m_guiText.getString("SelectionPanelTitle")), BorderFactory.createEmptyBorder(5,5,5,5)));
		selectionPanel.setFont(m_font);		
		
		m_commandSelection.addItem(null);
		m_commandSelection.setFont(m_font);
		for (String item : getCommands()) {
			m_commandSelection.addItem(item);
		}
		
		m_commandSelection.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				if ("comboBoxChanged".equals(e.getActionCommand())) {
					setCommandPanel(m_commandSelection.getSelectedIndex());
				}				
			}
		});
		
		selectionPanel.add(m_commandSelection);
		selectionPanel.setPreferredSize(new Dimension(200, 55));
		
		return selectionPanel;
	}
	
	/**
	 * This method sets the command selection panel depending on the command id.
	 * 
	 * @param command id
	 */
	abstract protected void setCommandPanel(int command);
	
	/**
	 * This method returns the names of all commands.
	 * 
	 * @return command names
	 */
	abstract protected String[] getCommands();
	
	/**
	 * This method adds a execute button to the panel.
	 * 
	 * @param panel to which the execute button should be added
	 */
	protected void addExecuteButton(JPanel panel) {

		if (m_execButtonPanel == null) {

			JButton execButton = new JButton(m_guiText
					.getString("ExecuteButtonLabel"));
			execButton.setFont(m_font);
			execButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					executeCommand();
				}
			});

			m_execButtonPanel = new JPanel();
			m_execButtonPanel.setFont(m_font);
			m_execButtonPanel.setLayout(new GridLayout(1, 3));
			m_execButtonPanel.add(new JPanel());
			m_execButtonPanel.add(execButton);
			m_execButtonPanel.add(new JPanel());

		}

		panel.add(m_execButtonPanel);
	}
	
	/**
	 * This method executes the command which is selected in the command
	 * selection combobox with the parameters which are set in the corresponding
	 * fields. To execute the commands, the methods of the proxy will be
	 * invoked.
	 */
	abstract protected void executeCommand();
	
	/**
	 * This method adds a choose file field to the panel.
	 * 
	 * @param panel to which the choose file field should be added
	 */
	protected void addChooseFileField(JPanel panel) {

		m_filePathField = new JTextField();
		m_filePathField.setFont(m_font);
		
		final FileDialog fileDialog = new FileDialog(m_parent);
		fileDialog.setModal(true); 
		fileDialog.addComponentListener(
				new	ComponentAdapter() 
				{ 
					public void componentHidden(ComponentEvent e) 
					{
						if (fileDialog.getFile() != null) 
						{
							m_filePathField.setText(fileDialog.getDirectory() +	fileDialog.getFile()); 
						} 
					} 
				});
		 
		final JButton chooseFileButton = new JButton(m_guiText.getString("ChooseFileButtonLabel"));
		chooseFileButton.addActionListener(
				new ActionListener() 
				{ 
					public void	actionPerformed(ActionEvent e) 
					{ 
						fileDialog.setVisible(true); 
					} 
				});		  
		chooseFileButton.setFont(m_font);
		 
		JLabel lbl = new JLabel(m_guiText.getString("FilePathLabel"));
		lbl.setFont(m_font);
		panel.add(lbl);
		panel.add(m_filePathField); 
		panel.add(chooseFileButton);
	}
	
	/**
	 * This method adds a separator to the panel.
	 * 
	 * @param panel to which the separator should be added
	 */
	protected void addSeparator(JPanel panel) {	
		panel.add(new JPanel());
		panel.add(new JSeparator());		
	}
	
	/**
	 * This method displays the result in the result text area.
	 * 
	 * @param result to display
	 */
	protected void showResult(Object result) {

		m_resultTextArea.setText(null);
		if (result instanceof String) {
			m_resultTextArea.append((String) result);
		} if (result instanceof Exception) {
			Exception e = (Exception) result;
			StringBuffer sb = new StringBuffer();
			for (StackTraceElement line : e.getStackTrace()) {
				sb.append(line.toString());
				sb.append("\n");
			}
			m_resultTextArea.append(sb.toString());
		}else {
			StringBuffer sb = new StringBuffer();
			decodeResult(sb, result);
			m_resultTextArea.append(sb.toString());
		}
	}
	
	/**
	 * decodes the result object into the provided string buffer.
	 * @param sb the string buffer where to store the result.
	 * @param result the result object.
	 */
	abstract protected void decodeResult(StringBuffer sb, Object result);
}
