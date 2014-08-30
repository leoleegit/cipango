// ========================================================================
// Copyright 2011 NEXCOM Systems
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================
package org.cipango.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.sip.SipURI;

import org.cipango.server.Server;
import org.cipango.server.SipConnection;
import org.cipango.server.SipConnector;
import org.cipango.server.SipConnectors;
import org.cipango.server.SipHandler;
import org.cipango.server.SipMessage;
import org.cipango.server.bio.UdpConnector.EventHandler;
import org.cipango.sip.SipParser;
import org.cipango.sip.SipURIImpl;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.websocket.WebSocket;
import org.zoolu.sip.address.SipURL;
import org.zoolu.sip.header.ContactHeader;
import org.zoolu.sip.header.Header;
import org.zoolu.sip.header.MultipleHeader;
import org.zoolu.sip.header.SipHeaders;
import org.zoolu.sip.header.ViaHeader;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipProviderListener;

public class WebSocketConnector extends AbstractLifeCycle implements SipConnector
{
	private static final Logger LOG = Log.getLogger(WebSocketConnection.class);
	
	public static final int WS_DEFAULT_PORT = 80;
	public static final int WSS_DEFAULT_PORT = 443;
	
	private Connector _httpConnector;
	private InetAddress _localAddr;
	private Map<String, WebSocketConnection> _connections;
	private SipURI _sipUri;
    private SipHandler _handler;
    private Server _server;
    private ThreadPool _threadPool;
	
    private LinkedList<Integer> availabeSIPPorts;
    
    private int startSIPPort = 5070;  
    private int numSIPPorts  = 500; 
    private int sipStep      = 2;
    
	public WebSocketConnector(Connector connector)
	{
		_httpConnector = connector;
		try
		{
			_localAddr =  InetAddress.getByName(_httpConnector.getHost());
		}
		catch (Exception e) 
		{
			LOG.warn(e);
		}
		_connections = new HashMap<String, WebSocketConnection>();
	}
	
	@Override
    protected void doStart() throws Exception 
    {    	
    	_sipUri = new SipURIImpl(null, getHost(), getPort());
    	_sipUri.setTransportParam(getTransport().toLowerCase());
    	
    	if (_threadPool == null && _server != null)
        	_threadPool = _server.getSipThreadPool();
    	
        open();
        availabeSIPPorts = new LinkedList<Integer>();
        initAvaliablePorts(availabeSIPPorts, startSIPPort, numSIPPorts, sipStep);
        LOG.info("Started {}", this);
    }
	
	public void open() throws IOException
	{
	}

	public void close() throws IOException
	{
		// FIXME close connections ???
	}
	
	public String getHost()
	{
		return _httpConnector.getHost();
	}

	public int getPort()
	{
		return _httpConnector.getPort();
	}

	public String getExternalHost()
	{
		return null;
	}

	public String getTransport()
	{
		if (isSecure())
			return SipConnectors.WSS;
		return SipConnectors.WS;
	}

	public SipURI getSipUri()
	{
		return _sipUri;
	}

	public void setServer(Server server)
	{
		_server = server;
	}

	public void setHandler(SipHandler handler)
	{
		_handler = handler;
	}

	public long getNbParseError()
	{
		return 0;
	}

	public void setStatsOn(boolean on)
	{
	}

	public void statsReset()
	{
	}

	public InetAddress getAddr()
	{
		return _localAddr;
	}

	public int getLocalPort()
	{
		return _httpConnector.getLocalPort();
	}

	public Object getConnection()
	{
		// TODO Auto-generated method stub
		return null;
	}

	public int getTransportOrdinal()
	{
		if (isSecure())
			return SipConnectors.WSS_ORDINAL;
		return SipConnectors.WS_ORDINAL;
	}

	public int getDefaultPort()
	{
		if (isSecure())
			return WSS_DEFAULT_PORT;
		return WS_DEFAULT_PORT;
	}
	
    public ThreadPool getThreadPool()
    {
        return _threadPool;
    }
    
    public void setThreadPool(ThreadPool threadPool)
    {
    	_threadPool = threadPool;
    }

