package cache.reverseproxy;
/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */



import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpServerConnection;
import org.apache.http.RequestLine;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.UriHttpRequestHandlerMapper;
import org.apache.http.util.EntityUtils;

/**
 * @Author: Sreedhar Meduru
 * Elemental HTTP/1.1 reverse proxy.
 */
public class CacheableServicesReverseProxy {
	private static final Log log = LogFactory.getLog(CacheableServicesReverseProxy.class);
	
    private static final String HTTP_IN_CONN = "http.proxy.in-conn";
    private static final String HTTP_OUT_CONN = "http.proxy.out-conn";
    private static final String HTTP_CONN_KEEPALIVE = "http.proxy.conn-keepalive";

    public static void main(final String[] args) throws Exception {
        int port = 8888;
        if (args.length > 0) {
        	try {
        		port = Integer.parseInt(args[0]);
        	} 
        	catch(Exception e) { 
        		System.err.println("->Invalid Port!!"); 
        	}
        }
        final Thread t = new RequestListenerThread(port);
        t.setDaemon(false);
        t.start();
    }

    static class ProxyHandler implements HttpRequestHandler  {
    	final int bufsize = 8 * 1024;
        
        private final HttpProcessor httpproc;
        private final HttpRequestExecutor httpexecutor;
        private final ConnectionReuseStrategy connStrategy;

