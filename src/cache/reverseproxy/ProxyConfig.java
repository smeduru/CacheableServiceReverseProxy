package cache.reverseproxy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.http.Header;

/**
 * 
 * @author Sreedhar Meduru
 *
 */
public class ProxyConfig {
	private static String outputDir = "./docroot";
	private static String soapDir = outputDir + "/soap";	
	private static String restDir = outputDir + "/rest";
	
	private static Properties serverConfig = new Properties();
	private static Properties headerOnlyRequests = new Properties();
	private static Map<String, List<String>> ignoreHeaderMap = new HashMap<String, List<String>>();
	private static Map<String, List<String>> ignoreSoapElementsMap = new HashMap<String, List<String>>();

	static {
		try {
			serverConfig
					.load(new FileInputStream("services-config.properties"));
			headerOnlyRequests.load(new FileInputStream(
					"header-only-requests.properties"));
			configureIgnoringProperties("ignore-headers.properties", ignoreHeaderMap);
			configureIgnoringProperties("ignore-enitity-elements.properties", ignoreSoapElementsMap);
			createDir(soapDir);
			createDir(restDir);			
		} catch (IOException e) {
			System.err.println("Error loading Properties files. Please check the stacktrace.");
			e.printStackTrace();
			System.exit(0);
		}
	}

	public static void createDir(String dir) throws IOException {
		/*File file = new File(outputDir);
		if (!file.exists() && !file.mkdir()) {
			throw new IOException("Couldn't create directory:" + outputDir);
		}*/
		File file = new File(dir);
		if (!file.exists() && !file.mkdirs()) {
			throw new IOException("Couldn't create directory:" + dir);
		}
	}
	public static void configureIgnoringProperties(String configFile, Map map) throws IOException {
		Properties includedHeaders = new Properties();
		includedHeaders.load(new FileInputStream(configFile));
				//"ignore-headers.properties"));
		Set<Entry<Object, Object>> set = includedHeaders.entrySet();
		if (set != null) {
			for (Entry<Object, Object> entry: set) {
				String key = (String) entry.getKey();
				String value = (String) entry.getValue();

				if (value != null) {
					List<String> valuesList = new ArrayList<String>();
	
					StringTokenizer st = new StringTokenizer(value, "|");
					while (st.hasMoreTokens()) {
						valuesList.add(st.nextToken());
					}
					//ignoreHeaderMap.put(key, valuesList);
					map.put(key, valuesList);
				}
			}
		}
	}
	
	/**
	 * Return flag indicating whether soap element needs to be considered for 
	 * HashCode generation.
	 * @param context
	 * @param node
	 * @param soapActionHeader
	 * @return
	 */
	public static boolean isSkipNode(String context, String node, Header soapActionHeader) {
		List<String> values = ignoreSoapElementsMap.get("ALL_REQUESTS");
		if (values != null) {
			if (values.contains(node)) {
				return true;
			}
		}
		
		String key = getHeaderBasedKey(context, soapActionHeader);
		values = ignoreSoapElementsMap.get(key);
		if (values != null) {
			int idx = node.indexOf(':');
			if (idx != -1) {
				node = node.substring(idx + 1);
			}
			if (values.contains(node)) {
				return true;
			}
		}	
		return false;
	}
	
	/**
	 * Return flag indicating whether Header Elements needs to be considered for 
	 * HashCode generation.
	 * @param context
	 * @param node
	 * @param soapActionHeader
	 * @return
	 */
	public static boolean isHeaderIncluded(String context, Header header, Header soapActionHeader) {
		List<String> values = ignoreHeaderMap.get("ALL_REQUESTS");
		if (values != null) {
			if (values.contains(header.getName())) {
				return false;
			}
		}
		
		String key = getHeaderBasedKey(context, soapActionHeader);
		values = ignoreHeaderMap.get(key);
		if (values != null) {
			if (values.contains(header.getName())) {
				return false;
			}
		}	
		return true;
	}
	
	public static String getOutputDir(boolean isSoap) {
		return (isSoap) ? soapDir : restDir;
	}

	public static URIConfig getReverseProxyUrl(String url)
			throws ProxyException {
		url = url.substring(1);
		int idx = url.indexOf('/');
		String context = url.substring(idx);
		String serverId = url.substring(0, idx);

		String serverURL = serverConfig.getProperty(serverId);
		if (serverURL == null || serverURL.isEmpty()) {
			throw new ProxyException("Mapping Server URL is not found for server id: " + serverId);
		}
		if (serverURL.charAt(serverURL.length() - 1) == '/') {
			serverURL = serverURL.substring(0, serverURL.length() - 1);
		}

		URIConfig uriConfig = new URIConfig();
		uriConfig.context = context;
		uriConfig.id = serverId;
		uriConfig.config(serverURL);

		return uriConfig;
	}
	
	public static String getHeaderBasedKey(String context, Header header) {
		if (header != null) {
			String value = header.getValue();
			if (value != null) { 
				value = value.replace("\"", "");
				return context + "/" + value;
			}
		}
		return context;
	}

	public static boolean isHeaderBasedHashcode(String context, Header soapActionHeader) {
		String key = getHeaderBasedKey(context, soapActionHeader);
		return Boolean.valueOf(headerOnlyRequests.getProperty(key));
	}

	public static String getForwardedContext(String url) {
		url = url.substring(1);
		int idx = url.indexOf('/');
		return url.substring(idx);
	}

	public static class URIConfig {
		private String server;
		private int port;
		private boolean isSecure;
		private String context;
		private String id;

		public String getId() {
			return id;
		}

		public String getServer() {
			return server;
		}

		public int getPort() {
			return port;
		}

		public boolean isSecure() {
			return isSecure;
		}

		public String getContext() {
			return context;
		}

		private void config(String url) {
			isSecure = url.indexOf("https://") > -1;
			url = url.replace("https://", "");
			url = url.replace("http://", "");
			port = (isSecure) ? 443 : 80;
			if (url.indexOf(':') > -1) {
				int i = url.indexOf(':');
				port = Integer.parseInt(url.substring(i + 1));
				server = url.substring(0, i);
			} else {
				server = url;
			}
		}

		public String toString() {
			String s = (isSecure) ? "https" : "http";
			s = s + "://" + server + ":" + port + "/" + context;
			return s;
		}
	}

	public static void main(String[] args) {
		try {
			System.out
					.println(getReverseProxyUrl("/rsp/eProxy/service/OfferService_SOAP_V1"));
		} catch (Exception e) {
			System.err.println("ERROR:" + e.getMessage());
		}
	}
}
