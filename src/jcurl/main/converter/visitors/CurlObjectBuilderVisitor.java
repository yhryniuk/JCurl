package jcurl.main.converter.visitors;

import java.net.MalformedURLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jcurl.main.converter.CurlObject;
import jcurl.main.converter.syntaxtree.Curl;
import jcurl.main.converter.syntaxtree.Flag;
import jcurl.main.converter.syntaxtree.Method;
import jcurl.main.converter.syntaxtree.URL;
import jcurl.main.converter.syntaxtree.flags.Compressed;
import jcurl.main.converter.syntaxtree.flags.Data;
import jcurl.main.converter.syntaxtree.flags.H;

public class CurlObjectBuilderVisitor implements Visitor {

	private CurlObject curlObject;
	
	private static Logger log = Logger.getLogger(CurlObjectBuilderVisitor.class.getName());

	public CurlObject getCurlObject() {
		return curlObject;
	}

	@Override
	public void accept(Curl curl) {
		curlObject = new CurlObject();
		for (Flag flag : curl.getFlags()) {
			flag.accept(this);
		}
		curl.getMethod().accept(this);
		curl.getUrl().accept(this);
	}

	@Override
	public void accept(URL url) {
		try {
			curlObject.setUrl(new java.net.URL(url.getUrl()));
		} catch (MalformedURLException e) {
			log.log(Level.SEVERE, null, e);
		}
	}

	@Override
	public void accept(Method method) {
		// TODO Auto-generated method stub
		curlObject.setHttpMethod(method.getType());
	}

	@Override
	public void accept(Flag flag) {
	}

	@Override
	public void accept(H h) {
		Map<String, String> headers = curlObject.getHeaders();
		Pattern pattern = Pattern.compile("([^:]*): +(.*)");
		Matcher matcher = pattern.matcher(h.getValue());

		if (matcher.find()) {
			headers.put(matcher.group(1), matcher.group(2));
		}
	}

	@Override
	public void accept(Compressed compressed) {
		curlObject.setCompressed(true);
	}

	@Override
	public void accept(Data data) {
		// TODO Auto-generated method stub
		curlObject.setData(data.getData());
	}

}
