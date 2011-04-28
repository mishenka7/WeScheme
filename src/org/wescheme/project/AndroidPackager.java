package org.wescheme.project;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletContext;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.api.urlfetch.URLFetchService;

import static com.google.appengine.api.urlfetch.FetchOptions.Builder.*;

public class AndroidPackager {

	/**
	 * Deadline before we timeout
	 */
	private static Double DEADLINE = 10.0;
	
	private static String ANDROID_PACKAGER_SERVER_URL = 
		"ANDROID_PACKAGER_SERVER_URL";

		
	
	/**
	 * Creates an android package.
	 * @param ctx ServletContext
	 * @param programName String
	 * @param programBytecode String
	 * @param permissions Set<String> The list of android permissions this program needs.
	 * @return
	 */
	public static void queueAndroidPackageBuild(ServletContext ctx, String programName, String programBytecode, Set<String> permissions,
			String callbackUrl){
		try {
			Properties properties = new Properties();
			properties.load(ctx.getResourceAsStream("/web-services.properties"));			
			URL url = new URL(properties.getProperty(ANDROID_PACKAGER_SERVER_URL));

			ByteArrayOutputStream bout = getCompressedData(ctx, programName,
					programBytecode, permissions, callbackUrl);
						
			// We have to use the lower-level fetch service API because of the
			// potential for timeouts.
			HTTPRequest request = new HTTPRequest(url,
						HTTPMethod.POST,
						disallowTruncate().setDeadline(DEADLINE));
			request.setPayload(bout.toByteArray());
			URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();
			fetchService.fetch(request);
			return;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


	private static ByteArrayOutputStream getCompressedData(ServletContext ctx,
			String programName, String programSource, Set<String> permissions, String callbackUrl)
			throws UnsupportedEncodingException, IOException {
		String data = 
			("n=" + URLEncoder.encode(programName, "UTF-8") + 
					"&t=moby3" + 
					"&cb=" + URLEncoder.encode(callbackUrl, "UTF-8") +
					"&" + makeResourceChunk("program.js", 
							"var program = {};\nprogram.bytecode=" + programSource + ";") +
					"&" + makeResourceChunk("mindex.html", readStream(ctx.getResourceAsStream("/android-packager/index.html")))+
					"&" + makeResourceChunk("main.js", readStream(ctx.getResourceAsStream("/android-packager/main.js")))+
					"&" + makeResourceChunk("phonegap.js", readStream(ctx.getResourceAsStream("/android-packager/phonegap.js")))+
					"&" + makeResourceChunk("collections.js", readStream(ctx.getResourceAsStream("/js/mzscheme-vm/collections.js"))) +
					"&" + makeResourceChunk("support.js", readStream(ctx.getResourceAsStream("/js/mzscheme-vm/support.js"))) +
					"&" + makeResourceChunk("evaluator.js", readStream(ctx.getResourceAsStream("/js/mzscheme-vm/evaluator.js")))) +
					getPermissionChunks(permissions);
		ByteArrayOutputStream bout  = new ByteArrayOutputStream();
		GZIPOutputStream out = new GZIPOutputStream(bout);
		out.write(data.getBytes());
		out.close();
		System.out.println("size of compressed data: " + bout.size());
		return bout;
	}

	
	private static String getPermissionChunks(Set<String> permissions) throws UnsupportedEncodingException{
		StringBuilder b = new StringBuilder();
		for(String perm : permissions) { 
			b.append("&p=" + URLEncoder.encode(perm, "UTF-8"));
		}
		return b.toString();
	}
	
    @SuppressWarnings("unchecked")
	private static String makeResourceChunk(String path, String content) throws UnsupportedEncodingException {
    	JSONObject object = new JSONObject();
    	object.put("path", path);
    	object.put("bytes", content);
    	return "r=" + URLEncoder.encode(object.toJSONString(),"UTF-8");
    }
    
    
    private static String quoteString(String s) {
    	return JSONValue.toJSONString(s);
    }

	
    private static String readStream(InputStream stream) {
        BufferedInputStream bs = new BufferedInputStream(stream);
        int nextChar;
        StringBuilder builder = new StringBuilder();
        try {
            while ((nextChar = bs.read()) != -1) {
                builder.append((char) nextChar);
            }
            return builder.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Blob readStreamAsBlob(InputStream stream) {
        BufferedInputStream bs = new BufferedInputStream(stream);
        int nextChar;
        List<Byte> bytes = new ArrayList<Byte>();
        try {
            while ((nextChar = bs.read()) != -1) {
                bytes.add((byte) nextChar);
            }
            // There has to be a more direct way to construct a Blob from
            // a stream of data...
            byte[] barray = new byte[bytes.size()];
            for(int i = 0; i < bytes.size(); i++) {
            	barray[i] = bytes.get(i);
            }
            return new Blob(barray);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}