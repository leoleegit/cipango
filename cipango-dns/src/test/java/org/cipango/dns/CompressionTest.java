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
package org.cipango.dns;

import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

public class CompressionTest
{

	@Test
	public void testEncode()
	{
		Compression c = new Compression();
		Buffer buffer = new ByteArrayBuffer(512);
		Name cipango = new Name("cipango.org");
		Name www = new Name("www.cipango.org");
		Name org = new Name("org");
		c.encodeName(cipango, buffer);
		c.encodeName(www, buffer);
		c.encodeName(cipango, buffer);
		c.encodeName(org, buffer);
		
		Assert.assertEquals(23, buffer.length());

		c = new Compression();
		Assert.assertEquals(cipango, c.decodeName(buffer));
		Assert.assertEquals(www, c.decodeName(buffer));
		Assert.assertEquals(cipango, c.decodeName(buffer));
		Assert.assertEquals(org, c.decodeName(buffer));
		Assert.assertFalse(buffer.hasContent());
	}
}