    public void process(SipMessage message)
    {
    	if (!isRunning())
    		return;
    	
    	if (!getThreadPool().dispatch(new MessageTask(message)))
		{
    		LOG.warn("No threads to dispatch message from {}:{}",
					message.getRemoteAddr(), message.getRemotePort());
		}
    }
	
	public boolean isReliable()
	{
		return true;
	}

	public boolean isSecure()
	{
		return false; // FIXME todo
	}

	public SipConnection getConnection(InetAddress addr, int port) throws IOException
	{
		synchronized (_connections)
		{
			return _connections.get(key(addr, port));
		}
	}
	
	public WebSocketConnection addConnection(HttpServletRequest request)
	{
		WebSocketConnection connection = new WebSocketConnection(request);
		synchronized (_connections)
		{
			_connections.put(key(connection), connection);
		}
		return connection;
	}
	
	public void removeConnection(WebSocketConnection connection)
	{
		synchronized (_connections)
		{
			_connections.put(key(connection), connection);
		}
	}
	
	private String key(WebSocketConnection connection) 
	{
		return key(connection.getRemoteAddress(), connection.getRemotePort());
	}
	
	private String key(InetAddress addr, int port) 
	{
		return addr.getHostAddress() + ":" + port;
	}

	
	public int getStartSIPPort() {
		return startSIPPort;
	}

	public void setStartSIPPort(int startSIPPort) {
		this.startSIPPort = startSIPPort;
	}

	public int getNumSIPPorts() {
		return numSIPPorts;
	}

	public void setNumSIPPorts(int numSIPPorts) {
		this.numSIPPorts = numSIPPorts;
	}

	public int getSipStep() {
		return sipStep;
	}

	public void setSipStep(int sipStep) {
		this.sipStep = sipStep;
	}


	class WebSocketConnection implements SipConnection, WebSocket.OnTextMessage, SipProviderListener
	{		
		private InetAddress _localAddr;
		private int _localPort;
		private InetAddress _remoteAddr;
		private int _remotePort;
		private Connection _connection;
		
		private SipProvider provider;
		
		public WebSocketConnection(HttpServletRequest request)
		{
			try
			{
				_localAddr = InetAddress.getByName(request.getLocalAddr());
				_localPort = request.getLocalPort();
				_remoteAddr = InetAddress.getByName(request.getRemoteAddr());
				_remotePort = request.getRemotePort();
			}
			catch (Exception e) 
			{
				LOG.warn(e);
			}
		}
		
		
		public SipConnector getConnector()
		{
			return WebSocketConnector.this;
		}

		public InetAddress getLocalAddress()
		{
			return _localAddr;
		}

		public int getLocalPort()
		{
			return _localPort;
		}

		public InetAddress getRemoteAddress()
		{
			return _remoteAddr;
		}

		public int getRemotePort()
		{
			return _remotePort;
		}

		public void write(Buffer buffer) throws IOException
		{
			write(buffer.toString());
		}

		public boolean isOpen()
		{
			return _connection != null;
		}


		public void onOpen(Connection connection)
		{
			_connection = connection;
			int port = allocPorts(availabeSIPPorts);
			String[] protocols = {SipProvider.PROTO_UDP};
			provider = new SipProvider(getHost(),port, protocols,null);
			provider.addSipProviderListener(this);
		}


		public void onClose(int closeCode, String message)
		{
			releasePorts(availabeSIPPorts, provider.getPort());
			if(provider!=null)
				provider.halt();
			_connection = null;
			provider = null;
			removeConnection(this);
		}


		public void onMessage(String data)
		{  
			org.zoolu.sip.message.BaseMessage sipMessage = new org.zoolu.sip.message.Message(data);
			data = checkMessage(sipMessage).toString();
			
			Buffer buffer = new ByteArrayBuffer(data.getBytes());
			
			EventHandler handler = new EventHandler();
			SipParser parser = new SipParser(buffer, handler);
			
			try
			{
				parser.parse();
				
				SipMessage message = handler.getMessage();
				message.setConnection(this);			
				process(message);
			}
			catch (Throwable t) 
			{
				LOG.warn(t);
				//if (handler.hasException())
					//Log.warn(handler.getException());
	        
				if (LOG.isDebugEnabled())
					LOG.debug("Buffer content: \r\n" + data);

			}
		}

