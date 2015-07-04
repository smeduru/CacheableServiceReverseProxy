package cache.reverseproxy;


import org.apache.http.Header;

/**
 * 
 * @author Sreedhar Meduru
 *
 */
public class ProxyHelper {
	public static final String getContentType(Header header) {
		 String extension = "txt";
		 if (header != null) {
	 		String[] tokens = header.getValue().split(";");
	 		for (String token : tokens) { 
	    		int idx = token.indexOf("/"); 
	    		if (idx > -1) {
	    			extension = token.substring(idx + 1);
	    			break;
	    		}
	 		}
	     }
		 return extension;
	}
	
	public static final String getContentLength(Header header) {
         if (header != null) {
     		String length = header.getValue();
     		if (length != null && !length.isEmpty()) {
     			return length;
     		}
         }
         return "0";
	}
	
	public static final String getResponseType(Header header, String reqContentType) {
		 String extension = "txt";
		 if (header != null) {
	 		String[] tokens = header.getValue().split(",");
	 		for (String token : tokens) { 
	    		int idx = token.indexOf("/"); 
	    		if (idx > -1) {
	    			String type = token.substring(idx + 1);
	    			if ("xml".equalsIgnoreCase(type) || "json".equalsIgnoreCase(type)) {
	    				extension = type;
	    				break;
	    			}
	    			else if (type != null && type.indexOf('*') > -1) {
	    				continue;
	    			}
	    		}
	 		}
	     }

         if ("txt".equals(extension) && ("xml".equals(reqContentType) || "json".equals(reqContentType))) {
        	 extension = reqContentType;
         }
		 return extension;
	}
}
