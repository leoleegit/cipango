// ========================================================================
// Copyright 2008-2009 NEXCOM Systems
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

package org.cipango.server.bio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.cipango.server.AbstractSipConnector;
import org.cipango.server.SipConnection;
import org.cipango.server.SipConnector;
import org.cipango.server.SipConnectors;
import org.cipango.server.SipMessage;
import org.cipango.server.transaction.Transaction;
import org.cipango.sip.BufferOverflowException;
import org.cipango.sip.SipParser;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.io.bio.SocketEndPoint;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;

public class TcpConnector extends AbstractSipConnector //implements Buffers
{
	private static final Logger LOG = Log.getLogger(TcpConnector.class);
	
	public static final int DEFAULT_PORT = 5060;
	public static final boolean RELIABLE = true;
	
	public static final int DEFAULT_TCP_MESSAGE = 1024 * 2;
	public static final int MAX_TCP_MESSAGE = 1024 * 400;
	
	public static final int DEFAULT_SO_TIMEOUT = 2 * Transaction.__T1 * 64;
    
    private ServerSocket _serverSocket;
    private InetAddress _addr;
    private Map<String, TcpConnection> _connections;
    private int _connectionTimeout = DEFAULT_SO_TIMEOUT;
    private int _backlogSize = 50;
    
    private ThreadPool _tcpThreadPool;
    private List<Buffer> _buffers = new ArrayList<Buffer>();
		
	protected void doStart() throws Exception 
	{
		_connections = new HashMap<String, TcpConnection>();
		
        if (_tcpThreadPool == null)
        	_tcpThreadPool = new QueuedThreadPool();
        
		if (_tcpThreadPool instanceof LifeCycle)
			((LifeCycle) _tcpThreadPool).start();
		
		super.doStart();
	}
	
	protected void doStop() throws Exception
	{
		super.doStop();
		
		if (_tcpThreadPool instanceof LifeCycle)
            ((LifeCycle)_tcpThreadPool).stop();
		
		Object[]  connections = _connections.values().toArray();
		for (Object o : connections)
		{
			TcpConnection connection =  (TcpConnection) o;
			try
			{
				connection.close();
			} 
			catch (Exception e) 
			{
				LOG.ignore(e);
			}
		}
	}
	
	public InetAddress getAddr()
	{
		return _addr;
	}
	
	public ThreadPool getTcpThreadPool()
	{
		return _tcpThreadPool;
	}
	
	public void open() throws IOException
	{
		_serverSocket = newServerSocket();
		_addr = _serverSocket.getInetAddress();
	}
	
	public int getLocalPort()
	{
		if (_serverSocket==null || _serverSocket.isClosed())
            return -1;
        return _serverSocket.getLocalPort();
	}
	
	public Object getConnection()
	{
		return _serverSocket;
	}
	
	public ServerSocket newServerSocket() throws IOException
	{
		if (getHost() == null) 
			return new ServerSocket(getPort(), _backlogSize);
		else
			return new ServerSocket(
					getPort(), 
					_backlogSize, 
					InetAddress.getByName(getHost()));
	}
	
	public void close() throws IOException 
	{
		if (_serverSocket != null)
			_serverSocket.close();
		_serverSocket = null;
	}
	
	public void accept(int acceptorId) throws IOException, InterruptedException
	{
		Socket socket = _serverSocket.accept();
		TcpConnection connection = new TcpConnection(socket);
		addConnection(socket.getInetAddress(), socket.getPort(), connection);
		connection.dispatch();
	}
	
	protected void addConnection(InetAddress host, int port, TcpConnection connection)
	{
		synchronized (_connections)
		{
			_connections.put(key(host, port), connection);
		}
	}

	protected ServerSocket getServerSocket()
	{
		return _serverSocket;
	}
	
	public Buffer getBuffer(int size) 
    {
        synchronized (_buffers)
        {
            if (_buffers.size() == 0)
            {
                //System.out.println("Creating new buffer");
                return newBuffer(10);
            }
            return (Buffer) _buffers.remove(_buffers.size() - 1);
        }
    }
    
    public void returnBuffer(Buffer buffer)
    {
        synchronized (_buffers)
        {
            buffer.clear();
            _buffers.add(buffer);
        }
    }
    
	public Buffer newBuffer(int size) 
	{
		return new ByteArrayBuffer(size);
	}

	public int getDefaultPort() 
	{
		return DEFAULT_PORT;
	}

	public boolean isReliable() 
	{
		return RELIABLE;
	}
	
	public boolean isSecure()
	{
		return false;
	}

	public int getTransportOrdinal() 
	{
		return SipConnectors.TCP_ORDINAL;
	}