        public ProxyHandler(
                final HttpProcessor httpproc,
                final HttpRequestExecutor httpexecutor) {
            super();
            this.httpproc = httpproc;
            this.httpexecutor = httpexecutor;
            this.connStrategy = DefaultConnectionReuseStrategy.INSTANCE;
        }

        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException{

        	try {
	            ProxyConfig.URIConfig uriConfig = ProxyConfig.getReverseProxyUrl(request.getRequestLine().getUri());
	            
	            //request.addHeader("Access-Control-Allow-Origin", "*");

	            CacheableRequest cacheableRequest = RequestHashCodeStrategy.generateHashCode(request, uriConfig);
	            
	            String requestFileName = cacheableRequest.getRequestFileName();
	            String responseFileName = cacheableRequest.getResponseFileName();
	            String hashCodeFileName = cacheableRequest.getHashCodeFileName();
	            
	            boolean isSoap = cacheableRequest.isSoap();
            	
	            if (log.isDebugEnabled()) {
		            /*log.debug("--------------------------------------------------------------> Original Request Begin");
		            log.debug(cacheableRequest.toString()); 
		            log.debug("--------------------------------------------------------------> Original Request End");
		            
		            log.debug("--------------------------------------------------------------> HashCode Request String Begin");
		            log.debug(cacheableRequest.getHashCodeString());
		            log.debug("HashCode:" + cacheableRequest.getHashCode());
		            log.debug("--------------------------------------------------------------> HashCode Request String End");
		            */
	            }

	            CachedResponseParser.saveFile(hashCodeFileName, cacheableRequest.getHashCodeString(), isSoap);    	            
	
	            HttpResponse targetResponse = CachedResponseParser.getResponseFromParsedFile(responseFileName, isSoap);
	            boolean isFileSave = false;
	            StringBuilder logStr = new StringBuilder();
	            
	            if (targetResponse == null) {
            		logStr.append("==> Hitting Back End Server...");

            		CachedResponseParser.saveFile(requestFileName, cacheableRequest.toString(), isSoap);
    	            
	                // Set up outgoing HTTP connection
	                //final Socket outsocket = new Socket(this.target.getHostName(), this.target.getPort());
	            	final Socket outsocket;
	            	if (uriConfig.isSecure())
	            		outsocket = createSecureSocket(uriConfig.getServer(), uriConfig.getPort());
	            	else
	            		outsocket = createInsecureSocket(uriConfig.getServer(), uriConfig.getPort());
	            	
	                final DefaultBHttpClientConnection outconn = new CacheableBHttpClientConnection(bufsize);
	
	                //context.setAttribute(HTTP_OUT_CONN, outconn);
	                //context.setAttribute(HttpCoreContext.HTTP_CONNECTION, outconn);
	                //context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, this.target);
	                RequestLine origRequestLine = request.getRequestLine();
	                BasicHttpRequest basicHttpRequest = null;
	                if (request instanceof HttpEntityEnclosingRequest) {  
	                	BasicHttpEntityEnclosingRequest enclosingRequest = 
	                			new BasicHttpEntityEnclosingRequest(origRequestLine.getMethod(), 
	                												uriConfig.getContext(), origRequestLine.getProtocolVersion());	
	                	enclosingRequest.setEntity(((HttpEntityEnclosingRequest)request).getEntity());
	                	basicHttpRequest = enclosingRequest;
	                }
	                else {
	                	basicHttpRequest = new BasicHttpRequest(origRequestLine.getMethod(), 
		                		uriConfig.getContext(), origRequestLine.getProtocolVersion());
	                }
	                basicHttpRequest.setHeaders(request.getAllHeaders());
	                basicHttpRequest.setParams(request.getParams());
	                
	                outconn.bind(outsocket);
	                this.httpexecutor.preProcess(basicHttpRequest, this.httpproc, context);
	                targetResponse = this.httpexecutor.execute(basicHttpRequest, outconn, context);
	                this.httpexecutor.postProcess(response, this.httpproc, context);
	                isFileSave = true;
	            }
	            else {
	            	logStr.append("==> Using cache to send the response...");
	            }
	            
            	logStr.append("[Context:" + request.getRequestLine().getUri());
            	logStr.append(", SoapAction:" + cacheableRequest.getSoapAction());
            	//logStr.append(", HashCode:" + cacheableRequest.getHashCode()); 
            	logStr.append(", ReqFile:" + requestFileName);
            	logStr.append(", RespFile:" + responseFileName + "]" );
            	
            	StringBuilder sb = new StringBuilder();
	            sb.append(targetResponse.getStatusLine()).append("\r\n");
	            
	            boolean isResponseOK = targetResponse.getStatusLine().toString().indexOf(" 200 ") > -1;
	            
	            for (Header header : targetResponse.getAllHeaders()) {
	            	sb.append(header.toString()).append("\r\n");
	            }
	            HttpEntity entity = new BufferedHttpEntity(targetResponse.getEntity());
	            targetResponse.setEntity(entity);
	            if (entity != null) {
	            	byte[] entityContent = EntityUtils.toByteArray(entity);
	            	String content = new String(entityContent);
	            	sb.append("\r\n");
	            	sb.append(content);
	            }
	            String responseString = sb.toString();
	            
        		if (isFileSave) {
	            	if (isResponseOK) {
		            	CachedResponseParser.saveFile(responseFileName, responseString, isSoap);
	            	}
	            	else {
	            		logStr.append("\\n   Backend Failure on the above request!");
	            	}
	            }
	            
	            System.out.println(logStr);

	            if (log.isDebugEnabled()) { 
		            log.debug("--------------------------------------------------------------> Response Begin");
		            log.debug(responseString);
		            log.debug("--------------------------------------------------------------> Response End");
	            }
	            
	            // Remove hop-by-hop headers
	            targetResponse.removeHeaders(HTTP.CONTENT_LEN);
	            targetResponse.removeHeaders(HTTP.TRANSFER_ENCODING);
	            targetResponse.removeHeaders(HTTP.CONN_DIRECTIVE);
	            targetResponse.removeHeaders("Keep-Alive");
	            targetResponse.removeHeaders("TE");
	            targetResponse.removeHeaders("Trailers");
	            targetResponse.removeHeaders("Upgrade");
	            targetResponse.removeHeaders("Upgrade");
	            targetResponse.removeHeaders("Transfer-Encoding");
	
	            response.setStatusLine(targetResponse.getStatusLine());
	            response.setHeaders(targetResponse.getAllHeaders());
	            response.setEntity(targetResponse.getEntity());
	
	            final boolean keepalive = this.connStrategy.keepAlive(response, context);
	            context.setAttribute(HTTP_CONN_KEEPALIVE, new Boolean(keepalive));
	        }
            catch (Exception e) {
            	e.printStackTrace();
            	throw new IOException(e.getMessage());
            }
        }
    }

    static class RequestListenerThread extends Thread {
        private final ServerSocket serversocket;
        private final HttpService httpService;

