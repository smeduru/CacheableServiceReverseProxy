package cache.reverseproxy;

/**
 * 
 * @author Sreedhar Meduru
 *
 */
public class ProxyException extends Exception {
	public ProxyException(String msg) {
		super(msg);
	}
	
	public String toString() {
		return super.getMessage();
	}
}