	public SipConnection getConnection(InetAddress addr, int port) throws IOException 
	{
		synchronized (_connections) // TODO check blocked
		{
			TcpConnection cnx = _connections.get(key(addr, port));
			if (cnx == null) 
			{
				cnx = newConnection(addr, port);
				addConnection(addr, port, cnx);
				cnx.dispatch();
			}
			return cnx;
		}
	}
	
	protected TcpConnection newConnection(InetAddress addr, int port) throws IOException
	{
		return new TcpConnection(new Socket(addr, port));
	}
	
	protected Map<String, TcpConnection> getConnections()
	{
		return _connections;
	}
	
	public void connectionOpened(TcpConnection connection)
	{
		
	}
	
	public void connectionClosed(TcpConnection connection) 
	{
		synchronized (_connections) 
		{
			_connections.remove(connection.getRemoteAddr() + ":" + connection.getRemotePort());
		}
	}
	
	private String key(InetAddress addr, int port) 
	{
		return addr.getHostAddress() + ":" + port;
	}
	
	public int getBacklogSize()
	{
		return _backlogSize;
	}

	public void setBacklogSize(int backlogSize)
	{
		_backlogSize = backlogSize;
	}
	
	public int getConnectionTimeout()
	{
		return _connectionTimeout;
	}

	public void setConnectionTimeout(int connectionTimeout)
	{
		_connectionTimeout = connectionTimeout;
	}
	
	public class TcpConnection extends SocketEndPoint implements SipConnection, Runnable
	{
		private InetAddress _local;
		private InetAddress _remote;
		
		public TcpConnection(Socket socket) throws IOException 
		{
			super(socket);
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(_connectionTimeout);
			
			_local = socket.getLocalAddress();
			_remote = socket.getInetAddress(); 
		}
		
		public void dispatch() throws IOException
        {
            if (!getTcpThreadPool().dispatch(this))
            {
                LOG.warn("dispatch failed for {}", this);
                close();
            }
        }
		
		public InetAddress getLocalAddress()
		{
			return _local;
		}
		
		public InetAddress getRemoteAddress()
		{
			return _remote;
		}
		

		public void write(Buffer buffer) throws IOException 
		{
			synchronized (this)
			{
				int nb = super.flush(buffer);
				flush();
				//return nb;
			}
		}
		
		public void run()
		{
			EventHandler handler = new EventHandler();
			Buffer buffer = newBuffer(DEFAULT_TCP_MESSAGE);
			
			SipParser parser = new SipParser(buffer, this, handler);

			try 
			{
				connectionOpened(this);
				
				SipMessage message = null;
				
				while (isStarted() && !isClosed())
				{
					int size = DEFAULT_TCP_MESSAGE;
					boolean overflow = false;
						
					do
					{
						overflow = false;
						try
						{
							parser.parse();
							size = DEFAULT_TCP_MESSAGE;
							parser.setBuffer(newBuffer(size));
						}
						catch (BufferOverflowException e)
						{
							//System.out.println("Overflow");
							overflow = true;
							size = size * 2;
							if (size > MAX_TCP_MESSAGE)
								throw new IOException("Message too large");
							Buffer extended = newBuffer(size);
							parser.setBuffer(extended, true);
						}
					} 
					while (overflow);
					
					message = handler.getMessage();
					message.setConnection(this);

					/*message.set5uple(
							getTransportOrdinal(), 
							getLocalAddress(),
							getLocalPort(),
							getRemoteAddress(), 
							getRemotePort());
					
					if (message.isRequest())
						((SipRequest) message).setEndpoint(this);*/
					
					process(message);
				} 
			} 
			catch (EofException e)
			{
				//System.out.println(parser.getState());
				LOG.debug("EOF: {}", this);
				try 
				{
					close();
				} 
				catch (IOException e2)
				{
					LOG.ignore(e2);
				}
			} 
			
			catch (Throwable e) 
			{
				System.out.println(parser.getState());
				if (_statsStartedAt != -1) 
				{
					synchronized (_statsLock) 
					{
						_nbParseErrors++;
					}
				}
				LOG.warn("TCP handle failed", e);
				if (handler.hasException())
					LOG.warn(handler.getException());
				try 
				{
					close();
				} 
				catch (IOException e2) 
				{
					LOG.ignore(e2);
				}
			} 
			finally 
			{
				connectionClosed(this);
			}
		}
		
		public SipConnector getConnector() 
		{
			return TcpConnector.this;
		}
		
		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			sb.append("TCP Connection ");
			sb.append(getLocalAddr()).append(":").append(getLocalPort());
			sb.append(" - ");
			sb.append(getRemoteAddr()).append(":").append(getRemotePort());
			return sb.toString();
		}


	}


}
