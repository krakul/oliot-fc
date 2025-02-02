/*
 *  
 *  Fosstrak LLRP Commander (www.fosstrak.org)
 * 
 *  Copyright (C) 2014 KAIST
 *	@author Janggwan Im <limg00n@kaist.ac.kr>
 * 
 *  Copyright (C) 2008 ETH Zurich
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/> 
 *
 */

package kr.ac.kaist.resl.fosstrak.ale;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;

import kr.ac.kaist.resl.ltk.net.LLRPAcceptor;
import kr.ac.kaist.resl.ltk.net.LLRPConnection;
import kr.ac.kaist.resl.ltk.net.LLRPConnectionAttemptFailedException;
import kr.ac.kaist.resl.ltk.net.LLRPConnector;
import kr.ac.kaist.resl.ltk.net.LLRPEndpoint;
import kr.ac.kaist.resl.ltk.net.LLRPIoHandlerAdapter;

import org.apache.log4j.Logger;
import org.apache.mina.core.session.IoSession;
import org.fosstrak.ale.server.readers.llrp.LLRPAdaptor;
import org.fosstrak.llrp.adaptor.AsynchronousNotifiable;
import org.fosstrak.llrp.adaptor.Constants;
import org.fosstrak.llrp.adaptor.ReaderMetaData;
import org.fosstrak.llrp.adaptor.exception.LLRPRuntimeException;
import org.fosstrak.llrp.adaptor.util.AsynchronousNotifiableList;
import org.fosstrak.llrp.client.LLRPExceptionHandlerTypeMap;
import org.llrp.ltk.exceptions.InvalidLLRPMessageException;
import kr.ac.kaist.resl.ltk.generated.LLRPMessageFactory;
import kr.ac.kaist.resl.ltk.generated.enumerations.KeepaliveTriggerType;
import kr.ac.kaist.resl.ltk.generated.messages.KEEPALIVE;
import kr.ac.kaist.resl.ltk.generated.messages.SET_READER_CONFIG;
import kr.ac.kaist.resl.ltk.generated.parameters.KeepaliveSpec;
import org.llrp.ltk.types.Bit;
import org.llrp.ltk.types.LLRPMessage;
import org.llrp.ltk.types.UnsignedInteger;

/**
 * This class implements the ReaderInterface. The Reader implementation 
 * maintains two queues to decouple the user interface from the actual message 
 * delivery over the network.<br/>
 * 1. from the user to the LLRP reader: the message to be sent is put into a 
 * queue. a queue watch-dog awakes as soon as there are messages in the queue 
 * and delivers them via LTK.<br/>
 * 2. from the LLRP reader to user: the incoming message from the reader is 
 * stored into a queue. a queue watch-dog awakes as soon as there are messages 
 * in the queue and delivers them to the user.
 * @author sawielan
 *
 */
public class ReaderImpl extends UnicastRemoteObject implements LLRPEndpoint, Reader {
	
	/**
	 * serial version.
	 */
	private static final long serialVersionUID = 1L;

	/** the logger. */
	private static Logger log = Logger.getLogger(ReaderImpl.class);
	
	/** the llrp connector to the physical reader. */
	private LLRPConnection connector = null;
	
	/** the adaptor where the reader belongs to. */
	private Adaptor adaptor = null;

	/** a list with all the receivers of asynchronous messages. */
	private AsynchronousNotifiableList toNotify = new AsynchronousNotifiableList();
	
	/** the default keep-alive interval for the reader. */
	public static final int DEFAULT_KEEPALIVE_PERIOD = 10000; 
	
	/** default how many times a keep-alive can be missed. */
	public static final int DEFAULT_MISS_KEEPALIVE = 3;
	
	/** flag whether to throw an exception when a timeout occurred. */
	private boolean throwExceptionKeepAlive = true;
	
	/** meta-data about the reader, if connection is up, number of packages, etc... */
	private ReaderMetaData metaData = new ReaderMetaData();
	
	/** IO handler. */
	private LLRPIoHandlerAdapter handler = null;
	
	/** handle to the connection watch-dog. */
	private Thread wd = null;
	
