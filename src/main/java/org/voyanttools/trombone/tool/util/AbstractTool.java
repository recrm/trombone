/*******************************************************************************
 * Trombone is a flexible text processing and analysis library used
 * primarily by Voyant Tools (voyant-tools.org).
 * 
 * Copyright (©) 2007-2012 Stéfan Sinclair & Geoffrey Rockwell
 * 
 * This file is part of Trombone.
 * 
 * Trombone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Trombone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Trombone.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.voyanttools.trombone.tool.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.voyanttools.trombone.model.Corpus;
import org.voyanttools.trombone.model.Keywords;
import org.voyanttools.trombone.model.VariantsDB;
import org.voyanttools.trombone.storage.Storage;
import org.voyanttools.trombone.util.FlexibleParameters;

import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * @author sgs
 *
 */
public abstract class AbstractTool implements RunnableTool {
	
	private List<Message> messages = null;

	@XStreamOmitField
	protected FlexibleParameters parameters;
	
	@XStreamOmitField
	protected transient Storage storage;
	
	private static float VERSION = 5.7f;
	
	@XStreamOmitField
	private boolean isVerbose;
	
	@XStreamOmitField
	private DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
	
	/**
	 * @param storage 
	 * 
	 */
	public AbstractTool(Storage storage, FlexibleParameters parameters) {
		this.storage = storage;
		this.parameters = parameters;
		this.isVerbose = parameters.getParameterBooleanValue("verbose");
	}
	
	/**
	 * Create a message intended for the client (which may or may not appear to the user).
	 * @param message the message
	 * @param type the {@link Message.Type}
	 */
	protected void message(Message.Type type, String code, String message) {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		int i = 1;
		if (stackTraceElements.length>1) {
			i++;
			if (stackTraceElements.length>2 && stackTraceElements[i].getClassName().equals("org.voyanttools.trombone.tool.utils.AbstractTool") && stackTraceElements[i].getMethodName().equals("message")) {
				i++;
			}
		}
		if (messages==null) {
			messages = new ArrayList<Message>();
		}
		messages.add(new Message(type, code, message, stackTraceElements[i].getClassName(), stackTraceElements[i].getMethodName(), stackTraceElements[i].getLineNumber()));
	}
	
	/**
	 * Determine if this tool has any messages.
	 * @return whether or not this tool has any messages
	 */
	public boolean hasMessages() {
		return messages!=null && messages.isEmpty()==false;
	}
	
	/**
	 * Get the list of messages for this tool
	 * @return a list of {@link Message messages}
	 */
	public List<Message> getMessages() {
		return messages;
	}
	
	public float getVersion() {
		return VERSION;
	}
	
	protected boolean isVerbose() {
		return isVerbose;
	}
	
	protected void log(String string) {
		log(string, null);
	}
	
	protected void log(String string, Calendar start) {
		if (isVerbose()) {
			Calendar now = Calendar.getInstance();
			System.out.println(dateFormat.format(now.getTime())+"\t"+string+(start!=null ? "("+(now.getTimeInMillis()-start.getTimeInMillis())+" ms)" : ""));
		}
	}

	public FlexibleParameters getParameters() {
		return parameters;
	}
	protected Keywords getStopwords(Corpus corpus) throws IOException {
		Keywords keywords = new Keywords();
		if (parameters.containsKey("stopList")) {
			if (parameters.getParameterValue("stopList", "").equals("auto")) {
				Set<String> langs = new HashSet<String>();
				URL url = this.getClass().getResource("/org/voyanttools/trombone/keywords");
				File dir = new File(url.getFile());
				Map<String, String> stopLists = new HashMap<String, String>();
				if (dir.exists() && dir.isDirectory()) {
					for (File file : dir.listFiles()) {
						String filename = file.getName();
						if (file.isFile() && filename.startsWith("stop.")) {
							String langCode = filename.substring(5, filename.indexOf('.', 5));
							stopLists.put(langCode, filename);
						}
					}
				}
				for (String lang : corpus.getLanguageCodes()) {
					if (lang.isEmpty() || lang.equals("en")) {langs.add("stop.en.taporware.txt");}
					else if (lang.equals("fr")) {langs.add("stop.fr.veronis.txt");}
					else if (lang.equals("se")) {langs.add("stop.se.long.txt");}
					else if (stopLists.containsKey(lang)) {
						langs.add(stopLists.get(lang));
					}
				}
				if (langs.isEmpty()==false) {
					keywords.load(storage, langs.toArray(new String[0]));
				}
			}
			else {
				keywords.load(storage, parameters.getParameterValues("stopList"));
			}
		}
		return keywords;
	}
	
	protected String[] getQueries() throws IOException {
		return	getQueries(parameters.getParameterValues("query"));
	}
	protected String[] getQueries(String[] queryStrings) throws IOException {
		List<String> queries = new ArrayList<String>();
		VariantsDB variantsDB = null;
//			if (parameters.containsKey("variants")) {
//				variantsDB = new VariantsDB(storage, parameters.getParameterValue("variants"), true);
//			}
		for (String query : queryStrings) {
			// facets can be complex strings so they should be provided as individual queries and sent through
			String[] qs = query.startsWith("facet.") ? new String[]{query} : query.split("\\s*,\\s*");
			for (String q :qs) {
				if (!q.trim().isEmpty()) {
					if (variantsDB!=null) {
						String[] variants = variantsDB.get(q);
						if (variants==null) {
							queries.add(q);
						}
						else {
							queries.add("("+StringUtils.join(variants, "|")+")");
						}
					}
					else {
						queries.add(q);
					}
				}
			}
		}
		if (variantsDB!=null) {variantsDB.close();}
		return queries.toArray(new String[0]);
	}
	
	/**
	 * This should only be used during tool serialization to show messages from tools.
	 * @param tool
	 * @param writer
	 * @param context
	 */
	protected void writeMessages(HierarchicalStreamWriter writer, MarshallingContext context) {
		if (this.hasMessages()) {
			ToolSerializer.startNode(writer, "messages", List.class);
			context.convertAnother(this.getMessages());
			ToolSerializer.endNode(writer);
		}
	}
}