		MultipleHeader contacts;  
		MultipleHeader vias; 
		private org.zoolu.sip.message.BaseMessage checkMessage(org.zoolu.sip.message.BaseMessage message) {
			if(message.isRegister()){
				try{
					MultipleHeader contacts_ = new MultipleHeader(SipHeaders.Contact);		
					contacts  = message.getContacts();
					vias  	  = message.getVias();
					message.removeContacts();
					Vector<?> vh = contacts.getHeaders();
					Iterator<?> it = vh.iterator();
					while(it.hasNext()){
						Header header = (Header) it.next();
						ContactHeader contact = new ContactHeader(header);
						ContactHeader contact_ = new ContactHeader(new SipURL(contact.getNameAddress().getAddress().getUserName(),getHost(),provider.getPort()));
						Vector<?> vp = contact.getParameterNames();
						Iterator<?> vt = vp.iterator();
						while(vt.hasNext()){
							String parameter_name = (String) vt.next(); 
							String parameter_value = contact.getParameter(parameter_name);
							contact_.setParameter(parameter_name, parameter_value);
						}
						contacts_.addBottom(contact_);
					}
					message.setContacts(contacts_);
					
					MultipleHeader vias_ = new MultipleHeader(SipHeaders.Via);		
					message.removeVias();
					ViaHeader via=new ViaHeader(SipProvider.PROTO_UDP,getHost(),provider.getPort());
					via.setRport();
				    via.setBranch(SipProvider.pickBranch());
					vias_.addBottom(via);
					message.setVias(vias_);
				}catch(Exception e){
					LOG.warn(e);
				}			
			}
			return message;
		}
		
		private String checkMessage(String string) {
			// TODO Auto-generated method stub
			Message message = new Message(string);
			if(message.hasContactHeader() && contacts!=null){
				message.removeContacts();
				message.setContacts(contacts);
			}
			if(message.hasViaHeader() && vias!=null){
				message.removeVias();
				message.setVias(vias);
			}
			return message.toString();
		}
		
		public void onReceivedMessage(SipProvider arg0, Message arg1) {
			// TODO Auto-generated method stub
			try {
				write(arg1.toString());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				LOG.debug("Ignore notify lost connection", e);
			}
		}


		private void write(String msg)  throws IOException
		{
			_connection.sendMessage(checkMessage(msg));
		}	
	}
	
	class MessageTask implements Runnable
    {
    	private SipMessage _message;
    	
    	public MessageTask(SipMessage message)
    	{
    		_message = message;
    	}
    	
    	public void run()
    	{
    		try 
    		{
    			_handler.handle(_message);
    		}
    		catch (Exception e)
    		{
    			LOG.warn(e);
    		}
    	}
    }
	
	private void initAvaliablePorts(LinkedList<Integer> list, int base, int size, int step)
	{	
		if (base <= 1024)
		{
			throw new IllegalArgumentException("Invalid base port: " + base);
		}

		if (step <= 0)
		{
			throw new IllegalArgumentException("Invalid port step: " + step);
		}
		
		if ((base + size * step) > 0xffff)
		{
			throw new IllegalArgumentException("Invalid port range");
		}

		int port = base;
		
		while (size-- > 0)
		{
			list.add(port);
			
			port += step;
		}
	}
	
	private Integer allocPorts(LinkedList<Integer> availablePorts) throws NoSuchElementException
	{
		Integer port = 0;
		
		if(availablePorts.size()!=0)
			synchronized (availablePorts)
			{
				port = availablePorts.remove();
				LOG.info("Alloc port number:" + port);
			}
		return port;
	}
	
	private void releasePorts(LinkedList<Integer> availablePorts, Integer port)
	{
		synchronized (availablePorts)
		{
			availablePorts.add(0,port);
			LOG.info("Release port number:" + port);
		}
	}
}
