// ========================================================================
// Copyright (c) 2006-2010 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.cipango.sipapp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.cipango.servlet.SipServletHolder;
import org.cipango.sip.security.Constraint;
import org.cipango.sip.security.ConstraintMapping;
import org.cipango.sip.security.ConstraintSecurityHandler;
import org.cipango.sip.security.SipSecurityHandler.IdentityAssertionScheme;
import org.cipango.sipapp.rules.AndRule;
import org.cipango.sipapp.rules.ContainsRule;
import org.cipango.sipapp.rules.EqualsRule;
import org.cipango.sipapp.rules.ExistsRule;
import org.cipango.sipapp.rules.MatchingRule;
import org.cipango.sipapp.rules.NotRule;
import org.cipango.sipapp.rules.OrRule;
import org.cipango.sipapp.rules.SubdomainRule;
import org.eclipse.jetty.security.UserDataConstraint;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.webapp.Descriptor;
import org.eclipse.jetty.webapp.FragmentDescriptor;
import org.eclipse.jetty.webapp.IterativeDescriptorProcessor;
import org.eclipse.jetty.webapp.Origin;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.xml.XmlParser;

/**
 * StandardDescriptorProcessor
 *
 * Process a sip.xml, sip-defaults.xml, sip-overrides.xml.
 */
public class StandardDescriptorProcessor extends IterativeDescriptorProcessor
{
	private static final Logger LOG = Log.getLogger(StandardDescriptorProcessor.class);
	
    public static final String STANDARD_PROCESSOR = "org.eclipse.jetty.standardDescriptorProcessor";
        
