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


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.DefaultHttpResponseParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.util.EntityUtils;

/**
 * @author Sreedhar Meduru.
 */
public class CachedResponseParser {
	private static class GenericHttpResponse {
		String headers;
		String entity;
	}

	public static GenericHttpResponse getResponse(String fileName, boolean isSoap) throws IOException {
		String fileDir = ProxyConfig.getOutputDir(isSoap);
		
	    File file = new File(fileDir + "/" + fileName);
	    if (!file.exists() || !file.canRead() || file.isDirectory()) {
	    	return null;
	    }
	    
	    GenericHttpResponse response = new GenericHttpResponse();
	    
	    BufferedReader br = new BufferedReader(new FileReader(file));
	    StringBuffer fileContents = new StringBuffer();
	    String line = br.readLine();
	    boolean isHeadRetrieved = false;
	    boolean isFirst = true;
	    while (line != null) {
	    	if (!isFirst  && !isHeadRetrieved) {
	    		fileContents.append("\r\n");
	    	}
	    	if (!isFirst || !"".equals(line)) {
	    		fileContents.append(line);
	    	}
	    	
    		isFirst = false;
	    	line = br.readLine();
	    	if ("".equals(line) && !isHeadRetrieved) {
	    		isHeadRetrieved = true;
	    		response.headers = fileContents.toString();
	    		fileContents = new StringBuffer();
	    		isFirst = true;
	    	}
	    }
	    
	    if (isHeadRetrieved) {
	    	response.entity = fileContents.toString();
	    }
	    else {
	    	response.headers = fileContents.toString();
	    }
	    
	    br.close();
	    /*System.out.println("FromFile:BEGIN");
	    System.out.println(response.headers);
	    System.out.println(response.entity);
	    System.out.println("FromFile:END");*/
	    return response;
	}

	public static void saveFile(String fileName, String request, boolean isSoap) throws IOException {
		String fileDir = ProxyConfig.getOutputDir(isSoap);
		File file = new File(fileDir + "/" + fileName);
		FileUtils.writeStringToFile(file, request);
	}
	
	public static HttpResponse getResponseFromParsedFile(String fileName, boolean isSoap) throws IOException, HttpException {
    	final GenericHttpResponse genericResponse = getResponse(fileName, isSoap);
    	if (genericResponse == null) {
    		return null;
    	}
        final SessionInputBuffer inbuffer = new PlainSessionInputBuffer(genericResponse.headers, Consts.ASCII);
        final DefaultHttpResponseParser parser = new DefaultHttpResponseParser(inbuffer);
        
        final HttpResponse httpresponse = parser.parse();
        if (genericResponse.entity != null)  {
        	HttpEntity entity = new StringEntity(genericResponse.entity);
        	httpresponse.setEntity(entity);
        }
        
        return httpresponse;
	}
	
    public static void main(String[] args) throws Exception {
        final HttpResponse httpresponse = getResponseFromParsedFile("address-validation-response.txt", false);
        System.out.println("PARSED RESPONSE ========>");
        final StatusLine statusline = httpresponse.getStatusLine();
        System.out.println(statusline.toString());
        final Header[] headers = httpresponse.getAllHeaders();
        for (Header header: headers) {
        	System.out.println(header);
        }
        HttpEntity entity = httpresponse.getEntity();
        if (entity != null) {
        	byte[] entityContent = EntityUtils.toByteArray(entity);
            System.out.println(new String(entityContent));
        }
    }
}

