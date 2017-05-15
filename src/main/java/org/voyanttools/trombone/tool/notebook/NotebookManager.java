/**
 * 
 */
package org.voyanttools.trombone.tool.notebook;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.cxf.helpers.IOUtils;
import org.voyanttools.trombone.input.source.UriInputSource;
import org.voyanttools.trombone.storage.Storage;
import org.voyanttools.trombone.storage.Storage.Location;
import org.voyanttools.trombone.tool.utils.AbstractTool;
import org.voyanttools.trombone.util.FlexibleParameters;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * @author sgs
 *
 */
@XStreamAlias("notebook")
public class NotebookManager extends AbstractTool {
	
	String notebook = null; // notebook source (ID, URL, etc.)
	
	String jsonData = null; // notebook data as JSON
	
	public NotebookManager(Storage storage, FlexibleParameters parameters) {
		super(storage, parameters);
	}
	public float getVersion() {
		return super.getVersion()+1f;
	}

	@Override
	public void run() throws IOException {
		if (parameters.containsKey("jsonData")) { // this might be provided by Trombone
			jsonData = parameters.getParameterValue("jsonData");
		} 
		if (parameters.containsKey("notebook")) {
			notebook = parameters.getParameterValue("notebook");
			if (parameters.getParameterBooleanValue("autosave")) {
				notebook+=".autosave";
			}
			if (jsonData==null && notebook.startsWith("http")) {
				URI uri;
				try {
					uri = new URI(notebook);
				} catch (URISyntaxException e) {
					throw new IllegalArgumentException("Unable to parse URL: "+notebook);
				}
				UriInputSource inputSource = new UriInputSource(uri);
				InputStream is = null;
				try {
					is = inputSource.getInputStream();
					jsonData = IOUtils.readStringFromStream(is);
				} finally {
					if (is!=null) is.close();
				}
			} else if (jsonData!=null && notebook!=null && notebook.isEmpty()==false && parameters.getParameterBooleanValue("autosave")) {
				// notebook and jsonData defined, let's assume auto-save
				storage.storeString(jsonData, notebook, Location.notebook, true);
				jsonData = null;
				return;
			} else if (jsonData==null && storage.isStored(notebook, Location.notebook)) {
				jsonData = storage.retrieveString(notebook, Storage.Location.notebook);
			}
		} else if (jsonData!=null) { // we have sent data but no notebook, so we're saving
			// assign new notebook ID
			notebook = storage.storeString(jsonData, Location.notebook);
			jsonData = null;
			return;
		}
		if (jsonData==null) {
			throw new RuntimeException("Unable to locate requested notebook.");
		}
	}
}