        public RequestListenerThread(final int port) throws IOException {
            this.serversocket = new ServerSocket(port);
            System.out.println("Running Server on the port:" + port);

            // Set up HTTP protocol processor for incoming connections
            final HttpProcessor inhttpproc = new ImmutableHttpProcessor(
                    new HttpRequestInterceptor[] {
                            new RequestContent(true),
                            new RequestTargetHost(),
                            new RequestConnControl(),
                            new RequestUserAgent("Test/1.1"),
                            new RequestExpectContinue(false)
             });

            // Set up HTTP protocol processor for outgoing connections
            final HttpProcessor outhttpproc = new ImmutableHttpProcessor(
                    new HttpResponseInterceptor[] {
                            new ResponseDate(),
                            new ResponseServer("Test/1.1"),
                            new ResponseContent(true),
                            new ResponseConnControl()
            });

            // Set up outgoing request executor
            final HttpRequestExecutor httpexecutor = new HttpRequestExecutor();

            // Set up incoming request handler
            final UriHttpRequestHandlerMapper reqistry = new UriHttpRequestHandlerMapper();
            reqistry.register("*", new ProxyHandler(
                    outhttpproc,
                    httpexecutor));

            // Set up the HTTP service
            this.httpService = new HttpService(inhttpproc, reqistry);
        }

        @Override
        public void run() {
            System.out.println("Listening on port " + this.serversocket.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    final int bufsize = 8 * 1024;
                    // Set up incoming HTTP connection
                    final Socket insocket = this.serversocket.accept();
                    final DefaultBHttpServerConnection inconn = new DefaultBHttpServerConnection(bufsize);
                    //System.out.println("Incoming connection from " + insocket.getInetAddress());
                    inconn.bind(insocket);

                    // Start worker thread
                    final Thread t = new ProxyThread(this.httpService, inconn);
                    t.setDaemon(true);
                    t.start();
                } catch (final InterruptedIOException ex) {
                    break;
                } catch (final IOException e) {
                    System.err.println("I/O error initialising connection thread: "
                            + e.getMessage());
                    break;
                }
            }
        }
    }

    static class ProxyThread extends Thread {

        private final HttpService httpservice;
        private final HttpServerConnection inconn;
        private HttpClientConnection outconn;

        public ProxyThread(
                final HttpService httpservice,
                final HttpServerConnection inconn) {
            super();
            this.httpservice = httpservice;
            this.inconn = inconn;
        }

        @Override
        public void run() {
            //System.out.println("New connection thread");
            final HttpContext context = new BasicHttpContext(null);

            // Bind connection objects to the execution context
            context.setAttribute(HTTP_IN_CONN, this.inconn);
            //context.setAttribute(HTTP_OUT_CONN, this.outconn);

            try {
                while (!Thread.interrupted()) {
                	if (this.outconn == null) {
                		this.outconn = (HttpClientConnection) context.getAttribute(HTTP_OUT_CONN);
                	}
                	if (!this.inconn.isOpen() && this.outconn != null) {
                    	this.outconn.close();
                        break;
                    }

                    this.httpservice.handleRequest(this.inconn, context);

                    final Boolean keepalive = (Boolean) context.getAttribute(HTTP_CONN_KEEPALIVE);
                    if (!Boolean.TRUE.equals(keepalive)) {
                        if (this.outconn != null)
                    	this.outconn.close();
                        this.inconn.close();
                        break;
                    }
                }
            } catch (final ConnectionClosedException ex) {
                //System.err.println("Client closed connection");
            } catch (final IOException ex) {
                System.err.println("I/O error: " + ex.getMessage());
            } catch (final HttpException ex) {
                System.err.println("Unrecoverable HTTP protocol violation: " + ex.getMessage());
            } finally {
                try {
                    this.inconn.shutdown();
                } catch (final IOException ignore) {}
                try {
                	if (this.outconn != null)
                		this.outconn.shutdown();
                } catch (final IOException ignore) {}
            }
        }

    }
    
    public static Socket createSecureSocket(String host, int port) throws IOException {
        SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        
        SSLSocket newSocket = (SSLSocket) sslFactory.createSocket(host, port);

        newSocket.setUseClientMode(true);
        newSocket.startHandshake();

        return newSocket;
    }
    
    public static Socket createInsecureSocket(String host, int port) throws IOException {
        Socket newSocket = (Socket) SocketFactory.getDefault().createSocket(host, port);
        return newSocket;
    }

}
