/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.cxf;

import org.mule.api.DefaultMuleException;
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.config.MuleProperties;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.transformer.TransformerException;
import org.mule.api.transport.DispatchException;
import org.mule.message.DefaultExceptionPayload;
import org.mule.module.cxf.i18n.CxfMessages;
import org.mule.module.cxf.security.WebServiceSecurityException;
import org.mule.processor.AbstractInterceptingMessageProcessor;
import org.mule.transport.http.HttpConnector;
import org.mule.transport.http.HttpConstants;
import org.mule.util.StringUtils;
import org.mule.util.TemplateParser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;

import org.apache.commons.httpclient.HttpException;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.MethodDispatcher;
import org.apache.cxf.service.model.BindingOperationInfo;

/**
 * The CxfOutboundMessageProcessor performs outbound CXF processing, sending an event through
 * the CXF client, then on to the next MessageProcessor. 
 */
public class CxfOutboundMessageProcessor 
    extends AbstractInterceptingMessageProcessor
{

    private static final String URI_REGEX = "cxf:\\[(.+?)\\]:(.+?)/\\[(.+?)\\]:(.+?)";
    Pattern URI_PATTERN = Pattern.compile(URI_REGEX);

    private final TemplateParser soapActionTemplateParser = TemplateParser.createMuleStyleParser();
    private CxfPayloadToArguments payloadToArguments = CxfPayloadToArguments.NULL_PAYLOAD_AS_PARAMETER;
    private Client client;
    private boolean proxy;
    private String operation;
    private BindingProvider clientProxy;
    
    public CxfOutboundMessageProcessor(Client client)
    {
        this.client = client;
    }

    protected void cleanup()
    {
        // MULE-4899: cleans up client's request and response context to avoid a memory leak.
        Map<String, Object> requestContext = client.getRequestContext();
        requestContext.clear();
        Map<String, Object> responseContext = client.getResponseContext();
        responseContext.clear();
    }

    protected Object[] getArgs(MuleEvent event) throws TransformerException
    {
        Object payload;
        
        payload = event.getMessage().getPayload();
        
        if (proxy)
        {
            return new Object[] { event.getMessage() };
        }
        
        Object[] args = payloadToArguments.payloadToArrayOfArguments(payload);

        MuleMessage message = event.getMessage();
        Set<?> attachmentNames = message.getOutboundAttachmentNames();
        if (attachmentNames != null && !attachmentNames.isEmpty())
        {
            List<DataHandler> attachments = new ArrayList<DataHandler>();
            for (Object attachmentName : attachmentNames)
            {
                attachments.add(message.getAttachment((String) attachmentName));
            }
            List<Object> temp = new ArrayList<Object>(Arrays.asList(args));
            temp.add(attachments.toArray(new DataHandler[attachments.size()]));
            args = temp.toArray();
        }

        if (args.length == 0)
        {
            return null;
        }
        return args;
    }

    
    public MuleEvent process(MuleEvent event) throws MuleException
    {
        try {
            MuleEvent res;
            if (!isClientProxyAvailable())
            {
                res = doSendWithClient(event);
            }
            else
            {
                res = doSendWithProxy(event);
            }
            return res;
        } catch (MuleException e) {
            throw e;
        } catch (Exception e) {
            throw new DefaultMuleException(e);
        }
    }

    /**
     * This method is public so it can be invoked from the MuleUniversalConduit.
     */
    @Override
    public MuleEvent processNext(MuleEvent event) throws MuleException
    {
        return next.process(event);
    }

    protected MuleEvent doSendWithProxy(MuleEvent event) throws Exception
    {
        Method method = getMethod(event);

        Map<String, Object> props = getInovcationProperties(event);
        
        Holder<MuleEvent> responseHolder = new Holder<MuleEvent>();
        props.put("holder", responseHolder); 
        // Set custom soap action if set on the event or endpoint
        String soapAction = event.getMessage().getOutboundProperty(SoapConstants.SOAP_ACTION_PROPERTY);
        if (soapAction != null)
        {
            soapAction = parseSoapAction(soapAction, new QName(method.getName()), event);
            props.put(org.apache.cxf.binding.soap.SoapBindingConstants.SOAP_ACTION, soapAction);
        }
        
        clientProxy.getRequestContext().putAll(props);
        
        Object response;
        try
        {
            Object[] args = getArgs(event);
            response = method.invoke(clientProxy, args);
        }
        catch (InvocationTargetException e)
        {
            Throwable ex = e.getTargetException();

            if (ex != null && ex.getMessage().contains("Security"))
            {
                throw new WebServiceSecurityException(event, e);
            }
            else
            {
                throw e;
            }
        }        
        
        // TODO: handle holders
        MuleEvent muleRes = responseHolder.value;
        return buildResponseMessage(event, muleRes, new Object[] { response });
    }

    protected MuleEvent doSendWithClient(MuleEvent event) throws Exception
    {
        BindingOperationInfo bop = getOperation(event);
        
        Map<String, Object> props = getInovcationProperties(event);
        
        // Holds the response from the transport
        Holder<MuleEvent> responseHolder = new Holder<MuleEvent>();
        props.put("holder", responseHolder); 
        
        // Set custom soap action if set on the event or endpoint
        String soapAction = event.getMessage().getOutboundProperty(SoapConstants.SOAP_ACTION_PROPERTY);
        if (soapAction != null)
        {
            soapAction = parseSoapAction(soapAction, bop.getName(), event);
            props.put(org.apache.cxf.binding.soap.SoapBindingConstants.SOAP_ACTION, soapAction);
            event.getMessage().setProperty(SoapConstants.SOAP_ACTION_PROPERTY, soapAction);
        }
        
        Map<String, Object> ctx = new HashMap<String, Object>();
        ctx.put(Client.REQUEST_CONTEXT, props); 
        ctx.put(Client.RESPONSE_CONTEXT, props); 
        
        // Set Custom Headers on the client
        Object[] arr = event.getMessage().getPropertyNames().toArray();
        String head;

        for (int i = 0; i < arr.length; i++)
        {
            head = (String) arr[i];
            if ((head != null) && (!head.startsWith("MULE")))
            {
                props.put((String) arr[i], event.getMessage().getProperty((String) arr[i]));
            }
        }
        
        Object[] response = client.invoke(bop, getArgs(event), ctx);

        return buildResponseMessage(event, responseHolder.value, response);
    }
    
    public Method getMethod(MuleEvent event) throws Exception
    {
        Method method = null;
        if (method == null)
        {
            String opName = (String) event.getMessage().getProperty(CxfConstants.OPERATION);
            if (opName != null) 
            {
                method = getMethodFromOperation(opName);
            }

            if (method == null)
            {
                opName = operation;
                if (opName != null) 
                {
                    method = getMethodFromOperation(opName);
                }
            }
        }

        if (method == null)
        {
            throw new MessagingException(CxfMessages.noOperationWasFoundOrSpecified(), event);
        }
        return method;
    }


    protected BindingOperationInfo getOperation(final String opName) throws Exception
    {
        // Normally its not this hard to invoke the CXF Client, but we're
        // sending along some exchange properties, so we need to use a more advanced
        // method
        Endpoint ep = client.getEndpoint();
        BindingOperationInfo bop = getBindingOperationFromEndpoint(ep, opName);
        if (bop == null)
        {
            bop = tryToGetTheOperationInDotNetNamingConvention(ep, opName);
            if (bop == null)
            {
                throw new Exception("No such operation: " + opName);
            }
        }

        if (bop.isUnwrappedCapable())
        {
            bop = bop.getUnwrappedOperation();
        }
        return bop;
    }

    /**
     * This method tries to call
     * {@link #getBindingOperationFromEndpoint(Endpoint, String)} with the .net
     * naming convention for .net webservices (method names start with a capital
     * letter).
     * <p>
     * CXF generates method names compliant with Java naming so if the WSDL operation
     * names starts with uppercase letter, matching with method name does not work -
     * thus the work around.
     * 
     * @param opName
     * @param ep
     * @return
     */
    protected BindingOperationInfo tryToGetTheOperationInDotNetNamingConvention(Endpoint ep,
                                                                                final String opName)
    {
        final String capitalizedOpName = opName.substring(0, 1).toUpperCase() + opName.substring(1);
        return getBindingOperationFromEndpoint(ep, capitalizedOpName);
    }

    protected BindingOperationInfo getBindingOperationFromEndpoint(Endpoint ep, final String operationName)
    {
        QName q = new QName(ep.getService().getName().getNamespaceURI(), operationName);
        BindingOperationInfo bop = ep.getBinding().getBindingInfo().getOperation(q);
        return bop;
    }

    private Method getMethodFromOperation(String op) throws Exception
    {
        BindingOperationInfo bop = getOperation(op);
        MethodDispatcher md = (MethodDispatcher) client.getEndpoint().getService().get(
            MethodDispatcher.class.getName());
        return md.getMethod(bop);
    }


    protected String getMethodOrOperationName(MuleEvent event) throws DispatchException
    {
        // People can specify a CXF operation, which may in fact be different
        // than the method name. If that's not found, we'll default back to the 
        // mule method property. 
        String method = event.getMessage().getInvocationProperty(CxfConstants.OPERATION);
        
        if (method == null)
        {
            method = event.getMessage().getInvocationProperty(MuleProperties.MULE_METHOD_PROPERTY);
        }

        if (method == null)
        {
            method = operation;
        }
        
        if (method == null && proxy)
        {
            return "invoke";
        }

        return method;
    }

    public BindingOperationInfo getOperation(MuleEvent event) throws Exception
    {
        String opName = getMethodOrOperationName(event);

        if (opName == null)
        {
            opName = operation;
        }

        return getOperation(opName);
    }

    private Map<String, Object> getInovcationProperties(MuleEvent event)
    {
        Map<String, Object> props = new HashMap<String, Object>();
        props.put(MuleProperties.MULE_EVENT_PROPERTY, event); 
        props.put(CxfConstants.CXF_OUTBOUND_MESSAGE_PROCESSOR, this);
        return props;
    }
    
    protected MuleEvent buildResponseMessage(MuleEvent request, MuleEvent transportResponse, Object[] response) 
    {
        // One way dispatches over an async transport result in this
        if (transportResponse == null) 
        {
            return null;
        }
        
        // Otherwise we may have a response!
        Object payload;
        if (response == null || response.length == 0) {
            payload = null;
        }
        else if (response.length == 1)
        {
            payload = response[0];
        }
        else
        {
            payload = response;
        }
        
        MuleMessage message = transportResponse.getMessage();
        String statusCode = message.getInboundProperty(HttpConnector.HTTP_STATUS_PROPERTY);
        if (statusCode != null && Integer.parseInt(statusCode) != HttpConstants.SC_OK)
        {
            String exPayload;
            try
            {
                exPayload = message.getPayloadAsString();
            }
            catch (Exception e)
            {
                exPayload = "Invalid status code: " + statusCode;
            }
            message.setExceptionPayload(new DefaultExceptionPayload(new HttpException(exPayload)));
        } 
        else 
        {
            message.setPayload(payload);
        }

        return transportResponse;
    }
    
    public String parseSoapAction(String soapAction, QName method, MuleEvent event)
    {
        EndpointURI endpointURI = event.getEndpoint().getEndpointURI();
        Map<String, String> properties = new HashMap<String, String>();
        MuleMessage msg = event.getMessage();
        // propagate only invocation- and outbound-scoped properties
        for (String name : msg.getInvocationPropertyNames())
        {
            final String value = msg.getInvocationProperty(name, StringUtils.EMPTY);
            properties.put(name, value);
        }
        for (String name : msg.getOutboundPropertyNames())
        {
            final String value = msg.getOutboundProperty(name, StringUtils.EMPTY);
            properties.put(name, value);
        }
        properties.put(MuleProperties.MULE_METHOD_PROPERTY, method.getLocalPart());
        properties.put("methodNamespace", method.getNamespaceURI());
        properties.put("address", endpointURI.getAddress());
        properties.put("scheme", endpointURI.getScheme());
        properties.put("host", endpointURI.getHost());
        properties.put("port", String.valueOf(endpointURI.getPort()));
        properties.put("path", endpointURI.getPath());
        properties.put("hostInfo", endpointURI.getScheme()
                                   + "://"
                                   + endpointURI.getHost()
                                   + (endpointURI.getPort() > -1
                                                   ? ":" + String.valueOf(endpointURI.getPort()) : ""));
        if (event.getFlowConstruct() != null)
        {
            properties.put("serviceName", event.getFlowConstruct().getName());
        }

        soapAction = soapActionTemplateParser.parse(properties, soapAction);

        if (logger.isDebugEnabled())
        {
            logger.debug("SoapAction for this call is: " + soapAction);
        }

        return soapAction;
    }

    public void setPayloadToArguments(CxfPayloadToArguments payloadToArguments)
    {
        this.payloadToArguments = payloadToArguments;
    }

    protected boolean isClientProxyAvailable()
    {
        return clientProxy != null;
    }
    public boolean isProxy()
    {
        return proxy;
    }

    public void setProxy(boolean proxy)
    {
        this.proxy = proxy;
    }

    public String getOperation()
    {
        return operation;
    }

    public void setOperation(String operation)
    {
        this.operation = operation;
    }

    public void setClientProxy(BindingProvider clientProxy)
    {
        this.clientProxy = clientProxy;
    }

    public CxfPayloadToArguments getPayloadToArguments()
    {
        return payloadToArguments;
    }

    public Client getClient()
    {
        return client;
    }
    
}


