// ========================================================================
// Copyright 2007-2008 NEXCOM Systems
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


import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipURI;

import org.cipango.server.SipHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TcpConnectorTest
{
	private TcpConnector _connector;
	private SipServletMessage _message;
	
	@Before
	public void setUp() throws Exception
	{
		_connector = new TcpConnector();
		_connector.setHost("localhost");
		_connector.setPort(45040);
		_connector.setThreadPool(new QueuedThreadPool());
		_connector.setHandler(new TestHandler());
		_connector.start();
		_message = null;
	}
	
	@After
	public void tearDown() throws Exception
	{
		Thread.sleep(40);
		_connector.stop();
		Thread.sleep(10);
	}

	@Test
	public void testLifeCycle() throws Exception
	{
		TcpConnector connector = new TcpConnector();
		connector.setHost("localhost");
		connector.setPort(45070);
		connector.setThreadPool(new QueuedThreadPool());
		for (int i = 0; i < 5; i++)
		{
			connector.start();
			assertTrue(connector.isRunning());
			connector.stop();
			assertFalse(connector.isRunning());
			Thread.sleep(10);
		}
	}
	
	
	@Test
	public void testMessage() throws Exception
	{
		send(_msg);
		
		SipServletMessage message = getMessage(1000);
		send(_msg2);
		Thread.sleep(300);
		assertNotNull(message);
		assertEquals("REGISTER", message.getMethod());
		assertEquals("c117fdfda2ffd6f4a859a2d504aedb25@127.0.0.1", message.getCallId());
	}
	
	
	
	@Test
	public void testBigRequest() throws Exception
	{
		testBigRequest(256 * 1024 - 379);
	}
	
	
	public void testBigRequest(int bodySize) throws Exception
	{
		String message = 
				"MESSAGE sip:test@127.0.0.1:5060 SIP/2.0\r\n"
				+ "Call-ID: 13a769769217a57d911314c67df8c729@192.168.1.205\r\n"
				+ "CSeq: 1 MESSAGE\r\n"
				+ "From: \"Alice\" <sip:alice@192.168.1.205:5071>;tag=1727584951\r\n"
				+ "To: \"JSR289_TCK\" <sip:JSR289_TCK@127.0.0.1:5060>\r\n"
				+ "Via: SIP/2.0/UDP 192.168.1.205:5071;branch=z9hG4bKaf9d7cee5d176c7edf2fbf9b1e33fc3a\r\n"
				+ "Max-Forwards: 5\r\n"
				+ "Content-Type: text/plain\r\n"
				+ "Content-Length: ${length}\r\n\r\n";
		message = message.replace("${length}", String.valueOf(bodySize));
		int headersSize = message.length();
		System.out.println(headersSize);
		StringBuilder sb = new StringBuilder(message);
		int lines = 0;
		for (int i = 0; i < bodySize; i++)
		{
			sb.append((char) ('a' + lines));
			if ((i+1) % 200 == 0)
			{
				sb.append("\r\n");
				i += 2;
				lines++;
				if (lines > 25)
					lines = 0;
			}
		}
		message = sb.toString();
		//System.out.println(message);
		
		send(message);
		
		SipServletMessage received = getMessage(1000);
		assertNotNull(received);
		System.out.println(received.getContentLength());
		assertEquals(bodySize, received.getContentLength());
		System.out.println(received.toString().length());
	}
	
	private SipServletMessage getMessage(long timeout) throws InterruptedException
	{
		if (_message != null)
			return _message;
		long absTimeout = System.currentTimeMillis() + timeout;
		while (absTimeout - System.currentTimeMillis() > 0)
		{
			Thread.sleep(50);
			if (_message != null)
				return _message;
		}
		return null;
	}

	@Test
	public void testRoute() throws Exception
	{
		
		send(_test);
		
		SipServletMessage message = getMessage(1000);
		send(_msg2);
		send(_msg2);
		send(_msg2);
		send(_msg2);
		
		Thread.sleep(100);
		assertNotNull(_message);
		
		Iterator<Address> it = message.getAddressHeaders("route");
		assertEquals("proxy-gen2xx", ((SipURI) it.next().getURI()).getUser());
		assertTrue(it.hasNext());
		
		assertEquals("com.bea.sipservlet.tck.apps.spectestapp.uas", message.getHeader("application-name"));
	}

	private void send(String message) throws Exception
	{
		Socket ds = new Socket();
		
		byte[] b = message.getBytes("UTF-8");
		ds.connect(new InetSocketAddress(InetAddress.getByName("localhost"), _connector.getPort()));
		ds.getOutputStream().write(b);
	}
	
	class TestServerSocket extends Thread
	{
		private List<byte[]> _read = new ArrayList<byte[]>();
		private ServerSocket _serverSocket;
		private Socket _socket;
		
		private boolean _active = true;
		
		public TestServerSocket(ServerSocket socket)
		{
			_serverSocket = socket;
		}
		
		public void setUnactive()
		{
			_active = false;
		}
		
		@Override
		public void run()
		{
			try
			{
				_socket = _serverSocket.accept();
				byte[] b = new byte[2048];
				while (_active)
				{
					int i = _socket.getInputStream().read(b);
					if (i == -1)
						continue;
					byte[] b2 = new byte[i];
					System.arraycopy(b, 0, b2, 0, i);
					_read.add(b2);
				}
			}
			catch (Exception e)
			{
				if (_active)
					e.printStackTrace();
			}
			finally
			{
				try
				{
					_serverSocket.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}

		public byte[] pop()
		{
			if (_read.size() > 0)
				return _read.remove(0);
			return null;
		}

		public Socket getSocket()
		{
			return _socket;
		}
		
		public void send(byte[] b) throws IOException
		{
			getSocket().getOutputStream().write(b);
		}
	}
	
	class TestClientSocket extends Thread
	{
		private List<byte[]> _read = new ArrayList<byte[]>();
		private Socket _socket;
		
		private boolean _active = true;
		
		public TestClientSocket() throws UnknownHostException, IOException
		{
			_socket = new Socket();			
			_socket.connect(new InetSocketAddress(InetAddress.getByName("localhost"), _connector.getPort()));

		}
		
		public void setUnactive()
		{
			_active = false;
		}
		
		@Override
		public void run()
		{
			try
			{
				byte[] b = new byte[2048];
				while (_active)
				{
					int i = _socket.getInputStream().read(b);
					if (i == -1)
						continue;
					byte[] b2 = new byte[i];
					System.arraycopy(b, 0, b2, 0, i);
					_read.add(b2);
				}
			}
			catch (Exception e)
			{
				if (_active)
					e.printStackTrace();
			}
			finally
			{
				try
				{
					_socket.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}

		public byte[] pop()
		{
			if (_read.size() > 0)
				return _read.remove(0);
			return null;
		}

		public Socket getSocket()
		{
			return _socket;
		}
		
		public void send(byte[] b) throws IOException
		{
			getSocket().getOutputStream().write(b);
		}
	}
	
	class TestHandler implements SipHandler
	{
		
		public void handle(SipServletMessage message) throws IOException, ServletException
		{
			_message = message;
		}

		public Server getServer() {
			return null;
		}

		public void setServer(Server server) {
			
		}	
	}
	
	String _pingEolEol = "\r\n\r\n";
	String _pingEol = "\r\n";
	
	String _msg = 
        "REGISTER sip:127.0.0.1:5070 SIP/2.0\r\n"
        + "Call-ID: c117fdfda2ffd6f4a859a2d504aedb25@127.0.0.1\r\n"
        + "CSeq: 2 REGISTER\r\n"
        + "From: <sip:cipango@cipango.org>;tag=9Aaz+gQAAA\r\n"
        + "To: <sip:cipango@cipango.org>\r\n"
        + "Via: SIP/2.0/UDP 127.0.0.1:6010\r\n"
        + "Max-Forwards: 70\r\n"
        + "User-Agent: Test Script\r\n"
        + "Contact: \"Cipango\" <sip:127.0.0.1:6010;transport=udp>\r\n"
        + "Allow: INVITE, ACK, BYE, CANCEL, PRACK, REFER, MESSAGE, SUBSCRIBE\r\n"
        + "MyHeader: toto\r\n"
        + "Content-Length: 0\r\n\r\n";
	
	String _msg2 = 
        "REGISTER sip:127.0.0.1:5070 SIP/2.0\r\n"
        + "Call-ID: foo@bar\r\n"
        + "CSeq: 2 REGISTER\r\n"
        + "From: <sip:cipango@cipango.org>;tag=9Aaz+gQAAA\r\n"
        + "To: <sip:cipango@cipango.org>\r\n"
        + "Via: SIP/2.0/UDP 127.0.0.1:6010\r\n"
        + "Max-Forwards: 70\r\n"
        + "User-Agent: Test Script\r\n"
        + "Contact: \"Cipango\" <sip:127.0.0.1:6010;transport=udp>\r\n"
        + "Allow: INVITE, ACK, BYE, CANCEL, PRACK, REFER, MESSAGE, SUBSCRIBE\r\n"
        + "MyHeader: toto\r\n"
        + "Content-Length: 0\r\n\r\n";
	
	String _test = 
		"MESSAGE sip:proxy-gen2xx@127.0.0.1:5060 SIP/2.0\r\n"
		+ "Call-ID: 13a769769217a57d911314c67df8c729@192.168.1.205\r\n"
		+ "CSeq: 1 MESSAGE\r\n"
		+ "From: \"Alice\" <sip:alice@192.168.1.205:5071>;tag=1727584951\r\n"
		+ "To: \"JSR289_TCK\" <sip:JSR289_TCK@127.0.0.1:5060>\r\n"
		+ "Via: SIP/2.0/UDP 192.168.1.205:5071;branch=z9hG4bKaf9d7cee5d176c7edf2fbf9b1e33fc3a\r\n"
		+ "Max-Forwards: 5\r\n"
		+ "Route: \"JSR289_TCK\" <sip:proxy-gen2xx@127.0.0.1:5060;lr>,<sip:127.0.0.1:5060;transport=udp;lr>\r\n"
		+ "Application-Name: com.bea.sipservlet.tck.apps.spectestapp.uas\r\n"
		+ "Servlet-Name: Addressing\r\n"
		+ "Content-Type: text/plain\r\n"
		+ "Content-Length: 0\r\n\r\n";
}
