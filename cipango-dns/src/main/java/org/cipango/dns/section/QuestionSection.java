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
package org.cipango.dns.section;


import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.cipango.dns.DnsClass;
import org.cipango.dns.DnsMessage;
import org.cipango.dns.Name;
import org.cipango.dns.Type;
import org.cipango.dns.record.Record;
import org.cipango.dns.util.BufferUtil;
import org.eclipse.jetty.io.Buffer;

/**
 * 
 * <pre>
 *                                  1  1  1  1  1  1
 *    0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
 *  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *  |                                               |
 *  /                     QNAME                     /
 *  /                                               /
 *  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *  |                     QTYPE                     |
 *  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *  |                     QCLASS                    |
 *  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * </pre>
 */
public class QuestionSection extends AbstractList<Record>
{
	private List<Record> _records = new ArrayList<Record>();
	private DnsMessage _message;
	
	public QuestionSection(DnsMessage message)
	{
		_message = message;
	}

	public void encode(Buffer buffer) throws IOException
	{
		for (Record record : _records)
		{
			getMessage().getCompression().encodeName(record.getName(), buffer);
			record.getType().encode(buffer);
			record.getDnsClass().encode(buffer);
		}
	}

	public void decode(Buffer buffer) throws IOException
	{
		int nbRecord = getMessage().getHeaderSection().getQuestionRecords();
		for (int i = 0; i < nbRecord; i++)
		{
			Name name = getMessage().getCompression().decodeName(buffer);
			Type type = Type.getType(BufferUtil.get16(buffer));
			DnsClass clazz = DnsClass.getClass(BufferUtil.get16(buffer));
			
			Record record = type.newRecord();
			record.setDnsClass(clazz);
			record.setName(name);
			_records.add(record);
		}
	}
	
	public int size()
	{
		return _records.size();
	}
	
	public void add(int index,Record record)
	{
		_records.add(index,record);
	}
		
	public Record get(int index)
	{
		return _records.get(index);
	}
	
	public StringBuilder append(StringBuilder sb)
	{
		sb.append("Queries\n");
		for (Record record : _records)
			sb.append("  ").append(record).append("\n");
		return sb;
	}
	
	public DnsMessage getMessage()
	{
		return _message;
	}
	
	public String toString()
	{
		return _records.toString();
	}
	
}
