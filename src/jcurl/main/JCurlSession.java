package jcurl.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jcurl.main.converter.CurlConverter;
import jcurl.main.converter.CurlObject;
import jcurl.main.converter.syntaxtree.Method;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.BasicClientCookie;

import sun.misc.IOUtils;

public class JCurlSession {

	/**
	 * Logger
	 */
	private Logger log = Logger.getLogger(JCurlSession.class.getName());

	/**
	 * The timeout of each curl call
	 */
	private int timeout = 0;

	/**
	 * To replace a variable in curl string, use this as front regex
	 */
	private String frontParamDetect = "\\$\\{";

	/**
	 * To replace a variable in curl string use this as the end of regex
	 */
	private String backParamDetect = "\\}";

	private CookieStore cookieStore = new BasicCookieStore();

	private PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();;

	private CloseableHttpClient client;

	/**
	 * Create a new default JCurlSession instance timeout is infinite/0
	 * ${variable}
	 */
	JCurlSession() {
		client = HttpClients.custom().setConnectionManager(cm)
				.setRedirectStrategy(new LaxRedirectStrategy())
				.setDefaultCookieStore(cookieStore).build();
	}

	public void setMaxThreads(int maxThreads) {
		cm.setMaxTotal(maxThreads);
	}

	public void setMaxThreadsPerRoute(int maxThreads) {
		cm.setDefaultMaxPerRoute(maxThreads);
	}

	/**
	 * Input a file that contains curl string
	 * 
	 * @Todo add caching of file string
	 * @param curlFile
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public JCurlResponse callCurl(File curlFile, KeyValuePair... args)
			throws ScrapeException {
		FileInputStream fis;
		String curlString;
		try {
			fis = new FileInputStream(curlFile);
			curlString = new String(IOUtils.readFully(fis, -1, true),
					Charset.forName("UTF-8"));
			log.info(MessageFormat.format("Read curl string {0}", curlString));
			return callCurl(curlString, args);
		} catch (IOException e) {
			throw new ScrapeException(e);
		}

	}

	public String getFrontParamDetect() {
		return frontParamDetect;
	}

	public void setFrontParamDetect(String frontParamDetect) {
		this.frontParamDetect = frontParamDetect;
	}

	public String getBackParamDetect() {
		return backParamDetect;
	}

	public void setBackParamDetect(String backParamDetect) {
		this.backParamDetect = backParamDetect;
	}

	public JCurlResponse callCurl(String curlString, KeyValuePair... args)
			throws ScrapeException {
		for (KeyValuePair pair : args) {
			curlString = curlString.replaceAll(frontParamDetect + pair.getKey()
					+ backParamDetect, pair.getValue());
		}

		return callCurl(curlString);
	}

	/**
	 * Input a curl string that would run in command line and return the
	 * response object
	 * 
	 * @param curlString
	 * @throws ScrapeException
	 * @throws IOException
	 */
	private JCurlResponse callCurl(String curlString) throws ScrapeException {
		log.fine(MessageFormat.format("Calling curl string {0}", curlString));

		log.fine("Converting curl string to curl object");
		// convert string to curl object
		CurlObject curlObject = CurlConverter.convertCurl(curlString);
		log.fine("Done converting, creating connection now");

		HttpRequestBase request = null;
		try {
			// set http method
			request = getRequestObject(curlObject);

			request.setConfig(RequestConfig.custom()
					.setRedirectsEnabled(curlObject.isFollowRedirects())
					.setConnectTimeout(timeout).build());

			log.fine("Connection created, adding headers now");

			// iterate over headers and add to request properties
			Map<String, String> headers = curlObject.getHeaders();
			for (String key : headers.keySet()) {
				request.addHeader(key, headers.get(key));
			}

			log.fine("Trying to connect to url");

			// connect to the url
			HttpClientContext context = HttpClientContext.create();

			HttpResponse response = client.execute(request, context);

			log.fine("Done connection to url, getting output");

			JCurlResponse curlResponse = new JCurlResponse(response, curlObject);

			log.fine(MessageFormat.format("Done creating response \n{0}\n",
					curlResponse));

			return curlResponse;
		} catch (UnsupportedEncodingException e) {
			log.log(Level.SEVERE, "", e);
			throw new ScrapeException(e);
		} catch (ClientProtocolException e) {
			log.log(Level.SEVERE, "", e);
			throw new ScrapeException(e);
		} catch (IOException e) {
			log.log(Level.SEVERE, "", e);
			throw new ScrapeException(e);
		} finally {
			if (request != null) {
				request.releaseConnection();
			}
		}
	}

	private HttpRequestBase getRequestObject(CurlObject curlObject)
			throws UnsupportedEncodingException {
		String meth = curlObject.getHttpMethod();

		if (Method.POST.equals(meth)) {
			HttpPost post = new HttpPost(curlObject.getUrl());
			post.setEntity(new StringEntity(curlObject.getData()));
			return post;
		}
		if (Method.GET.equals(meth)) {
			return new HttpGet(curlObject.getUrl());
		}
		if (Method.PUT.equals(meth)) {
			HttpPut put = new HttpPut(curlObject.getUrl());

			put.setEntity(new StringEntity(curlObject.getData()));
			return put;
		}
		return null;
	}

	public void addCookie(String key, String value) {
		cookieStore.addCookie(new BasicClientCookie(key, value));
	}

	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("\n<JCURLSESSION>\n");
		stringBuilder.append(" CURRENT COOKIES\n----------------\n");
		for (Cookie cookie : cookieStore.getCookies()) {
			stringBuilder.append(" ++ ").append(cookie).append("\n");
		}
		stringBuilder.append(" ------------\n</JCURLSESSION>\n");

		return stringBuilder.toString();

	}

	/**
	 * Clean up the curl session, should be called after done using a
	 * JCurlSession object
	 * 
	 * @throws Throwable
	 */
	public void close() throws Throwable {
		this.finalize();
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public static class KeyValuePair {
		private String key, value;

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		public KeyValuePair(String key, String value) {
			super();
			this.key = key;
			this.value = value;
		}

	}

}