	/** handle to the out queue worker. */
	private Thread outQueueWorker = null;
	
	/** handle to the in queue worker. */
	private Thread inQueueWorker = null;
	
	/** queue to hold the incoming messages.*/
	private LinkedList<byte[]> inqueue = new LinkedList<byte[]> ();
	
	/** queue to hold the outgoing messages. */
	private LinkedList<LLRPMessage> outqueue = new LinkedList<LLRPMessage> ();
	
	/** queue policies. */
	public enum QueuePolicy {
		DROP_QUEUE_ON_ERROR,
		KEEP_QUEUE_ON_ERROR
	};
	
	/**
	 * ReaderImpl and LLRPAdaptor have 1:1 correspondence. 
	 * This provides the link to the LLRPAdaptor
	 */
	private LLRPAdaptor llrpAdaptor = null;
	
	/**
	 * Session for send LLRP message
	 */
	
	private IoSession ioSession = null;
	
	/**
	 * constructor for a local reader stub. the stub maintains connection
	 * to the llrp reader.
	 * @param adaptor the adaptor responsible for this reader.
	 * @param readerName the name of this reader.
	 * @param readerAddress the address where to connect.
	 * @throws RemoteException whenever there is an RMI exception
	 */
	public ReaderImpl(Adaptor adaptor, String readerName, String readerAddress) throws RemoteException {
		this.adaptor = adaptor;
		metaData._setAllowNKeepAliveMisses(DEFAULT_MISS_KEEPALIVE);
		metaData._setKeepAlivePeriod(DEFAULT_KEEPALIVE_PERIOD);
		metaData._setReaderName(readerName);
		metaData._setReaderAddress(readerAddress);
	}
	