    public StandardDescriptorProcessor ()
    {
 
        try
        {
        	registerVisitor("app-name", this.getClass().getDeclaredMethod("visitAppName", __signature));
        	registerVisitor("servlet-selection", this.getClass().getDeclaredMethod("visitServletSelection", __signature));
        	registerVisitor("proxy-config", this.getClass().getDeclaredMethod("visitProxyConfig", __signature));
        	
            registerVisitor("context-param", this.getClass().getDeclaredMethod("visitContextParam", __signature));
            registerVisitor("display-name", this.getClass().getDeclaredMethod("visitDisplayName", __signature));
            registerVisitor("servlet", this.getClass().getDeclaredMethod("visitServlet",  __signature));
            registerVisitor("servlet-mapping", this.getClass().getDeclaredMethod("visitServletMapping",  __signature));
            registerVisitor("session-config", this.getClass().getDeclaredMethod("visitSessionConfig",  __signature));
            registerVisitor("security-constraint", this.getClass().getDeclaredMethod("visitSecurityConstraint",  __signature));
            registerVisitor("login-config", this.getClass().getDeclaredMethod("visitLoginConfig",  __signature));
            registerVisitor("security-role", this.getClass().getDeclaredMethod("visitSecurityRole",  __signature));
            registerVisitor("listener", this.getClass().getDeclaredMethod("visitListener",  __signature));
            registerVisitor("distributable", this.getClass().getDeclaredMethod("visitDistributable",  __signature));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    
    
    /** 
     * @see org.eclipse.jetty.webapp.IterativeDescriptorProcessor#start()
     */
    public void start(WebAppContext context, Descriptor descriptor)
    { 
    }
    
    
    
    /** 
     * @see org.eclipse.jetty.webapp.IterativeDescriptorProcessor#end()
     */
    public void end(WebAppContext context, Descriptor descriptor)
    {
    }
    
    /**
     * @param context
     * @param descriptor
     * @param node
     */
    public void visitContextParam (WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
        String name = node.getString("param-name", false, true);
        String value = node.getString("param-value", false, true);
 
        context.getInitParams().put(name, value);
    
        if (LOG.isDebugEnabled()) 
        	LOG.debug("ContextParam: " + name + "=" + value);

    }
    

    /* ------------------------------------------------------------ */
    /**
     * @param context
     * @param descriptor
     * @param node
     */
    public void visitDisplayName(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
        context.setDisplayName(node.toString(false, true));
    }
    
    
    /**
     * @param context
     * @param descriptor
     * @param node
     */
	@SuppressWarnings("rawtypes")
    public void visitServlet(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
    	String servletName = node.getString("servlet-name", false, true);
		String servletClass = node.getString("servlet-class", false, true);
		// FIXME allow deploy with prefix: javaee:servlet-name
		SipServletHolder holder = new SipServletHolder();
		holder.setName(servletName);
		holder.setClassName(servletClass);
		
		Iterator params = node.iterator("init-param");
		
		while (params.hasNext()) 
		{
			XmlParser.Node param = (XmlParser.Node) params.next();
			String pName = param.getString("param-name", false, true);
			String pValue = param.getString("param-value", false, true);
			holder.setInitParameter(pName, pValue);
		}
		
		XmlParser.Node startup = node.get("load-on-startup");
		if (startup != null) 
		{
			String s = startup.toString(false, true);
			int order = 0; 
			if (s != null && s.trim().length() > 0) 
			{
				try 
				{
					order = Integer.parseInt(s);
				} 
				catch (NumberFormatException e) 
				{
					LOG.warn("Cannot parse load-on-startup " + s);
				}
			}
			holder.setInitOrder(order);
		}
		
        // FIXME use same instance for listener and servlet
		((SipAppContext) context).addSipServlet(holder);
		
		
        Iterator sRefsIter = node.iterator("security-role-ref");
        while (sRefsIter.hasNext())
        {
            XmlParser.Node securityRef = (XmlParser.Node) sRefsIter.next();
            String roleName = securityRef.getString("role-name", false, true);
            String roleLink = securityRef.getString("role-link", false, true);
            if (roleName != null && roleName.length() > 0 && roleLink != null && roleLink.length() > 0)
            	holder.setUserRoleLink(roleName, roleLink);             
            else
                LOG.warn("Ignored invalid security-role-ref element: " + "servlet-name=" + holder.getName() + ", " + securityRef);
        }
        
        XmlParser.Node run_as = node.get("run-as");
        if (run_as != null)
        { 
            String roleName = run_as.getString("role-name", false, true);

            if (roleName != null)
                holder.setRunAsRole(roleName);
        }
    }
    
    

    /**
     * @param context
     * @param descriptor
     * @param node
     */
	public void visitServletMapping(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
        String servletName = node.getString("servlet-name", false, true); 
        
        SipServletMapping mapping = new SipServletMapping();
		
		XmlParser.Node pattern = node.get("pattern");
		XmlParser.Node start = null;
		@SuppressWarnings("rawtypes")
		Iterator it = pattern.iterator();
		
		while (it.hasNext() && start == null) 
		{
			Object o = it.next();
			if (!(o instanceof XmlParser.Node)) 
				continue;

			start = (XmlParser.Node) o;
		}
        
        MatchingRule rule = initRule(start);
		mapping.setServletName(servletName);
		mapping.setMatchingRule(rule);
        
        ((SipAppContext) context).getSipServletHandler().addSipServletMapping(mapping);
    }

	@SuppressWarnings("rawtypes")
	public MatchingRule initRule(XmlParser.Node node) 
	{
		String name = node.getTag();
		if ("and".equals(name)) 
		{
			AndRule and = new AndRule();
			Iterator it = node.iterator();
			while (it.hasNext()) 
			{
				Object o = it.next();
				if (!(o instanceof XmlParser.Node)) 
					continue;
				
				and.addCriterion(initRule((XmlParser.Node) o));
			}
			return and;
		} 
		else if ("equal".equals(name)) 
		{
			String var = node.getString("var", false, true);
			String value = node.getString("value", false, true);
			boolean ignoreCase = "true".equalsIgnoreCase(node.getAttribute("ignore-case"));
			return new EqualsRule(var, value, ignoreCase);
		} 
		else if ("subdomain-of".equals(name)) 
		{
			String var = node.getString("var", false, true);
			String value = node.getString("value", false, true);
			return new SubdomainRule(var, value);
		} 
		else if ("or".equals(name)) 
		{
			OrRule or = new OrRule();
			Iterator it = node.iterator();
			while (it.hasNext()) 
			{
				Object o = it.next();
				if (!(o instanceof XmlParser.Node)) 
					continue;

				or.addCriterion(initRule((XmlParser.Node) o));
			}
			return or;
		} 
		else if ("not".equals(name)) 
		{
			NotRule not = new NotRule();
			Iterator it = node.iterator();
			while (it.hasNext()) 
			{
				Object o = it.next();
				if (!(o instanceof XmlParser.Node)) 
					continue;
				
				not.setCriterion(initRule((XmlParser.Node) o));
			}
			return not;
		} 
		else if ("contains".equals(name)) 
		{
			String var = node.getString("var", false, true);
			String value = node.getString("value", false, true);
			boolean ignoreCase = "true".equalsIgnoreCase(node.getAttribute("ignore-case"));
			return new ContainsRule(var, value, ignoreCase);
		} 
		else if ("exists".equals(name)) 
		{
			return new ExistsRule(node.getString("var", false, true));
		} 
		else 
		{
			throw new IllegalArgumentException("Unknown rule: " + name);
		}
	}
    
    
    /**
     * @param context
     * @param descriptor
     * @param node
     */
	public void visitSessionConfig(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
        XmlParser.Node tNode = node.get("session-timeout");
        if (tNode != null)
        {
            int timeout = Integer.parseInt(tNode.toString(false, true));
            ((SipAppContext) context).setSessionTimeout(timeout);
        }
    }
    
	public void visitAppName(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
    	((SipAppContext) context).getSipMetaData().setAppName(node.toString(false, true)); 
    }
    
	public void visitServletSelection(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
    	XmlParser.Node mainServlet = node.get("main-servlet");
		if (mainServlet != null)
			((SipAppContext) context).getSipMetaData().setMainServletName(mainServlet.toString(false, true));
		else
		{
			@SuppressWarnings("rawtypes")
			Iterator it = node.iterator("servlet-mapping");
			while (it.hasNext())
			{
				visitServletMapping(context, descriptor, (XmlParser.Node)it.next());
			}
		}
    }
    
	public void visitProxyConfig(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
    	 String s = node.getString("proxy-timeout", false, true);
         
         if (s == null)
         	s = node.getString("sequential-search-timeout", false, true);
         
         if (s != null)
         {
             try 
             {
                 int timeout = Integer.parseInt(s);
                 ((SipAppContext) context).setProxyTimeout(timeout);
             }
             catch (NumberFormatException e)
             {
                 LOG.warn("Invalid sequential-search-timeout value: " + s);
             }
         }
    }
    
    /**
     * @param context
     * @param descriptor
     * @param node
     */
	public void visitListener(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
        String className = node.getString("listener-class", false, true);
        try
        {
            if (className != null && className.length()> 0)
            {
            	((SipAppContext) context).getSipMetaData().addListener(className); 
            }
        }
        catch (Exception e)
        {
            LOG.warn("Could not instantiate listener " + className, e);
            return;
        }
    }
    
    /**
     * @param context
     * @param descriptor
     * @param node
     */
	public void visitDistributable(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
        // the element has no content, so its simple presence
        // indicates that the webapp is distributable...
        ((SipDescriptor)descriptor).setDistributable(true);
    }
	
	   /**
     * @param context
     * @param descriptor
     * @param node
     */
    public void visitSecurityConstraint(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
        Constraint scBase = new Constraint();
       ConstraintSecurityHandler securityHandler = ((SipAppContext) context).getSipSecurityHandler();

        //ServletSpec 3.0, p74 security-constraints, as minOccurs > 1, are additive 
        //across fragments
        try
        {
            XmlParser.Node auths = node.get("auth-constraint");

            if (auths != null)
            {
                scBase.setAuthenticate(true);
                // auth-constraint
                Iterator<XmlParser.Node> iter = auths.iterator("role-name");
                List<String> roles = new ArrayList<String>();
                while (iter.hasNext())
                {
                    String role = iter.next().toString(false, true);
                    roles.add(role);
                }
                scBase.setRoles(roles.toArray(new String[roles.size()]));
            }
            
            scBase.setProxyMode(node.get("proxy-authentication") != null);

            XmlParser.Node data = node.get("user-data-constraint");
            if (data != null)
            {
                data = data.get("transport-guarantee");
                String guarantee = data.toString(false, true).toUpperCase();
                if (guarantee == null || guarantee.length() == 0 || "NONE".equals(guarantee))
                    scBase.setUserDataConstraint(UserDataConstraint.None);
                else if ("INTEGRAL".equals(guarantee))
                	scBase.setUserDataConstraint(UserDataConstraint.Integral);
                else if ("CONFIDENTIAL".equals(guarantee))
                	 scBase.setUserDataConstraint(UserDataConstraint.Confidential);
                else
                {
                    LOG.warn("Unknown user-data-constraint:" + guarantee);
                    scBase.setUserDataConstraint(UserDataConstraint.Confidential);
                }
            }
            Iterator<XmlParser.Node> iter = node.iterator("resource-collection");
            while (iter.hasNext())
            {
                XmlParser.Node collection =  iter.next();
                String name = collection.getString("resource-name", false, true);
                Constraint sc = (Constraint) scBase.clone();
                sc.setName(name);

                Iterator<XmlParser.Node> iter3 = collection.iterator("sip-method");
                List<String> methods = null;
                if (iter3.hasNext())
                {
                    methods = new ArrayList<String>();
                    while (iter3.hasNext())
                        methods.add(((XmlParser.Node) iter3.next()).toString(false, true));
                }
                
                iter3 = collection.iterator("servlet-name");
                List<String> servletNames = null;
                if (iter3.hasNext())
                {
                	servletNames = new ArrayList<String>();
                    while (iter3.hasNext())
                    	servletNames.add(((XmlParser.Node) iter3.next()).toString(false, true));
                }
                
                ConstraintMapping mapping = new ConstraintMapping();
                mapping.setServletNames(servletNames);
                mapping.setMethods(methods);
                mapping.setConstraint(sc);
                
                securityHandler.addConstraintMapping(mapping);
            }
        }
        catch (CloneNotSupportedException e)
        {
            LOG.warn(e);
        }
    }
    
    public void visitLoginConfig(WebAppContext context, Descriptor descriptor, XmlParser.Node node) throws Exception
    {
    	ConstraintSecurityHandler securityHandler = ((SipAppContext) context).getSipSecurityHandler();
        XmlParser.Node method = node.get("auth-method");
        if (method != null)
        	securityHandler.setAuthMethod(method.toString(false, true));
            
            
        //handle realm-name merge
        XmlParser.Node name = node.get("realm-name");
        String nameStr = (name == null ? "default" : name.toString(false, true));
        securityHandler.setRealmName(nameStr);
            
 
        XmlParser.Node identityAssertion = node.get("identity-assertion");
        if (identityAssertion != null)
        {
        	String scheme = identityAssertion.getString("identity-assertion-scheme", false, true);
        	securityHandler.setIdentityAssertionScheme(IdentityAssertionScheme.getByName(scheme));
        	String supported = identityAssertion.getString("identity-assertion-support", false, true);
        	securityHandler.setIdentityAssertionRequired("REQUIRED".equalsIgnoreCase(supported));
        }
    }
    
    public void visitSecurityRole(WebAppContext context, Descriptor descriptor, XmlParser.Node node)
    {
        XmlParser.Node roleNode = node.get("role-name");
        String role = roleNode.toString(false, true);
        ConstraintSecurityHandler securityHandler = ((SipAppContext) context).getSipSecurityHandler();
        securityHandler.addRole(role);
    }

}
