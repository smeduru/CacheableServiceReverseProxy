package cache.reverseproxy;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * 
 * @author Sreedhar Meduru.
 *
 */
public class CacheableRequest {
	private HttpRequest httpRequest;
	private StringBuilder requestStr = new StringBuilder("");
	private StringBuilder filteredRequestStrForHashCode = new StringBuilder("");
	private String context;
	private Header soapActionHeader;
	
	String reqFileExtension = "";
	String reqFileName = "";
	String respFileExtension = "";
	String respFileName = "";
	
	XMLReader soapContentFilter;
	
	public CacheableRequest(HttpRequest request, String context) {
		this.httpRequest = request;
		this.context = context;
		
		String method = request.getRequestLine().getMethod();
        Header contentTypeHeader = request.getFirstHeader("Content-Type");
        String reqContentType = ProxyHelper.getContentType(contentTypeHeader);
        
//        Header contentLengthHeader = request.getFirstHeader("Content-Length");
//        String length = ProxyHelper.getContentLength(contentLengthHeader);
//        reqFileExtension = method + length + "." + reqContentType;
        reqFileExtension = method + "." + reqContentType;
        
        Header acceptHeader = request.getFirstHeader("Accept");
        String respContentType = ProxyHelper.getResponseType(acceptHeader, reqContentType);
//        respFileExtension = method + length + "." + respContentType;
        respFileExtension = method + "." + respContentType;
        
        try {
        	// Filter SOAP Content based 
            soapContentFilter = new XMLFilterImpl(XMLReaderFactory.createXMLReader()) {
                private boolean skip;
                private String skipElement;
                
                @Override
                public void startElement(String uri, String localName, String qName, Attributes atts)
                        throws SAXException {
                	if (skip) { 
                		return;
                	}
                	
                    if (isSkipNode(qName)) {
                        skip = true;
                        skipElement = qName;
                        return;
                    }
                    super.startElement(uri, localName, qName, atts);
                }

                public void endElement(String uri, String localName, String qName) throws SAXException {
                	if (skip && qName.equals(skipElement)) {
                    	skip = false;
                    	skipElement = null;
                    }
                    else if (!skip) {
                    	super.endElement(uri, localName, qName);
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) throws SAXException {
                    if (!skip) {
                        super.characters(ch, start, length);
                    }
                }
            };        	
        }
        catch (Exception e) {
        	e.printStackTrace();
        }
	}
	
	public HttpRequest getHttpRequest() {
		return httpRequest;
	}
	
	public void setFileNames() {
		String hashCode = getHashCode();
		String reqPrefix = getRequestPrefix();
		if (reqPrefix != null) {
			reqPrefix = reqPrefix.replace("\"", "");
			if (hashCode.charAt(0) != '_') {
				reqPrefix = reqPrefix + "_";
			}
		}
		
		reqFileName = reqPrefix + hashCode + "_REQ_" + reqFileExtension;
		respFileName = reqPrefix + hashCode + "_RESP_" + respFileExtension;
	}
	
	public void setContext(String context) {
		this.context = context;
	}
	
	public void setSoapHeaderContext(Header header) {
		this.soapActionHeader = header;
	}
	
	public String getSoapAction() {
		if (soapActionHeader != null) {
			return soapActionHeader.getValue();
		}
		return "";
	}
	
	public String getRequestPrefix() {
		String prefix = getSoapAction();
		if (prefix == null || prefix.isEmpty()) {
			int idx = context.lastIndexOf('/');
			if (idx > -1) {
				prefix = context.substring(idx + 1);
			}
		}
		if (prefix == null) {
			prefix = "";
		}
		return prefix;
	}
	
	public boolean isSoap() {
		return soapActionHeader != null;
	}
	
	public String getRequestFileName() {
		return reqFileName;
	}
	
	public String getResponseFileName() {
		return respFileName;
	}
	
	public void append(String value, boolean includedInHashcode) {
		requestStr.append(value);
		if (includedInHashcode) {
			filteredRequestStrForHashCode.append(value);
		}
	}
	
	public void append(String value, boolean isSoap, boolean includedInHashcode) throws Exception {
		requestStr.append(value);
		if (includedInHashcode) {
			if (isSoap) {
				Source src = new SAXSource(soapContentFilter, new InputSource(new StringReader(value)));
		        ByteArrayOutputStream bo = new ByteArrayOutputStream();
		        Result res = new StreamResult(bo);
		        TransformerFactory.newInstance().newTransformer().transform(src, res);
		        filteredRequestStrForHashCode.append(bo.toString());
			}
			else { 
				filteredRequestStrForHashCode.append(value);
			}
		}
	}

	public void append(String value) {
		append(value, true);
	}
	
	public int hashCode() {
		return getHashCodeString().hashCode();
	}
	
	public String getHashCode() {
		int hashCode = hashCode(); 
		String hash = String.valueOf(hashCode);
		hash = hash.replace('-', '_');
		return hash;
	}
	
	public String getHashCodeString() {
		return filteredRequestStrForHashCode.toString();
	}
	
	private boolean isSkipNode(String node) {
		 return ProxyConfig.isSkipNode(context, node, soapActionHeader);
	}
	
	/*private static boolean isSkipNode(String node) {
		if ("partnerId".equals(node) || "requestId".equals(node) || "partnerTimestamp".equals(node)
				|| "partnerSessionId".equals(node) || "partnerTransactionId".equals(node)) {
			return true;
		}
		return false;
	}*/
	
	public String toString() {
		return requestStr.toString();
	}
}