	/**
	 * constructor for a local reader stub. the stub maintains connection
	 * to the llrp reader.
	 * @param adaptor the adaptor responsible for this reader.
	 * @param readerName the name of this reader.
	 * @param readerAddress the address where to connect.
	 * @param port the port where to connect.
	 * @throws RemoteException whenever there is an RMI exception
	 */
	public ReaderImpl(Adaptor adaptor, String readerName, String readerAddress, int port) throws RemoteException {
		this.adaptor = adaptor;
		metaData._setAllowNKeepAliveMisses(DEFAULT_MISS_KEEPALIVE);
		metaData._setKeepAlivePeriod(DEFAULT_KEEPALIVE_PERIOD);
		metaData._setReaderName(readerName);
		metaData._setReaderAddress(readerAddress);
		metaData._setPort(port);
	}
	
	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#connect(boolean)
	 */
	public void connect(boolean clientInitiatedConnection) throws LLRPRuntimeException, RemoteException {
		
		try {
			String address = metaData.getReaderAddress();
			metaData._setClientInitiated(clientInitiatedConnection);
			
			// start a new counter session
			metaData._newSession();
			
			if (metaData.getPort() == -1) {
				metaData._setPort(Constants.DEFAULT_LLRP_PORT);
				log.warn("port for reader '" + metaData.getReaderName() + "' not specified. using default port " + metaData.getPort());
			}
			
			if (clientInitiatedConnection) {
				if (address == null) {
					log.error("address for reader '" + metaData.getReaderName() + "' is empty!");
					reportException(new LLRPRuntimeException("address for reader '" + metaData.getReaderName() + "' is empty!"));
					return;
				}
					
				// run ltk connector.
				LLRPConnector connector = new LLRPConnector(this, address, metaData.getPort());
				connector.getHandler().setKeepAliveAck(true);
				connector.getHandler().setKeepAliveForward(true);
				try {
					connector.connect();
				} catch (LLRPConnectionAttemptFailedException e) {
					log.error("connection attempt to reader " + metaData.getReaderName() + " failed");
					reportException(new LLRPRuntimeException("connection attempt to reader " + metaData.getReaderName() + " failed"));
				}
				
				this.connector = connector;
			} else {
				
				// do nothing
				
				// in case of reader-initiated connection,
				// LogicalReaderAcceptor already did binding, and
				// LLRPConnection is already established
				
			}
			metaData._setConnected(true);
			
			outQueueWorker = new Thread(getOutQueueWorker());
			outQueueWorker.start();
			
			inQueueWorker = new Thread(getInQueueWorker());
			inQueueWorker.start();
	
			// only do heart beat in client initiated mode.
			if (clientInitiatedConnection) {
				enableHeartBeat();
			}
			log.info(String.format("reader %s connected.", metaData.getReaderName()));
			
		} catch (Exception e) {
			// catch all unexpected errors...
			LLRPRuntimeException ex = new LLRPRuntimeException(
					String.format("Could not connect to reader %s on adapter %s:\nException: %s",
							getReaderName(), adaptor.getAdaptorName(), e.getMessage()));
			reportException(ex);
			throw ex;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#disconnect()
	 */
	public void disconnect() throws RemoteException {
		log.debug("disconnecting the reader.");
		setReportKeepAlive(false);
		
		if (connector != null) {
			try {
				if (connector instanceof LLRPConnector) {
					// disconnect from the reader.
					((LLRPConnector)connector).disconnect();
				} else if (connector instanceof LLRPAcceptor) {
					// close the acceptor.
					((LLRPAcceptor)connector).close();
				}
			} catch (Exception e) {
				connector = null;
			}
		}
		
		metaData._setConnected(false);
		
		// stop the outqueue worker
		if (null != outQueueWorker) {
			outQueueWorker.interrupt();
		}
		
		// stop the inqueue worker
		if (null != inQueueWorker) {
			inQueueWorker.interrupt();
		}
		
		// stop the connection watch-dog.
		if (null != wd) {
			wd.interrupt();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#reconnect()
	 */
	public void reconnect() throws LLRPRuntimeException, RemoteException  {
		// first try to disconnect
		disconnect();
		connect(isClientInitiated());
	}
	
	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#send(byte[])
	 */
	public void send(byte[] message) throws RemoteException {
		if (!metaData.isConnected() || (connector == null)) {
			reportException(new LLRPRuntimeException(String.format("reader %s is not connected", metaData.getReaderName())));
			return;
		}
		
		// try to create the llrp message from the byte array.
		LLRPMessage llrpMessage = null;
		try {
			llrpMessage = LLRPMessageFactory.createLLRPMessage(message);
		} catch (InvalidLLRPMessageException e) {
			reportException(new LLRPRuntimeException(e.getMessage()));
		}
		
		if (llrpMessage == null) {
			log.warn(String.format("do not send empty llrp message on reader %s", metaData.getReaderName()));
			return;
		}
		
		// put the message into the outqueue
		synchronized (outqueue) {
			outqueue.add(llrpMessage);
			outqueue.notifyAll();
		}
	}
	
	/**
	 * performs the actual sending of the LLRP message to the reader.
	 * @param llrpMessage the LLRP message to be sent.
	 * @throws RemoteException at RMI exception.
	 */
	private void sendLLRPMessage(LLRPMessage llrpMessage) throws RemoteException {
		try {
			// send the message asynchronous.
			connector.send(llrpMessage);
			//ioSession.write(llrpMessage); // TODO Oliot project used this instead connector.send
			metaData._packageSent();
		} catch (NullPointerException npe) {
			// a null-pointer exception occurs when the reader is no more connected.
			// we therefore report the exception to the GUI.
			disconnect();
			reportException(new LLRPRuntimeException(String.format("reader %s is not connected", metaData.getReaderName()),
					LLRPExceptionHandlerTypeMap.EXCEPTION_READER_LOST));
		} catch (Exception e) {
			// just to be sure...
			disconnect();			
		}		
	}

	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#isConnected()
	 */
	public boolean isConnected() throws RemoteException {
		return metaData.isConnected();
	}

	/**
	 * when there is an error, the ltk will call this method.
	 * @param message the error message from ltk.
	 */
	public void errorOccured(String message) {
		reportException(new LLRPRuntimeException(message));
	}

	/**
	 * when a message arrives through ltk, this method is called.
	 * @param message the llrp message delivered by ltk.
	 */
	public void messageReceived(LLRPMessage message) {
		if (message == null) {
			return;
		}
		byte[] binaryEncoded;
		try {
			binaryEncoded = message.encodeBinary();
		} catch (InvalidLLRPMessageException e1) {
			reportException(new LLRPRuntimeException(e1.getMessage()));
			return;
		}
		metaData._packageReceived();
		if (message instanceof KEEPALIVE) {
			
			metaData._setAlive(true);
			log.debug("received keepalive message from the reader:" + metaData.getReaderName());
			if (!metaData.isReportKeepAlive()) {
				return;
			}
		}
		
		// put the message into the inqueue.
		synchronized (inqueue) {
			inqueue.add(binaryEncoded);
			inqueue.notifyAll();
		}
	}

	/**
	 * deliver a received message to the handlers.
	 * @param binaryEncoded the binary encoded LLRP message.
	 */
	private void deliverMessage(byte[] binaryEncoded) {
		try {
			adaptor.messageReceivedCallback(binaryEncoded, metaData.getReaderName());
		} catch (RemoteException e) {
			reportException(new LLRPRuntimeException(e.getMessage()));
		}
		
		// also notify all the registered notifyables.
		try {
			toNotify.notify(binaryEncoded, metaData.getReaderName());
		} catch (RemoteException e) {
			reportException(new LLRPRuntimeException(e.getMessage()));
		}
	}

	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#getReaderAddress()
	 */
	public String getReaderAddress() throws RemoteException {
		return metaData.getReaderAddress();
	}

	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#getPort()
	 */
	public int getPort() throws RemoteException {
		return metaData.getPort();
	}
	
	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#isClientInitiated()
	 */
	public boolean isClientInitiated() throws RemoteException {
		return metaData.isClientInitiated();
	}
	
	public void setClientInitiated(boolean clientInitiated)
			throws RemoteException {
		metaData._setClientInitiated(clientInitiated);
	}

	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#registerForAsynchronous(org.fosstrak.llrp.adaptor.AsynchronousNotifiable)
	 */
	public void registerForAsynchronous(AsynchronousNotifiable receiver) throws RemoteException {
		toNotify.add(receiver);
	}
	
	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.ReaderIface#deregisterFromAsynchronous(org.fosstrak.llrp.adaptor.AsynchronousNotifiable)
	 */
	public void deregisterFromAsynchronous(AsynchronousNotifiable receiver) throws RemoteException {
		toNotify.remove(receiver);
	}
	public String getReaderName() throws RemoteException {
		return metaData.getReaderName();
	}

	public boolean isConnectImmediate() throws RemoteException {
		return metaData.isConnectImmediately();
	}

	public void setConnectImmediate(boolean value) throws RemoteException {
		metaData._setConnectImmediately(value);
	}

	/**
	 * reports an exception the the adaptor. if the reporting of the 
	 * exception also fails, the stack-trace gets logged but the 
	 * reader continues to work.
	 * @param e the exception to report.
	 */
	private void reportException(LLRPRuntimeException e) {
		if (adaptor == null) {
			log.error("no adaptor to report exception to on reader: " + metaData.getReaderName());
			return;
		}
		try {
			adaptor.errorCallback(e, metaData.getReaderName());
		} catch (RemoteException e1) {
			// print the stacktrace to the console
			log.debug(e1.getStackTrace().toString());
		}
	}
	
	/**
	 * sends a SET_READER_CONFIG message that sets the keepalive value. 
	 */
	private void enableHeartBeat() {		
		// build the keepalive settings
		SET_READER_CONFIG sr = new SET_READER_CONFIG();
		KeepaliveSpec ks = new KeepaliveSpec();
		ks.setKeepaliveTriggerType(new KeepaliveTriggerType(KeepaliveTriggerType.Periodic));
		ks.setPeriodicTriggerValue(new UnsignedInteger(metaData.getKeepAlivePeriod()));
		
		sr.setKeepaliveSpec(ks);
		sr.setResetToFactoryDefault(new Bit(0));
		
		log.debug(String.format("using keepalive periode: %d", metaData.getKeepAlivePeriod()));		
		try {
			send(sr.encodeBinary());
		} catch (RemoteException e) {
			if (throwExceptionKeepAlive) {
				reportException(new LLRPRuntimeException("Could not install keepalive message: " + e.getMessage()));
			} else {
				e.printStackTrace();
			}
		} catch (InvalidLLRPMessageException e) {
			e.printStackTrace();
		}
		
		// run the watch-dog
		wd = new Thread(new Runnable() {
			public void run() {
				log.debug("starting connection watchdog.");
				try {
					while (isConnected()) {
						try {
							Thread.sleep(metaData.getAllowNKeepAliveMisses() * metaData.getKeepAlivePeriod());
							if (!metaData.isAlive()) {
								log.debug("connection timed out...");
								disconnect();
								if (throwExceptionKeepAlive) {
									reportException(new LLRPRuntimeException("Connection timed out",
											LLRPExceptionHandlerTypeMap.EXCEPTION_READER_LOST));
								}
							}
							metaData._setAlive(false);
						} catch (InterruptedException e) {
							log.debug("received interrupt - stopping watchdog.");
						}
						
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				log.debug("connection watchdog stopped.");
			}
		});
		wd.start();
	}

	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.Reader#getKeepAlivePeriod()
	 */
	public int getKeepAlivePeriod() throws RemoteException {
		return metaData.getKeepAlivePeriod();
	}

	/* (non-Javadoc)
	 * @see org.fosstrak.llrp.adaptor.Reader#setKeepAlivePeriod(int, int, boolean, boolean)
	 */
	public void setKeepAlivePeriod(int keepAlivePeriod, int times,
			boolean report, boolean throwException) throws RemoteException {
		
		metaData._setKeepAlivePeriod(keepAlivePeriod);
		metaData._setAllowNKeepAliveMisses(times);
		metaData._setReportKeepAlive(report);
		this.throwExceptionKeepAlive = throwException;
		
	}

	public void setReportKeepAlive(boolean report) throws RemoteException {
		metaData._setReportKeepAlive(report);
	}

	public boolean isReportKeepAlive() throws RemoteException {
		return metaData.isReportKeepAlive();
	}

	public final ReaderMetaData getMetaData() throws RemoteException {
		return new ReaderMetaData(metaData);
	}
	
	/**
	 * creates a runnable that watches the out queue for new messages to be 
	 * sent. at arrival of a new message, the message is sent via LTK.
	 * @return a runnable.
	 */
	private Runnable getOutQueueWorker() {
		final LinkedList<LLRPMessage> queue = outqueue;
		return new Runnable() {
			public void run() {
				try {
					while (true) {
						synchronized (queue) {
							while (queue.isEmpty()) queue.wait();
							
							LLRPMessage msg = queue.removeFirst();
							try {
								sendLLRPMessage(msg);
							} catch (RemoteException e) {
								log.debug(String.format(
										"Could not send message: %s", 
										e.getMessage()));
							}
						}
					}
				} catch (InterruptedException e) {
					log.debug("stopping out queue worker.");
				}
			}
			
		};
	}
	
	/**
	 * creates a runnable that watches the in queue for new messages and at 
	 * arrival, delivers them to the management.
	 * @return a runnable.
	 */
	private Runnable getInQueueWorker() {
		final LinkedList<byte[]> queue = inqueue;
		return new Runnable() {
			public void run() {
				try {
					while (true) {
						synchronized (queue) {
							while (queue.isEmpty()) queue.wait();
							
							byte[] msg = queue.removeFirst();
							deliverMessage(msg);
						}
					}
				} catch (InterruptedException e) {
					log.debug("stopping in queue worker.");
				}
			}			
		};
	}

	/*
	public void setConnection(LLRPConnection conn) {
		this.connector = conn; 
	}

	
	public LLRPConnection getConnection() {
		return connector;
	}*/

	public LLRPAdaptor getLlrpAdaptor() {
		return llrpAdaptor;
	}

	public void setLlrpAdaptor(LLRPAdaptor llrpAdaptor) {
		this.llrpAdaptor = llrpAdaptor;
	}

	public IoSession getIoSession() {
		return ioSession;
	}

	public void setIoSession(IoSession ioSession) {
		this.ioSession = ioSession;
	}

}
