package cache.reverseproxy;
import java.util.Arrays;
import java.util.Comparator;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

/**
 * 
 * @author Sreedhar Meduru
 *
 */
public class RequestHashCodeStrategy {
	
	public static CacheableRequest generateHashCode(HttpRequest request, 
			ProxyConfig.URIConfig uriConfig) throws Exception {
		CacheableRequest cacheableRequest = new CacheableRequest(request, uriConfig.getContext());
		
		cacheableRequest.append(request.getRequestLine().toString());
		cacheableRequest.append("\r\n");
		
		Header[] allheaders = request.getAllHeaders();
		Arrays.sort(allheaders, new Comparator<Header>() {
		    public int compare(Header h1, Header h2) {
		        return h1.getName().compareTo(h2.getName());
		    }
		});
		request.setHeaders(allheaders);
		
        Header soapActionHeader = request.getFirstHeader("SOAPAction");
        cacheableRequest.setSoapHeaderContext(soapActionHeader);
		
        for (Header header : request.getAllHeaders()) {
			 if (header.getName().equals("Content-Type")) {
				 header = new BasicHeader(header.getName(), header.getValue().toLowerCase());
			 }
			 boolean isHeaderIncluded = ProxyConfig.isHeaderIncluded(uriConfig.getContext(), header, soapActionHeader);
			 cacheableRequest.append(header.toString(), isHeaderIncluded);
			 cacheableRequest.append("\r\n", isHeaderIncluded);
	     }
		 
		boolean isHeaderBasedHashCode 
			= ProxyConfig.isHeaderBasedHashcode(uriConfig.getContext(), soapActionHeader);
        
        if (request instanceof HttpEntityEnclosingRequest) {            	
            HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
            entity = new BufferedHttpEntity(entity);
            ((HttpEntityEnclosingRequest) request).setEntity(entity);
            if (entity != null) {
            	boolean isSoap = soapActionHeader != null;
            	byte[] entityContent = EntityUtils.toByteArray(entity);
    	        cacheableRequest.append("\r\n", !isHeaderBasedHashCode);
    	        cacheableRequest.append(new String(entityContent), isSoap, !isHeaderBasedHashCode);
            }
        }


        cacheableRequest.setFileNames();
        return cacheableRequest;
    }
}
